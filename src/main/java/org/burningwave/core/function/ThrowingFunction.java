package org.burningwave.core.function;


import java.util.Objects;


@FunctionalInterface
public interface ThrowingFunction<T, R> {


    R apply(T t) throws Throwable;

    
    default <V> ThrowingFunction<V, R> compose(ThrowingFunction<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }

    
    default <V> ThrowingFunction<T, V> andThen(ThrowingFunction<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    
    static <T> ThrowingFunction<T, T> identity() {
        return t -> t;
    }
}
