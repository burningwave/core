package org.burningwave.core.function;

import java.util.Objects;

@FunctionalInterface
public interface TriConsumer<P0, P1, P2> {

    void accept(P0 p0, P1 p1, P2 p2);

    default TriConsumer<P0, P1, P2> andThen(TriConsumer<? super P0, ? super P1, ? super P2> after) {
        Objects.requireNonNull(after);
        return (p0, p1, p2) -> {
            accept(p0, p1, p2);
            after.accept(p0, p1, p2);
        };
    }
}
