package group.gnometrading.collections;

import java.util.Collection;

public interface IntMap<T> {
    T get(int key);

    T put(int key, T value);

    boolean containsKey(int key);

    int size();

    boolean isEmpty();

    T remove(int key);

    void clear();

    Collection<Integer> keys();
}
