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

import static org.burningwave.core.assembler.StaticComponentsContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentsContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.classes.source.Class;
import org.burningwave.core.classes.source.Function;
import org.burningwave.core.classes.source.Generic;
import org.burningwave.core.classes.source.Statement;
import org.burningwave.core.classes.source.TypeDeclaration;
import org.burningwave.core.classes.source.Unit;
import org.burningwave.core.classes.source.Variable;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private Classes.Loaders classesLoaders;
	private JavaMemoryCompiler javaMemoryCompiler;
	
	private BiFunction<String, java.lang.Class<?>[], Unit> codeGeneratorForPojo = (className, superClasses) -> {
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Pojo could not be a inner class");
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		Class cls = Class.create(
			TypeDeclaration.create(classSimpleName)
		).addModifier(
			Modifier.PUBLIC
		);
		java.lang.Class<?> superClass = null;
		Collection<java.lang.Class<?>> interfaces = new LinkedHashSet<>();
		for (java.lang.Class<?> iteratedSuperClass : superClasses) {
			if (iteratedSuperClass.isInterface()) {
				cls.addConcretizedType(iteratedSuperClass);
				interfaces.add(iteratedSuperClass);
			} else if (superClass == null) {
				cls.expands(iteratedSuperClass);
				superClass = iteratedSuperClass;
			} else {
				throw Throwables.toRuntimeException(className + " Pojo could not extends more than one class");
			}
		}
		if (superClass != null) {
			for (Constructor<?> constructor : Classes.getDeclaredConstructors(superClass, constructor -> 
				Modifier.isPublic(constructor.getModifiers()) || 
				Modifier.isProtected(constructor.getModifiers()))
			) {
				Function ctor = Function.create(classSimpleName).addModifier(constructor.getModifiers());
				Collection<String> params = new ArrayList<>();
				for (Parameter paramType : constructor.getParameters()) {
					ctor.addParameter(
						Variable.create(TypeDeclaration.create(paramType.getType()), paramType.getName())
					);
					params.add(paramType.getName());
				}
				ctor.addBodyCodeRow("super(" + String.join(", ", params) + ");");
				cls.addConstructor(ctor);
			}
		}
		Map<String, Variable> fieldsMap = new HashMap<>();
		for (java.lang.Class<?> interf : interfaces) {
			for (Method method : Classes.getDeclaredMethods(interf, method -> 
				method.getName().startsWith("set") || method.getName().startsWith("get") || method.getName().startsWith("is")
			)) {
				Integer modifiers = method.getModifiers();
				if (Modifier.isAbstract(modifiers)) {
					modifiers ^= Modifier.ABSTRACT;
				}
				Function mth = Function.create(method.getName()).addModifier(modifiers);
				mth.setReturnType(method.getReturnType());
				if (method.getName().startsWith("set")) {
					String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst("set", ""));
					java.lang.Class<?> paramType = method.getParameters()[0].getType();
					fieldsMap.put(fieldName, Variable.create(paramType, fieldName));
					mth.addParameter(Variable.create(paramType, fieldName));
					mth.addBodyCodeRow("this." + fieldName + " = " + fieldName + ";");
				} else if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
					String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst("get", ""));
					fieldsMap.put(fieldName, Variable.create(method.getReturnType(), fieldName));
					mth.addBodyCodeRow("return this." + fieldName + ";");
				}
				cls.addMethod(mth);
			}
			fieldsMap.entrySet().forEach(entry -> {
				cls.addField(entry.getValue());
			});
		}
		return Unit.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Statement, Unit> codeGeneratorForExecutor = (className, statement) -> {
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " CodeExecutor could not be a inner class");
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		TypeDeclaration typeDeclaration = TypeDeclaration.create(classSimpleName);
		Generic returnType = Generic.create("T");
		Function executeMethod = Function.create("execute").setReturnType(
			returnType
		).addModifier(
			Modifier.PUBLIC
		).addParameter(
			Variable.create(
				TypeDeclaration.create(ComponentSupplier.class), "componentSupplier"
			)
		).addParameter(
			Variable.create(
				TypeDeclaration.create("Object... "), "objects"
			)
		).addOuterCodeRow("@Override").addBodyElement(statement);
		typeDeclaration.addGeneric(returnType);		
		Class cls = Class.create(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).addConcretizedType(
			CodeExecutor.class
		).addMethod(
			executeMethod
		);
		return Unit.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, Unit> codeGeneratorForConsumer = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Consumer could not be a inner class");
		}
		TypeDeclaration typeDeclaration = TypeDeclaration.create(classSimpleName);
		Function acceptMethod = Function.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		Function varArgsAcceptMethod = Function.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			Variable.create(TypeDeclaration.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsAcceptMethod.addBodyCodeRow("accept(");
		Statement applyMethodCodeOne = Statement.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(Generic.create("P" + i));
			acceptMethod.addParameter(Variable.create(TypeDeclaration.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsAcceptMethod.addBodyElement(applyMethodCodeOne);
		varArgsAcceptMethod.addBodyCode(");");
		Class cls = Class.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclaration.create(MultiParamsConsumer.class)
		).addMethod(
			acceptMethod
		).addMethod(
			varArgsAcceptMethod
		).addOuterCodeRow("@FunctionalInterface");
		return Unit.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, Unit> codeGeneratorForPredicate = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Predicate could not be a inner class");
		}
		TypeDeclaration typeDeclaration = TypeDeclaration.create(classSimpleName);
		Function testMethod = Function.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		Function varArgsTestMethod = Function.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			Variable.create(TypeDeclaration.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsTestMethod.addBodyCodeRow("return test(");
		Statement applyMethodCodeOne = Statement.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(Generic.create("P" + i));
			testMethod.addParameter(Variable.create(TypeDeclaration.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsTestMethod.addBodyElement(applyMethodCodeOne);
		varArgsTestMethod.addBodyCode(");");
		Class cls = Class.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclaration.create(MultiParamsPredicate.class)
		).addMethod(
			testMethod
		).addMethod(
			varArgsTestMethod
		).addOuterCodeRow("@FunctionalInterface");
		return Unit.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, Unit> codeGeneratorForFunction = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Function could not be a inner class");
		}
		TypeDeclaration typeDeclaration = TypeDeclaration.create(classSimpleName);
		Generic returnType = Generic.create("R");
		Function applyMethod = Function.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		Function varArgsApplyMethod = Function.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			Variable.create(TypeDeclaration.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsApplyMethod.addBodyCodeRow("return apply(");
		Statement applyMethodCodeOne = Statement.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(Generic.create("P" + i));
			applyMethod.addParameter(Variable.create(TypeDeclaration.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsApplyMethod.addBodyElement(applyMethodCodeOne);
		varArgsApplyMethod.addBodyCode(");");
		typeDeclaration.addGeneric(returnType);		
		Class cls = Class.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclaration.create(MultiParamsFunction.class).addGeneric(returnType)
		).addMethod(
			applyMethod
		).addMethod(
			varArgsApplyMethod
		).addOuterCodeRow("@FunctionalInterface");
		return Unit.create(packageName).addClass(cls);
	};
	
	private ClassFactory(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo
	) {	
		this.sourceCodeHandler = sourceCodeHandler;
		this.classesLoaders = classesLoaders;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
	}
	
	public static ClassFactory create(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo
	) {
		return new ClassFactory(
			sourceCodeHandler, classesLoaders,
			javaMemoryCompiler, pathHelper, codeGeneratorForPojo
		);
	}
	
	public Map<String, ByteBuffer> build(Collection<String> unitsCode) {
		return javaMemoryCompiler.compile(
			unitsCode, 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	public Map<String, ByteBuffer> build(String unitCode) {
		logInfo("Try to compile unit code:\n\n" + unitCode +"\n");
		return javaMemoryCompiler.compile(
			Arrays.asList(unitCode), 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	private java.lang.Class<?> buildAndUploadTo(String classCode, ClassLoader classLoader) {
		String className = sourceCodeHandler.extractClassName(classCode);
		Map<String, ByteBuffer> compiledFiles = build(classCode);
		logInfo("Class " + className + " succesfully created");
		try {
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (ClassNotFoundException e) {
			throw Throwables.toRuntimeException(e);
		}
	}
	
	public java.lang.Class<?> getOrBuild(String className, Unit unitCode, ClassLoader classLoader) {
		return getOrBuild(className, () -> unitCode, classLoader);
	}	
	
	public java.lang.Class<?> getOrBuild(String className, Supplier<Unit> unitCode, ClassLoader classLoader) {
		java.lang.Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(className, unitCode, classLoader);
		} else {
			logInfo("Class " + className + " succesfully retrieved");
		}
		return toRet;
	}	
	
	private java.lang.Class<?> buildAndUploadTo(String className, Supplier<Unit> unitCodeSupplier, ClassLoader classLoader) {
		Unit unit = unitCodeSupplier.get();
		unit.getAllClasses().values().forEach(cls -> {
			cls.addConcretizedType(TypeDeclaration.create(Virtual.class));
		});
		
		try {
			Map<String, ByteBuffer> compiledFiles = build(unit.make());
			logInfo("Class " + className + " succesfully created");
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}

	public java.lang.Class<?> getOrBuild(String classCode, ClassLoader classLoader) {
		String className = sourceCodeHandler.extractClassName(classCode);
		java.lang.Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classCode, classLoader);
		}
		return toRet;
	}	
	
	public java.lang.Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, java.lang.Class<?>... superClasses) {
		return getOrBuild(
			className, 
			() -> codeGeneratorForPojo.apply(className, superClasses),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildFunctionSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "FunctionFor" + parametersLength +	"Parameters";
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForFunction.apply(className, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildConsumerSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "ConsumerFor" + parametersLength + "Parameters";
		String packageName = MultiParamsConsumer.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForConsumer.apply(className, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildPredicateSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "PredicateFor" + parametersLength + "Parameters";
		String packageName = MultiParamsPredicate.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForPredicate.apply(className, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildCodeExecutorSubType(ClassLoader classLoader, String className, Statement statement) {
		return getOrBuild(
			className,
			() -> codeGeneratorForExecutor.apply(className, statement),
			classLoader
		);

	}
}
