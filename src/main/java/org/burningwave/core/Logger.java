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
