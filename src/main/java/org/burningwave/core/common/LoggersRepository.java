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
package org.burningwave.core.common;

import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

public class LoggersRepository {
	private final static Map<Class<?>, Map.Entry<org.slf4j.Logger, Boolean>> LOGGERS = new ConcurrentHashMap<>();
	
	public static Map.Entry<org.slf4j.Logger, Boolean> getLoggerEntry(Class<?> client) {
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = LOGGERS.get(client);
		if (loggerEntry == null) {
			LOGGERS.put(client, loggerEntry = new AbstractMap.SimpleEntry<>(LoggerFactory.getLogger(client), Boolean.TRUE));
		}
		return loggerEntry;
	}
	
	public static org.slf4j.Logger getLogger(Class<?> client) {
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = getLoggerEntry(client);
		return loggerEntry.getValue()? loggerEntry.getKey() : null;
	}
	
	public static void logError(Class<?> client, String message, Throwable exc) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.error(message, exc);
		}
	}
	
	public static void logError(Class<?> client, String message) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.error(message);
		}
	}
	
	public static void logDebug(Class<?> client, String message) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.debug(message);
		}
	}
	
	public static void logDebug(Class<?> client, String message, Object... arguments) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.debug(message, arguments);
		}
	}
	
	public static void logInfo(Class<?> client, String message) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.debug(message);
		}
	}
	
	public static void logInfo(Class<?> client, String message, Object... arguments) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.info(message, arguments);
		}
	}
	
	public static void logWarn(Class<?> client, String message) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.warn(message);
		}
	}
	
	public static void logWarn(Class<?> client, String message, Object... arguments) {
		org.slf4j.Logger logger = getLogger(client);
		if (logger != null) {
			logger.warn(message, arguments);
		}
	}
	
	public static void disableLogging(Class<?> client) {
		getLoggerEntry(client).setValue(Boolean.FALSE);
	}
	
	public static void enableLogging(Class<?> client) {
		getLoggerEntry(client).setValue(Boolean.TRUE);
	}
}