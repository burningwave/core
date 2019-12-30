package org.burningwave.core.function;

import org.burningwave.Throwables;

@FunctionalInterface
public interface ThrowingRunnable {

    public abstract void run() throws Throwable;
    
    static void run(ThrowingRunnable runnable) {
		try {
			runnable.run();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
    
}