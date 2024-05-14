package group.gnometrading.collections;

import java.util.Collection;

public interface LongMap<T> {
    T get(long key);

    T put(long key, T value);

    boolean containsKey(long key);

    int size();

    boolean isEmpty();

    T remove(long key);

    void clear();

    Collection<Long> keys();
}
