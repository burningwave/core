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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private SourceCodeHandler sourceCodeHandler;
	private PathHelper pathHelper;
	private Classes.Loaders classesLoaders;
	private JavaMemoryCompiler javaMemoryCompiler;
	private CodeGenerator codeGeneratorForPojo;
	private CodeGenerator codeGeneratorForConsumer;
	private CodeGenerator codeGeneratorForFunction;
	private CodeGenerator codeGeneratorForPredicate;
	private CodeGenerator codeGeneratorForExecutor;
	
	private ClassFactory(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForFunction codeGeneratorForFunction,
		CodeGenerator.ForConsumer codeGeneratorForConsumer,
		CodeGenerator.ForPredicate codeGeneratorForPredicate,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {	
		this.sourceCodeHandler = sourceCodeHandler;
		this.classesLoaders = classesLoaders;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.codeGeneratorForPojo = codeGeneratorForPojo;
		this.codeGeneratorForConsumer = codeGeneratorForConsumer;
		this.codeGeneratorForFunction = codeGeneratorForFunction;
		this.codeGeneratorForPredicate = codeGeneratorForPredicate;
		this.codeGeneratorForExecutor = codeGeneratorForExecutor;
	}
	
	public static ClassFactory create(
		SourceCodeHandler sourceCodeHandler,
		Classes.Loaders classesLoaders,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForFunction codeGeneratorForFunction,
		CodeGenerator.ForConsumer codeGeneratorForConsumer,
		CodeGenerator.ForPredicate codeGeneratorForPredicate,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {
		return new ClassFactory(
			sourceCodeHandler, classesLoaders,
			javaMemoryCompiler, pathHelper, codeGeneratorForPojo, 
			codeGeneratorForFunction, codeGeneratorForConsumer, codeGeneratorForPredicate, codeGeneratorForExecutor
		);
	}
	
	public Map<String, ByteBuffer> build(String classCode) {
		logInfo("Try to compile virtual class:\n\n" + classCode +"\n");
		return javaMemoryCompiler.compile(
			Arrays.asList(classCode), 
			pathHelper.getPaths(PathHelper.MAIN_CLASS_PATHS, PathHelper.MAIN_CLASS_PATHS_EXTENSION),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	private Class<?> buildAndUploadTo(String classCode, ClassLoader classLoader) {
		String className = sourceCodeHandler.extractClassName(classCode);
		Map<String, ByteBuffer> compiledFiles = build(classCode);
		logInfo("Virtual class " + className + " succesfully created");
		try {
			return classesLoaders.loadOrUploadClass(className, compiledFiles, classLoader);
		} catch (ClassNotFoundException e) {
			throw Throwables.toRuntimeException(e);
		}
	}
	
	
	public Class<?> getOrBuild(String classCode, ClassLoader classLoader) {
		String className = sourceCodeHandler.extractClassName(classCode);
		Class<?> toRet = classesLoaders.retrieveLoadedClass(classLoader, className);
		if (toRet == null) {
			toRet = buildAndUploadTo(classCode, classLoader);
		}
		return toRet;
	}	
	
	public Class<?> getOrBuildPojoSubType(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return getOrBuild(codeGeneratorForPojo.generate(className, superClasses), classLoader);
	}
	
	public Class<?> getOrBuildFunctionSubType(ClassLoader classLoader, int parametersLength) {
		return getOrBuild(codeGeneratorForFunction.generate(parametersLength), classLoader);
	}
	
	public Class<?> getOrBuildConsumerSubType(ClassLoader classLoader, int parametersLength) {
		return getOrBuild(codeGeneratorForConsumer.generate(parametersLength), classLoader);
	}
	
	public Class<?> getOrBuildPredicateSubType(ClassLoader classLoader, int parametersLength) {
		return getOrBuild(codeGeneratorForPredicate.generate(parametersLength),classLoader);
	}
	
	public Class<?> getOrBuildCodeExecutorSubType(String imports, String classSimpleName, String supplierCode, ComponentSupplier componentSupplier, ClassLoader classLoader) {
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
