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
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.Executable;
import org.burningwave.core.ManagedLogger;
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
import org.burningwave.core.classes.SearchResult;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemItem.Criteria;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings({"unchecked", "resource"})
public class ComponentContainer implements ComponentSupplier, Properties.Listener, ManagedLogger {

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
	private QueuedTasksExecutor.Task afterInitTask;
	private String instanceId;

	static {
		instances = ConcurrentHashMap.newKeySet();
	}

	ComponentContainer(Supplier<Map<?, ?>> propertySupplier) {
		this.instanceId = getId();
		this.propertySupplier = propertySupplier;
		this.components = new ConcurrentHashMap<>();
		this.config = new Properties();
		checkAndListenTo(GlobalProperties);
		checkAndListenTo(this.config);
		instances.add(this);
	}

	public final static ComponentContainer create(String configFileName) {
		try {
			return new ComponentContainer(() -> {
				try {
					Set<ClassLoader> classLoaders = new HashSet<>();
					classLoaders.add(ComponentContainer.class.getClassLoader());
					classLoaders.add(Thread.currentThread().getContextClassLoader());
					java.util.Properties config = io.github.toolfactory.jvm.util.Properties.loadFromResourcesAndMerge(
						configFileName,
						"priority-of-this-configuration-file",
						classLoaders.toArray(new ClassLoader[classLoaders.size()])
					);
					if (config.isEmpty()) {
						ManagedLoggersRepository.logInfo(ComponentContainer.class::getName, "No custom properties found for file {}", configFileName);
					}
					return config;
				} catch (Throwable exc) {
					return Driver.throwException(exc);
				}
			}).init();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(() -> ComponentContainer.class.getName(), "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			return Driver.throwException(exc);
		}
	}

	public final static ComponentContainer create(Map<?, ?> properties) {
		try {
			return new ComponentContainer(() -> properties).init();
		} catch (Throwable exc){
			ManagedLoggersRepository.logError(() -> ComponentContainer.class.getName(), "Exception while creating  " + ComponentContainer.class.getSimpleName() , exc);
			return Driver.throwException(exc);
		}
	}

	public final static ComponentContainer create() {
		return create((Map<?, ?>)null);
	}

	private ComponentContainer init() {
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
		setAfterInitTask();
		return this;
	}

	private ComponentContainer setAfterInitTask() {
		if (config.getProperty(Configuration.Key.AFTER_INIT) != null) {
			Synchronizer.execute(getMutexForComponentsId(), () -> {
				this.afterInitTask = BackgroundExecutor.createTask(task -> {
					if (preAfterInitCall != null) {
						preAfterInitCall.accept(this);
					}
					Collection<QueuedTasksExecutor.TaskAbst<?, ?>> tasks = resolveProperty(this.config, Configuration.Key.AFTER_INIT, null);
					if (tasks != null) {
						for (QueuedTasksExecutor.TaskAbst<?, ?> iteratedTask : tasks) {
							iteratedTask.waitForFinish();
						}
					}
				});
			});
		}
		return this;
	}

	public ComponentContainer preAfterInit(Consumer<ComponentContainer> preAfterInitCall) {
		this.preAfterInitCall = preAfterInitCall;
		return this;
	}

	public void logConfigProperties() {
		Properties componentContainerConfig = new Properties();
		componentContainerConfig.putAll(this.config);
		componentContainerConfig.keySet().removeAll(GlobalProperties.keySet());
		ManagedLoggersRepository.logInfo(getClass()::getName,
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
						PathScannerClassLoader pathScannerClassLoader = (PathScannerClassLoader)components.get(PathScannerClassLoader.class);
						if (pathScannerClassLoader != null) {
							ClassLoaders.setAsParent(pathScannerClassLoader, resolveProperty(
								this.config,
								PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER
							));
						}
					} else if (keyAsString.equals(PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION)) {
						PathScannerClassLoader pathScannerClassLoader = (PathScannerClassLoader)components.get(PathScannerClassLoader.class);
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
	}

	private String getMutexForComponentsId() {
		return instanceId + "_components";
	}

	public void reInit() {
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			clear();
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
		Map<Class<?>, Component> components = this.components;
		T component = (T)components.get(cls);
		if (component == null) {
			component = Synchronizer.execute(getMutexForComponentsId(), () -> {
				T componentTemp = (T)components.get(cls);
				if (componentTemp == null) {
					QueuedTasksExecutor.Task afterInitTask = this.afterInitTask;
					if (afterInitTask != null) {
						this.afterInitTask = null;
						afterInitTask.submit();
					}
					components.put(cls, componentTemp = (T)componentSupplier.get());
				}
				return componentTemp;
			});
		}
		return component;
	}

	@Override
	public PathScannerClassLoader getPathScannerClassLoader() {
		return getOrCreate(PathScannerClassLoader.class, () -> {
			PathScannerClassLoader classLoader = new ComponentContainer.PathScannerClassLoader(
				resolveProperty(
					this.config,
					PathScannerClassLoader.Configuration.Key.PARENT_CLASS_LOADER
				), getPathHelper(),
				FileSystemItem.Criteria.forClassTypeFiles(
						IterableObjectHelper.resolveStringValue(
							ResolveConfig.forNamedKey(PathScannerClassLoader.Configuration.Key.SEARCH_CONFIG_CHECK_FILE_OPTION)
							.on(config)
						)
				),
				() -> {
					Synchronizer.execute(getMutexForComponentsId(), () -> {
						PathScannerClassLoader cL = (PathScannerClassLoader)components.remove(PathScannerClassLoader.class);
						if (cL != null) {
							cL.unregister(this, true);
						}
					});
				}
			);
			classLoader.register(this);
			return classLoader;
		});
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
		Map<Class<?>, Component> components = this.components;
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			this.components = new ConcurrentHashMap<>();
		});
		if (!components.isEmpty()) {
			BackgroundExecutor.createTask(task ->
				IterableObjectHelper.deepClear(components, (type, component) -> {
					if (!(component instanceof PathScannerClassLoader)) {
						component.close();
					} else {
						((PathScannerClassLoader)component).unregister(this, true);
					}
				}),Thread.MIN_PRIORITY
			).submit();
		}
		return this;
	}

	public static void resetAll() {
		for (ComponentContainer componentContainer : instances) {
			try {
				componentContainer.reset();
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(() -> ComponentContainer.class.getName(), "Exception occurred while executing clear on " + componentContainer.toString(), exc);
			}
		}
		Cache.clear(false);
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
				ManagedLoggersRepository.logError(() -> ComponentContainer.class.getName(), "Exception occurred while closing " + componentContainer, exc);
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
		Synchronizer.execute(getMutexForComponentsId(), () -> {
			if (closeHuntersResults) {
				closeHuntersSearchResults();
			}
			resetClassFactory(closeClassRetrievers);
			Cache.clear(true, Cache.pathForFileSystemItems);
			if (clearFileSystemItemReferences) {
				Cache.pathForFileSystemItems.iterateParallel((path, fileSystemItem) -> {
					fileSystemItem.reset();
				});
			}
		});
	}
	
	public static void clearAll() {
		clearAll(true, true, true);
	}

	public static void clearAll(boolean closeHuntersResults, boolean closeClassRetrievers) {
		clearAll(closeHuntersResults, closeClassRetrievers, true);
	}

	public static void clearAll(boolean closeHuntersResults, boolean closeClassRetrievers, boolean clearFileSystemItemReferences) {
		for (ComponentContainer componentContainer : instances) {
			if (closeHuntersResults) {
				componentContainer.closeHuntersSearchResults();
			}
			componentContainer.resetClassFactory(closeClassRetrievers);
		}
		Cache.clear(true, clearFileSystemItemReferences ? null : Cache.pathForFileSystemItems);
	}

	@Override
	public void resetClassFactory(boolean closeClassRetrievers) {
		ClassFactory classFactory = (ClassFactory)components.get(ClassFactory.class);
		if (classFactory != null) {
			classFactory.reset(closeClassRetrievers);
		}
	}

	@Override
	public void closeHuntersSearchResults() {
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


	public static class PathScannerClassLoader extends org.burningwave.core.classes.PathScannerClassLoader {

		static {
	        ClassLoader.registerAsParallelCapable();
	    }

		Runnable markAsCloseableAlgorithm;
		PathScannerClassLoader(ClassLoader parentClassLoader, PathHelper pathHelper,
			Criteria scanFileCriteria, Runnable markAsCloseableAlgorithm
		) {
			super(parentClassLoader, pathHelper, scanFileCriteria);
			this.markAsCloseableAlgorithm = markAsCloseableAlgorithm;
		}

		public void markAsCloseable() {
			markAsCloseableAlgorithm.run();
		}
	}
}
