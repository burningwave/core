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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.concurrent.QueuedTasksExecutor;
import org.burningwave.core.function.Executor;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unused")
public class StaticComponentContainer {
	public static class Configuration {
		public static class Key {
			
			private static final String GROUP_NAME_FOR_NAMED_ELEMENTS = "group-name-for-named-elements";
			private static final String HIDE_BANNER_ON_INIT = "hide-banner-on-init";
			private static final String BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED = "background-executor.task-creation-tracking.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED = "background-executor.all-tasks-monitoring.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_MINIMUM_ELAPSED_TIME_TO_CONSIDER_A_TASK_AS_PROBABLE_DEAD_LOCKED = "background-executor.all-tasks-monitoring.minimum-elapsed-time-to-consider-a-task-as-probable-dead-locked";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_LOGGER_ENABLED = "background-executor.all-tasks-monitoring.logger.enabled";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_INTERVAL = "background-executor.all-tasks-monitoring.interval";
			private static final String BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_PROBABLE_DEAD_LOCKED_TASKS_HANDLING_POLICY = "background-executor.all-tasks-monitoring.probable-dead-locked-tasks-handling.policy";
			private static final String SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED = "synchronizer.all-threads-monitoring.enabled";
			private static final String SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL = "synchronizer.all-threads-monitoring.interval";	
		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues =  new HashMap<>(); 
			
			defaultValues.put(
				Key.GROUP_NAME_FOR_NAMED_ELEMENTS, 
				"Burningwave"
			);
			
			defaultValues.put(Key.HIDE_BANNER_ON_INIT, false);
			
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
			
			DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
		}
	}
	
	private static final org.burningwave.core.iterable.Properties.Listener GlobalPropertiesListener;
	
	public static final org.burningwave.core.concurrent.QueuedTasksExecutor.Group BackgroundExecutor;
	public static final org.burningwave.core.classes.PropertyAccessor ByFieldOrByMethodPropertyAccessor;
	public static final org.burningwave.core.classes.PropertyAccessor ByMethodOrByFieldPropertyAccessor;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferHandler ByteBufferHandler;
	public static final org.burningwave.core.Cache Cache;
	public static final org.burningwave.core.classes.Classes Classes;
	public static final org.burningwave.core.classes.Classes.Loaders ClassLoaders;
	public static final org.burningwave.core.classes.Constructors Constructors;
	public static final org.burningwave.core.io.FileSystemHelper FileSystemHelper;
	public static final org.burningwave.core.classes.Fields Fields;
	public static final org.burningwave.core.iterable.Properties GlobalProperties;
	public static final org.burningwave.core.iterable.IterableObjectHelper IterableObjectHelper;
	public static final org.burningwave.core.jvm.JVMInfo JVMInfo;
	public static final org.burningwave.core.jvm.LowLevelObjectsHandler LowLevelObjectsHandler;
	public static final org.burningwave.core.ManagedLogger.Repository ManagedLoggersRepository;
	public static final org.burningwave.core.classes.Members Members;
	public static final org.burningwave.core.classes.Methods Methods;
	public static final org.burningwave.core.Objects Objects;
	public static final org.burningwave.core.Strings.Paths Paths;
	public static final org.burningwave.core.io.Resources Resources;
	public static final org.burningwave.core.classes.SourceCodeHandler SourceCodeHandler;
	public static final org.burningwave.core.io.Streams Streams;
	public static final org.burningwave.core.Strings Strings;
	public static final org.burningwave.core.concurrent.Synchronizer Synchronizer;
	public static final org.burningwave.core.concurrent.Thread.Holder ThreadHolder;
	public static final org.burningwave.core.concurrent.Thread.Supplier ThreadSupplier;
	public static final org.burningwave.core.Throwables Throwables;
	
	static {
		try {
			long startTime = System.nanoTime();
			Strings = org.burningwave.core.Strings.create();
			Throwables = org.burningwave.core.Throwables.create();
			Objects = org.burningwave.core.Objects.create();
			Resources = new org.burningwave.core.io.Resources();
			Properties properties = new Properties();
			properties.putAll(org.burningwave.core.io.Streams.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.iterable.IterableObjectHelper.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.concurrent.Thread.Supplier.Configuration.DEFAULT_VALUES);
			properties.putAll(Configuration.DEFAULT_VALUES);
			Map.Entry<org.burningwave.core.iterable.Properties, URL> propBag =
				Resources.loadFirstOneFound(properties, "burningwave.static.properties", "burningwave.static.default.properties");
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
									Throwables.throwException("The reconfiguration of property '{}' is not allowed", key);
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
			}.listenTo(GlobalProperties = propBag.getKey());
			IterableObjectHelper = org.burningwave.core.iterable.IterableObjectHelper.create(GlobalProperties);
			ThreadSupplier = org.burningwave.core.concurrent.Thread.Supplier.create(
				getName("Thread supplier"),
				GlobalProperties,
				true
			);
			ThreadHolder = new org.burningwave.core.concurrent.Thread.Holder(ThreadSupplier);
			BackgroundExecutor = org.burningwave.core.concurrent.QueuedTasksExecutor.Group.create(
				getName("BackgroundExecutor"),
				ThreadSupplier,
				ThreadSupplier, 
				ThreadSupplier,
				true,
				true
			);
			Synchronizer = org.burningwave.core.concurrent.Synchronizer.create(
				Optional.ofNullable(GlobalProperties.resolveStringValue(Configuration.Key.GROUP_NAME_FOR_NAMED_ELEMENTS)).map(nm -> nm + " - ").orElseGet(() -> "") + "Synchronizer", 
				true
			);
			if (Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED))) {
				BackgroundExecutor.setTasksCreationTrackingFlag(true);
			}
			if (Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_ENABLED))) {
				BackgroundExecutor.startAllTasksMonitoring(
					retrieveAllTasksMonitoringConfig()
				);
			}			
			
			if (!Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.HIDE_BANNER_ON_INIT))) {
				showBanner();
			}
			ManagedLoggersRepository = ManagedLogger.Repository.create(GlobalProperties);
			URL globalPropertiesFileUrl = propBag.getValue();
			if (globalPropertiesFileUrl != null) {
				ManagedLoggersRepository.logInfo(
					() -> StaticComponentContainer.class.getName(), 
					"Building static components by using " + Executor.get(() ->
						URLDecoder.decode(
							globalPropertiesFileUrl.toString(), StandardCharsets.UTF_8.name()
						)
					)
				);
			} else {
				ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Building static components by using default configuration");
			}
			ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Instantiated {}", ManagedLoggersRepository.getClass().getName());
			ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(),
				"\n\n\tConfiguration values for static components:\n\n{}\n\n",
				GlobalProperties.toPrettyString(2)
			);
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create(getName("FileSystemHelper"));
			JVMInfo = org.burningwave.core.jvm.JVMInfo.create();
			ByteBufferHandler = org.burningwave.core.jvm.LowLevelObjectsHandler.ByteBufferHandler.create();
			Streams = org.burningwave.core.io.Streams.create(GlobalProperties);
			synchronized (org.burningwave.core.jvm.LowLevelObjectsHandler.class) {
				LowLevelObjectsHandler = org.burningwave.core.jvm.LowLevelObjectsHandler.create();
				org.burningwave.core.jvm.LowLevelObjectsHandler.class.notifyAll();
			}
			Classes = org.burningwave.core.classes.Classes.create();
			ClassLoaders = org.burningwave.core.classes.Classes.Loaders.create();
			Cache = org.burningwave.core.Cache.create();
			synchronized (org.burningwave.core.classes.Members.class) {
				Members = org.burningwave.core.classes.Members.create();
				Fields = org.burningwave.core.classes.Fields.create();
				Constructors = org.burningwave.core.classes.Constructors.create();
				Methods = org.burningwave.core.classes.Methods.create();
				org.burningwave.core.classes.Members.class.notifyAll();
			}			
			ByFieldOrByMethodPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByFieldOrByMethod.create();
			ByMethodOrByFieldPropertyAccessor = org.burningwave.core.classes.PropertyAccessor.ByMethodOrByField.create();
			SourceCodeHandler = org.burningwave.core.classes.SourceCodeHandler.create();
			Runtime.getRuntime().addShutdownHook(
				ThreadSupplier.getOrCreate(getName("Resource releaser")).setExecutable(thread -> {
					Executor.runAndLogExceptions(
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "... Waiting for all tasks ending before closing all component containers");
							BackgroundExecutor.waitForTasksEnding(true, true);
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Closing all component containers");
							ComponentContainer.closeAll();
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Closing FileSystemHelper");
							FileSystemHelper.close();
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "... Waiting for all tasks ending before shuting down BackgroundExecutor");
							BackgroundExecutor.waitForTasksEnding(true, true);
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Shuting down BackgroundExecutor");
							BackgroundExecutor.shutDown(false);
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Stopping all threads monitoring thread");
							Synchronizer.stopAllThreadsMonitoring(false);
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Closing ThreadHolder");
							ThreadHolder.close();
						},
						() -> {
							ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Shuting down ThreadSupplier");
							ThreadSupplier.shutDownAll();
						}
					);
				})
			);
			FileSystemHelper.startSweeping();
			if (Objects.toBoolean(
				GlobalProperties.resolveValue(
					Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_ENABLED
				)
			)) {
				Synchronizer.startAllThreadsMonitoring(
					Objects.toLong(
						GlobalProperties.resolveValue(Configuration.Key.SYNCHRONIZER_ALL_THREADS_MONITORING_INTERVAL)
					)
				);
			}
			ManagedLoggersRepository.logInfo(() -> 
				StaticComponentContainer.class.getName(), 
				"{} initialized in {} seconds",
				StaticComponentContainer.class.getName(),
				Double.valueOf(((double) (System.nanoTime() - startTime)) / 1_000_000_000).toString()
			);
		} catch (Throwable exc){
			exc.printStackTrace();
			throw exc;
		}
		
	}

	private static String getName(String simpleName) {
		return Optional.ofNullable(GlobalProperties.resolveStringValue(Configuration.Key.GROUP_NAME_FOR_NAMED_ELEMENTS)).map(nm -> nm + " - ").orElseGet(() -> "") + simpleName;
	}
	
	private static final QueuedTasksExecutor.Group.TasksMonitorer.Config retrieveAllTasksMonitoringConfig() {
		String probablyDeadLockedThreadsHandlingPolicy = GlobalProperties.resolveStringValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_PROBABLE_DEAD_LOCKED_TASKS_HANDLING_POLICY);
		return new QueuedTasksExecutor.Group.TasksMonitorer.Config().setAllTasksLoggerEnabled(
			Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_LOGGER_ENABLED))
		).setInterval(
			Objects.toLong(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_INTERVAL))
		).setMinimumElapsedTimeToConsiderATaskAsProbablyDeadLocked(
			Objects.toLong(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_ALL_TASKS_MONITORING_MINIMUM_ELAPSED_TIME_TO_CONSIDER_A_TASK_AS_PROBABLE_DEAD_LOCKED))
		).setMarkAsProbableDeadLocked(	
			probablyDeadLockedThreadsHandlingPolicy.toLowerCase().contains("mark as probable dead locked")
		).setKillProbableDeadLockedTasks(
			probablyDeadLockedThreadsHandlingPolicy.toLowerCase().contains("abort")
		);
	};
	
	static void showBanner() {
		List<String> bannerList = Arrays.asList(
			Resources.getAsStringBuffer(
				StaticComponentContainer.class.getClassLoader(), "org/burningwave/banner.bwb"
			).toString().split("-------------------------------------------------------------------------------------------------------------")	
		);
		Collections.shuffle(bannerList);
		System.out.println("\n" + bannerList.get(new Random().nextInt(bannerList.size())));
	}
	
}
