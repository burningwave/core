package org.burningwave.core;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.LoggerFactory;

public class SLF4JManagedLoggerRepository extends ManagedLogger.Repository.Abst {
	private Map<Class<?>, Map.Entry<org.slf4j.Logger, Boolean>> loggers;
	
	
	public SLF4JManagedLoggerRepository() {
		super();
		loggers = new HashMap<>();
	}
	
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
		if (!isEnabled || namesOfClassesForWhichLoggingIsDisabled.contains(client.getName())) {
			return null;
		}
		Map.Entry<org.slf4j.Logger, Boolean> loggerEntry = getLoggerEntry(client);
		return loggerEntry.getValue()? loggerEntry.getKey() : null;
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
		getLoggerEntry(client).setValue(Boolean.FALSE);
	}
	
	public void enableLogging(Class<?> client) {
		super.enableLogging(client);
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
