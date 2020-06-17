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

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

public class SLF4JManagedLoggerRepository extends ManagedLogger.Repository.Abst {
	private Map<String, Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable>> loggers;
	
	
	public SLF4JManagedLoggerRepository(Properties properties) {
		super(properties);
	}
	
	@Override
	void init(Properties properties) {
		loggers = new HashMap<>();		
	}
	
	@Override
	public void setLoggingLevelFlags(Class<?> cls, Integer flags) {
		getLoggerEntry(cls.getName()).getValue().set(flags);
	}

	@Override
	public Integer getLoggingLevelFlags(Class<?> cls) {
		return getLoggerEntry(cls.getName()).getValue().flags;
	}
	
	@Override
	public void addLoggingLevelFor(LoggingLevel logLevel, String... classNames) {
		for (String className : classNames) {
			getLoggerEntry(className).getValue().add(logLevel.flags);
		}		
	}

	@Override
	public void removeLoggingLevelFor(LoggingLevel logLevel, String... classNames) {
		for (String className : classNames) {
			getLoggerEntry(className).getValue().remove(logLevel.flags);
		}	
	}
	
	@Override
	public void setLoggingLevelFor(LoggingLevel level, String... classNames) {
		for (String className : classNames) {
			getLoggerEntry(className).getValue().set(level.flags);
		}
	}
	
	private Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable> getLoggerEntry(String clientName) {
		Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable> loggerEntry = loggers.get(clientName);
		if (loggerEntry == null) {
			synchronized (loggers) {
				loggerEntry = loggers.get(clientName);
				if (loggerEntry == null) {
					loggers.put(clientName, loggerEntry = new AbstractMap.SimpleEntry<>(
						LoggerFactory.getLogger(clientName), new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED)));
				}
			}
		}
		return loggerEntry;
	}

	private void log(Class<?> client, LoggingLevel loggingLevel, Consumer<org.slf4j.Logger> loggerConsumer) {
		Optional.ofNullable(getLogger(client, loggingLevel)).ifPresent(logger -> loggerConsumer.accept(logger));
	}
	
	private org.slf4j.Logger getLogger(Class<?> client, LoggingLevel loggingLevel) {
		if (!isEnabled) {
			return null;
		}
		Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable> loggerEntry = getLoggerEntry(client.getName());
		return loggerEntry.getValue().partialyMatch(loggingLevel)? loggerEntry.getKey() : null;
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
	
	public void disableLogging(Class<?> client) {
		getLoggerEntry(client.getName()).getValue().set(LoggingLevel.ALL_LEVEL_DISABLED);
	}
	
	public void enableLogging(Class<?> client) {
		getLoggerEntry(client.getName()).getValue().set(LoggingLevel.ALL_LEVEL_ENABLED);
	}
	
	public void logError(Class<?> client, String message, Throwable exc) {
		log(client, LoggingLevel.ERROR, (logger) -> logger.error(message, exc));
	}
	
	public void logError(Class<?> client, String message) {
		log(client, LoggingLevel.ERROR, (logger) -> logger.error(message));
	}
	
	public void logDebug(Class<?> client, String message) {
		log(client, LoggingLevel.DEBUG, (logger) -> logger.debug(message));
	}
	
	public void logDebug(Class<?> client, String message, Object... arguments) {
		log(client, LoggingLevel.DEBUG, (logger) -> logger.debug(message, arguments));
	}
	
	public void logInfo(Class<?> client, String message) {
		log(client, LoggingLevel.INFO, (logger) -> logger.info(message));
	}
	
	public void logInfo(Class<?> client, String message, Object... arguments) {
		log(client, LoggingLevel.INFO, (logger) -> logger.info(message, arguments));
	}
	
	public void logWarn(Class<?> client, String message) {
		log(client, LoggingLevel.WARN, (logger) -> logger.warn(message));
	}
	
	public void logWarn(Class<?> client, String message, Object... arguments) {
		log(client, LoggingLevel.WARN, (logger) -> logger.warn(message, arguments));
	}

	@Override
	public void logTrace(Class<?> client, String message) {
		log(client, LoggingLevel.TRACE, (logger) -> logger.trace(message));		
	}

	@Override
	public void logTrace(Class<?> client, String message, Object... arguments) {
		log(client, LoggingLevel.TRACE, (logger) -> logger.trace(message, arguments));
	}
}
