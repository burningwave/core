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
 * Copyright (c) 2019-2021 Roberto Gentili
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

import static org.burningwave.core.assembler.StaticComponentContainer.Driver;
import static org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository;

public interface TimeCountExecutor {


	static <E extends Throwable> void runAndLogExceptions(ThrowingRunnable<? extends Throwable> runnable) {
		long initialTime = System.currentTimeMillis();
		Executor.runAndLogExceptions(runnable);
    	ManagedLoggerRepository.logInfo(
			TimeCountExecutor.class::getName,
    		"{} - Elapsed time: {}",
    		runnable, 
    		getFormattedDifferenceOfMillis(
    			System.currentTimeMillis(), initialTime
    		)
    	);
	}


	static <E extends Throwable> void runAndIgnoreExceptions(ThrowingRunnable<? extends Throwable> runnable) {
		long initialTime = System.currentTimeMillis();
		Executor.runAndIgnoreExceptions(runnable);
    	ManagedLoggerRepository.logInfo(
    		TimeCountExecutor.class::getName,
    		"{} - Elapsed time: {}",
    		runnable, 
    		getFormattedDifferenceOfMillis(
    			System.currentTimeMillis(), initialTime
    		)
    	);
	}

    static <E extends Throwable> void run(ThrowingRunnable<E> runnable) {
		long initialTime = System.currentTimeMillis();
		try {
	    	Executor.run(runnable);
		} finally {
	    	ManagedLoggerRepository.logInfo(
    			TimeCountExecutor.class::getName,
        		"{} - Elapsed time: {}",
        		runnable, 
        		getFormattedDifferenceOfMillis(
        			System.currentTimeMillis(), initialTime
        		)
	    	);
		}
	}
    
    static <E extends Throwable> void run(ThrowingRunnable<E> runnable, int attemptsNumber) {
    	long initialTime = System.currentTimeMillis();
		try {
			Executor.run(runnable, attemptsNumber);
		} finally {
	    	ManagedLoggerRepository.logInfo(
    			TimeCountExecutor.class::getName,
        		"{} - Elapsed time: {}",
        		runnable, 
        		getFormattedDifferenceOfMillis(
        			System.currentTimeMillis(), initialTime
        		)
	    	);
		}
	}
    
    static <I, E extends Throwable> void accept(ThrowingConsumer<I, E> consumer, I input) {
		long initialTime = System.currentTimeMillis();
		try {
			Executor.accept(consumer, input);
		} finally {
	    	ManagedLoggerRepository.logInfo(
    			TimeCountExecutor.class::getName,
        		"{} - Elapsed time: {}",
        		consumer, 
        		getFormattedDifferenceOfMillis(
        			System.currentTimeMillis(), initialTime
        		)
	    	);
		}
	}

	static <T, E extends Throwable> T get(ThrowingSupplier<T, ? extends E> supplier) {
    	long initialTime = System.currentTimeMillis();
		try {
			return Executor.get(supplier);
		} finally {
	    	ManagedLoggerRepository.logInfo(
    			TimeCountExecutor.class::getName,
        		"{} - Elapsed time: {}",
        		supplier, 
        		getFormattedDifferenceOfMillis(
        			System.currentTimeMillis(), initialTime
        		)
	    	);
		}
	}

	static <T, E extends Throwable> T get(ThrowingSupplier<T, ? extends E> supplier, int attemptsNumber) {
		while (true) {
			try {
				return supplier.get();
			} catch (Throwable exc) {
				if (attemptsNumber > 1) {
					Driver.throwException(exc);
				}
			}
			--attemptsNumber;
		}
	}
	
	static String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}
}