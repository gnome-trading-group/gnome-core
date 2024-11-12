package group.gnometrading.collections;

import group.gnometrading.utils.Resettable;

import java.util.Collection;

public interface IntMap<T> extends Resettable {
    T get(int key);

    T put(int key, T value);

    boolean containsKey(int key);

    int size();

    boolean isEmpty();

    T remove(int key);

    void clear();

    Collection<Integer> keys();

    @Override
    default void reset() {
        clear();
    }
}
