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
import java.util.Random;

import org.burningwave.core.ManagedLogger;
import org.burningwave.core.function.ThrowingSupplier;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unused")
public class StaticComponentContainer {
	public static class Configuration {
		public static class Key {
			private static final String HIDE_BANNER_ON_INIT = "static-component-container.hide-banner-on-init";
			private static final String BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED = "background-executor.task-creation-tracking.enabled";
			private static final String ALL_THREADS_STATE_LOGGER_ENABLED = "synchronizer.all-threads-state-logger.enabled";
			private static final String ALL_THREADS_STATE_LOGGER_LOG_INTERVAL = "synchronizer.all-threads-state-logger.log.interval";

		}
		
		public final static Map<String, Object> DEFAULT_VALUES;
		
		static {
			Map<String, Object> defaultValues =  new HashMap<>(); 
			
			defaultValues.put(Key.HIDE_BANNER_ON_INIT, false);
			defaultValues.put(
				Key.ALL_THREADS_STATE_LOGGER_ENABLED, 
				false
			);
			defaultValues.put(
				Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL,
				30000
			);	
			defaultValues.put(Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED, "${" + Key.ALL_THREADS_STATE_LOGGER_ENABLED +"}");	
			
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
	public static final org.burningwave.core.concurrent.Thread.Pool ThreadPool;
	public static final org.burningwave.core.Throwables Throwables;
	
	static {
		try {
			ThreadPool = org.burningwave.core.concurrent.Thread.Pool.create("Burningwave thread pool", 16, 48, true, 3000, 8, 30000, true);
			Synchronizer = org.burningwave.core.concurrent.Synchronizer.create(true);
			Strings = org.burningwave.core.Strings.create();
			Throwables = org.burningwave.core.Throwables.create();
			Objects = org.burningwave.core.Objects.create();
			BackgroundExecutor = org.burningwave.core.concurrent.QueuedTasksExecutor.Group.create("Background executor", true, true);
			Resources = new org.burningwave.core.io.Resources();
			Properties properties = new Properties();
			properties.putAll(Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.io.Streams.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES);
			properties.putAll(org.burningwave.core.iterable.IterableObjectHelper.Configuration.DEFAULT_VALUES);
			Map.Entry<org.burningwave.core.iterable.Properties, URL> propBag =
				Resources.loadFirstOneFound(properties, "burningwave.static.properties", "burningwave.static.default.properties");
			GlobalPropertiesListener = new org.burningwave.core.iterable.Properties.Listener() {
				@Override
				public <K, V> void processChangeNotification(Properties config, org.burningwave.core.iterable.Properties.Event event, K key, V newValue, V previousValue) {
					
					if (key instanceof String) {
						String keyAsString = (String)key;
						if (event.name().equals(Event.PUT.name())) {
							if (keyAsString.equals(ManagedLogger.Repository.Configuration.Key.TYPE)) {
								ManagedLogger.Repository toBeReplaced = ManagedLoggersRepository;
								Fields.setStaticDirect(StaticComponentContainer.class, "ManagedLoggersRepository", ManagedLogger.Repository.create(config));
								toBeReplaced.close();
							} else if (keyAsString.equals(Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED)) {
								BackgroundExecutor.setTasksCreationTrackingFlag(
									Objects.toBoolean(
										GlobalProperties.resolveValue(
											Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED
										)
									)
								);
							} else if (keyAsString.equals(Configuration.Key.ALL_THREADS_STATE_LOGGER_ENABLED)) {
								if (Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.ALL_THREADS_STATE_LOGGER_ENABLED))) {
									Synchronizer.startLoggingAllThreadsState(
										Objects.toLong(
											GlobalProperties.resolveValue(
												Configuration.Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL
											)
										)
									);
								} else {
									Synchronizer.stopLoggingAllThreadsState();
								}
							} else if (keyAsString.equals(Configuration.Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL)) {
								Synchronizer.startLoggingAllThreadsState(
									Objects.toLong(
										GlobalProperties.resolveValue(
											Configuration.Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL
										)
									)
								);
							}
						}
					}
					
				};
				
			}.listenTo(GlobalProperties = propBag.getKey());
			IterableObjectHelper = org.burningwave.core.iterable.IterableObjectHelper.create(GlobalProperties);
			if (Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.BACKGROUND_EXECUTOR_TASK_CREATION_TRACKING_ENABLED))) {
				BackgroundExecutor.setTasksCreationTrackingFlag(true);
			}
			if (Objects.toBoolean(GlobalProperties.resolveValue(Configuration.Key.HIDE_BANNER_ON_INIT))) {
				showBanner();
			}
			ManagedLoggersRepository = ManagedLogger.Repository.create(GlobalProperties);
			URL globalPropertiesFileUrl = propBag.getValue();
			if (globalPropertiesFileUrl != null) {
				ManagedLoggersRepository.logInfo(
					() -> StaticComponentContainer.class.getName(), 
					"Building static components by using " + ThrowingSupplier.get(() ->
						URLDecoder.decode(
							globalPropertiesFileUrl.toString(), StandardCharsets.UTF_8.name()
						)
					)
				);
			} else {
				ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Building static components by using default configuration");
			}
			ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Instantiated {}", ManagedLoggersRepository.getClass().getName());
			Paths = org.burningwave.core.Strings.Paths.create();
			FileSystemHelper = org.burningwave.core.io.FileSystemHelper.create();
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
				ThreadPool.getOrCreate("Resources releaser").setExecutable(thread -> {
					try {
						ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "... Waiting for all tasks ending before closing all component containers");
						BackgroundExecutor.waitForTasksEnding(true);
						ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Closing all component containers");
						ComponentContainer.closeAll();
					} catch (Throwable exc) {
						ManagedLoggersRepository.logError(() -> StaticComponentContainer.class.getName(), "Exception occurred while closing component containers", exc);
					}
					try {
						ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Closing FileSystemHelper");
						FileSystemHelper.close();
					} catch (Throwable exc) {
						ManagedLoggersRepository.logError(() -> StaticComponentContainer.class.getName(), "Exception occurred while closing FileSystemHelper", exc);
					}
					ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "... Waiting for all tasks ending before shuting down BackgroundExecutor");
					BackgroundExecutor.waitForTasksEnding(true);
					ManagedLoggersRepository.logInfo(() -> StaticComponentContainer.class.getName(), "Shuting down BackgroundExecutor");
					BackgroundExecutor.shutDown(false);
					Synchronizer.close();
					ThreadPool.shutDownAll();
				})
			);
			FileSystemHelper.startScavenger();
			if (Objects.toBoolean(
				GlobalProperties.resolveValue(
					Configuration.Key.ALL_THREADS_STATE_LOGGER_ENABLED
				)
			)) {
				Synchronizer.startLoggingAllThreadsState(
					Objects.toLong(
						GlobalProperties.resolveValue(Configuration.Key.ALL_THREADS_STATE_LOGGER_LOG_INTERVAL)
					)
				);
			}
		} catch (Throwable exc){
			exc.printStackTrace();
			throw new RuntimeException(exc);
		}
		
	}

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
