package group.gnometrading.collections;

import java.util.Collection;
import java.util.function.IntConsumer;

public interface IntToIntMap {
    int get(int key);

    void put(int key, int value);

    boolean containsKey(int key);

    int size();

    boolean isEmpty();

    int remove(int key);

    void clear();

    @SuppressWarnings("checkstyle:IllegalType")
    Collection<Integer> keys();

    void forEachValue(IntConsumer consumer);

    void forEachKey(IntConsumer consumer);
}
