package group.gnometrading.collections;

import group.gnometrading.pools.Pool;
import group.gnometrading.pools.PoolNode;
import group.gnometrading.pools.SingleThreadedObjectPool;

import java.util.Collection;
import java.util.HashSet;

public class PooledHashMap<K, V> implements GnomeMap<K, V> {
    static final int MAX_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_CAPACITY = 1 << 7;

    private final Pool<Node<K, V>> nodePool;
    private final float loadFactor;
    private Node<K, V>[] hashTable;
    private int count;
    private int loadThreshold;

    public PooledHashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public PooledHashMap(final int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }


    public PooledHashMap(final int initialCapacity, final float loadFactor) {
        this.nodePool = new SingleThreadedObjectPool<Node<K, V>>(() -> new Node(), 100); // TODO: Good number here for capacity?

        int capacity = 1;
        while(capacity < initialCapacity) capacity <<= 1;

        this.loadFactor = loadFactor;
        this.count = 0;
        final Node<K, V>[] table = Node.createArray(capacity);
        setTable(table);
    }

    @Override
    public V get(final K key) {
        final int hash = hash(key);
        Node<K, V> node = this.hashTable[hash & (this.hashTable.length - 1)];
        while (node != null) {
            if (node.key.equals(key)) {
                return node.value;
            }
            node = node.next;
        }
        return null;
    }

    @Override
    public V put(final K key, final V value) {
        final int hash = hash(key);
        if (count == loadThreshold) {
            rehash();
        }

        int idx = hash & (this.hashTable.length - 1);
        Node<K, V> node = this.hashTable[idx];
        while (node != null && !node.key.equals(key)) {
            node = node.next;
        }

        V prev = null;
        if (node != null) {
            prev = node.value;
            node.value = value;
        } else {
            this.count++;
            var newPoolNode = nodePool.acquire();
            var newNode = newPoolNode.getItem();

            newNode.key = key;
            newNode.value = value;
            newNode.self = newPoolNode;
            newNode.next = this.hashTable[idx];

            this.hashTable[idx] = newNode;
        }
        return prev;
    }

    @Override
    public boolean containsKey(final K key) {
        return get(key) != null;
    }

    @Override
    public int size() {
        return this.count;
    }

    @Override
    public boolean isEmpty() {
        return this.count == 0;
    }

    @Override
    public V remove(final K key) {
        final int hash = hash(key);
        int idx = hash & (this.hashTable.length - 1);

        var node = this.hashTable[idx];
        var prev = this.hashTable[idx];
        while (node != null && !node.key.equals(key)) {
            prev = node;
            node = node.next;
        }

        V prevValue = null;
        if (node != null) {
            prevValue = node.value;
            if (node == this.hashTable[idx]) {
                this.hashTable[idx] = node.next;
            } else {
                prev.next = node.next;
            }

            nodePool.release(node.self);
            this.count--;
        }
        return prevValue;
    }

    @Override
    public void clear() {
        for (Node<K, V> tNode : this.hashTable) {
            Node<K, V> at = tNode;
            while (at != null) {
                nodePool.release(at.self);
                at = at.next;
            }
        }
        this.count = 0;
    }

    @Override
    public Collection<K> keys() {
        Collection<K> keys = new HashSet<>(size());
        for (var node : this.hashTable) {
            while (node != null) {
                keys.add(node.key);
                node = node.next;
            }
        }
        return keys;
    }

    private void setTable(Node<K, V>[] hashTable) {
        this.hashTable = hashTable;
        this.loadThreshold = (int) (this.hashTable.length * this.loadFactor);
    }

    private void rehash() {
        // TODO: Should we avoid rehashing entirely?
        final var oldTable = this.hashTable;
        if (oldTable.length >= MAX_CAPACITY) {
            return;
        }

        final Node<K, V>[] newTable = Node.createArray(oldTable.length << 1);
        for (Node<K, V> node : oldTable) {
            if (node == null) continue;

            do {
                var next = node.next;
                int newIdx = hash(node.key) & (newTable.length - 1);

                final var nodeNewTable = newTable[newIdx];
                newTable[newIdx] = node;

                while (next != null) {
                    final int nextIdx = hash(next.key) & (newTable.length - 1);
                    if (nextIdx == newIdx) {
                        node = next;
                        next = next.next;
                    } else {
                        break;
                    }
                }

                node.next = nodeNewTable;
                node = next;
            } while (node != null);
        }
        setTable(newTable);
    }

    private static <K> int hash(final K key) {
        return key.hashCode(); // hopefully compiled away
    }

    private static class Node<K, V> {
        K key = null;
        V value = null;
        Node<K, V> next = null;
        PoolNode<Node<K, V>> self = null;

        @SuppressWarnings("unchecked")
        public static <K, V> Node<K, V>[] createArray(int size) {
            return new Node[size];
        }
    }
}
