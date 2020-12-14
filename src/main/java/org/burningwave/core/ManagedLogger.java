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
package org.burningwave.core;

import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.ClassHunter;
import org.burningwave.core.classes.ClassPathHunter;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.iterable.Properties.Event;
import org.burningwave.core.jvm.LowLevelObjectsHandler;

public interface ManagedLogger {	
	
	default void logTrace(String message) {
		ManagedLoggersRepository.logTrace(getClass()::getName, message);
	}
	
	default void logTrace(String message, Object... arguments) {
		ManagedLoggersRepository.logTrace(getClass()::getName, message, arguments);
	}
	
	default void logDebug(String message) {
		ManagedLoggersRepository.logDebug(getClass()::getName, message);
	}
	
	default void logDebug(String message, Object... arguments) {
		ManagedLoggersRepository.logDebug(getClass()::getName, message, arguments);
	}
	
	default void logInfo(String message) {
		ManagedLoggersRepository.logInfo(getClass()::getName, message);
	}
	
	default void logInfo(String message, Object... arguments) {
		ManagedLoggersRepository.logInfo(getClass()::getName, message, arguments);
	}
	
	default void logWarn(String message) {
		ManagedLoggersRepository.logWarn(getClass()::getName, message);
	}
	
	default void logWarn(String message, Object... arguments) {
		ManagedLoggersRepository.logWarn(getClass()::getName, message, arguments);
	}
	
	default void logError(String message, Throwable exc, Object... arguments) {
		ManagedLoggersRepository.logError(getClass()::getName, message, exc, arguments);
	}
	
	default void logError(String message, Object... arguments) {
		ManagedLoggersRepository.logError(getClass()::getName, message, arguments);
	}
	
	default void logError(String message, Throwable exc) {
		ManagedLoggersRepository.logError(getClass()::getName, message, exc);
	}
	
	default void logError(String message) {
		ManagedLoggersRepository.logError(() -> this.getClass().getName(), message);
	}
	
	default void logError(Throwable exc) {
		ManagedLoggersRepository.logError(() -> this.getClass().getName(), exc);
	}
	
	
	public static interface Repository extends Closeable {
		public static class Configuration {			
			
			public static class Key {
				
				public static final String TYPE = "managed-logger.repository";
				public static final String ENABLED_FLAG = "managed-logger.repository.enabled";
				
				private static final String LOGGING_LEVEL_FLAG_PREFIX = "managed-logger.repository.logging";
				private static final String LOGGING_LEVEL_DISABLED_FLAG_SUFFIX = "disabled-for";
				
				public static final String ALL_LOGGING_LEVEL_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".all-levels." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
				public static final String TRACE_LOGGING_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".trace." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
				public static final String DEBUG_LOGGING_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".debug." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
				public static final String INFO_LOGGING_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".info." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
				public static final String WARN_LOGGING_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".warn." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
				public static final String ERROR_LOGGING_DISABLED_FOR = LOGGING_LEVEL_FLAG_PREFIX + ".error." + LOGGING_LEVEL_DISABLED_FLAG_SUFFIX;
			}
			
			public final static Map<String, Object> DEFAULT_VALUES;
			
			static {
				Map<String, Object> defaultValues = new HashMap<>();
				
				defaultValues.put(Key.TYPE, "autodetect");
				defaultValues.put(Key.ENABLED_FLAG, String.valueOf(true));
				defaultValues.put(Key.WARN_LOGGING_DISABLED_FOR,
					ClassHunter.class.getName() + "Impl$SearchContext;" +
					ClassPathHunter.class.getName() + "Impl$SearchContext;" +
					ComponentContainer.PathScannerClassLoader.class.getName() + ";" +
					LowLevelObjectsHandler.class.getName() + ";" +
					MemoryClassLoader.class.getName() + ";" +
					PathScannerClassLoader.class.getName() + ";" +
					ClassHunter.class.getPackage().getName() + ".SearchContext;"
				);				
				
				DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
			}
		}
		
		public static org.burningwave.core.ManagedLogger.Repository create(
			org.burningwave.core.iterable.Properties config
		) {
			try {
				String className = config.resolveStringValue(
					org.burningwave.core.ManagedLogger.Repository.Configuration.Key.TYPE,
					org.burningwave.core.ManagedLogger.Repository.Configuration.DEFAULT_VALUES
				);
				if ("autodetect".equalsIgnoreCase(className = className.trim())) {
					try {
						Class.forName("org.slf4j.Logger");
						return new org.burningwave.core.SLF4JManagedLoggerRepository(config);
					} catch (Throwable exc2) {
						return new org.burningwave.core.SimpleManagedLoggerRepository(config);
					}
				} else {
					return (org.burningwave.core.ManagedLogger.Repository)
						Class.forName(className).getConstructor(java.util.Properties.class).newInstance(config);
				}
				
			} catch (Throwable exc) {
				exc.printStackTrace();
				return Throwables.throwException(exc);
			}
		}
		
		public void setLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public void setLoggingLevelFlags(Class<?> cls, Integer flag);

		public Integer getLoggingLevelFlags(Class<?> cls);

		public void addLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public void removeLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public boolean isEnabled();
		
		public void disableLogging();
		
		public void enableLogging();
		
		public void disableLogging(String clientName);
		
		public void enableLogging(String clientName);
		
		public void logError(Supplier<String> clientNameSupplier, String message, Object... arguments);
		
		public void logError(Supplier<String> clientNameSupplier, String message, Throwable exc, Object... arguments);
		
		public void logError(Supplier<String> clientNameSupplier, String message, Throwable exc);
		
		public void logError(Supplier<String> clientNameSupplier, String message);
		
		public void logError(Supplier<String> clientNameSupplier, Throwable exc);
		
		public void logDebug(Supplier<String> clientNameSupplier, String message);
		
		public void logDebug(Supplier<String> clientNameSupplier, String message, Object... arguments);
		
		public void logInfo(Supplier<String> clientNameSupplier, String message);
		
		public void logInfo(Supplier<String> clientNameSupplier, String message, Object... arguments);
		
		public void logWarn(Supplier<String> clientNameSupplier, String message);
		
		public void logWarn(Supplier<String> clientNameSupplier, String message, Object... arguments);
		
		public void logTrace(Supplier<String> clientNameSupplier, String message);
		
		public void logTrace(Supplier<String> clientNameSupplier, String message, Object... arguments);
		
		public static abstract class Abst implements Repository, org.burningwave.core.iterable.Properties.Listener  {
			boolean isEnabled;
			String instanceId;
			Properties config;
			Abst(Properties config) {
				this.config = config;
				instanceId = this.toString();
				initSpecificElements(config);
				if (getEnabledLoggingFlag(config)) {
					enableLogging();
				}
				removeLoggingLevels(config);			
				if (config instanceof org.burningwave.core.iterable.Properties) {
					listenTo((org.burningwave.core.iterable.Properties)config);
				}
			}
			
			abstract void initSpecificElements(Properties properties);
			
			abstract void resetSpecificElements();
			
			boolean getEnabledLoggingFlag(Properties properties) {
				return Objects.toBoolean(IterableObjectHelper.resolveStringValue(
					properties,
					Configuration.Key.ENABLED_FLAG
				));
			}
			
			@Override
			public <K, V> void processChangeNotification(org.burningwave.core.iterable.Properties properties, Event event,
					K key, V newValue, V oldValue) {
				if (key instanceof String) {
					String keyAsString = (String)key;
					if (keyAsString.equals(Configuration.Key.ENABLED_FLAG)) {
						if (getEnabledLoggingFlag(properties)) {
							enableLogging();
						}
					}
					if (keyAsString.startsWith(Configuration.Key.LOGGING_LEVEL_FLAG_PREFIX)) {
						resetSpecificElements();
						removeLoggingLevels(properties);
					}
				}
			}

			void removeLoggingLevels(Properties properties) {
				removeLoggingLevels(properties, Repository.Configuration.Key.TRACE_LOGGING_DISABLED_FOR, LoggingLevel.TRACE);
				removeLoggingLevels(properties, Repository.Configuration.Key.DEBUG_LOGGING_DISABLED_FOR, LoggingLevel.DEBUG);
				removeLoggingLevels(properties, Repository.Configuration.Key.INFO_LOGGING_DISABLED_FOR, LoggingLevel.INFO);
				removeLoggingLevels(properties, Repository.Configuration.Key.WARN_LOGGING_DISABLED_FOR, LoggingLevel.WARN);
				removeLoggingLevels(properties, Repository.Configuration.Key.ERROR_LOGGING_DISABLED_FOR, LoggingLevel.ERROR);
				removeLoggingLevels(properties, Repository.Configuration.Key.ALL_LOGGING_LEVEL_DISABLED_FOR,
					LoggingLevel.TRACE, LoggingLevel.DEBUG, LoggingLevel.INFO, LoggingLevel.WARN, LoggingLevel.ERROR
				);
			}
			
			protected void removeLoggingLevels(Properties properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerDisabledFor = properties.getProperty(configKey);
				if (loggerDisabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						removeLoggingLevelFor(loggingLevel, loggerDisabledFor.split(";"));
					}
				}
			}
			
			protected void addLoggingLevels(Properties properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerEnabledFor = properties.getProperty(configKey);
				if (loggerEnabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						addLoggingLevelFor(loggingLevel, loggerEnabledFor.split(";"));
					}
				}
			}
			
			@Override
			public boolean isEnabled() {
				return isEnabled;
			}
			
			@Override
			public void disableLogging() {
				isEnabled = false;	
			}

			@Override
			public void enableLogging() {
				isEnabled = true;		
			}
			
			public String addDetailsToMessage(String message, StackTraceElement stackTraceElement) {
				return "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ") - " + message;
			}
			
			@Override
			public void close() {
				if (config instanceof org.burningwave.core.iterable.Properties) {
					unregister((org.burningwave.core.iterable.Properties)config);
				}
				config = null;
				instanceId = null;
			}
		}
	}
}
