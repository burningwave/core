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
package org.burningwave.core.assembler;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ByteCodeHunter;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.SourceCodeHandler;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileScanConfigAbst;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.reflection.PropertyAccessor;

public class ComponentContainer implements ComponentSupplier {
	protected Map<Class<? extends Component>, Component> components;
	private Supplier<Properties> propertySupplier;
	private Properties config;
	private Thread initializerTask;
	
	ComponentContainer(Supplier<Properties> propertySupplier) {
		this.propertySupplier = propertySupplier;
		this.components = new HashMap<>();
		this.config = new Properties();
	}
	
	@SuppressWarnings("resource")
	public final static ComponentContainer create(String configFileName) {
		try {
			return new ComponentContainer(() -> {
				try(InputStream inputStream = Resources.getAsInputStream(ComponentContainer.class.getClassLoader(), configFileName)) {
					Properties config = new Properties();
					if (inputStream != null) {
						config.load(inputStream);
						ManagedLoggersRepository.logInfo(ComponentContainer.class, configFileName + " loaded");
					} else {
						ManagedLoggersRepository.logInfo(ComponentContainer.class, configFileName + " not found");
					}
					return config;
				} catch (Throwable exc) {
					throw Throwables.toRuntimeException(exc);
				}
			}).launchInit();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(ComponentContainer.class, "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	@SuppressWarnings("resource")
	public final static ComponentContainer create(Properties properties) {
		try {
			return new ComponentContainer(() -> properties).launchInit();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(ComponentContainer.class, "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public final static ComponentContainer create() {
		return create((Properties)null);
	}
	
	private ComponentContainer init() {
		config.put(PathHelper.PATHS_KEY_PREFIX + PathHelper.MAIN_CLASS_PATHS_EXTENSION, PathHelper.MAIN_CLASS_PATHS_EXTENSION_DEFAULT_VALUE);
		config.put(ClassFactory.DEFAULT_CLASS_LOADER_CONFIG_KEY, "Thread.currentThread().getContextClassLoader()");
		config.put(
			PathHelper.PATHS_KEY_PREFIX + ClassFactory.CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY, 
			"${classPaths};" +
			"${" + PathHelper.PATHS_KEY_PREFIX + PathHelper.MAIN_CLASS_PATHS_EXTENSION + "};"
		);
		config.put(
			PathHelper.PATHS_KEY_PREFIX + ClassFactory.CLASS_REPOSITORIES_FOR_DEFAULT_CLASSLOADER_CONFIG_KEY, 
			"${" + PathHelper.PATHS_KEY_PREFIX + ClassFactory.CLASS_REPOSITORIES_FOR_JAVA_MEMORY_COMPILER_CONFIG_KEY + "};"
		);
		config.put(ClassHunter.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER_CONFIG_KEY, "Thread.currentThread().getContextClassLoader()");
		
		Properties customConfig = propertySupplier.get();
		if (customConfig != null) {
			config.putAll(customConfig);
		}
		logInfo(
			"Configuration values:\n\n{}\n\n... Are assumed",
			config.entrySet().stream().map(entry -> "\t" + entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n"))
		);
		return this;
	}
	
	private ComponentContainer launchInit() {
		initializerTask = new Thread(() -> {
			init();
			synchronized (components) {
				initializerTask = null;
				components.notifyAll();
			}
		});
		initializerTask.start();
		return this;
	}
	
	public void reInit() {
		clear();
		config.clear();
		launchInit();
	}
	
	protected void waitForInitializationEnding() {
		if (initializerTask != null) {
			synchronized (components) {
				if (initializerTask != null) {
					try {
						components.wait();
					} catch (InterruptedException exc) {
						logError("Exception while waiting " + ComponentContainer.class.getSimpleName() + " initializaziont", exc);
						throw Throwables.toRuntimeException(exc);
					}
				}
			}
		}
	}
	
	public static ComponentContainer getInstance() {
		return LazyHolder.getComponentContainerInstance();
	}
	
	public String getConfigProperty(String propertyName) {
		return getIterableObjectHelper().get(config, propertyName);
	}
	
	public String getConfigProperty(String propertyName, Map<String, String> defaultValues) {
		return getIterableObjectHelper().get(config, propertyName, defaultValues);
	}
	
	@SuppressWarnings("unchecked")
	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier) {
		T component = (T)components.get(componentType);
		if (component == null) {	
			waitForInitializationEnding();
			synchronized (Classes.getId(components, componentType.getName())) {
				if ((component = (T)components.get(componentType)) == null) {
					component = componentSupplier.get();
					components.put(componentType, component);
				}				
			}
		}
		return component;
	}
	
	@Override
	public ClassFactory getClassFactory() {
		return getOrCreate(ClassFactory.class, () -> 
			ClassFactory.create(
				getByteCodeHunter(),
				getSourceCodeHandler(),
				getJavaMemoryCompiler(),
				getPathHelper(),
				() -> retrieveClassLoader(ClassFactory.DEFAULT_CLASS_LOADER_CONFIG_KEY, null),
				FileScanConfigAbst.parseCheckFileOptionsValue(
					getConfigProperty(ClassFactory.BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY),
					FileScanConfigAbst.CHECK_FILE_OPTIONS_DEFAULT_VALUE
				)
			)
		);	
	}

	@Override
	public JavaMemoryCompiler getJavaMemoryCompiler() {
		return getOrCreate(JavaMemoryCompiler.class, () ->
			JavaMemoryCompiler.create(
				getPathHelper(),
				getSourceCodeHandler(),
				getClassPathHunter(),
				FileScanConfigAbst.parseCheckFileOptionsValue(
					getConfigProperty(JavaMemoryCompiler.CLASS_PATH_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY),
					FileScanConfigAbst.CHECK_FILE_OPTIONS_DEFAULT_VALUE
				)
			)
		);
	}

	@Override
	public ClassHunter getClassHunter() {
		return getOrCreate(ClassHunter.class, () -> {
			return ClassHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemScanner(),
				getPathHelper(),
				retrieveClassLoader(ClassHunter.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER_CONFIG_KEY, ClassHunter.DEFAULT_CONFIG_VALUES),
				FileScanConfigAbst.parseCheckFileOptionsValue(
					getConfigProperty(ClassHunter.PATH_SCANNER_CLASS_LOADER_BYTE_CODE_HUNTER_SEARCH_CONFIG_CHECK_FILE_OPTIONS_CONFIG_KEY),
					FileScanConfigAbst.CHECK_FILE_OPTIONS_DEFAULT_VALUE
				)
			);
		});
	}

	
	@Override
	public ClassPathHunter getClassPathHunter() {
		return getOrCreate(ClassPathHunter.class, () -> 
			ClassPathHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemScanner(),
				getPathHelper()
			)
		);
	}
	
	@Override
	public ByteCodeHunter getByteCodeHunter() {
		return getOrCreate(ByteCodeHunter.class, () -> 
			ByteCodeHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemScanner(),
				getPathHelper()
			)
		);
	}

	@Override
	public PropertyAccessor.ByFieldOrByMethod getByFieldOrByMethodPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByFieldOrByMethod.class, () ->  
			PropertyAccessor.ByFieldOrByMethod.create(
				() -> getClassFactory(),
				() -> getIterableObjectHelper()
			)
		);
	}
	
	@Override
	public PropertyAccessor.ByMethodOrByField getByMethodOrByFieldPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByMethodOrByField.class, () ->  
			PropertyAccessor.ByMethodOrByField.create(
				() -> getClassFactory(),
				() -> getIterableObjectHelper()
			)
		);
	}

	@Override
	public FunctionalInterfaceFactory getFunctionalInterfaceFactory() {
		return getOrCreate(FunctionalInterfaceFactory.class, () -> 
			FunctionalInterfaceFactory.create(
				getClassFactory()
			)
		);
	}

	@Override
	public PathHelper getPathHelper() {
		return getOrCreate(PathHelper.class, () ->
			PathHelper.create(
				getIterableObjectHelper(),
				config
			)
		);
	}
	
	@Override
	public FileSystemScanner getFileSystemScanner() {
		return getOrCreate(FileSystemScanner.class, () -> 
			FileSystemScanner.create(
				getPathHelper()::optimize
			)
		);
	}
	
	@Override
	public ConcurrentHelper getConcurrentHelper() {
		return getOrCreate(ConcurrentHelper.class, ConcurrentHelper::create);
	}

	@Override
	public IterableObjectHelper getIterableObjectHelper() {
		return getOrCreate(IterableObjectHelper.class, () ->
			IterableObjectHelper.create(
				getByFieldOrByMethodPropertyAccessor()
			)
		);
	}

	@Override
	public SourceCodeHandler getSourceCodeHandler() {
		return getOrCreate(SourceCodeHandler.class, () ->
			SourceCodeHandler.create()
		);
	}
	
	private ClassLoader retrieveClassLoader(String configKey, Map<String, String> defaultValues) {
		Object object = config.get(configKey);
		if (object instanceof ClassLoader) {
			return (ClassLoader)object;
		} else if (object instanceof String) {
			return getByFieldOrByMethodPropertyAccessor().retrieveFrom(
				config,
				configKey,
				defaultValues,
				this
			);
		} else {
			throw Throwables.toRuntimeException("Value " + object + " of configuration property" + 
				configKey + " is not valid"
			);
		}
	}
	
	public ComponentSupplier clear() {
		components.forEach((type, instance) -> { 
			try {
				instance.close();
			} catch (Throwable exc) {
				logError("Exception occurred while closing " + instance, exc);
			}
			components.remove(type);
		});
		return this;
	}
	
	@Override
	public void close() {
		if (LazyHolder.getComponentContainerInstance() != this) {
			clear();
			components = null;
			config.clear();
			config = null;			
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + LazyHolder.COMPONENT_CONTAINER_INSTANCE);
		}
	}
	
	private static class LazyHolder {
		private static final ComponentContainer COMPONENT_CONTAINER_INSTANCE = ComponentContainer.create("burningwave.properties");
		
		private static ComponentContainer getComponentContainerInstance() {
			return COMPONENT_CONTAINER_INSTANCE;
		}
	}
}
