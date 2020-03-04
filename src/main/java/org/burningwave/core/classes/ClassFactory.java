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

import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
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
import org.burningwave.core.function.TriFunction;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private Classes.Loaders classesLoaders;
	private JavaMemoryCompiler javaMemoryCompiler;
	private CodeGenerator codeGeneratorForPojo;
	private CodeGenerator codeGeneratorForExecutor;

	private TriFunction<String, String, Integer, Unit> codeGeneratorForConsumer = (packageName, functionalInterfaceName, parametersLength) -> {
		TypeDeclaration typeDeclaration = TypeDeclaration.create(functionalInterfaceName);
		Function applyMethod = Function.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		Function varArgsApplyMethod = Function.create("accept").setReturnType(
			void.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			Variable.create(TypeDeclaration.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsApplyMethod.addBodyCodeRow("accept(");
		Statement applyMethodCodeOne = Statement.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(Generic.create("P" + i));
			applyMethod.addParameter(Variable.create(TypeDeclaration.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsApplyMethod.addBodyElement(applyMethodCodeOne);
		varArgsApplyMethod.addBodyCode(");");
		Class cls = Class.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclaration.create(MultiParamsConsumer.class)
		).addMethod(
			applyMethod
		).addMethod(
			varArgsApplyMethod
		).addOuterCodeRow("@FunctionalInterface");
		return Unit.create(packageName).addClass(cls);
	};
	
	private TriFunction<String, String, Integer, Unit> codeGeneratorForPredicate = (packageName, functionalInterfaceName, parametersLength) -> {
		TypeDeclaration typeDeclaration = TypeDeclaration.create(functionalInterfaceName);
		Function applyMethod = Function.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
		Function varArgsApplyMethod = Function.create("test").setReturnType(
			boolean.class
		).addModifier(Modifier.PUBLIC).setDefault().addParameter(
			Variable.create(TypeDeclaration.create("Object..."), "params")
		).addOuterCodeRow("@Override");
		varArgsApplyMethod.addBodyCodeRow("return test(");
		Statement applyMethodCodeOne = Statement.createSimple().setBodyElementSeparator(", ");
		for (int i = 0; i < parametersLength; i++) {
			typeDeclaration.addGeneric(Generic.create("P" + i));
			applyMethod.addParameter(Variable.create(TypeDeclaration.create("P" + i), "p" + i));
			applyMethodCodeOne.addCode("(P" + i + ")params["+i+"]");
		}
		varArgsApplyMethod.addBodyElement(applyMethodCodeOne);
		varArgsApplyMethod.addBodyCode(");");
		Class cls = Class.createInterface(
			typeDeclaration
		).addModifier(
			Modifier.PUBLIC
		).expands(
			TypeDeclaration.create(MultiParamsPredicate.class)
		).addMethod(
			applyMethod
		).addMethod(
			varArgsApplyMethod
		).addOuterCodeRow("@FunctionalInterface");
		return Unit.create(packageName).addClass(cls);
	};
	
	private TriFunction<String, String, Integer, Unit> codeGeneratorForFunction = (packageName, functionalInterfaceName, parametersLength) -> {
		TypeDeclaration typeDeclaration = TypeDeclaration.create(functionalInterfaceName);
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
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {	
		this.sourceCodeHandler = sourceCodeHandler;
		this.classesLoaders = classesLoaders;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.codeGeneratorForPojo = codeGeneratorForPojo;
		this.codeGeneratorForExecutor = codeGeneratorForExecutor;
	}
	
	public static ClassFactory create(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {
		return new ClassFactory(
			sourceCodeHandler, classesLoaders,
			javaMemoryCompiler, pathHelper, codeGeneratorForPojo, 
			codeGeneratorForExecutor
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
	
	public java.lang.Class<?> getOrBuild(String className, Supplier<Unit> unitCode, ClassLoader classLoader) {
		java.lang.Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(className, unitCode, classLoader);
		}
		return toRet;
	}	
	
	private java.lang.Class<?> buildAndUploadTo(String className, Supplier<Unit> unitCodeSupplier, ClassLoader classLoader) {
		Unit unit = unitCodeSupplier.get();
		unit.getAllClasses().values().forEach(cls -> {
			cls.addConcretizedType(TypeDeclaration.create(Virtual.class));
		});
		Map<String, ByteBuffer> compiledFiles = build(unit.make());
		logInfo("Class " + className + " succesfully created");
		try {
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (ClassNotFoundException e) {
			throw Throwables.toRuntimeException(e);
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
		return getOrBuild(codeGeneratorForPojo.generate(className, superClasses), classLoader);
	}
	
	public java.lang.Class<?> getOrBuildFunctionSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "FunctionFor" + parametersLength +	"Parameters";
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForFunction.apply(packageName, functionalInterfaceName, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildConsumerSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "ConsumerFor" + parametersLength + "Parameters";
		String packageName = MultiParamsConsumer.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForConsumer.apply(packageName, functionalInterfaceName, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildPredicateSubType(ClassLoader classLoader, int parametersLength) {
		String functionalInterfaceName = "PredicateFor" + parametersLength + "Parameters";
		String packageName = MultiParamsPredicate.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		return getOrBuild(
			className,
			() -> codeGeneratorForPredicate.apply(packageName, functionalInterfaceName, parametersLength),
			classLoader
		);
	}
	
	public java.lang.Class<?> getOrBuildCodeExecutorSubType(String imports, String classSimpleName, String supplierCode, ComponentSupplier componentSupplier, ClassLoader classLoader) {
		String classCode = codeGeneratorForExecutor.generate(
			imports, classSimpleName, supplierCode
		);	
		try {
			return classesLoaders.loadOrUploadClass(sourceCodeHandler.extractClassName(classCode), build(classCode), classLoader);
		} catch (ClassNotFoundException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
}
