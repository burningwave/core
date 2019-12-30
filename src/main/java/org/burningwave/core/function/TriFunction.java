package org.burningwave.core.function;

import java.util.Objects;
import java.util.function.Function;

@FunctionalInterface
public interface TriFunction<P0, P1, P2, R> {

    R apply(P0 p0, P1 p1, P2 p2);

    default <V> TriFunction<P0, P1, P2, V> andThen(Function<? super R, ? extends V> after) {
    	Objects.requireNonNull(after);
    	return (P0 p0, P1 p1, P2 p2) -> after.apply(apply(p0, p1, p2));
    }
}
