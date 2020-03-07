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

import static org.burningwave.core.assembler.StaticComponentsContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentsContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentsContainer.Throwables;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.Component;
import org.burningwave.core.classes.ByteCodeHunter;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.Classes;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.SourceCodeHandler;
import org.burningwave.core.concurrent.ConcurrentHelper;
import org.burningwave.core.io.FileSystemHelper;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.io.StreamHelper;
import org.burningwave.core.iterable.IterableObjectHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.reflection.PropertyAccessor;

public class ComponentContainer implements ComponentSupplier {
	protected Map<Class<? extends Component>, Component> components;
	private String configFileName;
	private Properties config;
	private Thread initializerTask;
	
	ComponentContainer(String fileName) {
		configFileName = fileName;
		components = new ConcurrentHashMap<>();
		config = new Properties();
	}
	
	public final static ComponentContainer create(String fileName) {
		try {
			ComponentContainer componentContainer = new ComponentContainer(fileName);
			componentContainer.launchInit();
			return componentContainer;
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(ComponentContainer.class, "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	private ComponentContainer init() {
		config.put(PathHelper.PATHS_KEY_PREFIX + ClassFactory.CLASS_REPOSITORIES, "${classPaths}");
		config.put(PathHelper.PATHS_KEY_PREFIX + PathHelper.MAIN_CLASS_PATHS_EXTENSION, PathHelper.MAIN_CLASS_PATHS_EXTENSION_DEFAULT_VALUE);
		config.put(MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY, "Thread.currentThread().getContextClassLoader()");
		config.put(ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY, "componentSupplier.getMemoryClassLoader()");
		
		InputStream customConfig = Optional.ofNullable(this.getClass().getClassLoader()).orElseGet(() ->
		ClassLoader.getSystemClassLoader()).getResourceAsStream(configFileName);
		if (customConfig != null) {
			try(InputStream inputStream = customConfig) {
				config.load(inputStream);
			} catch (Throwable exc) {
				Throwables.toRuntimeException(exc);
			}
		} else {
			logInfo("Custom configuration file burningwave.properties not found.");
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
	public MemoryClassLoader getMemoryClassLoader() {
		return getOrCreate(MemoryClassLoader.class, () -> {
			return MemoryClassLoader.create(
				getByFieldOrByMethodPropertyAccessor().retrieveFromFile(
					config,
					MemoryClassLoader.PARENT_CLASS_LOADER_SUPPLIER_CONFIG_KEY,
					MemoryClassLoader.DEFAULT_CONFIG_VALUES,
					this
				),
				getClassesLoaders()
			);
		});
	}
	
	@Override
	public Classes.Loaders getClassesLoaders() {
		return getOrCreate(Classes.Loaders.class, () -> 
			org.burningwave.core.classes.Classes.Loaders.create()
		);	
	}
	
	@Override
	public ClassFactory getClassFactory() {
		return getOrCreate(ClassFactory.class, () -> 
			ClassFactory.create(
				getSourceCodeHandler(),
				getClassesLoaders(),
				getJavaMemoryCompiler(),
				getPathHelper()
			)
		);	
	}

	@Override
	public JavaMemoryCompiler getJavaMemoryCompiler() {
		return getOrCreate(JavaMemoryCompiler.class, () ->
			JavaMemoryCompiler.create(
				getPathHelper(),
				getSourceCodeHandler(),
				getClassPathHunter()
			)
		);
	}

	@Override
	public ClassHunter getClassHunter() {
		return getOrCreate(ClassHunter.class, () -> {
			return ClassHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(),
				getFileSystemScanner(),
				getPathHelper(),
				getStreamHelper(),
				getClassesLoaders(),
				getByFieldOrByMethodPropertyAccessor().retrieveFromFile(
					config,
					ClassHunter.PARENT_CLASS_LOADER_SUPPLIER_FOR_PATH_MEMORY_CLASS_LOADER_CONFIG_KEY,
					ClassHunter.DEFAULT_CONFIG_VALUES,
					this
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
				getFileSystemHelper(),
				getFileSystemScanner(),
				getPathHelper(),
				getStreamHelper(),
				getClassesLoaders()
			)
		);
	}
	
	@Override
	public ByteCodeHunter getByteCodeHunter() {
		return getOrCreate(ByteCodeHunter.class, () -> 
			ByteCodeHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getFileSystemHelper(),
				getFileSystemScanner(),
				getPathHelper(),
				getStreamHelper(),
				getClassesLoaders()
			)
		);
	}

	@Override
	public PropertyAccessor.ByFieldOrByMethod getByFieldOrByMethodPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByFieldOrByMethod.class, () ->  
			PropertyAccessor.ByFieldOrByMethod.create(
				() -> getSourceCodeHandler(),
				() -> getIterableObjectHelper()
			)
		);
	}
	
	@Override
	public PropertyAccessor.ByMethodOrByField getByMethodOrByFieldPropertyAccessor() {
		return getOrCreate(PropertyAccessor.ByMethodOrByField.class, () ->  
			PropertyAccessor.ByMethodOrByField.create(
				() -> getSourceCodeHandler(),
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
				() -> getFileSystemHelper(),
				getIterableObjectHelper(),
				config
			)
		);
	}
	
	public StreamHelper getStreamHelper() {
		return getOrCreate(StreamHelper.class, () -> 
			StreamHelper.create(
				getFileSystemHelper()
			)
		);
	}
	
	public FileSystemHelper getFileSystemHelper() {
		return getOrCreate(FileSystemHelper.class, () -> 
			FileSystemHelper.create(
				() -> getPathHelper()
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
			SourceCodeHandler.create(
				() -> getClassFactory(),
				getClassesLoaders()
			)
		);
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
