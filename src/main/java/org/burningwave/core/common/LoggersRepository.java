/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentli
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.LoggerFactory;

public class LoggersRepository {
	private final static Map<Class<?>, org.slf4j.Logger> LOGGERS = new ConcurrentHashMap<>();
	
	private static org.slf4j.Logger getLogger(Object object) {
		Class<?> cls = Classes.retrieveFrom(object);
		org.slf4j.Logger logger = LOGGERS.get(cls);
		if (logger == null) {
			LOGGERS.put(cls, logger = LoggerFactory.getLogger(cls));
		}
		return logger;
	}
	
	public static void logError(Object client, String message, Throwable exc) {
		getLogger(client).error(message, exc);
	}
	
	public static void logError(Object client, String message) {
		getLogger(client).error(message);
	}
	
	public static void logDebug(Object client, String message) {
		getLogger(client).debug(message);
	}
	
	public static void logDebug(Object client, String message, Object... arguments) {
		getLogger(client).debug(message, arguments);
	}
	
	public static void logInfo(Object client, String message) {
		getLogger(client).info(message);
	}
	
	public static void logInfo(Object client, String message, Object... arguments) {
		getLogger(client).info(message, arguments);
	}
	
	public static void logWarn(Object client, String message) {
		getLogger(client).warn(message);
	}
	
	public static void logWarn(Object client, String message, Object... arguments) {
		getLogger(client).warn(message, arguments);
	}
}