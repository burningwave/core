package org.burningwave.core.function;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingPredicate<T> {

    boolean test(T t) throws Throwable;

    default ThrowingPredicate<T> and(ThrowingPredicate<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) && other.test(t);
    }

    default ThrowingPredicate<T> negate() {
        return (t) -> !test(t);
    }

    default ThrowingPredicate<T> or(ThrowingPredicate<? super T> other) {
        Objects.requireNonNull(other);
        return (t) -> test(t) || other.test(t);
    }

    static <T> ThrowingPredicate<T> isEqual(Object targetRef) {
        return (null == targetRef)
                ? Objects::isNull
                : object -> targetRef.equals(object);
    }
}

