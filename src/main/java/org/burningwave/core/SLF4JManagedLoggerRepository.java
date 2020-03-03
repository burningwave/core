package org.burningwave.core;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

public class SLF4JManagedLoggerRepository implements ManagedLogger.Repository{
	private Map<Class<?>, Map.Entry<org.slf4j.Logger, Boolean>> loggers = new HashMap<>();
	private boolean isDisabled;
	
	private Map.Entry<org.slf4j.Logger, Boolean> getLoggerEntry(Class<?> client) {
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = loggers.get(client);
		if (loggerEntry == null) {
			synchronized (loggers.toString() + "_" + client.toString()) {
				loggerEntry = loggers.get(client);
				if (loggerEntry == null) {
					loggers.put(client, loggerEntry = new AbstractMap.SimpleEntry<>(LoggerFactory.getLogger(client), Boolean.TRUE));
				}
			}
		}
		return loggerEntry;
	}

	private void log(Class<?> client, Consumer<org.slf4j.Logger> loggerConsumer) {
		Optional.ofNullable(getLogger(client)).ifPresent(logger -> loggerConsumer.accept(logger));
	}
	
	private org.slf4j.Logger getLogger(Class<?> client) {
		if (isDisabled) {
			return null;
		}
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = getLoggerEntry(client);
		return loggerEntry.getValue()? loggerEntry.getKey() : null;
	}	

	public void disableLogging() {
		isDisabled = true;	
	}

	public void enableLogging() {
		isDisabled = false;		
	}
	
	public void disableLogging(Class<?> client) {
		getLoggerEntry(client).setValue(Boolean.FALSE);
	}
	
	public void enableLogging(Class<?> client) {
		getLoggerEntry(client).setValue(Boolean.TRUE);
	}
	
	public void logError(Class<?> client, String message, Throwable exc) {
		log(client, (logger) -> logger.error(message, exc));
	}
	
	public void logError(Class<?> client, String message) {
		log(client, (logger) -> logger.error(message));
	}
	
	public void logDebug(Class<?> client, String message) {
		log(client, (logger) -> logger.debug(message));
	}
	
	public void logDebug(Class<?> client, String message, Object... arguments) {
		log(client, (logger) -> logger.debug(message, arguments));
	}
	
	public void logInfo(Class<?> client, String message) {
		log(client, (logger) -> logger.info(message));
	}
	
	public void logInfo(Class<?> client, String message, Object... arguments) {
		log(client, (logger) -> logger.info(message, arguments));
	}
	
	public void logWarn(Class<?> client, String message) {
		log(client, (logger) -> logger.warn(message));
	}
	
	public void logWarn(Class<?> client, String message, Object... arguments) {
		log(client, (logger) -> logger.warn(message, arguments));
	}

}
