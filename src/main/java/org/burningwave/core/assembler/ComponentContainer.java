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

import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.Executor;
import org.burningwave.core.classes.ByteCodeHunter;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.CodeExecutor;
import org.burningwave.core.classes.ExecuteConfig;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;

public class ComponentContainer implements ComponentSupplier {
	private static Collection<ComponentContainer> instances;
	protected Map<Class<? extends Component>, Component> components;
	private Supplier<Properties> propertySupplier;
	private Properties config;
	private Thread initializerTask;
	
	static {
		instances = ConcurrentHashMap.newKeySet();
	}
	
	ComponentContainer(Supplier<Properties> propertySupplier) {
		this.propertySupplier = propertySupplier;
		this.components = new HashMap<>();
		this.config = new Properties();
		this.config.putAll(GlobalProperties);
		instances.add(this);
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
		config.putAll(ClassFactory.Configuration.DEFAULT_VALUES);
		config.putAll(ClassHunter.Configuration.DEFAULT_VALUES);
		
		Properties customConfig = propertySupplier.get();
		if (customConfig != null) {
			config.putAll(customConfig);
		}
		logInfo(
			"Configuration values:\n\n{}\n\n... Are assumed",
			new TreeMap<>(config).entrySet().stream().map(entry -> "\t" + entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("\n"))
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
		return IterableObjectHelper.get(config, propertyName);
	}
	
	public String getConfigProperty(String propertyName, Map<String, String> defaultValues) {
		return IterableObjectHelper.get(config, propertyName, defaultValues);
	}
	
	@SuppressWarnings("unchecked")
	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier) {
		T component = (T)components.get(componentType);
		if (component == null) {	
			waitForInitializationEnding();
			synchronized (components) {
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
				() -> getClassPathHunter(),
				getJavaMemoryCompiler(),
				getPathHelper(),
				() -> retrieveFromConfig(ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER, ClassFactory.Configuration.DEFAULT_VALUES),
				config
			)
		);	
	}
	
	public CodeExecutor getCodeExecutor() {
		return getOrCreate(CodeExecutor.class, () -> 
			CodeExecutor.create(
				() -> getClassFactory(),
				getPathHelper(),
				config
			)
		);	
	}
	
	@Override
	public JavaMemoryCompiler getJavaMemoryCompiler() {
		return getOrCreate(JavaMemoryCompiler.class, () ->
			JavaMemoryCompiler.create(
				getPathHelper(),
				getClassPathHunter(),
				config
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
				retrieveFromConfig(
					ClassHunter.Configuration.Key.PARENT_CLASS_LOADER_FOR_PATH_SCANNER_CLASS_LOADER,
					ClassHunter.Configuration.DEFAULT_VALUES
				),
				config
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
	
	private <T> T retrieveFromConfig(String configKey, Map<String, Object> defaultValues) {
		T object = IterableObjectHelper.get(config, configKey);
		if (object instanceof String) {
			return getCodeExecutor().execute(
				ExecuteConfig.fromDefaultProperties()
				.setPropertyName(configKey)
				.withParameter(this)
				.withDefaultPropertyValues(defaultValues)
				.useAsParentClassLoader(Classes.getClassLoader(Executor.class))
			);
		} else {
			return (T)object;
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
			propertySupplier = null;
			initializerTask = null;
			instances.remove(this);
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + LazyHolder.COMPONENT_CONTAINER_INSTANCE);
		}
	}
	
	public static void clearAllCaches() {
		for (ComponentContainer componentContainer : instances) {
			componentContainer.clearCache();
		}
		Cache.clear();
		System.gc();
	}
	
	private static class LazyHolder {
		private static final ComponentContainer COMPONENT_CONTAINER_INSTANCE = ComponentContainer.create("burningwave.properties");
		
		private static ComponentContainer getComponentContainerInstance() {
			return COMPONENT_CONTAINER_INSTANCE;
		}
	}
}
