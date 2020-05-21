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

import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executor;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;

public class CodeExecutor implements Component {
	public final static String PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX = ".imports";
	
	private SourceCodeHandler sourceCodeHandler;
	private ClassFactory classFactory;
	private PathHelper pathHelper;
	private Supplier<ClassFactory> classFactorySupplier;
	private IterableObjectHelper iterableObjectHelper;	
	private Supplier<IterableObjectHelper> iterableObjectHelperSupplier;
	private Properties config;
	
	private CodeExecutor(
		Supplier<ClassFactory> classFactorySupplier,
		SourceCodeHandler sourceCodeHandler,
		PathHelper pathHelper,
		Supplier<IterableObjectHelper> iterableObjectHelperSupplier,
		Properties config
	) {	
		this.classFactorySupplier = classFactorySupplier;
		this.sourceCodeHandler = sourceCodeHandler;
		this.pathHelper = pathHelper;
		this.iterableObjectHelperSupplier = iterableObjectHelperSupplier;
		this.config = config;
		listenTo(config);
	}
		
	public static CodeExecutor create(
		Supplier<ClassFactory> classFactorySupplier,
		SourceCodeHandler sourceCodeHandler,
		PathHelper pathHelper,
		Supplier<IterableObjectHelper> iterableObjectHelperSupplier,
		Properties config
	) {
		return new CodeExecutor(
			classFactorySupplier,
			sourceCodeHandler, 
			pathHelper,
			iterableObjectHelperSupplier,
			config
		);
	}
	
	private ClassFactory getClassFactory() {
		return classFactory != null? classFactory :
			(classFactory = classFactorySupplier.get());
	}
	
	protected IterableObjectHelper getIterableObjectHelper() {
		return iterableObjectHelper != null ?
			iterableObjectHelper :
			(iterableObjectHelper = iterableObjectHelperSupplier.get());
	}
	
	public <T extends Executor> Class<T> loadOrBuildAndDefineExecutorSubType(String className, BodySourceGenerator body) {
		return loadOrBuildAndDefineExecutorSubType(getClassFactory().getDefaultClassLoader(), className, body);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Executor> Class<T> loadOrBuildAndDefineExecutorSubType(ClassLoader classLoader, String className, BodySourceGenerator body) {
		return (Class<T>) getClassFactory().loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				sourceCodeHandler.generateExecutor(className, body)
			).useClassLoader(
				classLoader
			)
		).get(
			className
		);
	}
	
	public <T> T execute(String propertyName, Object... params) {
		return execute(ExecuteConfig.ForProperties.forProperty(propertyName).withParameter(params));
	}
	
	public <T> T execute(ExecuteConfig.ForProperties config) {
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = getClassFactory().getDefaultClassLoader();
		}
		
		java.util.Properties properties = config.getProperties();
		if (properties == null) {
			if (config.getFilePath() == null) {
				properties = this.config; 
			} else {
				Properties tempProperties = new Properties();
				if (config.isAbsoluteFilePath()) {
					ThrowingRunnable.run(() -> 
						tempProperties.load(FileSystemItem.ofPath(config.getFilePath()).toInputStream())
					);
				} else {
					ThrowingRunnable.run(() ->
						tempProperties.load(pathHelper.getResourceAsStream(config.getFilePath()))
					);
				}
				properties = tempProperties;
			}
			
		}
		
		BodySourceGenerator body = BodySourceGenerator.createSimple().setElementPrefix("\t");
		if (config.getParams() != null && config.getParams().length > 0) {
			for (Object param : config.getParams()) {
				body.useType(param.getClass());
			}
		}
		String importFromConfig = getIterableObjectHelper().get(properties, config.getPropertyName() + PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX, config.getDefaultValues());
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.split(";")).forEach(imp -> {
				body.useType(imp);
			});
		}
		String code = getIterableObjectHelper().get(properties, config.getPropertyName(), config.getDefaultValues());
		if (code.contains(";")) {
			for (String codeRow : code.split(";")) {
				body.addCodeRow(codeRow + ";");
			}
		} else {
			body.addCodeRow(code.contains("return")?
				code:
				"return (T)" + code + ";"
			);
		}
		return execute(
			parentClassLoader, body, config.getParams()
		);
	}		
	
	public <T> T execute(BodySourceGenerator body) {
		return execute(ExecuteConfig.forBodySourceGenerator(body));
	}
	
	public <T> T execute(
		ExecuteConfig.ForBodySourceGenerator config
	) {	
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = getClassFactory().getDefaultClassLoader();
		}
		return execute(parentClassLoader, config.getBody(), config.getParams());
	}
	
	private <T> T execute(
		ClassLoader classLoaderParentOfOneShotClassLoader,
		BodySourceGenerator body,
		Object... parameters
	) {	
		return ThrowingSupplier.get(() -> {
			try (MemoryClassLoader memoryClassLoader = 
				MemoryClassLoader.create(
					classLoaderParentOfOneShotClassLoader
				)
			) {
				String packageName = Executor.class.getPackage().getName();
				Class<? extends Executor> executableClass = loadOrBuildAndDefineExecutorSubType(
					memoryClassLoader, packageName + ".CodeExecutor_" + UUID.randomUUID().toString().replaceAll("-", ""), body
				);
				Executor executor = Constructors.newInstanceOf(executableClass);
				T retrievedElement = executor.execute(parameters);
				return retrievedElement;
			}
		});
	}
}
