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


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.burningwave.core.Component;
import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

import io.github.toolfactory.jvm.Driver;


public class StaticComponentContainer {
	public static class Configuration {
		public static class Key {

			private static final String GROUP_NAME_FOR_NAMED_ELEMENTS = "group-name-for-named-elements";
			private static final String BANNER_HIDE = "banner.hide";
			private static final String BANNER_FILE = "banner.file";
			private static final String BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED = "background-executor.task-creation-tracking.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED = "background-executor.all-tasks-monitoring.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_MINIMUM_ELAPSED_TIME_TO_CONSIDER_A_TASK_AS_PROBABLE_DEAD_LOCKED = "background-executor.all-tasks-monitoring.minimum-elapsed-time-to-consider-a-task-as-probable-dead-locked";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_LOGGER_ENABLED = "background-executor.all-tasks-monitoring.logger.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_INTERVAL = "background-executor.all-tasks-monitoring.interval";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_PROBABLE_DEAD_LOCKED_TASKS_HANDLING_POLICY = "background-executor.all-tasks-monitoring.probable-dead-locked-tasks-handling.policy";
			private static final String JVM_DRIVER_TYPE = "jvm.driver.type";
			private static final String JVM_DRIVER_INIT = "jvm.driver.init";
			private static final String MODULES_EXPORT_ALL_TO_ALL = "modules.export-all-to-all";
			private static final String SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED = "synchronizer.all-threads-monitoring.enabled";
			private static final String SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL = "synchronizer.all-threads-monitoring.interval";
		
		}
		
		public static class Value {
			
			public static String FILE_NAME = "burningwave.static.properties";
			
		}

		public final static Map<String, Object> DEFAULT_VALUES;

		static {
			Map<String, Object> defaultValues =  new HashMap<>();

			defaultValues.put(
				Key.GROUP_NAME_FOR_NAMED_ELEMENTS,
				"Burningwave"
			);

			defaultValues.put(Key.BANNER_HIDE, false);

			defaultValues.put(Key.BANNER_FILE, "org/burningwave/banner.bwb");

			defaultValues.put(
				Key.SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED,
				false
			);

			defaultValues.put(
				Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL,
				90000
			);

			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED,
				true
			);


			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_MINIMUM_ELAPSED_TIME_TO_CONSIDER_A_TASK_AS_PROBABLE_DEAD_LOCKED,
				300000
			);

			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_INTERVAL,
				30000
			);

			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_PROBABLE_DEAD_LOCKED_TASKS_HANDLING_POLICY,
				"log only"
			);

			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED,
				"${" + Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED +"}"
			);

			defaultValues.put(
				Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_LOGGER_ENABLED,
				false
			);

			if (io.github.toolfactory.jvm.Info.Provider.getInfoInstance().getVersion() > 8) {
				defaultValues.put(Key.MODULES_EXPORT_ALL_TO_ALL, true);
			}

			defaultValues.put(
				Key.JVM_DRIVER_INIT,
				false
			);

			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}

	@SuppressWarnings("unused")
	private static final org.burningwave.core.iterable.Properties.Listener GlobalPropertiesListener;

	public static final org.burningwave.core.concurrent.QueuedTasksExecutor.Group BackgroundExecutor;
	public static final org.burningwave.core.jvm.BufferHandler BufferHandler;
	public static final org.burningwave.core.classes.PropertyAccessor ByFieldOrByMethodPropertyAccessor;
	public static final org.burningwave.core.classes.PropertyAccessor ByMethodOrByFieldPropertyAccessor;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
	//Since 9.4.0 (previous version is 9.3.6)
	public static final io.github.toolfactory.jvm.Driver Driver;
	public static final org.burningwave.core.classes.Constructors Constructors;
	public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
	public static final org.burningwave.core.classes.Fields Fields;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.iterable.IterableObjectHelper IterableObjectHelper;
	//Since 9.4.0
	public static final io.github.toolfactory.jvm.Info JVMInfo;
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.classes.Members Members;
	public static final org.burningwave.core.classes.Methods Methods;
	public static final org.burningwave.core.classes.Modules Modules; // null on JDK 8
	public static final org.burningwave.core.Objects Objects;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Resources Resources;
	public static final org.burningwave.core.classes.SourceCodeHandler SourceCodeHandler;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.concurrent.Synchronizer Synchronizer;
	public static final java.util.Map<String, String> SystemProperties;
	public static final org.burningwave.core.concurrent.Thread.Holder ThreadHolder;
	public static final org.burningwave.core.concurrent.Thread.Supplier ThreadSupplier;

	static {
		try {
			long startTime = System.nanoTime();
			Map<String, String> systemProperties = new ConcurrentHashMap<>();
			for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
				systemProperties.put((String)entry.getKey(), (String)entry.getValue());
			}
			SystemProperties = Collections.unmodifiableMap(systemProperties);
			JVMInfo = io.github.toolfactory.jvm.Info.Provider.getInfoInstance();
			Strings = org.burningwave.core.Strings.create();
			Objects = org.burningwave.core.Objects.create();
			Resources = new org.burningwave.core.io.Resources();
			Properties properties = new Properties();
			properties.putAll(org.burningwave.core.jvm.BufferHandler.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.iterable.IterableObjectHelper.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.concurrent.Thread.Supplier.Configuration.DEFAULT_VALUES);
			properties.putAll(Configuration.DEFAULT_VALUES);
			String configFileName = Configuration.Value.FILE_NAME;
			java.util.Properties propertiesFromConfigurationFile = loadPropertiesFromFile(configFileName);
			properties.putAll(propertiesFromConfigurationFile);
			adjustConfigurationValues(properties);
			GlobalPropertiesListener = new org.burningwave.core.iterable.Properties.Listener() {
				@Override
				public <K, V> void processChangeNotification(Properties config, org.burningwave.core.iterable.Properties.Event event, K key, V newValue, V previousValue) {

					if (key instanceof String) {
						String keyAsString = (String)key;
						if (event.name().equals(Event.PUT.name())) {
							if (keyAsString.startsWith("thread-supplier.")) {
								boolean calledByThreadSupplier = false;
								boolean mustThrowException = true;
								//Check that parameter has not modified by org.burningwave.core.concurrent.Thread.Supplier constructor
								for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
									if (stackTraceElement.getClassName().equals(org.burningwave.core.concurrent.Thread.Supplier.class.getName())) {
										calledByThreadSupplier = true;
										if (stackTraceElement.getMethodName().equals("<init>")) {
											mustThrowException = false;
										}
									} else if (calledByThreadSupplier) {
										break;
									}
								}
								if (mustThrowException) {
									Driver.throwException("The reconfiguration of property '{}' is not allowed", key);
								}
							} else if (keyAsString.equals(ManagedLogger.Repository.Configuration.Key.TYPE)) {
								ManagedLogger.Repository toBeReplaced = ManagedLoggersRepository;
								Fields.setStaticDirect(StaticComponentContainer.class, "ManagedLoggersRepository", ManagedLogger.Repository.create(config));
								toBeReplaced.close();
							} else if (keyAsString.startsWith(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED.substring(0, Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED.lastIndexOf(".")))) {
								if (keyAsString.equals(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED)) {
									if (!Objects.toBoolean(config.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED))) {
										BackgroundExecutor.stopAllTasksMonitoring();
									}
								} else {
									BackgroundExecutor.startAllTasksMonitoring(
										StaticComponentContainer.retrieveAllTasksMonitoringConfig()
									);
								}
							} else if (keyAsString.equals(Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED)) {
								BackgroundExecutor.setTasksCreationTrackingFlag(
									Objects.toBoolean(
										config.resolveValue(
											Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED
										)
									)
								);
							} else if (keyAsString.equals(Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED)) {
								if (Objects.toBoolean(config.resolveValue(Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED))) {
									Synchronizer.startAllThreadsMonitoring(
										Objects.toLong(
											config.resolveValue(
												Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL
											)
										)
									);
								} else {
									Synchronizer.stopAllThreadsMonitoring();
								}
							} else if (keyAsString.equals(Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL)) {
								Synchronizer.startAllThreadsMonitoring(
									Objects.toLong(
										config.resolveValue(
											Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL
										)
									)
								);
							}
						}
					}

				}
			}.checkAndListenTo(GlobalProperties = properties);
			IterableObjectHelper = org.burningwave.core.iterable.IterableObjectHelper.create(GlobalProperties);
			String driverClassName = IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.JVM_DRIVER_TYPE));
			if (driverClassName != null) {
				Driver = Executor.get(() -> (Driver)StaticComponentContainer.class.getClassLoader().loadClass(
					driverClassName
				).getDeclaredConstructor().newInstance());
				if (Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.JVM_DRIVER_INIT)))) {
					Driver.init();
				}
			} else if (Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.JVM_DRIVER_INIT)))) {
				Driver = io.github.toolfactory.jvm.Driver.Factory.getNew();
			} else {
				Driver = io.github.toolfactory.jvm.Driver.Factory.getNewDynamic();
			}
			ThreadSupplier = org.burningwave.core.concurrent.Thread.Supplier.create(
				getName("ThreadSupplier"),
				GlobalProperties,
				true
			);
			ThreadHolder = org.burningwave.core.concurrent.Thread.Holder.create(ThreadSupplier, true);
			BackgroundExecutor = org.burningwave.core.concurrent.QueuedTasksExecutor.Group.create(
				"background-executor",
				getAndAdjustConfigurationForBackgroundExecutor()
			);
			Synchronizer = org.burningwave.core.concurrent.Synchronizer.create(
				Optional.ofNullable(IterableObjectHelper.resolveStringValue(onGlobalPropertiesforNamedKey(Configuration.Key.GROUP_NAME_FOR_NAMED_ELEMENTS))).map(nm -> nm + " - ").orElseGet(() -> "") + "Synchronizer",
				true
			);
			if (Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED)))) {
				BackgroundExecutor.setTasksCreationTrackingFlag(true);
			}

			if (!Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BANNER_HIDE)))) {
				showBanner();
			}
			ManagedLoggersRepository = ManagedLogger.Repository.create(GlobalProperties);
			if (propertiesFromConfigurationFile.isEmpty()) {
				ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "No custom properties found for file {}", configFileName);
			}
			ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Instantiated {}", ManagedLoggersRepository.getClass().getName());
			ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName,
				"\n\n\tConfiguration values for static components:\n\n{}\n\n",
				GlobalProperties.toPrettyString(2)
			);
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create(getName("FileSystemHelper"));
			BufferHandler = org.burningwave.core.jvm.BufferHandler.create(GlobalProperties);
			Streams = org.burningwave.core.io.Streams.create();
			Classes = org.burningwave.core.classes.Classes.create();
			Cache = org.burningwave.core.Cache.create();
			Members = org.burningwave.core.classes.Members.create();
			Fields = org.burningwave.core.classes.Fields.create();
			Constructors = org.burningwave.core.classes.Constructors.create();
			Methods = org.burningwave.core.classes.Methods.create();
			ClassLoaders = org.burningwave.core.classes.Classes.Loaders.create();
			ByFieldOrByMethodPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByFieldOrByMethod.create();
			ByMethodOrByFieldPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByMethodOrByField.create();
			SourceCodeHandler = org.burningwave.core.classes.SourceCodeHandler.create();
			Runtime.getRuntime().addShutdownHook(
				ThreadSupplier.getOrCreate(getName("Resource releaser")).setExecutable(thread -> {
					org.burningwave.core.function.ThrowingRunnable<Throwable> closingOperations = () -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "... Waiting for all tasks ending");
							BackgroundExecutor.waitForTasksEnding(true, true);
						});
					};
					closingOperations = closingOperations.andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Closing all component containers");
							ComponentContainer.closeAll();
						});
					});
					closingOperations = closingOperations.andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Closing FileSystemHelper");
							FileSystemHelper.close();
						});
					});
					closingOperations = closingOperations.andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "... Waiting for all tasks ending before shutting down the BackgroundExecutor");
							BackgroundExecutor.waitForTasksEnding(true, true);
						});
					}).andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Shutting down BackgroundExecutor");
							BackgroundExecutor.shutDown(false);
						});

					}).andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Stopping all threads monitoring thread");
							Synchronizer.stopAllThreadsMonitoring(false);
						});
					}).andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Closing ThreadHolder");
							ThreadHolder.close();
						});
					}).andThen(() -> {
						Executor.runAndIgnoreExceptions(() -> {
							ManagedLoggersRepository.logInfo(StaticComponentContainer.class::getName, "Shutting down ThreadSupplier");
							ThreadSupplier.shutDownAll();
						});
					});

					Executor.runAndIgnoreExceptions(closingOperations);

				})
			);
			ManagedLoggersRepository.logInfo(
				StaticComponentContainer.class::getName,
				"{} initialized in {} seconds",
				StaticComponentContainer.class.getName(),
				Double.valueOf(((double) (System.nanoTime() - startTime)) / 1_000_000_000).toString()
			);
			if (Objects.toBoolean(
					IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(
						Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED
					)
				)
			)) {
				Synchronizer.startAllThreadsMonitoring(
					Objects.toLong(
						IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL))
					)
				);
			}
			if (Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED)))) {
				BackgroundExecutor.startAllTasksMonitoring(
					retrieveAllTasksMonitoringConfig()
				);
			}

			if (JVMInfo.getVersion() > 8) {
				Modules = org.burningwave.core.classes.Modules.create();
				if (Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.MODULES_EXPORT_ALL_TO_ALL)))) {
					try {
						Modules.exportAllToAll();
					} catch (Throwable exc) {
						ManagedLoggersRepository.logError(StaticComponentContainer.class::getName, "Unable to export all modules to all modules", exc);
					}
				}
			} else {
				Modules = null;
			}
		} catch (Throwable exc){
			exc.printStackTrace();
			throw new RuntimeException(exc);
		}

	}
	
	private static org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig.ForNamedKey onGlobalPropertiesforNamedKey(String key) {
		return org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig.forNamedKey(key).on(GlobalProperties);
	}

	private static void adjustConfigurationValues(Properties properties) {
		String defaultValuesSeparator = (String)org.burningwave.core.iterable.IterableObjectHelper.Configuration.DEFAULT_VALUES.get(
			org.burningwave.core.iterable.IterableObjectHelper.Configuration.Key.DEFAULT_VALUES_SEPERATOR		
		);
		org.burningwave.core.iterable.IterableObjectHelper temporaryPropertyResolver = org.burningwave.core.iterable.IterableObjectHelper.create(properties);
		((org.burningwave.core.iterable.IterableObjectHelperImpl)temporaryPropertyResolver).checkAndUnregister(properties);
		String propertyValue = properties.getProperty(org.burningwave.core.iterable.IterableObjectHelper.Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES);
		propertyValue = propertyValue.replace(defaultValuesSeparator, temporaryPropertyResolver.getDefaultValuesSeparator());
		properties.put(org.burningwave.core.iterable.IterableObjectHelper.Configuration.Key.PARELLEL_ITERATION_APPLICABILITY_OUTPUT_COLLECTION_ENABLED_TYPES, propertyValue);
		propertyValue = properties.getProperty(org.burningwave.core.ManagedLogger.Repository.Configuration.Key.WARN_LOGGING_DISABLED_FOR);
		propertyValue = propertyValue.replace(defaultValuesSeparator, temporaryPropertyResolver.getDefaultValuesSeparator());
		properties.put(org.burningwave.core.ManagedLogger.Repository.Configuration.Key.WARN_LOGGING_DISABLED_FOR, propertyValue);
	}

	private static Map<String, Object> getAndAdjustConfigurationForBackgroundExecutor() {
		if (IterableObjectHelper.resolveStringValues(
				ResolveConfig.forAllKeysThat((Predicate<String>)key ->
					key.matches("background-executor.queue-task-executor\\[\\d\\]\\.priority")
				).on(GlobalProperties)
			).isEmpty()
		) {
			GlobalProperties.put("background-executor.queue-task-executor[0].priority", Thread.MIN_PRIORITY);
			if (GlobalProperties.get("background-executor.queue-task-executor[0].name") == null) {
				GlobalProperties.put("background-executor.queue-task-executor[0].name", "Low priority tasks");
			}
			GlobalProperties.put("background-executor.queue-task-executor[1].priority", Thread.NORM_PRIORITY);
			if (GlobalProperties.get("background-executor.queue-task-executor[1].name") == null) {
				GlobalProperties.put("background-executor.queue-task-executor[1].name", "Normal priority tasks");
			}
			GlobalProperties.put("background-executor.queue-task-executor[2].priority", Thread.MAX_PRIORITY);
			if (GlobalProperties.get("background-executor.queue-task-executor[2].name") == null) {
				GlobalProperties.put("background-executor.queue-task-executor[2].name", "High priority tasks");
			}
		}
		Map<String, Object> configuration = IterableObjectHelper.resolveValues(
			ResolveConfig.forAllKeysThat((Predicate<String>)key ->
				key.startsWith("background-executor.")
			).on(GlobalProperties)
		);
		configuration.put("background-executor.thread-supplier", ThreadSupplier);
		configuration.put("background-executor.name", getName("BackgroundExecutor"));
		configuration.put("background-executor.daemon", true);
		configuration.put("background-executor.undestroyable-from-external", true);
		Map<String, Object> unvalidEntries = IterableObjectHelper.resolveValues(
			ResolveConfig.forAllKeysThat((Predicate<String>)key ->
				key.endsWith("].daemon")
			).on(GlobalProperties)
		);
		Iterator<Map.Entry<String, Object>> unvalidEntriesItr = unvalidEntries.entrySet().iterator();
		while (unvalidEntriesItr.hasNext()) {
			String key = unvalidEntriesItr.next().getKey();
			configuration.remove(key);
			GlobalProperties.remove(key);
		}
		return configuration;
	}


	private static java.util.Properties loadPropertiesFromFile(String fileName) throws IOException, ParseException {
		Set<ClassLoader> classLoaders = new HashSet<>();
		classLoaders.add(StaticComponentContainer.class.getClassLoader());
		classLoaders.add(Thread.currentThread().getContextClassLoader());

		return io.github.toolfactory.jvm.util.Properties.loadFromResourcesAndMerge(
			fileName,
			"priority-of-this-configuration-file",
			classLoaders.toArray(new ClassLoader[classLoaders.size()])
		);
	}


	private static String getName(String simpleName) {
		return Optional.ofNullable(IterableObjectHelper.resolveStringValue(
			onGlobalPropertiesforNamedKey(Configuration.Key.GROUP_NAME_FOR_NAMED_ELEMENTS))
		).map(nm -> nm + " - ").orElseGet(() -> "") + simpleName;
	}

	private static final QueuedTasksExecutor.Group.TasksMonitorer.Config retrieveAllTasksMonitoringConfig() {
		String probablyDeadLockedThreadsHandlingPolicy = IterableObjectHelper.resolveStringValue(
			onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_PROBABLE_DEAD_LOCKED_TASKS_HANDLING_POLICY)
		);
		return new QueuedTasksExecutor.Group.TasksMonitorer.Config().setAllTasksLoggerEnabled(
			Objects.toBoolean(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_LOGGER_ENABLED)))
		).setInterval(
			Objects.toLong(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_INTERVAL)))
		).setMinimumElapsedTimeToConsiderATaskAsProbablyDeadLocked(
			Objects.toLong(IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_MINIMUM_ELAPSED_TIME_TO_CONSIDER_A_TASK_AS_PROBABLE_DEAD_LOCKED)))
		).setMarkAsProbableDeadLocked(
			probablyDeadLockedThreadsHandlingPolicy.toLowerCase().contains("mark as probable dead locked")
		).setKillProbableDeadLockedTasks(
			probablyDeadLockedThreadsHandlingPolicy.toLowerCase().contains("abort")
		);
	}

	private static void showBanner() throws IOException {
		try (InputStream inputStream = Resources.getAsInputStream(
				IterableObjectHelper.resolveValue(onGlobalPropertiesforNamedKey(Configuration.Key.BANNER_FILE)),
			Component.class.getClassLoader(),
			Thread.currentThread().getContextClassLoader()
		).getValue()) {
			List<String> bannerList = Arrays.asList(
				Resources.getAsStringBuffer(
					inputStream
				).toString().split("-------------------------------------------------------------------------------------------------------------")
			);
			Collections.shuffle(bannerList);
			System.out.println("\n" + bannerList.get(new Random().nextInt(bannerList.size())));
		}
	}

}
