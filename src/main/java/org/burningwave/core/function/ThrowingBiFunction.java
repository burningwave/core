package org.burningwave.core.function;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface ThrowingBiFunction<P0, P1, R> {

    R apply(P0 p0, P1 p1) throws Throwable;

    default <V> ThrowingBiFunction<P0, P1, V> andThen(Function<? super R, ? extends V> after) {
    	Objects.requireNonNull(after);
    	return (P0 p0, P1 p1) -> after.apply(apply(p0, p1));
    }
}
