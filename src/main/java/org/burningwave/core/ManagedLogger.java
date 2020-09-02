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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import org.burningwave.core.classes.MemoryClassLoader;
import org.burningwave.core.classes.SearchContext;
import org.burningwave.core.iterable.Properties.Event;
import org.burningwave.core.jvm.LowLevelObjectsHandler;

public interface ManagedLogger {	
	
	default void logTrace(String message) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logTrace(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement));
	}
	
	default void logTrace(String message, Object... arguments) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logTrace(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement), arguments);
	}
	
	default void logDebug(String message) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logDebug(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement));
	}
	
	default void logDebug(String message, Object... arguments) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logDebug(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement), arguments);
	}
	
	default void logInfo(String message) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logInfo(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement));
	}
	
	default void logInfo(String message, Object... arguments) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logInfo(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement), arguments);
	}
	
	default void logWarn(String message) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logWarn(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement));
	}
	
	default void logWarn(String message, Object... arguments) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logWarn(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement), arguments);
	}
	
	default void logError(String message, Throwable exc) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logError(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement), exc);
	}
	
	default void logError(String message) {
		StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
		((ManagedLogger.Repository.Abst)ManagedLoggersRepository).logError(() -> stackTraceElement.getClassName(), () -> ((ManagedLogger.Repository.Abst)ManagedLoggersRepository).addDetailsToMessage(message, stackTraceElement));
	}
	
	
	public static interface Repository {
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
				DEFAULT_VALUES = new HashMap<>();
				DEFAULT_VALUES.put(Key.TYPE, "autodetect");
				DEFAULT_VALUES.put(Key.ENABLED_FLAG, String.valueOf(true));
				DEFAULT_VALUES.put(Key.WARN_LOGGING_DISABLED_FOR,
					LowLevelObjectsHandler.class.getName() + ";" +
					MemoryClassLoader.class.getName() + ";" +
					SearchContext.class.getName() + ";"
				);
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
		
		public default void logError(String message, Throwable exc) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logError(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), exc);
		}
		
		public default void logError(String message) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logError(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement));
		}
		
		public default void logDebug(String message) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logDebug(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement));
		}
		
		public default void logDebug(String message, Object... arguments) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logDebug(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), arguments);
		}
		
		public default void logInfo(String message) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logInfo(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement));
		}	
		
		public default void logInfo(String message, Object... arguments) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logInfo(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), arguments);			
		}
		
		public default void logWarn(String message) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logWarn(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement));
		}
		
		public default void logWarn(String message, Object... arguments) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logWarn(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), arguments);
		}
		
		public default void logTrace(String message) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logTrace(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), message);
		}
		
		public default void logTrace(String message, Object... arguments) {
			StackTraceElement stackTraceElement = Thread.currentThread().getStackTrace()[2];
			((Repository.Abst)this).logTrace(() -> stackTraceElement.getClassName(), () -> ((Repository.Abst)this).addDetailsToMessage(message, stackTraceElement), arguments);
		}
		
		public static abstract class Abst implements Repository, org.burningwave.core.iterable.Properties.Listener  {
			boolean isEnabled;
			
			Abst(Properties properties) {
				initSpecificElements(properties);
				if (getEnabledLoggingFlag(properties)) {
					enableLogging();
				}
				removeLoggingLevels(properties);			
				if (properties instanceof org.burningwave.core.iterable.Properties) {
					listenTo((org.burningwave.core.iterable.Properties)properties);
				}
			}
			
			abstract void initSpecificElements(Properties properties);
			
			abstract void resetSpecificElements();
			
			boolean getEnabledLoggingFlag(Properties properties) {
				return Boolean.parseBoolean(IterableObjectHelper.resolveStringValue(
					properties,
					Configuration.Key.ENABLED_FLAG
				));
			}
			
			@Override
			public <K, V> void receiveNotification(org.burningwave.core.iterable.Properties properties, Event event,
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
				String loggerDisabledFor = (String)properties.getProperty(configKey);
				if (loggerDisabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						removeLoggingLevelFor(loggingLevel, loggerDisabledFor.split(";"));
					}
				}
			}
			
			protected void addLoggingLevels(Properties properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerEnabledFor = (String)properties.getProperty(configKey);
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
			
			public void disableLogging() {
				isEnabled = false;	
			}

			public void enableLogging() {
				isEnabled = true;		
			}
			
			public String addDetailsToMessage(String message, StackTraceElement stackTraceElement) {
				return "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ") - " + message;
			}
			
			abstract void logError(Supplier<String> clientName, Supplier<String> message, Throwable exc);
			
			abstract void logError(Supplier<String> clientName, Supplier<String> message);
			
			abstract void logDebug(Supplier<String> clientName, Supplier<String> message);
			
			abstract void logDebug(Supplier<String> clientName, Supplier<String> message, Object... arguments);
			
			abstract void logInfo(Supplier<String> clientName, Supplier<String> message);
			
			abstract void logInfo(Supplier<String> clientName, Supplier<String> message, Object... arguments);
			
			abstract void logWarn(Supplier<String> clientName, Supplier<String> message);
			
			abstract void logWarn(Supplier<String> clientName, Supplier<String> message, Object... arguments);
			
			abstract void logTrace(Supplier<String> clientName, Supplier<String> message);
			
			abstract void logTrace(Supplier<String> clientName, Supplier<String> message, Object... arguments);
		}
	}
}
