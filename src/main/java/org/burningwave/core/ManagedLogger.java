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
package org.burningwave.core;


import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Objects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.burningwave.core.assembler.ComponentContainer;
import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.PathScannerClassLoader;
import org.burningwave.core.iterable.IterableObjectHelper.ResolveConfig;
import org.burningwave.core.iterable.Properties.Event;


public interface ManagedLogger {

	default void logTrace(String message) {
		ManagedLoggerRepository.logTrace(getClass()::getName, message);
	}

	default void logTrace(String message, Object... arguments) {
		ManagedLoggerRepository.logTrace(getClass()::getName, message, arguments);
	}

	default void logDebug(String message) {
		ManagedLoggerRepository.logDebug(getClass()::getName, message);
	}

	default void logDebug(String message, Object... arguments) {
		ManagedLoggerRepository.logDebug(getClass()::getName, message, arguments);
	}

	default void logInfo(String message) {
		ManagedLoggerRepository.logInfo(getClass()::getName, message);
	}

	default void logInfo(String message, Object... arguments) {
		ManagedLoggerRepository.logInfo(getClass()::getName, message, arguments);
	}

	default void logWarn(String message) {
		ManagedLoggerRepository.logWarn(getClass()::getName, message);
	}

	default void logWarn(String message, Object... arguments) {
		ManagedLoggerRepository.logWarn(getClass()::getName, message, arguments);
	}

	default void logError(String message, Throwable exc, Object... arguments) {
		ManagedLoggerRepository.logError(getClass()::getName, message, exc, arguments);
	}

	default void logError(String message, Object... arguments) {
		ManagedLoggerRepository.logError(getClass()::getName, message, arguments);
	}

	default void logError(String message, Throwable exc) {
		ManagedLoggerRepository.logError(getClass()::getName, message, exc);
	}

	default void logError(String message) {
		ManagedLoggerRepository.logError(() -> this.getClass().getName(), message);
	}

	default void logError(Throwable exc) {
		ManagedLoggerRepository.logError(() -> this.getClass().getName(), exc);
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

				String defaultValuesSeparator = (String)org.burningwave.core.iterable.IterableObjectHelper.Configuration.DEFAULT_VALUES.get(
					org.burningwave.core.iterable.IterableObjectHelper.Configuration.Key.DEFAULT_VALUES_SEPERATOR
				);

				//The semicolons in this value value will be replaced by the method StaticComponentContainer.adjustConfigurationValues
				defaultValues.put(Key.WARN_LOGGING_DISABLED_FOR,
					ComponentContainer.class.getName() + "$ClassLoader" + defaultValuesSeparator +
					MemoryClassLoader.class.getName() + defaultValuesSeparator +
					PathScannerClassLoader.class.getName() + defaultValuesSeparator
				);

				DEFAULT_VALUES = Collections.unmodifiableMap(defaultValues);
			}
		}

		public static ManagedLogger.Repository create(
			Map<?, ?> config
		) {
			try {
				String className = IterableObjectHelper.resolveStringValue(
					ResolveConfig.ForNamedKey.forNamedKey(org.burningwave.core.ManagedLogger.Repository.Configuration.Key.TYPE)
					.on(config)
					.withDefaultValues(ManagedLogger.Repository.Configuration.DEFAULT_VALUES)
				);
				if ("autodetect".equalsIgnoreCase(className = className.trim())) {
					try {
						Driver.getClassByName("org.slf4j.Logger", false,
							ManagedLogger.Repository.class.getClassLoader(),
							ManagedLogger.Repository.class
						);
						return new org.burningwave.core.SLF4JManagedLoggerRepository(config);
					} catch (Throwable exc2) {
						return new org.burningwave.core.SimpleManagedLoggerRepository(config);
					}
				} else {
					return (org.burningwave.core.ManagedLogger.Repository)
						Driver.getClassByName(className, false,
							ManagedLogger.Repository.class.getClassLoader(),
							ManagedLogger.Repository.class
						).getConstructor(Map.class).newInstance(config);
				}

			} catch (Throwable exc) {
				exc.printStackTrace();
				return org.burningwave.core.assembler.StaticComponentContainer.Driver.throwException(exc);
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
			Map<?, ?> config;
			Abst(Map<?, ?> config) {
				this.config = config;
				instanceId = this.toString();
				initSpecificElements(config);
				if (getEnabledLoggingFlag(config)) {
					enableLogging();
				}
				removeLoggingLevels(config);
				checkAndListenTo(config);
			}

			abstract void initSpecificElements(Map<?, ?> properties);

			abstract void resetSpecificElements();

			boolean getEnabledLoggingFlag(Map<?, ?> properties) {
				return Objects.toBoolean(
					IterableObjectHelper.resolveStringValue(
						ResolveConfig.forNamedKey(Configuration.Key.ENABLED_FLAG)
						.on(properties)
					)
				);
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

			void removeLoggingLevels(Map<?, ?> properties) {
				removeLoggingLevels(properties, Repository.Configuration.Key.TRACE_LOGGING_DISABLED_FOR, LoggingLevel.TRACE);
				removeLoggingLevels(properties, Repository.Configuration.Key.DEBUG_LOGGING_DISABLED_FOR, LoggingLevel.DEBUG);
				removeLoggingLevels(properties, Repository.Configuration.Key.INFO_LOGGING_DISABLED_FOR, LoggingLevel.INFO);
				removeLoggingLevels(properties, Repository.Configuration.Key.WARN_LOGGING_DISABLED_FOR, LoggingLevel.WARN);
				removeLoggingLevels(properties, Repository.Configuration.Key.ERROR_LOGGING_DISABLED_FOR, LoggingLevel.ERROR);
				removeLoggingLevels(properties, Repository.Configuration.Key.ALL_LOGGING_LEVEL_DISABLED_FOR,
					LoggingLevel.TRACE, LoggingLevel.DEBUG, LoggingLevel.INFO, LoggingLevel.WARN, LoggingLevel.ERROR
				);
			}

			protected void removeLoggingLevels(Map<?, ?> properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerDisabledFor = (String)properties.get(configKey);
				if (loggerDisabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						removeLoggingLevelFor(loggingLevel, loggerDisabledFor.split(IterableObjectHelper.getDefaultValuesSeparator()));
					}
				}
			}

			protected void addLoggingLevels(Properties properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerEnabledFor = properties.getProperty(configKey);
				if (loggerEnabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						addLoggingLevelFor(loggingLevel, loggerEnabledFor.split(IterableObjectHelper.getDefaultValuesSeparator()));
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

			String addDetailsToMessage(String message, StackTraceElement stackTraceElement) {
				return "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ") - " + message;
			}

			@Override
			public void close() {
				checkAndUnregister(config);
				config = null;
				instanceId = null;
			}
		}
	}
}
