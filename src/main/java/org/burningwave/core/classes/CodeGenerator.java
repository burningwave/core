/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.Strings;
import org.burningwave.core.Virtual;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;
import org.burningwave.core.io.StreamHelper;

public abstract class CodeGenerator implements Component {
	protected static Pattern METHOD_INPUTS_PATTERN = Pattern.compile("\\(([\\w\\d,\\.]*)");
	protected static Pattern METHOD_NAME_PATTERN = Pattern.compile("([\\w\\.\\$]+)\\(");
	protected static Pattern METHOD_RETURN_PATTERN = Pattern.compile("([\\w\\.\\$\\<\\>]+)\\s+[\\w\\.\\$]+\\(");
	protected static Pattern METHOD_MODIFIERS_PATTERN = Pattern.compile("([\\w\\s]+)\\s+[\\w\\.\\$\\<\\>]+\\s+[\\w\\.\\$]+\\(");

	protected MemberFinder memberFinder;
	protected StreamHelper streamHelper;
	protected String BASE_PACKAGE_NAME_FOR_TEMPLATE = this.getClass().getPackage().getName().substring(0, this.getClass().getPackage().getName().lastIndexOf("."));
	protected String TEMPLATE;
	protected String PACKAGE_NAME;	

	
	protected CodeGenerator(MemberFinder memberFinder, StreamHelper pathHelper) {
		this.memberFinder = memberFinder;
		this.streamHelper = pathHelper;
	}
	
	String getImports() {
		StringBuffer imports = new StringBuffer();
		imports.append("import " + Virtual.class.getName() + ";");
		return imports.toString();
	}
	
	String readTemplate(String fileRelPath) {
		return streamHelper.getResourceAsStringBuffer(fileRelPath).toString();
	}

	
	protected String generateCommonImports(Class<?>... classes) {
		StringBuffer imports = new StringBuffer();
		Stream.of(classes).forEach(cls ->
			imports.append(("import " + cls.getName() + ";\n").replaceAll("\\$", "."))
		);
		return imports.toString();
	}
	
	protected String generateConstructors(String packageName, String classSimpleName, Class<?> superClass) {
		StringBuffer constructorsAsString = new StringBuffer();
		Collection<Constructor<?>> ctors = memberFinder.findAll(
			ConstructorCriteria.byScanUpTo(superClass).member(member ->
				Modifier.isPublic(member.getModifiers()) || 
				Modifier.isProtected(member.getModifiers()) ||
				(packageName.equals(Optional.ofNullable(superClass.getPackage()).map(pkg -> pkg.getName()).orElse(null)) && member.getModifiers() == 0)
			),
			superClass
		);
		for (Constructor<?> ctor : ctors) {
			String innerCode = "super(";
			String superClassName = superClass.getName().replaceAll("\\$", "\\\\\\$");
			String ctorAsString = ctor.toString().replaceAll(superClassName, classSimpleName).replaceAll("\\$", ".");
			String[] ctorAsStringArray = ctorAsString.split(",");
			if (ctorAsStringArray .length > 1) {
				String varLabel = "value";
				ctorAsString = "";
				for (int i = 0; i < ctorAsStringArray.length; i++) {
					if ((i < ctorAsStringArray.length -1)) {
						ctorAsString += ctorAsStringArray[i] + " " + varLabel + i + ", ";
						innerCode += varLabel + i + ", ";
					} else {
						ctorAsString += ctorAsStringArray[i].replace(")", " " + varLabel +  + i + ")");
						innerCode += varLabel + i;
					}
				}
			} else if (!ctorAsString.endsWith("()")) {
				if (!(!Modifier.isStatic(superClass.getModifiers()) && superClass.getEnclosingClass() != null && 
					superClass.getEnclosingClass().getName().equals(
						packageName + "." + ctorAsString.substring(ctorAsString.indexOf("(")+1, ctorAsString.indexOf(")"))
					)
										
				)) {
					ctorAsString = ctorAsString.replace(")", " value)");
					innerCode += "value";
				} else {
					ctorAsString = "";
				}
								
			}
			innerCode += ");";
			ctorAsString = !ctorAsString.isEmpty() ?
				"\t" + ctorAsString + "{\n"	+
				"\t\t" + innerCode +"\n" + 
				"\t}\n\n" : "";
			constructorsAsString.append(ctorAsString);
		}
		return constructorsAsString.toString();
	}
	
	protected Object generateCreateMethods(String contstuctors) {
		String ctorName = Strings.extractAllGroups(METHOD_NAME_PATTERN, contstuctors).get(1).get(0);
		String createMethods = contstuctors.replaceAll("protected|private", "public").replaceAll(ctorName, "static " + ctorName + " create").replaceAll("super", "return new " + ctorName);
		return createMethods;
	}
	
	public abstract String generate(Object... obj);
	
	public static class ForPojo extends CodeGenerator {
		protected Pattern METHOD_NAME_AND_INPUT_PATTERN_WITHOUT_LAST_BRACKET = Pattern.compile("(\\w*\\(.*)\\)");
		protected Pattern PROPERTY_NAME_PATTERN_FOR_GET = Pattern.compile("get(\\w*)\\(");
		protected Pattern PROPERTY_NAME_PATTERN_FOR_SET = Pattern.compile("set(\\w*)\\(");
		protected Pattern PROPERTY_NAME_PATTERN_FOR_IS = Pattern.compile("is(\\w*)\\(");

		
		protected ForPojo(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			super(memberFinder, streamHelper);
		}
		
		public static ForPojo create(MemberFinder memberFinder, StreamHelper streamHelper) {
			return new ForPojo(memberFinder, streamHelper);
		}
		
		private Map<Class<?>, ?> retrieveParameters(Object... objs) {
			String className = null;	
			List<Class<?>> interfaces = null;
			Class<?> superClass = null;
			Map<Class<?>, Object> parameters = new LinkedHashMap<>();
			for (Object obj : objs) {
				if (obj instanceof String) {
					className = (String)obj;
				}
				if (obj instanceof Class<?>[] && ((Class<?>[])obj).length > 0 && ((Class<?>[])obj)[0] != null) {
					Class<?>[] classes = (Class<?>[])obj;
					for (Class<?> cls : classes) {
						if (!cls.isInterface() && superClass == null) {
							superClass = cls;
						} else if (cls.isInterface() && interfaces != null) {
							interfaces.add(cls);
						} else if (cls.isInterface() && interfaces == null) {
							interfaces = new ArrayList<>();
							interfaces.add(cls);
						} else if (!superClass.equals(cls)) {
							throw Throwables.toRuntimeException("Can't extended more than one class. found " + superClass.getName() + " and " + cls.getName());
						}
					}
				}
				if (obj instanceof Class<?> && !((Class<?>)obj).isInterface()) {
					superClass = (Class<?>)obj;
				}
			}
			parameters.put(String.class, className);
			parameters.put(List.class, interfaces);
			parameters.put(Class.class, superClass);
			return parameters;
		}
		
		//Parameter[0] = class name
		//Parameter[1] = List of interfaces to be implemented + one class to be extended
		@SuppressWarnings("unchecked")
		@Override
		public String generate(Object... objs) {
			Map<Class<?>, ?> parameters = retrieveParameters(objs);
			String className = ((String)parameters.get(String.class)).replaceAll("\\$", ".");	
			List<Class<?>> interfaces = (List<Class<?>>)parameters.get(List.class);
			Class<?> superClass = (Class<?>)parameters.get(Class.class);
			
			String packageName = className.substring(0, className.lastIndexOf("."));
			String classSimpleName = className.substring(className.lastIndexOf(".") + 1, className.length());
			
			StringBuffer classAsString = new StringBuffer();
			classAsString.append("package " + packageName + ";\n\n\n");
			List<Class<?>> classes = new ArrayList<>();
			classes.add(Virtual.class);
			if (interfaces != null && interfaces.size() > 0) {
				classes.addAll(interfaces);
			}		
			if (superClass != null) {
				classes.add(superClass);
			}
			classAsString.append(generateCommonImports(classes.toArray(new Class<?>[classes.size()])) + "\n\n");
			String interfacesSimpleName = "";
			if (interfaces != null && interfaces.size() > 0) {
				for (Class<?> cls : interfaces) {
					interfacesSimpleName += ", " + cls.getSimpleName();
				}
			}
			classAsString.append(
					"public class " + classSimpleName + 
						(superClass != null ?" extends " + superClass.getSimpleName() : "") +
					" implements " + Virtual.class.getSimpleName() + interfacesSimpleName +
					" {\n\n");
			StringBuffer fieldsAsString = new StringBuffer();
			StringBuffer methodsAsString = new StringBuffer();
			if (interfaces != null) {				
				for (Class<?> interf : interfaces) {
					String[] fieldsAndMethods = generateMethodsAndFields(interf);
					fieldsAsString.append(fieldsAndMethods[0]);
					methodsAsString.append(fieldsAndMethods[1]);
				}
			}
			StringBuffer constructorsAsString = new StringBuffer();
			if (superClass != null) {
				constructorsAsString.append(generateConstructors(packageName, classSimpleName, superClass));
			}
			classAsString.append(fieldsAsString);
			classAsString.append(constructorsAsString);
			classAsString.append(methodsAsString);
			classAsString.append("}");
			return classAsString.toString();
		}

		protected String[] generateMethodsAndFields(Class<?> interf) {
			StringBuffer methodsAsString = new StringBuffer();
			StringBuffer fieldsAsString = new StringBuffer();
			Map<String, String> fields = new LinkedHashMap<>();
			memberFinder.findAll(
				MethodCriteria.forName(name ->
					name.startsWith("set") || name.startsWith("get") || name.startsWith("is")
				),
				interf
			)
			.forEach(method -> {
				String methodAsString = method.toString().replaceAll("\\$", ".");
				if (method.getName().startsWith("get")) {
					fields.put(
						Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_GET, methodAsString).get(1).get(0)),
						Strings.extractAllGroups(METHOD_RETURN_PATTERN, methodAsString).get(1).get(0)
					);
					methodsAsString.append(generateGetter(methodAsString));
				} else if (method.getName().startsWith("set")) {
					fields.put(
						Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_SET, methodAsString).get(1).get(0)), 
						Strings.extractAllGroups(METHOD_INPUTS_PATTERN, methodAsString).get(1).get(0)
					);
					methodsAsString.append(generateSetter(methodAsString));
				} else if (method.getName().startsWith("is")) {
					fields.put(
						Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_IS, methodAsString).get(1).get(0)), 
						Strings.extractAllGroups(METHOD_RETURN_PATTERN, methodAsString).get(1).get(0)
					);
					methodsAsString.append(generateChecker(methodAsString));
				}
			});
			fields.forEach((fieldName, fieldType) ->
				fieldsAsString.append("\tprivate " + fieldType + " " + fieldName + ";\n")
			);
			return new String[] {fieldsAsString.toString()+"\n", methodsAsString.toString()};
		}


		protected String generateGetter(String methodAsString) {
			return "\tpublic " + Strings.extractAllGroups(METHOD_RETURN_PATTERN, methodAsString).get(1).get(0) + " "
					+ Strings.extractAllGroups(METHOD_NAME_AND_INPUT_PATTERN_WITHOUT_LAST_BRACKET, methodAsString).get(1).get(0) + ") {\n"
					+ "\t\treturn this."
					+ Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_GET, methodAsString).get(1).get(0)) + ";\n" + "\t}\n\n";
		}
		
		protected String generateChecker(String methodAsString) {
			return "\tpublic " + Strings.extractAllGroups(METHOD_RETURN_PATTERN, methodAsString).get(1).get(0) + " "
					+ Strings.extractAllGroups(METHOD_NAME_AND_INPUT_PATTERN_WITHOUT_LAST_BRACKET, methodAsString).get(1).get(0) + ") {\n"
					+ "\t\treturn this."
					+ Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_IS, methodAsString).get(1).get(0)) + ";" + "\t}\n\n";
		}

		protected Object generateSetter(String methodAsString) {
			return "\tpublic " + Strings.extractAllGroups(METHOD_RETURN_PATTERN, methodAsString).get(1).get(0) + " "
					+ Strings.extractAllGroups(METHOD_NAME_AND_INPUT_PATTERN_WITHOUT_LAST_BRACKET, methodAsString).get(1).get(0) + " value) {\n"
					+ "\t\tthis." + Strings.lowerCaseFirstCharacter(Strings.extractAllGroups(PROPERTY_NAME_PATTERN_FOR_SET, methodAsString).get(1).get(0)) + " = value" + ";\n"
					+ "\t}\n\n";
		}
	}
	
	
	public static class ForFunction extends CodeGenerator {
			
		private ForFunction(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			super(memberFinder, streamHelper);
			PACKAGE_NAME = BASE_PACKAGE_NAME_FOR_TEMPLATE + ".function";
			TEMPLATE = readTemplate(PACKAGE_NAME.replaceAll("\\.", "/") + "/MultiParameterFunction.jt");
		}
		
		public static ForFunction create(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			return new ForFunction(memberFinder, streamHelper);
		}
		
		public String generate(Object... objs) {
			int paramsLength = (Integer)objs[0];
			String[] generics = new String[paramsLength];
			String[] params = new String[paramsLength];
			String[] genericParams = new String[paramsLength];
			String[] genericParamsCasted = new String[paramsLength];
			for (int i = 0; i < generics.length; i++) {
				generics[i] = "P" + i;
				params[i] = "p" + i;
				genericParams[i] = generics[i] + " " + params[i];
				genericParamsCasted[i] = "(" + generics[i] + ")params[" + i + "]"; 
			}
						
			String className = "FunctionFor" + paramsLength + "Parameters";
			String returnGenericType_01 = "R";
			String genericsAsString_01 = String.join(", ", String.join(", ", generics),returnGenericType_01);
			String genericParamsAsString_01 = String.join(", ", genericParams);
			String genericParamsCastedAsString_01 = String.join(", ", genericParamsCasted);
			
			Map<String, String> map = new LinkedHashMap<>();
			map.put("${packageName}", PACKAGE_NAME);
			map.put("${imports}", "import " + MultiParamsFunction.class.getName() + ";");
			map.put("${className}", className);
			map.put("${returnGenericType_01}", returnGenericType_01);
			map.put("${generics_01}", genericsAsString_01);
			map.put("${genericParamsCasted_01}", genericParamsCastedAsString_01);
			map.put("${genericParams_01}", genericParamsAsString_01);			
			
			return Strings.replace(TEMPLATE, map);
		}
	}
	
	public static class ForConsumer extends CodeGenerator {
	
		private ForConsumer(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			super(memberFinder, streamHelper);
			PACKAGE_NAME = BASE_PACKAGE_NAME_FOR_TEMPLATE + ".function";
			TEMPLATE = readTemplate(PACKAGE_NAME.replaceAll("\\.", "/") + "/MultiParameterConsumer.jt");
		}
		
		public static ForConsumer create(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			return new ForConsumer(memberFinder, streamHelper);
		}
		
		public String generate(Object... objs) {
			int paramsLength = (Integer)objs[0];
			String[] generics = new String[paramsLength];
			String[] params = new String[paramsLength];
			String[] genericParams = new String[paramsLength];
			String[] genericParamsCasted = new String[paramsLength];
			for (int i = 0; i < generics.length; i++) {
				generics[i] = "P" + i;
				params[i] = "p" + i;
				genericParams[i] = generics[i] + " " + params[i];
				genericParamsCasted[i] = "(" + generics[i] + ")params[" + i + "]";
			}
						
			String className = "ConsumerFor" + paramsLength + "Parameters";
			String genericsAsString_01 = String.join(", ", generics);		
			String genericParamsAsString_01 = String.join(", ", genericParams);
			String genericParamsCastedAsString_01 = String.join(", ", genericParamsCasted);
		
			Map<String, String> map = new LinkedHashMap<>();
			map.put("${packageName}", PACKAGE_NAME);
			map.put("${imports}", "import " + MultiParamsConsumer.class.getName() + ";");
			map.put("${className}", className);
			map.put("${generics_01}", genericsAsString_01);
			map.put("${genericParams_01}", genericParamsAsString_01);
			map.put("${genericParamsCasted_01}", genericParamsCastedAsString_01);			
			
			return Strings.replace(TEMPLATE, map);
		}
	}
	
	
	public static class ForPredicate extends CodeGenerator {
				
		private ForPredicate(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			super(memberFinder, streamHelper);
			PACKAGE_NAME = BASE_PACKAGE_NAME_FOR_TEMPLATE + ".function";
			TEMPLATE = readTemplate(PACKAGE_NAME.replaceAll("\\.", "/") + "/MultiParameterPredicate.jt");
		}
		
		public static ForPredicate create(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			return new ForPredicate(memberFinder, streamHelper);
		}
		
		public String generate(Object... objs) {
			int paramsLength = (Integer)objs[0];
			String[] generics = new String[paramsLength];
			String[] params = new String[paramsLength];
			String[] genericParams = new String[paramsLength];
			String[] genericParamsCasted = new String[paramsLength];
			for (int i = 0; i < generics.length; i++) {
				generics[i] = "P" + i;
				params[i] = "p" + i;
				genericParams[i] = generics[i] + " " + params[i];
				genericParamsCasted[i] = "(" + generics[i] + ")params[" + i + "]";
			}
						
			String className = "PredicateFor" + paramsLength + "Parameters";
			String genericsAsString_01 = String.join(", ", generics);		
			String genericParamsAsString_01 = String.join(", ", genericParams);
			String genericParamsCastedAsString_01 = String.join(", ", genericParamsCasted);
		
			Map<String, String> map = new LinkedHashMap<>();
			map.put("${packageName}", PACKAGE_NAME);
			map.put("${imports}", "import " + MultiParamsPredicate.class.getName() + ";");
			map.put("${className}", className);
			map.put("${generics_01}", genericsAsString_01);
			map.put("${genericParams_01}", genericParamsAsString_01);
			map.put("${genericParamsCasted_01}", genericParamsCastedAsString_01);			
			
			return Strings.replace(TEMPLATE, map);
		}
	}
	
	
	public static class ForCodeExecutor extends CodeGenerator {
		
		private ForCodeExecutor(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			super(memberFinder, streamHelper);
			PACKAGE_NAME = BASE_PACKAGE_NAME_FOR_TEMPLATE + ".classes";
			TEMPLATE = readTemplate(PACKAGE_NAME.replaceAll("\\.", "/") + "/CodeExecutor.jt");
		}
		
		public static ForCodeExecutor create(
				MemberFinder memberFinder,
				StreamHelper streamHelper) {
			return new ForCodeExecutor(memberFinder, streamHelper);
		}
		
		public String generate(Object... objs) {
			Map<String, String> map = new LinkedHashMap<>();
			map.put("${packageName}", PACKAGE_NAME);
			map.put("${imports}", (String)objs[0] + getImports() + "\n");
			map.put("${className}", (String)objs[1]);
			map.put("${code}", (String)objs[2]);
			//map.put("${returnType}",((Class<?>)objs[3]).getSimpleName());
			return Strings.replace(TEMPLATE, map);
		}
	}
}
