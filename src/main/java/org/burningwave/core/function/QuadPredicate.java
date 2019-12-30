package org.burningwave.core.function;


import java.util.Objects;


@FunctionalInterface
public interface QuadPredicate<P0, P1, P2, P3> {

    boolean test(P0 p0, P1 p1, P2 p2, P3 p3);

    default QuadPredicate<P0, P1, P2, P3> and(QuadPredicate<? super P0, ? super P1, ? super P2, ? super P3> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2, P3 p3) -> test(p0, p1, p2, p3) && other.test(p0, p1, p2, p3);
    }

    default QuadPredicate<P0, P1, P2, P3> negate() {
        return (P0 p0, P1 p1, P2 p2, P3 p3) -> !test(p0, p1, p2, p3);
    }

    default QuadPredicate<P0, P1, P2, P3> or(QuadPredicate<? super P0, ? super P1, ? super P2, ? super P3> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2, P3 p3) -> test(p0, p1, p2, p3) || other.test(p0, p1, p2, p3);
    }
    
}
