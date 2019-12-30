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