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
import java.util.function.Supplier;

import org.burningwave.Throwables;
import org.burningwave.core.Component;
import org.burningwave.core.assembler.ComponentSupplier;
import org.burningwave.core.io.PathHelper;


public class ClassFactory implements Component {
	public static String CLASS_REPOSITORIES = "class-factory.class-repositories";
	
	private ClassHelper classHelper;
	private PathHelper pathHelper;
	private Supplier<MemoryClassLoader> memoryClassLoaderSupplier;
	private MemoryClassLoader memoryClassLoader;
	private JavaMemoryCompiler javaMemoryCompiler;
	private CodeGenerator codeGeneratorForPojo;
	private CodeGenerator codeGeneratorForConsumer;
	private CodeGenerator codeGeneratorForFunction;
	private CodeGenerator codeGeneratorForPredicate;
	private CodeGenerator codeGeneratorForExecutor;
	
	private ClassFactory(
		ClassHelper classHelper,
		Supplier<MemoryClassLoader> memoryClassLoaderSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForFunction codeGeneratorForFunction,
		CodeGenerator.ForConsumer codeGeneratorForConsumer,
		CodeGenerator.ForPredicate codeGeneratorForPredicate,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {	
		this.classHelper = classHelper;
		this.memoryClassLoaderSupplier = memoryClassLoaderSupplier;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.codeGeneratorForPojo = codeGeneratorForPojo;
		this.codeGeneratorForConsumer = codeGeneratorForConsumer;
		this.codeGeneratorForFunction = codeGeneratorForFunction;
		this.codeGeneratorForPredicate = codeGeneratorForPredicate;
		this.codeGeneratorForExecutor = codeGeneratorForExecutor;
	}
	
	public static ClassFactory create(
		ClassHelper classHelper,
		Supplier<MemoryClassLoader> memoryClassLoaderSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		CodeGenerator.ForPojo codeGeneratorForPojo,
		CodeGenerator.ForFunction codeGeneratorForFunction,
		CodeGenerator.ForConsumer codeGeneratorForConsumer,
		CodeGenerator.ForPredicate codeGeneratorForPredicate,
		CodeGenerator.ForCodeExecutor codeGeneratorForExecutor
	) {
		return new ClassFactory(
			classHelper, memoryClassLoaderSupplier, 
			javaMemoryCompiler, pathHelper, codeGeneratorForPojo, 
			codeGeneratorForFunction, codeGeneratorForConsumer, codeGeneratorForPredicate, codeGeneratorForExecutor
		);
	}
	
	private MemoryClassLoader getMemoryClassLoader() {
		return memoryClassLoader != null ?
			memoryClassLoader :
			(memoryClassLoader = memoryClassLoaderSupplier.get());	

	}
	
	private Class<?> getFromMemoryClassLoader(String className) {
		Class<?> cls = null;
		try {
			cls = Class.forName(className, false, getMemoryClassLoader() );
		} catch (ClassNotFoundException e) {
			logInfo(className + " not found in " + getMemoryClassLoader() );
		}
		return cls;
	}	
	
	public Map<String, ByteBuffer> build(String classCode) {
		logInfo("Try to compile virtual class:\n\n" + classCode +"\n");
		return javaMemoryCompiler.compile(
			Arrays.asList(classCode), 
			pathHelper.getMainClassPaths(),
			pathHelper.getPaths(CLASS_REPOSITORIES)
		);
	}
	
	private Class<?> buildAndUploadToMemoryClassLoader(String classCode) {
		String className = classHelper.extractClassName(classCode);
		Map<String, ByteBuffer> compiledFiles = build(classCode);
		logInfo("Virtual class " + className + " succesfully created");
		if (!compiledFiles.isEmpty()) {
			compiledFiles.forEach((clsName, byteCode) -> 
				memoryClassLoader.addCompiledClass(
					clsName, byteCode
				)
			);
		}
		try {
			return getMemoryClassLoader().loadClass(className);
		} catch (ClassNotFoundException e) {
			throw Throwables.toRuntimeException(e);
		}
	}
	
	
	public Class<?> getOrBuild(String classCode) {
		String className = classHelper.extractClassName(classCode);
		Class<?> toRet = getFromMemoryClassLoader(className);
		if (toRet == null) {
			toRet = buildAndUploadToMemoryClassLoader(classCode);
		}
		return toRet;
	}	
	
	public Class<?> getOrBuildPojoSubType(String className, Class<?>... superClasses) {
		return getOrBuild(codeGeneratorForPojo.generate(className, superClasses));
	}
	
	public Class<?> getOrBuildFunctionSubType(int parametersLength) {
		return getOrBuild(codeGeneratorForFunction.generate(parametersLength));
	}
	
	public Class<?> getOrBuildConsumerSubType(int parametersLength) {
		return getOrBuild(codeGeneratorForConsumer.generate(parametersLength));
	}
	
	public Class<?> getOrBuildPredicateSubType(int parametersLength) {
		return getOrBuild(codeGeneratorForPredicate.generate(parametersLength));
	}
	
	public Class<?> getOrBuildCodeExecutorSubType(String imports, String className, String supplierCode, ComponentSupplier componentSupplier
	) {
		return getOrBuildCodeExecutorSubType(imports, className, supplierCode, componentSupplier, getMemoryClassLoader());
	}
	
	
	public Class<?> getOrBuildCodeExecutorSubType(String imports, String classSimpleName, String supplierCode, ComponentSupplier componentSupplier, MemoryClassLoader memoryClassLoader
	) {
		String classCode = codeGeneratorForExecutor.generate(
			imports, classSimpleName, supplierCode
		);
		Map<String, ByteBuffer> compiledFiles = build(classCode);
		if (!compiledFiles.isEmpty()) {
			logInfo("Virtual class " + classSimpleName + " succesfully created");
			if (!compiledFiles.isEmpty()) {
				compiledFiles.forEach((clsName, byteCode) -> 
					memoryClassLoader.addCompiledClass(
						clsName, byteCode
					)
				);
			}
		}

		
		try {
			return memoryClassLoader.loadClass(classHelper.extractClassName(classCode));
		} catch (ClassNotFoundException exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
}
