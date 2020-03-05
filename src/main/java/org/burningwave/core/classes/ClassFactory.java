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
import java.lang.reflect.Executable;
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
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Virtual;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.function.MultiParamsConsumer;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.function.MultiParamsPredicate;
import org.burningwave.core.function.TriConsumer;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private Classes.Loaders classesLoaders;
	private JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	
	private BiFunction<String, StatementSourceGenerator, UnitSourceGenerator> codeGeneratorForExecutor = (className, statement) -> {
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " CodeExecutor could not be a inner class");
		}
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		GenericSourceGenerator returnType = GenericSourceGenerator.create("T");
		FunctionSourceGenerator executeMethod = FunctionSourceGenerator.create("execute").setReturnType(
			returnType
		).addModifier(
			Modifier.PUBLIC
		).addParameter(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create(ComponentSupplier.class), "componentSupplier"
			)
		).addParameter(
			VariableSourceGenerator.create(
				TypeDeclarationSourceGenerator.create("Object... "), "objects"
			)
		).addOuterCodeRow("@Override").addBodyElement(statement);
		typeDeclaration.addGeneric(returnType);		
		ClassSourceGenerator cls = ClassSourceGenerator.create(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).addConcretizedType(
			CodeExecutor.class
		).addMethod(
			executeMethod
		);
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, UnitSourceGenerator> codeGeneratorForConsumer = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Consumer could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		FunctionSourceGenerator acceptMethod = FunctionSourceGenerator.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsAcceptMethod = FunctionSourceGenerator.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsAcceptMethod.addBodyCodeRow("accept(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			acceptMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsAcceptMethod.addBodyElement(applyMethodCodeOne);
		varArgsAcceptMethod.addBodyCode(");");
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsConsumer.class)
		).addMethod(
			acceptMethod
		).addMethod(
			varArgsAcceptMethod
		).addOuterCodeRow("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, UnitSourceGenerator> codeGeneratorForPredicate = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Predicate could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		FunctionSourceGenerator testMethod = FunctionSourceGenerator.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsTestMethod = FunctionSourceGenerator.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsTestMethod.addBodyCodeRow("return test(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			testMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsTestMethod.addBodyElement(applyMethodCodeOne);
		varArgsTestMethod.addBodyCode(");");
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsPredicate.class)
		).addMethod(
			testMethod
		).addMethod(
			varArgsTestMethod
		).addOuterCodeRow("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	private BiFunction<String, Integer, UnitSourceGenerator> codeGeneratorForFunction = (className, parametersLength) -> {
		String packageName = Classes.retrievePackageName(className);
		String classSimpleName = Classes.retrieveSimpleName(className);
		if (className.contains("$")) {
			throw Throwables.toRuntimeException(className + " Function could not be a inner class");
		}
		TypeDeclarationSourceGenerator typeDeclaration = TypeDeclarationSourceGenerator.create(classSimpleName);
		GenericSourceGenerator returnType = GenericSourceGenerator.create("R");
		FunctionSourceGenerator applyMethod = FunctionSourceGenerator.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		FunctionSourceGenerator varArgsApplyMethod = FunctionSourceGenerator.create("apply").setReturnType(
			returnType
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsApplyMethod.addBodyCodeRow("return apply(");
		StatementSourceGenerator applyMethodCodeOne = StatementSourceGenerator.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(GenericSourceGenerator.create("P" + i));
			applyMethod.addParameter(VariableSourceGenerator.create(TypeDeclarationSourceGenerator.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsApplyMethod.addBodyElement(applyMethodCodeOne);
		varArgsApplyMethod.addBodyCode(");");
		typeDeclaration.addGeneric(returnType);		
		ClassSourceGenerator cls = ClassSourceGenerator.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclarationSourceGenerator.create(MultiParamsFunction.class).addGeneric(returnType)
		).addMethod(
			applyMethod
		).addMethod(
			varArgsApplyMethod
		).addOuterCodeRow("@FunctionalInterface");
		return UnitSourceGenerator.create(packageName).addClass(cls);
	};
	
	private ClassFactory(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper
	) {	
		this.sourceCodeHandler = sourceCodeHandler;
		this.classesLoaders = classesLoaders;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
	}
	
	public static ClassFactory create(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper
	) {
		return new ClassFactory(
			sourceCodeHandler, classesLoaders,
			javaMemoryCompiler, pathHelper
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
	
	public java.lang.Class<?> buildAndUploadTo(ClassLoader classLoader, String classCode) {
		try {
			String className = sourceCodeHandler.extractClassName(classCode);
			Map<String, ByteBuffer> compiledFiles = build(classCode);
			logInfo("Class " + className + " succesfully created");
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public java.lang.Class<?> getOrBuild(ClassLoader classLoader, String className, UnitSourceGenerator unitCode) {
		return getOrBuild(classLoader, className, () -> unitCode);
	}	
	
	public java.lang.Class<?> getOrBuild(ClassLoader classLoader, String className, Supplier<UnitSourceGenerator> unitCode) {
		java.lang.Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classLoader, className, unitCode);
		} else {
			logInfo("Class " + className + " succesfully retrieved");
		}
		return toRet;
	}	
	
	public java.lang.Class<?> buildAndUploadTo(ClassLoader classLoader, String className, Supplier<UnitSourceGenerator> unitCodeSupplier) {
		try {
			UnitSourceGenerator unit = unitCodeSupplier.get();
			unit.getAllClasses().values().forEach(cls -> {
				cls.addConcretizedType(TypeDeclarationSourceGenerator.create(Virtual.class));
			});
			Map<String, ByteBuffer> compiledFiles = build(unit.make());
			logInfo("Class " + className + " succesfully created");
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}

	public java.lang.Class<?> getOrBuild(ClassLoader classLoader, String classCode) {
		String className = sourceCodeHandler.extractClassName(classCode);
		java.lang.Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classLoader, classCode);
		}
		return toRet;
	}	
	
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSubTypeRetriever.SourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	public java.lang.Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, java.lang.Class<?>... superClasses) {
		return pojoSubTypeRetriever.getOrBuild(classLoader, className, PojoSubTypeRetriever.SourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	public java.lang.Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, int options, java.lang.Class<?>... superClasses) {
		return pojoSubTypeRetriever.getOrBuild(classLoader, className, options, superClasses);
	}
	
	public java.lang.Class<?> getOrBuildFunctionSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "FunctionFor" + parametersLength +	"Parameters";
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> codeGeneratorForFunction.apply(className, parametersLength)
		);
	}
	
	public java.lang.Class<?> getOrBuildConsumerSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "ConsumerFor" + parametersLength + "Parameters";
		String packageName = MultiParamsConsumer.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> codeGeneratorForConsumer.apply(className, parametersLength)
		);
	}
	
	public java.lang.Class<?> getOrBuildPredicateSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "PredicateFor" + parametersLength + "Parameters";
		String packageName = MultiParamsPredicate.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			classLoader,
			className,
			() -> codeGeneratorForPredicate.apply(className, parametersLength)
		);
	}
	
	public java.lang.Class<?> getOrBuildCodeExecutorSubType(ClassLoader classLoader, String className, StatementSourceGenerator statement) {
		return getOrBuild(
			classLoader,
			className,
			() -> codeGeneratorForExecutor.apply(className, statement)
		);

	}
	
	public static class PojoSubTypeRetriever {
		private ClassFactory classFactory;
		private SourceGenerator sourceGenerator;
		
		private PojoSubTypeRetriever(
			ClassFactory classFactory,
			SourceGenerator sourceGenerator
		) {
			this.classFactory = classFactory;
			this.sourceGenerator = sourceGenerator;
		}
		
		public static PojoSubTypeRetriever create(ClassFactory classFactory, SourceGenerator sourceGenerator) {
			return new PojoSubTypeRetriever(classFactory, sourceGenerator) ;
		}

		public static PojoSubTypeRetriever createDefault(ClassFactory classFactory) {
			return new PojoSubTypeRetriever(classFactory, new SourceGenerator((fieldsMap, cls) -> {
				fieldsMap.entrySet().forEach(entry -> {
					cls.addField(entry.getValue().addModifier(Modifier.PRIVATE));
				});
			}, (method, fieldName) -> 
				method.addBodyCodeRow("this." + fieldName + " = " + fieldName + ";"), (method, fieldName) -> 
					method.addBodyCodeRow("return this." + fieldName + ";"), null
			));
		}
		
		public java.lang.Class<?> getOrBuild(
				ClassLoader classLoader,
			String className,
			java.lang.Class<?>... superClasses
		) {
			return getOrBuild(classLoader, className, SourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
		}	
		
		public java.lang.Class<?> getOrBuild(
			ClassLoader classLoader,
			String className,
			int options, 
			java.lang.Class<?>... superClasses
		) {
			return classFactory.getOrBuild(
				classLoader, 
				className,
				() -> sourceGenerator.make(className, options, superClasses)
			);
		}		
		
		public static class SourceGenerator {
			public static int ALL_OPTIONS_DISABLED = 0b00000000;
			public static int BUILDING_METHODS_CREATION_ENABLED = 0b00000001;
			public static int USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED = 0b00000010;
			
			private BiConsumer<Map<String, VariableSourceGenerator>, ClassSourceGenerator> fieldsBuilder;
			private BiConsumer<FunctionSourceGenerator, String> setterMethodsBodyBuilder;
			private BiConsumer<FunctionSourceGenerator, String> getterMethodsBodyBuilder;
			private TriConsumer<UnitSourceGenerator, java.lang.Class<?>, Collection<java.lang.Class<?>>> extraElementsBuilder;
			
			private SourceGenerator(
				BiConsumer<Map<String, VariableSourceGenerator>, ClassSourceGenerator> fieldsBuilder,
				BiConsumer<FunctionSourceGenerator, String> setterMethodsBodyBuilder,
				BiConsumer<FunctionSourceGenerator, String> getterMethodsBodyBuilder,
				TriConsumer<UnitSourceGenerator, java.lang.Class<?>, Collection<java.lang.Class<?>>> extraElementsBuilder
			) {
				this.fieldsBuilder = fieldsBuilder;
				this.setterMethodsBodyBuilder = setterMethodsBodyBuilder;
				this.getterMethodsBodyBuilder = getterMethodsBodyBuilder;
				this.extraElementsBuilder = extraElementsBuilder;
			}
			
			public static SourceGenerator createDefault() {
				return new SourceGenerator((fieldsMap, cls) -> {
					fieldsMap.entrySet().forEach(entry -> {
						cls.addField(entry.getValue().addModifier(Modifier.PRIVATE));
					});
				}, (method, fieldName) -> 
					method.addBodyCodeRow("this." + fieldName + " = " + fieldName + ";"), (method, fieldName) -> 
						method.addBodyCodeRow("return this." + fieldName + ";"), null
				);
			}
			
			public SourceGenerator setFieldsBuilder(BiConsumer<Map<String, VariableSourceGenerator>, ClassSourceGenerator> fieldsBuilder) {
				this.fieldsBuilder = fieldsBuilder;
				return this;
			}

			public SourceGenerator setSetterMethodsBodyBuilder(BiConsumer<FunctionSourceGenerator, String> setterMethodsBodyBuilder) {
				this.setterMethodsBodyBuilder = setterMethodsBodyBuilder;
				return this;
			}

			public SourceGenerator setGetterMethodsBodyBuilder(BiConsumer<FunctionSourceGenerator, String> getterMethodsBodyBuilder) {
				this.getterMethodsBodyBuilder = getterMethodsBodyBuilder;
				return this;
			}

			public SourceGenerator setExtraElementsBuilder(
					TriConsumer<UnitSourceGenerator, java.lang.Class<?>, Collection<java.lang.Class<?>>> extraElementsBuilder) {
				this.extraElementsBuilder = extraElementsBuilder;
				return this;
			}
			
			public UnitSourceGenerator make(String className, int options, java.lang.Class<?>... superClasses) {
				if (className.contains("$")) {
					throw Throwables.toRuntimeException(className + " Pojo could not be a inner class");
				}
				String packageName = Classes.retrievePackageName(className);
				String classSimpleName = Classes.retrieveSimpleName(className);
				ClassSourceGenerator cls = ClassSourceGenerator.create(
					TypeDeclarationSourceGenerator.create(classSimpleName)
				).addModifier(
					Modifier.PUBLIC
				);
				java.lang.Class<?> superClass = null;
				Collection<java.lang.Class<?>> interfaces = new LinkedHashSet<>();
				for (java.lang.Class<?> iteratedSuperClass : superClasses) {
					if (iteratedSuperClass.isInterface()) {
						cls.addConcretizedType(createTypeDeclaration((options & USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED) != 0, iteratedSuperClass));
						interfaces.add(iteratedSuperClass);
					} else if (superClass == null) {
						cls.expands(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), iteratedSuperClass));
						superClass = iteratedSuperClass;
					} else {
						throw Throwables.toRuntimeException(className + " Pojo could not extends more than one class");
					}
				}
				if (superClass != null) {
					String superClassPackage = Optional.ofNullable(superClass.getPackage()).map(pckg -> pckg.getName()).orElseGet(() -> "");
					Predicate<Executable> modifierTester = 
						Strings.areEquals(packageName, superClassPackage) ?
							executable ->
								!Modifier.isPrivate(executable.getModifiers()) :
							executable ->
								Modifier.isPublic(executable.getModifiers()) ||
								Modifier.isProtected(executable.getModifiers());						
					for (Constructor<?> constructor : Classes.getDeclaredConstructors(superClass, constructor -> 
						modifierTester.test(constructor))
					) {
						Integer modifiers = constructor.getModifiers();
						if (isBuildingMethodsCreationEnabled(options)) {
							if (Modifier.isPublic(modifiers)) {
								modifiers ^= Modifier.PUBLIC;
							}
						}
						cls.addConstructor(
							create(
								classSimpleName, constructor, modifiers, (funct, params) ->
								funct.addBodyCodeRow("super(" + String.join(", ", params) + ");"),
								isUseFullyQualifiedClassNamesEnabled(options)
							)
						);
						if (isBuildingMethodsCreationEnabled(options)) {
							cls.addMethod(
								create(
									"create", constructor, modifiers, (funct, params) ->
										funct.addBodyCodeRow("return new " + classSimpleName + "(" + String.join(", ", params) + ");"),
									isUseFullyQualifiedClassNamesEnabled(options)
								).addModifier(Modifier.STATIC | Modifier.PUBLIC).setReturnType(classSimpleName)
							);
						}
					}
				}
				Map<String, VariableSourceGenerator> fieldsMap = new HashMap<>();
				for (java.lang.Class<?> interf : interfaces) {
					for (Method method : Classes.getDeclaredMethods(interf, method -> 
						method.getName().startsWith("set") || method.getName().startsWith("get") || method.getName().startsWith("is")
					)) {
						Integer modifiers = method.getModifiers();
						if (Modifier.isAbstract(modifiers)) {
							modifiers ^= Modifier.ABSTRACT;
						}
						FunctionSourceGenerator mth = FunctionSourceGenerator.create(method.getName()).addModifier(modifiers);
						mth.setReturnType(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), method.getReturnType()));
						if (method.getName().startsWith("set")) {
							String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst("set", ""));
							java.lang.Class<?> paramType = method.getParameters()[0].getType();
							fieldsMap.put(fieldName, VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), paramType), fieldName));
							mth.addParameter(VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), paramType), fieldName));
							if (setterMethodsBodyBuilder != null) {
								setterMethodsBodyBuilder.accept(mth, fieldName);
							}
						} else if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
							String prefix = method.getName().startsWith("get")? "get" : "is";
							String fieldName = Strings.lowerCaseFirstCharacter(method.getName().replaceFirst(prefix, ""));
							fieldsMap.put(fieldName, VariableSourceGenerator.create(createTypeDeclaration(isUseFullyQualifiedClassNamesEnabled(options), method.getReturnType()), fieldName));
							if (getterMethodsBodyBuilder != null) {
								getterMethodsBodyBuilder.accept(mth, fieldName);
							}
						}
						cls.addMethod(mth);
					}
					if (fieldsBuilder != null) {
						fieldsBuilder.accept(fieldsMap, cls);
					}
				}
				UnitSourceGenerator unit = UnitSourceGenerator.create(packageName).addClass(cls);
				if (extraElementsBuilder != null) {
					extraElementsBuilder.accept(unit, superClass, interfaces);
				}
				return unit;
			}
			
			private boolean isUseFullyQualifiedClassNamesEnabled(int options) {
				return (options & USE_OF_FULLY_QUALIFIED_CLASS_NAMES_ENABLED) != 0;
			}
			
			private boolean isBuildingMethodsCreationEnabled(int options) {
				return (options & BUILDING_METHODS_CREATION_ENABLED) != 0;
			}
			
			protected TypeDeclarationSourceGenerator createTypeDeclaration(boolean useFullyQualifiedNames,
					java.lang.Class<?> cls) {
				if (useFullyQualifiedNames) {
					return TypeDeclarationSourceGenerator.create(cls.getName().replace("$", "."));
				} else {
					return TypeDeclarationSourceGenerator.create(cls);
				}
			};
			
			private FunctionSourceGenerator create(
				String functionName,
				java.lang.reflect.Executable executable,
				Integer modifiers,
				BiConsumer<FunctionSourceGenerator, Collection<String>> bodyBuilder,
				boolean useFullyQualifiedNames
			) {
				FunctionSourceGenerator function = FunctionSourceGenerator.create(functionName);
				Collection<String> params = new ArrayList<>();
				for (Parameter paramType : executable.getParameters()) {
					function.addParameter(
						VariableSourceGenerator.create(createTypeDeclaration(useFullyQualifiedNames, paramType.getType()), paramType.getName())
					);
					params.add(paramType.getName());
				}
				bodyBuilder.accept(function, params);
				return function;
			}
		}
	}
}
