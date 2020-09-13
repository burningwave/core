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

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.Cache;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Resources;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.classes.ByteCodeHunter;
import org.burningwave.core.classes.ClassFactory;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHelper;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.ClassPathScannerAbst;
import org.burningwave.core.classes.CodeExecutor;
import org.burningwave.core.classes.ExecuteConfig;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.classes.SearchResult;
import org.burningwave.core.concurrent.QueuedTasksExecutor.Task;
import org.burningwave.core.function.ThrowingRunnable;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings({"unchecked", "resource"})
public class ComponentContainer implements ComponentSupplier {
	
	public static class Configuration {
		
		public static class Key {
			
			public static final String AFTER_INIT = "component-container.after-init.operations";
					
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
	
		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Configuration.Key.AFTER_INIT + CodeExecutor.Configuration.Key.PROPERTIES_FILE_CODE_EXECUTOR_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR + 
				"${"+ Configuration.Key.AFTER_INIT + ".additional-imports}" + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR + 
				SearchResult.class.getName() + CodeExecutor.Configuration.Key.CODE_LINE_SEPARATOR
			);
			defaultValues.put(
				Configuration.Key.AFTER_INIT + CodeExecutor.Configuration.Key.PROPERTIES_FILE_CODE_EXECUTOR_NAME_SUFFIX, 
				ComponentContainer.class.getPackage().getName() + ".AfterInitOperations"
			);
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	private static Collection<ComponentContainer> instances;
	protected Map<Class<? extends Component>, Component> components;
	private Supplier<Properties> propertySupplier;
	private Properties config;
	private boolean isUndestroyable;
	
	static {
		instances = ConcurrentHashMap.newKeySet();
	}
	
	ComponentContainer(Supplier<Properties> propertySupplier) {
		this.propertySupplier = propertySupplier;
		this.components = new ConcurrentHashMap<>();
		this.config = new Properties();
		instances.add(this);
	}
	
	public final static ComponentContainer create(String configFileName) {
		try {
			return new ComponentContainer(() -> {
				try(InputStream inputStream = Resources.getAsInputStream(ComponentContainer.class.getClassLoader(), configFileName)) {
					Properties config = new Properties();
					if (inputStream != null) {
						config.load(inputStream);
						ManagedLoggersRepository.logInfo(configFileName + " loaded");
					} else {
						ManagedLoggersRepository.logInfo(configFileName + " not found");
					}
					return config;
				} catch (Throwable exc) {
					throw Throwables.toRuntimeException(exc);
				}
			}).init();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError("Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public final static ComponentContainer create(Properties properties) {
		try {
			return new ComponentContainer(() -> properties).init();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError("Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			throw Throwables.toRuntimeException(exc);
		}
	}
	
	public final static ComponentContainer create() {
		return create((Properties)null);
	}
	
	private ComponentContainer init() {
		Properties config = new Properties();
		TreeMap<Object, Object> defaultProperties = new TreeMap<>();
		defaultProperties.putAll(Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(CodeExecutor.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(PathHelper.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(JavaMemoryCompiler.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassFactory.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassHunter.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassPathScannerAbst.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassPathHelper.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(PathScannerClassLoader.Configuration.DEFAULT_VALUES);
				
		config.putAll(GlobalProperties);
		Optional.ofNullable(propertySupplier.get()).ifPresent(customConfig -> config.putAll(customConfig));
		for (Map.Entry<Object, Object> defVal : defaultProperties.entrySet()) {
			config.putIfAbsent(defVal.getKey(), defVal.getValue());
		}
		
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			IterableObjectHelper.refresh(this.config, config);
		});
		
		Properties componentContainerConfig = new Properties();
		componentContainerConfig.putAll(this.config);
		componentContainerConfig.keySet().removeAll(GlobalProperties.keySet());
		logInfo(
			"\nConfiguration values for...\n\n\tStatic components:\n{}\n\n\tDynamic components:\n{}\n\n... Are assumed",
			GlobalProperties.toSimplePrettyString(2),
			componentContainerConfig.toPrettyString(2)
		);
		listenTo(GlobalProperties);
		launchAfterInit();
		return this;
	}
	
	ComponentContainer markAsUndestroyable() {
		this.isUndestroyable = true;
		return this;
	}
	
	@Override
	public void receiveNotification(Properties properties, Event event, Object key, Object newValue, Object oldValue) {
		if (event == Event.PUT) {
			config.put(key, newValue);
		} else if (event == Event.REMOVE) {
			config.remove(key);
		}
	}
	
	private ComponentContainer launchAfterInit() {
		if (config.getProperty(Configuration.Key.AFTER_INIT) != null) {
			BackgroundExecutor.createTask(() -> {
				getCodeExecutor().executeProperty(Configuration.Key.AFTER_INIT, this);
			}).pureAsync().submit();
		}
		return this;
	}

	private String getMutexForComponentsId() {
		return getId() + "_components";
	}
	
	public void reset() {
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			clear();
			init();
		});
	}
	
	public static ComponentContainer getInstance() {
		return LazyHolder.getComponentContainerInstance();
	}
	
	public String getConfigProperty(String propertyName) {
		return IterableObjectHelper.resolveStringValue(config, propertyName);
	}
	
	public String getConfigProperty(String propertyName, Map<String, String> defaultValues) {
		return IterableObjectHelper.resolveStringValue(config, propertyName, defaultValues);
	}
	
	public<T extends Component> T getOrCreate(Class<T> componentType, Supplier<T> componentSupplier) {
		T component = (T)components.get(componentType);
		if (component == null) {
			component = Synchronizer.execute(getMutexForComponentsId(), () -> {
				T componentTemp = (T)components.get(componentType);
				if (componentTemp == null) {
					components.put(componentType, componentTemp = componentSupplier.get());
				}
				return componentTemp;
			});
		}
		return component;
	}
	
	@Override
	public PathScannerClassLoader getPathScannerClassLoader() {
		return getOrCreate(PathScannerClassLoader.class, () -> {
				PathScannerClassLoader classLoader = PathScannerClassLoader.create(
					retrieveFromConfig(
						PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER,
						PathScannerClassLoader.Configuration.DEFAULT_VALUES
					), getPathHelper(),
					FileSystemItem.Criteria.forClassTypeFiles(
						config.resolveStringValue(
							PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION
						)
					)
				);
				classLoader.register(this);
				return classLoader;
			}
		);
	}
	
	@Override
	public ClassFactory getClassFactory() {
		return getOrCreate(ClassFactory.class, () -> 
			ClassFactory.create(
				getByteCodeHunter(),
				() -> getClassPathHunter(),
				getJavaMemoryCompiler(),
				getPathHelper(),
				getClassPathHelper(),
				(Supplier<?>)() -> retrieveFromConfig(ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER, ClassFactory.Configuration.DEFAULT_VALUES),
				getClassLoaderResetter(),
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
				getClassPathHelper(),
				config
			)
		);
	}

	@Override
	public ClassHunter getClassHunter() {
		return getOrCreate(ClassHunter.class, () -> {
			return ClassHunter.create(
				() -> getClassHunter(),
				getPathHelper(),
				(Supplier<?>)() -> retrieveFromConfig(ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER, ClassHunter.Configuration.DEFAULT_VALUES),
				getClassLoaderResetter(),
				config
			);
		});
	}

	@Override
	public ClassPathHelper getClassPathHelper() {
		return getOrCreate(ClassPathHelper.class, () ->
			ClassPathHelper.create(
				getClassPathHunter(),
				config
			)
		);
	}
	
	@Override
	public ClassPathHunter getClassPathHunter() {
		return getOrCreate(ClassPathHunter.class, () -> 
			ClassPathHunter.create(
				() -> getByteCodeHunter(),
				() -> getClassHunter(),
				getPathHelper(),
				config
			)
		);
	}
	
	@Override
	public ByteCodeHunter getByteCodeHunter() {
		return getOrCreate(ByteCodeHunter.class, () -> 
			ByteCodeHunter.create(
				() -> getClassHunter(),
				getPathHelper(),
				config
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
	
	
	private <T> T retrieveFromConfig(String configKey, Map<String, Object> defaultValues) {
		T object = config.resolveValue(configKey, defaultValues);
		if (object instanceof String) {
			return getCodeExecutor().execute(
				ExecuteConfig.fromDefaultProperties()
				.setPropertyName(configKey)
				.withParameter(this)
				.withDefaultPropertyValues(defaultValues)
				.useAsParentClassLoader(Classes.getClassLoader(Executable.class))
				.setClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(new HashSet<>())
			);
		} else if (object instanceof Function) {
			return (T)(Supplier<?>)() -> ((Function<ComponentSupplier, ?>)object).apply(this);
		} else {
			return (T)object;
		}
	}
	
	private Consumer<ClassLoader> getClassLoaderResetter() {
		return classLoader -> {
			PathScannerClassLoader pathScannerClassLoader = (PathScannerClassLoader)components.get(PathScannerClassLoader.class);
			if (classLoader == pathScannerClassLoader) {
				resetPathScannerClassLoader();
			}
		};
	}
	
	@Override
	public ComponentContainer clear() {
		Map<Class<? extends Component>, Component> components = this.components;
		Synchronizer.execute(getMutexForComponentsId(), () -> { 
			this.components = new ConcurrentHashMap<>();
		});
		if (!components.isEmpty()) {
			BackgroundExecutor.createTask((ThrowingRunnable<?>)() ->
				IterableObjectHelper.deepClear(components, (type, component) -> {
					try {
						if (!(component instanceof PathScannerClassLoader)) {
							component.close();
						} else {
							((PathScannerClassLoader)component).unregister(this, true);
						}					
					} catch (Throwable exc) {
						logError("Exception occurred while closing " + component, exc);
					}
				}),Thread.MIN_PRIORITY
			).async().submit();
		}
		return this;
	}
	
	public static void clearAll() {
		for (ComponentContainer componentContainer : instances) {
			try {
				componentContainer.clear();
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError("Exception occurred while executing clear on " + componentContainer.toString(), exc);
			}
		}
		Cache.clear();
	}
	
	void close(boolean force) {
		if (force || !isUndestroyable) {
			closeResources(() -> !instances.contains(this),  () -> {
				Synchronizer.execute(getMutexForComponentsId(), () -> {
					instances.remove(this);
					unregister(GlobalProperties);
					clear();			
					components = null;
					propertySupplier = null;
					config = null;
				});
			});
		} else {
			throw Throwables.toRuntimeException("Could not close singleton instance " + LazyHolder.COMPONENT_CONTAINER_INSTANCE);
		}
	}
	
	public void close() {
		close(false);
	}
	
	static void closeAll() {
		for (ComponentContainer componentContainer : instances) {
			componentContainer.close(true);
		}
		Cache.clear();
		System.gc();
	}
	
	public static void clearAllCaches() {
		clearAllCaches(true, true, true);
	}
	
	public static void clearAllCaches(boolean closeHuntersResults, boolean closeClassRetrievers) {
		clearAllCaches(closeHuntersResults, closeClassRetrievers, true);
	}
	
	public static void clearAllCaches(boolean closeHuntersResults, boolean closeClassRetrievers, boolean clearFileSystemItemReferences) {
		for (ComponentContainer componentContainer : instances) {
			componentContainer.clearCache(closeHuntersResults, closeClassRetrievers);
		}
		Cache.clear(true, clearFileSystemItemReferences ? null : Cache.pathForFileSystemItems);
		System.gc();
	}
	
	@Override
	public void clearCache(boolean closeHuntersResults, boolean closeClassRetrievers) {
		clearHuntersCache(closeHuntersResults);
		ClassFactory classFactory = (ClassFactory)components.get(ClassFactory.class);
		if (classFactory != null) {
			classFactory.reset(closeClassRetrievers);
		}
		System.gc();
	}
	
	@Override
	public void clearHuntersCache(boolean closeHuntersResults) {
		ByteCodeHunter byteCodeHunter = (ByteCodeHunter)components.get(ByteCodeHunter.class);
		if (byteCodeHunter != null) {
			byteCodeHunter.clearCache(closeHuntersResults);
		}
		ClassHunter classHunter = (ClassHunter)components.get(ClassHunter.class);
		if (classHunter != null) {
			classHunter.clearCache(closeHuntersResults);
		}
		ClassPathHunter classPathHunter = (ClassPathHunter)components.get(ClassPathHunter.class);
		if (classPathHunter != null) {
			classPathHunter.clearCache(closeHuntersResults);
		}
	}
	
	private void resetPathScannerClassLoader() {
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			PathScannerClassLoader classLoader = (PathScannerClassLoader)components.remove(PathScannerClassLoader.class);
			if (classLoader != null) {
				classLoader.unregister(this, true);
			}
		});
	}
	
	private static class LazyHolder {
		private static final ComponentContainer COMPONENT_CONTAINER_INSTANCE = ComponentContainer.create("burningwave.properties").markAsUndestroyable();
		
		private static ComponentContainer getComponentContainerInstance() {
			return COMPONENT_CONTAINER_INSTANCE;
		}
	}

	public boolean isClosed() {
		return !instances.contains(this);
	}
}
