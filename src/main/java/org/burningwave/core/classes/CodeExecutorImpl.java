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
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;

import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.function.Executor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;


@SuppressWarnings("unchecked")
public class CodeExecutorImpl implements CodeExecutor, Component {
	private ClassFactory classFactory;
	private PathHelper pathHelper;
	private Supplier<ClassFactory> classFactorySupplier;
	private Properties config;

	CodeExecutorImpl(
		Supplier<ClassFactory> classFactorySupplier,
		PathHelper pathHelper,
		Properties config
	) {
		this.classFactorySupplier = classFactorySupplier;
		this.pathHelper = pathHelper;
		this.config = config;
		listenTo(config);
	}

	private ClassFactory getClassFactory() {
		return classFactory != null? classFactory :
			(classFactory = classFactorySupplier.get());
	}

	@Override
	public <T> T executeProperty(String propertyName, Object... params) {
		return execute(ExecuteConfig.forProperty(propertyName).withParameter(params));
	}

	@Override
	public <E extends ExecuteConfig<E>, T> T execute(ExecuteConfig.ForProperties config) {
		java.util.Properties properties = config.getProperties();
		if (properties == null) {
			if (config.getFilePath() == null) {
				properties = this.config;
			} else {
				Properties tempProperties = new Properties();
				Supplier<InputStream> inputStreamSupplier = 
					config.isAbsoluteFilePath() ?
						() -> FileSystemItem.ofPath(config.getFilePath()).toInputStream() :
						() -> pathHelper.getResourceAsStream(config.getFilePath());	
				Executor.run(() -> {
					try (InputStream inputStream = inputStreamSupplier.get()) {
						tempProperties.load(inputStream);
					}						
				});
				properties = tempProperties;
			}

		}

		BodySourceGenerator body = config.getBody();
		if (config.getParams() != null && config.getParams().length > 0) {
			for (Object param : config.getParams()) {
				if (param != null) {
					body.useType(param.getClass());
				}
			}
		}
		String importFromConfig = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_IMPORTS_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_IMPORTS_SUFFIX
		);
		if (Strings.isNotEmpty(importFromConfig)) {
			Arrays.stream(importFromConfig.replaceAll(";{2,}", ";").split(";")).forEach(imp -> {
				if (Strings.isNotEmpty(imp)) {
					body.useType(imp);
				}
			});
		}
		String executorName = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_CLASS_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_NAME_SUFFIX
		);
		String executorSimpleName = retrieveValue(
			config,
			properties,
			Configuration.Key.PROPERTIES_FILE_CLASS_SIMPLE_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_SUPPLIER_SIMPLE_NAME_SUFFIX,
			Configuration.Key.PROPERTIES_FILE_EXECUTOR_SIMPLE_NAME_SUFFIX
		);
		if (Strings.isNotEmpty(executorName)) {
			config.setName(executorName);
		} else if (Strings.isNotEmpty(executorSimpleName)) {
			config.setSimpleName(executorSimpleName);
		}
		String code = IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(config.getPropertyName())
			.on(properties)
			.deleteUnresolvedPlaceHolder(true)
			.withDefaultValues(config.getDefaultValues())
		);
		if (code.contains(";")) {
			if (config.isIndentCodeActive()) {
				code = code.replaceAll(";{2,}", ";");
				for (String codeLine : code.split(";")) {
					if (Strings.isNotEmpty(codeLine)) {
						body.addCodeLine(codeLine + ";");
					}
				}
			} else {
				body.addCodeLine(code);
			}
			if (!code.contains("return")) {
				body.addCodeLine("return null;");
			}
		} else {
			body.addCodeLine(code.contains("return")?
				code:
				"return " + code + ";"
			);
		}

		return execute(
			(E)config
		);
	}

	private String retrieveValue(ExecuteConfig.ForProperties config, java.util.Properties properties, String... suffixes) {
		for (String suffix : suffixes) {
			String value = IterableObjectHelper.resolveStringValue(
				ResolveConfig.forNamedKey(config.getPropertyName() + suffix)
				.on(properties)
				.deleteUnresolvedPlaceHolder(true)
				.withDefaultValues(config.getDefaultValues())
			);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	@Override
	public <E extends ExecuteConfig<E>, T> T execute(BodySourceGenerator body) {
		return execute((E)ExecuteConfig.forBodySourceGenerator(body));
	}

	@Override
	public <E extends ExecuteConfig<E>, T> T execute(
		E config
	) {
		Object executeClient = new Object() {};
		ClassLoader defaultClassLoader = null;
		ClassLoader parentClassLoader = config.getParentClassLoader();
		if (parentClassLoader == null && config.isUseDefaultClassLoaderAsParentIfParentClassLoaderIsNull()) {
			parentClassLoader = defaultClassLoader = ((ClassFactoryImpl)getClassFactory()).getDefaultClassLoader(executeClient);
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
				T retrievedElement = executor.executeAndCast(config.getParams());
				return retrievedElement;
			} catch (Throwable exc) {
				return Driver.throwException(exc);
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
					parentClassLoaderRestorer = ClassLoaders.setAsParent(config.getClassLoader(), parentClassLoader);
				}
				Class<? extends Executable> executableClass = loadOrBuildAndDefineExecutorSubType(
					config
				);
				Executable executor = Constructors.newInstanceDirectOf(executableClass);
				T retrievedElement = executor.executeAndCast(config.getParams());
				if (parentClassLoaderRestorer != null) {
					parentClassLoaderRestorer.apply(true);
				}
				return retrievedElement;
			} catch (Throwable exc) {
				return Driver.throwException(exc);
			} finally {
				if (defaultClassLoader instanceof MemoryClassLoader) {
					((MemoryClassLoader)defaultClassLoader).unregister(executeClient, true);
				}
			}
		}
	}

	@Override
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