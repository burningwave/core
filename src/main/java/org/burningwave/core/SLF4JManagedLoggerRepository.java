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
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.slf4j.LoggerFactory;

public class SLF4JManagedLoggerRepository extends ManagedLogger.Repository.Abst {
	private Map<String, Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable>> loggers;
	
	
	public SLF4JManagedLoggerRepository(Properties properties) {
		super(properties);
	}
	
	@Override
	void initSpecificElements(Properties properties) {
		loggers = new HashMap<>();		
	}
	
	@Override
	void resetSpecificElements() {
		loggers.clear();		
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
						LoggerFactory.getLogger(clientName), new LoggingLevel.Mutable(LoggingLevel.ALL_LEVEL_ENABLED))
					);
				}
			}
		}
		return loggerEntry;
	}

	private void log(Supplier<String> clientNameSupplier, LoggingLevel loggingLevel, BiConsumer<org.slf4j.Logger, StackTraceElement> loggerConsumer) {
		if (!isEnabled) {
			return;
		}
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		StackTraceElement stackTraceElement = stackTraceElements[3].getClassName().equals(ManagedLogger.class.getName()) ?
			stackTraceElements[4] : stackTraceElements[3];
		String clientName = clientNameSupplier.get();
		Optional.ofNullable(getLogger(clientName, loggingLevel)).ifPresent(logger -> loggerConsumer.accept(logger, stackTraceElement));
	}
	
	private org.slf4j.Logger getLogger(String clientName, LoggingLevel loggingLevel) {
		Map.Entry<org.slf4j.Logger, LoggingLevel.Mutable> loggerEntry = getLoggerEntry(clientName);
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
	
	public void disableLogging(String clientName) {
		getLoggerEntry(clientName).getValue().set(LoggingLevel.ALL_LEVEL_DISABLED);
	}
	
	public void enableLogging(String clientName) {
		getLoggerEntry(clientName).getValue().set(LoggingLevel.ALL_LEVEL_ENABLED);
	}
	
	@Override
	public void logError(Supplier<String> clientNameSupplier, String message, Throwable exc) {
		log(clientNameSupplier, LoggingLevel.ERROR, (logger, stackTraceElement) -> logger.error(addDetailsToMessage(message, stackTraceElement), exc));
	}
	
	@Override
	public void logError(Supplier<String> clientNameSupplier, String message) {
		log(clientNameSupplier, LoggingLevel.ERROR, (logger, stackTraceElement) -> logger.error(addDetailsToMessage(message, stackTraceElement)));
	}
	
	@Override
	public void logDebug(Supplier<String> clientNameSupplier, String message) {
		log(clientNameSupplier, LoggingLevel.DEBUG, (logger, stackTraceElement) -> logger.debug(addDetailsToMessage(message, stackTraceElement)));
	}
	
	@Override
	public void logDebug(Supplier<String> clientNameSupplier, String message, Object... arguments) {
		log(clientNameSupplier, LoggingLevel.DEBUG, (logger, stackTraceElement) -> logger.debug(addDetailsToMessage(message, stackTraceElement), arguments));
	}
	
	@Override
	public void logInfo(Supplier<String> clientNameSupplier, String message) {
		log(clientNameSupplier, LoggingLevel.INFO, (logger, stackTraceElement) -> logger.info(addDetailsToMessage(message, stackTraceElement)));
	}
	
	@Override
	public void logInfo(Supplier<String> clientNameSupplier, String message, Object... arguments) {
		log(clientNameSupplier, LoggingLevel.INFO, (logger, stackTraceElement) -> logger.info(addDetailsToMessage(message, stackTraceElement), arguments));
	}
	
	@Override
	public void logWarn(Supplier<String> clientNameSupplier, String message) {
		log(clientNameSupplier, LoggingLevel.WARN, (logger, stackTraceElement) -> logger.warn(addDetailsToMessage(message, stackTraceElement)));
	}
	
	@Override
	public void logWarn(Supplier<String> clientNameSupplier, String message, Object... arguments) {
		log(clientNameSupplier, LoggingLevel.WARN, (logger, stackTraceElement) -> logger.warn(addDetailsToMessage(message, stackTraceElement), arguments));
	}

	@Override
	public void logTrace(Supplier<String> clientNameSupplier, String message) {
		log(clientNameSupplier, LoggingLevel.TRACE, (logger, stackTraceElement) -> logger.trace(addDetailsToMessage(message, stackTraceElement)));		
	}

	@Override
	public void logTrace(Supplier<String> clientNameSupplier, String message, Object... arguments) {
		log(clientNameSupplier, LoggingLevel.TRACE, (logger, stackTraceElement) -> logger.trace(message, arguments));
	}
	
	@Override
	public void close() {
		this.loggers.clear();
		super.close();
	}
}
