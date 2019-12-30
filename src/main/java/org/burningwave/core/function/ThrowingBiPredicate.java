package org.burningwave.core.function;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingBiPredicate<T, U> {

    
    boolean test(T t, U u) throws Throwable;

    default ThrowingBiPredicate<T, U> and(ThrowingBiPredicate<? super T, ? super U> other) {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) && other.test(t, u);
    }

    default ThrowingBiPredicate<T, U> negate() {
        return (T t, U u) -> !test(t, u);
    }

    default ThrowingBiPredicate<T, U> or(ThrowingBiPredicate<? super T, ? super U> other) {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) || other.test(t, u);
    }
}
