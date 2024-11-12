package group.gnometrading.collections;

import group.gnometrading.utils.Resettable;

import java.util.Collection;

public interface LongMap<T> extends Resettable {
    T get(long key);

    T put(long key, T value);

    boolean containsKey(long key);

    int size();

    boolean isEmpty();

    T remove(long key);

    void clear();

    Collection<Long> keys();

    @Override
    default void reset() {
        clear();
    }
}
