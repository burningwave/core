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
 * Copyright (c) 2019-2021 Roberto Gentili
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
import static org.burningwave.core.assembler.StaticComponentContainer.ClassLoaders;
import static org.burningwave.core.assembler.StaticComponentContainer.Classes;
import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.Fields;
import static org.burningwave.core.assembler.StaticComponentContainer.GlobalProperties;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Strings;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import org.burningwave.core.classes.ClassPathScanner;
import org.burningwave.core.classes.CodeExecutor;
import org.burningwave.core.classes.ExecuteConfig;
import org.burningwave.core.classes.FunctionalInterfaceFactory;
import org.burningwave.core.classes.JavaMemoryCompiler;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.classes.SearchResult;
import org.burningwave.core.concurrent.QueuedTaskExecutor;
import org.burningwave.core.concurrent.QueuedTaskExecutor.Task;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;


@SuppressWarnings({"unchecked", "resource"})
public class ComponentContainer implements ComponentSupplier, Properties.Listener {

	public static class Configuration {

		public static class Key {

			public static final String AFTER_INIT = "component-container.after-init.operations";

		}

		public static class Value {

			public static String FILE_NAME = "burningwave.properties";

		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues = new HashMap<>();

			defaultValues.put(Configuration.Key.AFTER_INIT + CodeExecutor.Configuration.Key.PROPERTIES_FILE_IMPORTS_SUFFIX,
				"${"+ CodeExecutor.Configuration.Key.COMMON_IMPORTS + "}" + IterableObjectHelper.getDefaultValuesSeparator() +
				"${"+ Configuration.Key.AFTER_INIT + ".additional-imports}" + IterableObjectHelper.getDefaultValuesSeparator() +
				Arrays.class.getName() + IterableObjectHelper.getDefaultValuesSeparator() +
				SearchResult.class.getName() + IterableObjectHelper.getDefaultValuesSeparator()
			);
			defaultValues.put(
				Configuration.Key.AFTER_INIT + CodeExecutor.Configuration.Key.PROPERTIES_FILE_EXECUTOR_NAME_SUFFIX,
				ComponentContainer.class.getPackage().getName() + ".AfterInitOperationsExecutor"
			);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	private static Collection<ComponentContainer> instances;
	private Map<Class<?>, Component> components;
	private Supplier<Map<?, ?>> propertySupplier;
	private Properties config;
	private boolean isUndestroyable;
	private Consumer<ComponentContainer> preAfterInitCall;
	private QueuedTaskExecutor.Task afterInitTask;
	private String instanceId;

	static {
		instances = ConcurrentHashMap.newKeySet();
	}

	ComponentContainer(Supplier<Map<?, ?>> propertySupplier) {
		this.instanceId = getId();
		this.propertySupplier = propertySupplier;
		this.config = new Properties();
		checkAndListenTo(GlobalProperties);
		checkAndListenTo(this.config);
		instances.add(this);
	}

	public final static ComponentContainer create(String configFileName) {
		try {
			return new ComponentContainer(() -> {
				try {
					Set<java.lang.ClassLoader> classLoaders = new HashSet<>();
					classLoaders.add(ComponentContainer.class.getClassLoader());
					classLoaders.add(Thread.currentThread().getContextClassLoader());
					java.util.Properties config = io.github.toolfactory.jvm.util.Properties.loadFromResourcesAndMerge(
						configFileName,
						"priority-of-this-configuration-file",
						classLoaders.toArray(new java.lang.ClassLoader[classLoaders.size()])
					);
					if (config.isEmpty()) {
						ManagedLoggerRepository.logInfo(ComponentContainer.class::getName, "No custom properties found for file {}", configFileName);
					}
					return config;
				} catch (Throwable exc) {
					return Driver.throwException(exc);
				}
			}).init();
		} catch (Throwable exc){
			ManagedLoggerRepository.logError(() -> ComponentContainer.class.getName(), "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			return Driver.throwException(exc);
		}
	}

	public final static ComponentContainer create(Map<?, ?> properties) {
		try {
			return new ComponentContainer(() -> properties).init();
		} catch (Throwable exc){
			ManagedLoggerRepository.logError(() -> ComponentContainer.class.getName(), "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			return Driver.throwException(exc);
		}
	}

	public final static ComponentContainer create() {
		return create((Map<?, ?>)null);
	}

	private ComponentContainer init() {
		this.components = null;
		Properties config = new Properties();
		TreeMap<Object, Object> defaultProperties = new TreeMap<>();
		defaultProperties.putAll(Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(CodeExecutor.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(PathHelper.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(JavaMemoryCompiler.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassFactory.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ByteCodeHunter.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassHunter.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassPathHunter.Configuration.DEFAULT_VALUES);
		defaultProperties.putAll(ClassPathScanner.Configuration.DEFAULT_VALUES);
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

		logConfigProperties();
		return this;
	}

	private ComponentContainer setAndlaunchAfterInitTask() {
		if (config.getProperty(Configuration.Key.AFTER_INIT) != null) {
			Synchronizer.execute(getMutexForComponentsId(), () -> {
				this.afterInitTask = BackgroundExecutor.createTask(task -> {
					if (preAfterInitCall != null) {
						preAfterInitCall.accept(this);
					}
					Collection<QueuedTaskExecutor.TaskAbst<?, ?>> tasks = resolveProperty(this.config, Configuration.Key.AFTER_INIT, null);
					if (tasks != null) {
						for (QueuedTaskExecutor.TaskAbst<?, ?> iteratedTask : tasks) {
							iteratedTask.waitForFinish();
						}
					}
					this.afterInitTask = null;
				}).submit();
			});
		}
		return this;
	}

	private Map<Class<?>, Component> checkAndInitComponentMapAndAfterInitTask() {
		if (this.components == null) {
			Synchronizer.execute(getMutexForComponentsId(), () -> {
				if (this.components == null) {
					this.components = new ConcurrentHashMap<>();
					setAndlaunchAfterInitTask();
				}
			});
		}
		return this.components;
	}

	public ComponentContainer waitForAfterInitTask() {
		if (!waitForAfterInitTaskIfNotNull()) {
			//Ensure that component map was initialized and that the after init task was launched
			executeOnComponentMap(components -> {
				//Testing that component map is not null
				components.getClass();
			});
			waitForAfterInitTaskIfNotNull();
		}
		return this;
	}

	private boolean waitForAfterInitTaskIfNotNull() {
		QueuedTaskExecutor.Task afterInitTask = this.afterInitTask;
		if (afterInitTask != null) {
			return afterInitTask.waitForFinish() != null;
		}
		return false;
	}

	public ComponentContainer preAfterInit(Consumer<ComponentContainer> preAfterInitCall) {
		this.preAfterInitCall = preAfterInitCall;
		return this;
	}

	public void logConfigProperties() {
		Properties componentContainerConfig = new Properties();
		componentContainerConfig.putAll(this.config);
		componentContainerConfig.keySet().removeAll(GlobalProperties.keySet());
		ManagedLoggerRepository.logInfo(getClass()::getName,
			"\n\n\tConfiguration values for dynamic components:\n\n{}\n\n",
			componentContainerConfig.toPrettyString(2)
		);
	}

	ComponentContainer markAsUndestroyable() {
		this.isUndestroyable = true;
		return this;
	}

	@Override
	public void processChangeNotification(Properties properties, Event event, Object key, Object newValue, Object oldValue) {
		executeOnComponentMap(components -> {
			if (properties == GlobalProperties) {
				if (event.name().equals(Event.PUT.name())) {
					config.put(key, newValue);
				} else if (event.name().equals(Event.REMOVE.name())) {
					config.remove(key);
				}
			} else if (properties == this.config) {
				if (event.name().equals(Event.PUT.name())) {
					if (key instanceof String) {
						String keyAsString = (String)key;
						if (keyAsString.equals(PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER)) {
							ClassLoader pathScannerClassLoader = (ClassLoader)components.get(ClassLoader.class);
							if (pathScannerClassLoader != null) {
								ClassLoaders.setAsParent(pathScannerClassLoader, resolveProperty(
									this.config,
									PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER
								));
							}
						} else if (keyAsString.equals(PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION)) {
							ClassLoader pathScannerClassLoader = (ClassLoader)components.get(ClassLoader.class);
							if (pathScannerClassLoader != null) {
								Fields.setDirect(
									pathScannerClassLoader,
									"fileFilterAndProcessor",
									FileSystemItem.Criteria.forClassTypeFiles(
										IterableObjectHelper.resolveStringValue(
											ResolveConfig.forNamedKey(PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION)
											.on(config)
										)
									)
								);
							}
						}
					}
				}
			}
		});
	}

	private String getMutexForComponentsId() {
		return instanceId + "_components";
	}

	public void reInit() {
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			reset();
			init();
		});
	}

	public static ComponentContainer getInstance() {
		return Holder.getComponentContainerInstance();
	}

	public String getConfigProperty(String propertyName) {
		return IterableObjectHelper.resolveStringValue(ResolveConfig.forNamedKey(propertyName).on(config));
	}

	public String getConfigProperty(String propertyName, Map<String, String> defaultValues) {
		return IterableObjectHelper.resolveStringValue(
			ResolveConfig.forNamedKey(propertyName).on(config).withDefaultValues(defaultValues)
		);
	}

	public Object setConfigProperty(String propertyName, Object propertyValue) {
		return config.put(propertyName, propertyValue);
	}

	public Object removeConfigProperty(String propertyName) {
		return config.remove(propertyName);
	}

	@Override
	public <I, T extends Component> T getOrCreate(Class<I> cls, Supplier<I> componentSupplier) {
		return executeOnComponentMap(components -> {
			T component = (T)components.get(cls);
			if (component != null) {
				return component;
			}
			return Synchronizer.execute(getMutexForComponentsId() + "_" + cls.getName(), () -> {
				T componentTemp;
				if ((componentTemp = (T)components.get(cls)) == null) {
					components.put(cls, componentTemp = (T)componentSupplier.get());
				}
				return componentTemp;
			});
		});
	}

	private void executeOnComponentMap(Consumer<Map<Class<?>, Component>> executor) {
		Map<Class<?>, Component> components = this.components;
		try {
			executor.accept(components);
		} catch (NullPointerException exc) {
			if (components != null) {
				throw exc;
			}
			executor.accept(checkAndInitComponentMapAndAfterInitTask());
		}
	}

	private <T> T executeOnComponentMap(Function<Map<Class<?>, Component>, T> executor) {
		Map<Class<?>, Component> components = this.components;
		try {
			return executor.apply(components);
		} catch (NullPointerException exc) {
			if (components != null) {
				throw exc;
			}
			return executor.apply(checkAndInitComponentMapAndAfterInitTask());
		}
	}

	@Override
	public org.burningwave.core.classes.PathScannerClassLoader getPathScannerClassLoader() {
		return getOrCreate(
			ClassLoader.class,
			() -> {
				return new ComponentContainer.ClassLoader(this);
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
				(Supplier<?>)() -> resolveProperty(
					this.config, ClassFactory.Configuration.Key.DEFAULT_CLASS_LOADER
				),
				config
			)
		);
	}

	@Override
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
				getPathHelper(),
				(Supplier<?>)() -> resolveProperty(
					this.config,
					ClassHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER
				),
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
				getPathHelper(),
				(Supplier<?>)() -> resolveProperty(
					this.config,
					ClassPathHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER
				),
				config
			)
		);
	}

	@Override
	public ByteCodeHunter getByteCodeHunter() {
		return getOrCreate(ByteCodeHunter.class, () ->
			ByteCodeHunter.create(
				getPathHelper(),
				(Supplier<?>)() -> resolveProperty(
					this.config,
					ByteCodeHunter.Configuration.Key.DEFAULT_PATH_SCANNER_CLASS_LOADER
				),
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

	public <T> T resolveProperty(Map<?, ?> properties, String configKey) {
		return resolveProperty(properties, configKey, null);
	}

	public <T> T resolveProperty(Map<?, ?> properties, String configKey, Map<?, ?> defaultValues) {
		T object = IterableObjectHelper.resolveValue(ResolveConfig.forNamedKey(configKey).on(config).withDefaultValues(defaultValues));
		if (object instanceof String) {
			ExecuteConfig.ForProperties executeConfig = ExecuteConfig.fromDefaultProperties()
			.setPropertyName(configKey)
			.withParameter(this)
			.useAsParentClassLoader(Classes.getClassLoader(Executable.class))
			.setClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(new HashSet<>());
			if (defaultValues != null) {
				executeConfig.withDefaultPropertyValues(defaultValues);
			}
			return getCodeExecutor().execute(
				executeConfig
			);
		} else if (object instanceof Function) {
			return (T)(Supplier<?>)() -> ((Function<ComponentSupplier, ?>)object).apply(this);
		} else {
			return object;
		}
	}

	public ComponentContainer reset() {
		clear();
		return executeOnComponentMap(components -> {
			waitForAfterInitTaskIfNotNull();
			Synchronizer.execute(getMutexForComponentsId(), () -> {
				this.components = new ConcurrentHashMap<>();
			});
			if (!components.isEmpty()) {
				BackgroundExecutor.createTask(task -> {
					AtomicReference<ClassLoader> classLoaderWrapper = new AtomicReference<>();
					IterableObjectHelper.deepClear(components, (type, component) -> {
						if (!(component instanceof ClassLoader)) {
							component.close();
						} else {
							classLoaderWrapper.set(((ClassLoader)component));
						}
					});
					ClassLoader classLoader = classLoaderWrapper.get();
					if (classLoader != null) {
						classLoader.unregister(this, true, true);
					}
				},Thread.MIN_PRIORITY).submit();
			}
			return this;
		});

	}

	public static void resetAll() {
		for (ComponentContainer componentContainer : instances) {
			try {
				componentContainer.reset();
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(
					ComponentContainer.class::getName,
					"Exception occurred while executing reset on {}",
					exc,
					componentContainer.toString()
				);
			}
		}
	}

	void close(boolean force) {
		if (force || !isUndestroyable) {
			instances.remove(this);
			closeResources(() -> instanceId == null, task -> {
				checkAndUnregister(GlobalProperties);
				checkAndUnregister(config);
				clear();
				components = null;
				propertySupplier = null;
				config = null;
				instanceId = null;
			});
		} else {
			Driver.throwException("Could not close singleton instance {}", Holder.INSTANCE);
		}
	}

	@Override
	public void close() {
		close(false);
	}

	static void closeAll() {
		boolean clearCache = !instances.isEmpty();
		for (ComponentContainer componentContainer : instances) {
			try {
				componentContainer.close(true);
			} catch (Throwable exc) {
				ManagedLoggerRepository.logError(() -> ComponentContainer.class.getName(), "Exception occurred while closing " + componentContainer, exc);
			}
		}
		if (clearCache) {
			Cache.clear(false);
		}
	}

	@Override
	public void clear() {
		clear(true, true, true);
	}

	@Override
	public void clear(boolean closeHuntersResults, boolean closeClassRetrievers, boolean clearFileSystemItemReferences) {
		if (closeHuntersResults) {
			closeHuntersSearchResults();
		}
		resetClassFactory(closeClassRetrievers);
		if (!closeHuntersResults && !closeClassRetrievers) {
			waitForAfterInitTaskIfNotNull();
		}
		Cache.clear(true, Cache.pathForFileSystemItems);
		if (clearFileSystemItemReferences) {
			Cache.pathForFileSystemItems.iterateParallel((path, fileSystemItem) -> {
				fileSystemItem.reset();
			});
		}
	}

	public static void clearAll() {
		clearAll(true, true, true);
	}

	public static void clearAll(boolean closeHuntersResults, boolean closeClassRetrievers) {
		clearAll(closeHuntersResults, closeClassRetrievers, true);
	}

	public synchronized static void clearAll(boolean closeHuntersResults, boolean closeClassRetrievers, boolean clearFileSystemItemReferences) {
		for (ComponentContainer componentContainer : instances) {
			componentContainer.waitForAfterInitTaskIfNotNull();
			if (closeHuntersResults) {
				componentContainer.closeHuntersSearchResults();
			}
			componentContainer.resetClassFactory(closeClassRetrievers);
		}
		Cache.clear(true, Cache.pathForFileSystemItems);
		if (clearFileSystemItemReferences) {
			Cache.pathForFileSystemItems.iterateParallel((path, fileSystemItem) -> {
				fileSystemItem.reset();
			});
		}
	}

	@Override
	public void resetClassFactory(boolean closeClassRetrievers) {
		executeOnComponentMap(components -> {
			waitForAfterInitTaskIfNotNull();
			ClassFactory classFactory = (ClassFactory)components.get(ClassFactory.class);
			if (classFactory != null) {
				classFactory.reset(closeClassRetrievers);
			}
		});
	}

	@Override
	public void closeHuntersSearchResults() {
		executeOnComponentMap(components -> {
			waitForAfterInitTaskIfNotNull();
			ClassPathScanner.Abst<?, ?, ?> hunter = (ClassPathScanner.Abst<?, ?, ?>)components.get(ByteCodeHunter.class);
			if (hunter != null) {
				hunter.closeSearchResults();
			}
			 hunter = (ClassPathScanner.Abst<?, ?, ?>)components.get(ClassHunter.class);
			if (hunter != null) {
				hunter.closeSearchResults();
			}
			hunter = (ClassPathScanner.Abst<?, ?, ?>)components.get(ClassPathHunter.class);
			if (hunter != null) {
				hunter.closeSearchResults();
			}
		});
	}


	private static class Holder {
		private static final ComponentContainer INSTANCE = ComponentContainer.create(Configuration.Value.FILE_NAME).markAsUndestroyable();

		private static ComponentContainer getComponentContainerInstance() {
			return INSTANCE;
		}
	}

	public boolean isClosed() {
		return !instances.contains(this);
	}


	private static class ClassLoader extends org.burningwave.core.classes.PathScannerClassLoader {
		private ComponentContainer componentContainer;
		private Map<Class<?>, Component> components;


		static {
	        ClassLoader.registerAsParallelCapable();
	    }


		ClassLoader(
			ComponentContainer componentContainer
		) {
			super(
				componentContainer.resolveProperty(
					componentContainer.config,
					PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER
				),
				componentContainer.getPathHelper(),
				FileSystemItem.Criteria.forClassTypeFiles(
					IterableObjectHelper.resolveStringValue(
						ResolveConfig.forNamedKey(PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION)
						.on(componentContainer.config)
					)
				)
			);
			this.components = componentContainer.components;
			this.componentContainer = componentContainer;
			register(componentContainer);
		}


		@Override
		public synchronized boolean unregister(Object client, boolean close, boolean markAsCloseable) {
			boolean closeCalled = super.unregister(client, close, markAsCloseable);
			if ((!isClosed || !closeCalled) && markAsCloseable) {
				Map<Class<?>, Component> components = this.components;
				ComponentContainer componentContainer = this.componentContainer;
				if (components == null || componentContainer == null) {
					if (!isClosed) {
						throw new IllegalStateException(Strings.compile("components map is null but {} is not closed", this));
					}
					return isClosed;
				}
				closeCalled = Synchronizer.execute(
					componentContainer.getMutexForComponentsId(),
					() -> {
						ClassLoader cL = (ClassLoader)components.remove(ClassLoader.class);
						if (cL != null) {
							if (cL != this) {
								throw new IllegalStateException(Strings.compile("{} is not the same instance of {} in the components map", this, cL));
							}
							return super.unregister(componentContainer, close, markAsCloseable);
						}
						return false;
					}
				);
			}
			return isClosed || closeCalled;
		}

		@Override
		protected Task closeResources() {
			return closeResources(
				ComponentContainer.ClassLoader.class.getName() + "@" + System.identityHashCode(this),
				() ->
					this.componentContainer == null,
				task -> {
					super.closeResources().waitForFinish();
					components = null;
					componentContainer = null;
					if (this.getClass().equals(ClassLoader.class)) {
						ManagedLoggerRepository.logInfo(getClass()::getName, "ClassLoader {} successfully closed", this);
					}
				}
			);
		}
	}
}
