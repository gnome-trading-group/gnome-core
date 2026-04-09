package group.gnometrading.collections;

import group.gnometrading.utils.Resettable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface IntMap<T> extends Resettable {
    T get(int key);

    T put(int key, T value);

    boolean containsKey(int key);

    int size();

    boolean isEmpty();

    T remove(int key);

    void clear();

    Collection<Integer> keys();

    void forEachValue(Consumer<T> consumer);

    void forEachKey(IntConsumer consumer);

    @Override
    default void reset() {
        clear();
    }
}
