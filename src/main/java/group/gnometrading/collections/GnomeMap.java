package group.gnometrading.collections;

import java.util.Collection;

public interface GnomeMap<K, V> {
    V get(K key);

    V put(K key, V value);

    boolean containsKey(K key);

    int size();

    boolean isEmpty();

    V remove(K key);

    void clear();

    Collection<K> keys();
}
