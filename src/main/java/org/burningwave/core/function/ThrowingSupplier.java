package org.burningwave.core.function;

import org.burningwave.Throwables;

@FunctionalInterface
public interface ThrowingSupplier<T> {

	T get() throws Throwable;
	
	static <T> T get(ThrowingSupplier<T> supplier) {
		try {
			return supplier.get();
		} catch (Throwable exc) {
			throw Throwables.toRuntimeException(exc);
		}
	}
}
