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

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;

import java.util.Properties;;

public interface ManagedLogger {	
	
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T disableLogging() {
		ManagedLoggersRepository.disableLogging(this.getClass());
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T enableLogging() {
		ManagedLoggersRepository.enableLogging(this.getClass());
		return (T) this;
	}
	
	public default Integer getLoggingLevelValue() {
		return ManagedLoggersRepository.getLoggingLevelFlags(this.getClass());
	}
	
	public default void setLoggingLevelValue(Integer flag) {
		ManagedLoggersRepository.setLoggingLevelFlags(this.getClass(), flag);
	}
	
	default void logError(String message, Throwable exc) {
		ManagedLoggersRepository.logError(this.getClass(), message, exc);
	}
	
	default void logError(String message) {
		ManagedLoggersRepository.logError(this.getClass(), message);
	}
	
	default void logDebug(String message) {
		ManagedLoggersRepository.logDebug(this.getClass(), message);
	}
	
	default void logDebug(String message, Object... arguments) {
		ManagedLoggersRepository.logDebug(this.getClass(), message, arguments);
	}
	
	default void logInfo(String message) {
		ManagedLoggersRepository.logInfo(this.getClass(), message);
	}
	
	default void logInfo(String message, Object... arguments) {
		ManagedLoggersRepository.logInfo(this.getClass(), message, arguments);
	}
	
	default void logWarn(String message) {
		ManagedLoggersRepository.logWarn(this.getClass(), message);
	}
	
	default void logWarn(String message, Object... arguments) {
		ManagedLoggersRepository.logWarn(this.getClass(), message, arguments);
	}
	
	
	public static interface Repository {
		public static final String TYPE_CONFIG_KEY = "managed-logger.repository";
		public static final String ENABLED_FLAG_CONFIG_KEY = "managed-logger.repository.enabled";
		public static final String ALL_LEVEL_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.all-level.disabled-for";
		public static final String TRACE_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.trace.disabled-for";
		public static final String DEBUG_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.debug.disabled-for";
		public static final String INFO_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.info.disabled-for";
		public static final String WARN_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.warn.disabled-for";
		public static final String ERROR_LOGGING_DISABLED_FOR_CONFIG_KEY = "managed-logger.repository.logging.error.disabled-for";
		
		public void setLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public void setLoggingLevelFlags(Class<?> cls, Integer flag);

		public Integer getLoggingLevelFlags(Class<?> cls);

		public void addLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public void removeLoggingLevelFor(LoggingLevel logLevel, String... classNames);
		
		public boolean isEnabled();
		
		public void disableLogging();
		
		public void enableLogging();
		
		public void disableLogging(Class<?> client);
		
		public void enableLogging(Class<?> client);
		
		public void logError(Class<?> client, String message, Throwable exc);
		
		public void logError(Class<?> client, String message);
		
		public void logDebug(Class<?> client, String message);
		
		public void logDebug(Class<?> client, String message, Object... arguments);
		
		public void logInfo(Class<?> client, String message);
		
		public void logInfo(Class<?> client, String message, Object... arguments);
		
		public void logWarn(Class<?> client, String message);
		
		public void logWarn(Class<?> client, String message, Object... arguments);
		
		public void logTrace(Class<?> client, String message);
		
		public void logTrace(Class<?> client, String message, Object... arguments);
		
		public static abstract class Abst implements Repository{
			boolean isEnabled;
			
			Abst(Properties properties) {
				init(properties);
				String enabledFlag = (String)properties.getProperty(Repository.ENABLED_FLAG_CONFIG_KEY);
				if (enabledFlag != null && Boolean.parseBoolean(enabledFlag)) {
					enableLogging();
				}
				removeLoggingLevelFor(properties, TRACE_LOGGING_DISABLED_FOR_CONFIG_KEY, LoggingLevel.TRACE);
				removeLoggingLevelFor(properties, DEBUG_LOGGING_DISABLED_FOR_CONFIG_KEY, LoggingLevel.DEBUG);
				removeLoggingLevelFor(properties, INFO_LOGGING_DISABLED_FOR_CONFIG_KEY, LoggingLevel.INFO);
				removeLoggingLevelFor(properties, WARN_LOGGING_DISABLED_FOR_CONFIG_KEY, LoggingLevel.WARN);
				removeLoggingLevelFor(properties, ERROR_LOGGING_DISABLED_FOR_CONFIG_KEY, LoggingLevel.ERROR);
				removeLoggingLevelFor(properties, ALL_LEVEL_LOGGING_DISABLED_FOR_CONFIG_KEY,
					LoggingLevel.TRACE, LoggingLevel.DEBUG, LoggingLevel.INFO, LoggingLevel.WARN, LoggingLevel.ERROR
				);	
			}
			
			abstract void init(Properties properties);
			
			protected void removeLoggingLevelFor(Properties properties, String configKey, LoggingLevel... loggingLevels) {
				String loggerDisabledFor = (String)properties.getProperty(configKey);
				if (loggerDisabledFor != null) {
					for (LoggingLevel loggingLevel : loggingLevels) {
						removeLoggingLevelFor(loggingLevel, loggerDisabledFor.split(";"));
					}
				}
			}
			 
			public String getId(Object... objects) {
				String id = "_";
				for (Object object : objects) {
					id += System.identityHashCode(object) + "_";
				}
				return id;
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
		}
	}
}
