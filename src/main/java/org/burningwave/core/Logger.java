package org.burningwave.core;

import org.burningwave.core.common.LoggersRepository;

public interface Logger {
	
	default void logError(String message, Throwable exc) {
		LoggersRepository.logError(this, message, exc);
	}
	
	default void logError(String message) {
		LoggersRepository.logError(this, message);
	}
	
	default void logDebug(String message) {
		LoggersRepository.logDebug(this, message);
	}
	
	default void logDebug(String message, Object... arguments) {
		LoggersRepository.logDebug(this, message, arguments);
	}
	
	default void logInfo(String message) {
		LoggersRepository.logInfo(this, message);
	}
	
	default void logInfo(String message, Object... arguments) {
		LoggersRepository.logInfo(this, message, arguments);
	}
	
	default void logWarn(String message) {
		LoggersRepository.logWarn(this, message);
	}
	
	default void logWarn(String message, Object... arguments) {
		LoggersRepository.logWarn(this, message, arguments);
	}
	
}
