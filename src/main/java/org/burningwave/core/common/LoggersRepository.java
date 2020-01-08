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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

public class LoggersRepository {
	private final static Map<Class<?>, Map.Entry<org.slf4j.Logger, Boolean>> LOGGERS = new ConcurrentHashMap<>();
	
	public static Map.Entry<org.slf4j.Logger, Boolean> getLoggerEntry(Object object) {
		Class<?> cls = Classes.retrieveFrom(object);
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = LOGGERS.get(cls);
		if (loggerEntry == null) {
			LOGGERS.put(cls, loggerEntry = new AbstractMap.SimpleEntry<>(LoggerFactory.getLogger(cls), Boolean.TRUE));
		}
		return loggerEntry;
	}
	
	public static org.slf4j.Logger getLogger(Object object) {
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = getLoggerEntry(object);
		return loggerEntry.getValue()? loggerEntry.getKey() : null;
	}
	
	public static void logError(Object client, String message, Throwable exc) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.error(message, exc));
	}
	
	public static void logError(Object client, String message) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.error(message));
	}
	
	public static void logDebug(Object client, String message) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.debug(message));
	}
	
	public static void logDebug(Object client, String message, Object... arguments) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.debug(message, arguments));
	}
	
	public static void logInfo(Object client, String message) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.info(message));
	}
	
	public static void logInfo(Object client, String message, Object... arguments) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.info(message, arguments));
	}
	
	public static void logWarn(Object client, String message) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) -> logger.warn(message));
	}
	
	public static void logWarn(Object client, String message, Object... arguments) {
		Optional.ofNullable(getLogger(client)).ifPresent((logger) ->logger.warn(message, arguments));
	}
	
	public static void disableLogging(Object object) {
		getLoggerEntry(object).setValue(Boolean.FALSE);
	}
	
	public static void enableLogging(Object object) {
		getLoggerEntry(object).setValue(Boolean.TRUE);
	}
}