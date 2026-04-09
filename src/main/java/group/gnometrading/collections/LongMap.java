package group.gnometrading.collections;

import group.gnometrading.utils.Resettable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public interface LongMap<T> extends Resettable {
    T get(long key);

    T put(long key, T value);

    boolean containsKey(long key);

    int size();

    boolean isEmpty();

    T remove(long key);

    void clear();

    Collection<Long> keys();

    void forEachValue(Consumer<T> consumer);

    void forEachKey(LongConsumer consumer);

    @Override
    default void reset() {
        clear();
    }
}
