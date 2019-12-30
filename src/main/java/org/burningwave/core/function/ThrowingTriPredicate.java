package org.burningwave.core.function;


import java.util.Objects;


@FunctionalInterface
public interface ThrowingTriPredicate<P0, P1, P2> {

    boolean test(P0 p0, P1 p1, P2 p2) throws Throwable;

    default ThrowingTriPredicate<P0, P1, P2> and(ThrowingTriPredicate<? super P0, ? super P1, ? super P2> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2) -> test(p0, p1, p2) && other.test(p0, p1, p2);
    }

    default ThrowingTriPredicate<P0, P1, P2> negate() {
        return (P0 p0, P1 p1, P2 p2) -> !test(p0, p1, p2);
    }

    default ThrowingTriPredicate<P0, P1, P2> or(ThrowingTriPredicate<? super P0, ? super P1, ? super P2> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2) -> test(p0, p1, p2) || other.test(p0, p1, p2);
    }
    
}
