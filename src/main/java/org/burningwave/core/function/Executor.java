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
package org.burningwave.core.function;

import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggersRepository;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

public interface Executor {

    @SafeVarargs
	static <E extends Throwable> void runAndLogExceptions(ThrowingRunnable<? extends Throwable>... runnables) {
		for (ThrowingRunnable<? extends Throwable> runnable : runnables) {
	    	try {
				runnable.run();
			} catch (Throwable exc) {
				ManagedLoggersRepository.logError(() -> Executor.class.getName(), exc);
			}
		}
	}
    
    static <E extends Throwable> void run(ThrowingRunnable<E> runnable) {
		try {
			runnable.run();
		} catch (Throwable exc) {
			Throwables.throwException(exc);
		}
	}
    
    static <E extends Throwable> void run(ThrowingRunnable<E> runnable, int attemptsNumber) {
		while (true) {
			try {
				runnable.run();
			} catch (Throwable exc) {
				if (attemptsNumber > 1) {
					Throwables.throwException(exc);
				}
			}
			--attemptsNumber;
		}
	}
    
	static <T, E extends Throwable> T get(ThrowingSupplier<T, ? extends E> supplier) {
		try {
			return supplier.get();
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}
	
	static <T, E extends Throwable> T get(ThrowingSupplier<T, ? extends E> supplier, int attemptsNumber) {
		while (true) {
			try {
				return supplier.get();
			} catch (Throwable exc) {
				if (attemptsNumber > 1) {
					Throwables.throwException(exc);
				}
			}
			--attemptsNumber;
		}
	}
}