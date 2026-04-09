package group.gnometrading.collections;

import group.gnometrading.utils.Resettable;
import java.util.Collection;
import java.util.function.Consumer;

public interface GnomeMap<K, V> extends Resettable {
    V get(K key);

    V put(K key, V value);

    boolean containsKey(K key);

    int size();

    boolean isEmpty();

    V remove(K key);

    void clear();

    Collection<K> keys();

    void forEachValue(Consumer<V> consumer);

    void forEachKey(Consumer<K> consumer);

    @Override
    default void reset() {
        clear();
    }
}
