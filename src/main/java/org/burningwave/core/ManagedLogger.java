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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

public interface ManagedLogger {
		
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T disableLogging() {
		Repository.disableLogging(this.getClass());
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T enableLogging() {
		Repository.enableLogging(this.getClass());
		return (T) this;
	}
	
	default void logError(String message, Throwable exc) {
		Repository.logError(this.getClass(), message, exc);
	}
	
	default void logError(String message) {
		Repository.logError(this.getClass(), message);
	}
	
	default void logDebug(String message) {
		Repository.logDebug(this.getClass(), message);
	}
	
	default void logDebug(String message, Object... arguments) {
		Repository.logDebug(this.getClass(), message, arguments);
	}
	
	default void logInfo(String message) {
		Repository.logInfo(this.getClass(), message);
	}
	
	default void logInfo(String message, Object... arguments) {
		Repository.logInfo(this.getClass(), message, arguments);
	}
	
	default void logWarn(String message) {
		Repository.logWarn(this.getClass(), message);
	}
	
	default void logWarn(String message, Object... arguments) {
		Repository.logWarn(this.getClass(), message, arguments);
	}
	
	
	public static class Repository {
		private final static Map<Class<?>, Map.Entry<org.slf4j.Logger, Boolean>> LOGGERS = new ConcurrentHashMap<>();
		
		private static Map.Entry<org.slf4j.Logger, Boolean> getLoggerEntry(Class<?> client) {
			Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = LOGGERS.get(client);
			if (loggerEntry == null) {
				LOGGERS.put(client, loggerEntry = new AbstractMap.SimpleEntry<>(LoggerFactory.getLogger(client), Boolean.TRUE));
			}
			return loggerEntry;
		}
		
		private static void log(Class<?> client, Consumer<org.slf4j.Logger> loggerConsumer) {
			Optional.ofNullable(getLogger(client)).ifPresent(logger -> loggerConsumer.accept(logger));
		}
		
		public static void disableLogging(Class<?> client) {
			getLoggerEntry(client).setValue(Boolean.FALSE);
		}
		
		public static void enableLogging(Class<?> client) {
			getLoggerEntry(client).setValue(Boolean.TRUE);
		}
		
		private static org.slf4j.Logger getLogger(Class<?> client) {
			Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = getLoggerEntry(client);
			return loggerEntry.getValue()? loggerEntry.getKey() : null;
		}
		
		public static void logError(Class<?> client, String message, Throwable exc) {
			log(client, (logger) -> logger.debug(message, exc));
		}
		
		public static void logError(Class<?> client, String message) {
			log(client, (logger) -> logger.error(message));
		}
		
		public static void logDebug(Class<?> client, String message) {
			log(client, (logger) -> logger.debug(message));
		}
		
		public static void logDebug(Class<?> client, String message, Object... arguments) {
			log(client, (logger) -> logger.debug(message, arguments));
		}
		
		public static void logInfo(Class<?> client, String message) {
			log(client, (logger) -> logger.info(message));
		}
		
		public static void logInfo(Class<?> client, String message, Object... arguments) {
			log(client, (logger) -> logger.info(message, arguments));
		}
		
		public static void logWarn(Class<?> client, String message) {
			log(client, (logger) -> logger.warn(message));
		}
		
		public static void logWarn(Class<?> client, String message, Object... arguments) {
			log(client, (logger) -> logger.warn(message, arguments));
		}
	}
}
