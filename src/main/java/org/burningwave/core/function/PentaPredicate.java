package org.burningwave.core.function;


import java.util.Objects;


@FunctionalInterface
public interface PentaPredicate<P0, P1, P2, P3, P4> {

    boolean test(P0 p0, P1 p1, P2 p2, P3 p3, P4 p4);

    default PentaPredicate<P0, P1, P2, P3, P4> and(PentaPredicate<? super P0, ? super P1, ? super P2, ? super P3, ? super P4> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2, P3 p3, P4 p4) -> test(p0, p1, p2, p3, p4) && other.test(p0, p1, p2, p3, p4);
    }

    default PentaPredicate<P0, P1, P2, P3, P4> negate() {
        return (P0 p0, P1 p1, P2 p2, P3 p3, P4 p4) -> !test(p0, p1, p2, p3, p4);
    }

    default PentaPredicate<P0, P1, P2, P3, P4> or(PentaPredicate<? super P0, ? super P1, ? super P2, ? super P3, ? super P4> other) {
        Objects.requireNonNull(other);
        return (P0 p0, P1 p1, P2 p2, P3 p3, P4 p4) -> test(p0, p1, p2, p3, p4) || other.test(p0, p1, p2, p3, p4);
    }
}
