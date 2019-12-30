package org.burningwave.core.function;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingConsumer<T> {

    void accept(T t) throws Throwable;

    default ThrowingConsumer<T> andThen(ThrowingConsumer<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> { accept(t); after.accept(t); };
    }

}

