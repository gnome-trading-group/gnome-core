package group.gnometrading.utils;

import java.util.function.Supplier;

/**
 * A Builder of type T is used to configure an object
 * and provide an instance of T.
 * <p>
 * Extends {@link Supplier} to replicate use case.
 *
 * @param <T> type of the configurable object
 */
@FunctionalInterface
public interface Builder<T> extends Supplier<T> {

    /**
     * Builds and returns an instance of T.
     * <p>
     * If the type is mutable, the Builder should always create a
     * new instance. If the type is immutable, the Builder may
     * return a previously created instance or create a new instance.
     * @return an instance type of T
     */
    T build();

    @Override
    default T get() {
        return build();
    }
}
