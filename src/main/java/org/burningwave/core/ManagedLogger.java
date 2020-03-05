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

import static org.burningwave.core.assembler.StaticComponentsContainer.ManagedLoggersRepository;;

public interface ManagedLogger {
		
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T disableLogging() {
		ManagedLoggersRepository.disableLogging(this.getClass());
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public default <T extends ManagedLogger> T enableLogging() {
		ManagedLoggersRepository.enableLogging(this.getClass());
		return (T) this;
	}
	
	default void logError(String message, Throwable exc) {
		ManagedLoggersRepository.logError(this.getClass(), message, exc);
	}
	
	default void logError(String message) {
		ManagedLoggersRepository.logError(this.getClass(), message);
	}
	
	default void logDebug(String message) {
		ManagedLoggersRepository.logDebug(this.getClass(), message);
	}
	
	default void logDebug(String message, Object... arguments) {
		ManagedLoggersRepository.logDebug(this.getClass(), message, arguments);
	}
	
	default void logInfo(String message) {
		ManagedLoggersRepository.logInfo(this.getClass(), message);
	}
	
	default void logInfo(String message, Object... arguments) {
		ManagedLoggersRepository.logInfo(this.getClass(), message, arguments);
	}
	
	default void logWarn(String message) {
		ManagedLoggersRepository.logWarn(this.getClass(), message);
	}
	
	default void logWarn(String message, Object... arguments) {
		ManagedLoggersRepository.logWarn(this.getClass(), message, arguments);
	}
	
	
	public static interface Repository {
		public static final String REPOSITORY_TYPE_CONFIG_KEY = "managed-logger.repository";
		public static final String REPOSITORY_ENABLED_FLAG_CONFIG_KEY = "managed-logger.repository.enabled";
		
		public boolean isEnabled();
		
		public void disableLogging();
		
		public void enableLogging();
		
		public void disableLogging(Class<?> client);
		
		public void enableLogging(Class<?> client);
		
		public void logError(Class<?> client, String message, Throwable exc);
		
		public void logError(Class<?> client, String message);
		
		public void logDebug(Class<?> client, String message);
		
		public void logDebug(Class<?> client, String message, Object... arguments);
		
		public void logInfo(Class<?> client, String message);
		
		public void logInfo(Class<?> client, String message, Object... arguments);
		
		public void logWarn(Class<?> client, String message);
		
		public void logWarn(Class<?> client, String message, Object... arguments);
	}
}
