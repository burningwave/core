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

import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Constructors;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

@SuppressWarnings("unchecked")
public class CodeExecutor implements Component {
	static final String PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX = ".imports";
	static final String PROPERTIES_FILE_CODE_EXECUTOR_NAME_KEY_SUFFIX = ".name";
	static final String PROPERTIES_FILE_CODE_EXECUTOR_SIMPLE_NAME_KEY_SUFFIX = ".simple-name";
	
	private ClassFactory classFactory;
	private PathHelper pathHelper;
	private Supplier<ClassFactory> classFactorySupplier;
	private Properties config;
	
	private CodeExecutor(
		Supplier<ClassFactory> classFactorySupplier,
		PathHelper pathHelper,
		Properties config
	) {	
		this.classFactorySupplier = classFactorySupplier;
		this.pathHelper = pathHelper;
		this.config = config;
		listenTo(config);
	}
		
	public static CodeExecutor create(
		Supplier<ClassFactory> classFactorySupplier,
		PathHelper pathHelper,
		Properties config
	) {
		return new CodeExecutor(
			classFactorySupplier,
			pathHelper,
			config
		);
	}
	
	private ClassFactory getClassFactory() {
		return classFactory != null? classFactory :
			(classFactory = classFactorySupplier.get());
	}
	
	public <T> T executeProperty(String propertyName, Object... params) {
		return execute(ExecuteConfig.ForProperties.forProperty(propertyName).withParameter(params));
	}
	
	public <E extends ExecuteConfig<E>, T> T execute(ExecuteConfig.ForProperties config) {
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
		
		BodySourceGenerator body = config.getBody();
		if (config.getParams() != null && config.getParams().length > 0) {
			for (Object param : config.getParams()) {
				body.useType(param.getClass());
			}
		}
		String importFromConfig = IterableObjectHelper.resolveStringValue(properties, config.getPropertyName() + PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_KEY_SUFFIX, null, true, config.getDefaultValues());
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.replaceAll(";{2,}", ";").split(";")).forEach(imp -> {
				if (Strings.isNotEmpty(imp)) {
					body.useType(imp);
				}
			});
		}
		String executorName = IterableObjectHelper.resolveStringValue(properties, config.getPropertyName() + PROPERTIES_FILE_CODE_EXECUTOR_NAME_KEY_SUFFIX, null, true, config.getDefaultValues());
		String executorSimpleName = IterableObjectHelper.resolveStringValue(properties, config.getPropertyName() + PROPERTIES_FILE_CODE_EXECUTOR_SIMPLE_NAME_KEY_SUFFIX, null, true, config.getDefaultValues());

		if (Strings.isNotEmpty(executorName)) {
			config.setName(executorName);
		} else if (Strings.isNotEmpty(executorSimpleName)) {
			config.setSimpleName(executorSimpleName);
		}
		String code = IterableObjectHelper.resolveStringValue(properties, config.getPropertyName(), null, true, config.getDefaultValues());
		if (code.contains(";")) {
			if (config.isIndentCodeActive()) {
				code = code.replaceAll(";{2,}", ";");
				for (String codeRow : code.split(";")) {
					if (Strings.isNotEmpty(codeRow)) {
						body.addCodeRow(codeRow + ";");
					}
				}
			} else {
				body.addCodeRow(code);
			}
		} else {
			body.addCodeRow(code.contains("return")?
				code:
				"return (T)" + code + ";"
			);
		}

		return execute(
			(E)config
		);
	}		
	
	public <E extends ExecuteConfig<E>, T> T execute(BodySourceGenerator body) {
		return execute((E)ExecuteConfig.forBodySourceGenerator(body));
	}
	
	public <E extends ExecuteConfig<E>, T> T execute(
		E config
	) {	
		Object executeClient = new Object() {};
		ClassLoader defaultClassLoader = null;
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = defaultClassLoader = getClassFactory().getDefaultClassLoader(executeClient);
		}
		if (config.getClassLoader() == null) {
			MemoryClassLoader memoryClassLoader = MemoryClassLoader.create(
				parentClassLoader
			);
			try {
				memoryClassLoader.register(executeClient);
				Class<? extends Executable> executableClass = loadOrBuildAndDefineExecutorSubType(
					config.useClassLoader(memoryClassLoader)
				);
				Executable executor = Constructors.newInstanceDirectOf(executableClass);
				T retrievedElement = executor.execute(config.getParams());
				return retrievedElement;
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			} finally {
				if (defaultClassLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)defaultClassLoader).unregister(executeClient, true);
				}
				memoryClassLoader.unregister(executeClient, true);
			}
		} else {
			Function<Boolean, ClassLoader> parentClassLoaderRestorer = null;
			try {
				if (parentClassLoader != null) {
					parentClassLoaderRestorer = ClassLoaders.setAsParent(config.getClassLoader(), parentClassLoader, false);
				}
				Class<? extends Executable> executableClass = loadOrBuildAndDefineExecutorSubType(
					config
				);
				Executable executor = Constructors.newInstanceDirectOf(executableClass);
				T retrievedElement = executor.execute(config.getParams());
				if (parentClassLoaderRestorer != null) {
					parentClassLoaderRestorer.apply(true);
				}
				return retrievedElement;
			} catch (Throwable exc) {
				throw Throwables.toRuntimeException(exc);
			} finally {
				if (defaultClassLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)defaultClassLoader).unregister(executeClient, true);
				}
			}
		}
	}
	
	public <E extends LoadOrBuildAndDefineConfig.ForCodeExecutorAbst<E>, T extends Executable> Class<T> loadOrBuildAndDefineExecutorSubType(
		E config
	) {	
		ClassFactory.ClassRetriever classRetriever = getClassFactory().loadOrBuildAndDefine(
			config
		);
		Class<T> executableClass = (Class<T>) classRetriever.get(
			config.getExecutorName()
		);
		classRetriever.close();
		return executableClass;
	}
	
	@Override
	public void close() {
		unregister(config);
		classFactory = null;
		pathHelper = null;
		classFactorySupplier = null;
		config = null;
	}
}
