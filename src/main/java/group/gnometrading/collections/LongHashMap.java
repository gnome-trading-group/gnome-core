package group.gnometrading.collections;

import group.gnometrading.pools.Pool;
import group.gnometrading.pools.PoolNode;
import group.gnometrading.pools.SingleThreadedObjectPool;

import java.util.Collection;
import java.util.HashSet;

/**
 * Single-threaded int map to avoid boxing.
 * @param <T>
 */
public class LongHashMap<T> implements LongMap<T> {
    static final int MAX_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_CAPACITY = 1 << 7;

    private final Pool<Node<T>> nodePool;
    private final float loadFactor;
    private Node<T>[] hashTable;
    private int count;
    private int loadThreshold;

    public LongHashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public LongHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public LongHashMap(int initialCapacity, float loadFactor) {
        this.nodePool = new SingleThreadedObjectPool<Node<T>>(() -> new Node(), 100); // TODO: Good number here for capacity?

        int capacity = 1;
        while(capacity < initialCapacity) capacity <<= 1;

        this.loadFactor = loadFactor;
        this.count = 0;
        final Node<T>[] table = Node.createArray(capacity);
        setTable(table);
    }

    @Override
    public T get(final long key) {
        final int hash = hash(key);
        Node<T> node = this.hashTable[hash & (this.hashTable.length - 1)];
        while (node != null) {
            if (node.key == key) {
                return node.value;
            }
            node = node.next;
        }
        return null;
    }

    @Override
    public T put(final long key, final T value) {
        final int hash = hash(key);
        if (count == loadThreshold) {
            rehash();
        }

        int idx = hash & (this.hashTable.length - 1);
        Node<T> node = this.hashTable[idx];
        while (node != null && node.key != key) {
            node = node.next;
        }

        T prev = null;
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
    public boolean containsKey(final long key) {
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
    public T remove(final long key) {
        final int hash = hash(key);
        int idx = hash & (this.hashTable.length - 1);

        var node = this.hashTable[idx];
        var prev = this.hashTable[idx];
        while (node != null && node.key != key) {
            prev = node;
            node = node.next;
        }

        T prevValue = null;
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
        for (int i = 0; i < this.hashTable.length; i++) {
            Node<T> at = this.hashTable[i];
            while (at != null) {
                nodePool.release(at.self);
                at = at.next;
            }
            this.hashTable[i] = null;
        }
        this.count = 0;
    }

    @Override
    public Collection<Long> keys() {
        Collection<Long> keys = new HashSet<>( size() );
        for (var node : this.hashTable) {
            while (node != null) {
                keys.add(node.key);
                node = node.next;
            }
        }
        return keys;
    }

    private void setTable(Node<T>[] hashTable) {
        this.hashTable = hashTable;
        this.loadThreshold = (int) (this.hashTable.length * this.loadFactor);
    }

    private void rehash() {
        // TODO: Should we avoid rehashing entirely?
        final var oldTable = this.hashTable;
        if (oldTable.length >= MAX_CAPACITY) {
            return;
        }

        final Node<T>[] newTable = Node.createArray(oldTable.length << 1);
        for (Node<T> node : oldTable) {
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

    private static int hash(final long key) {
        return (int) (key ^ (key >>> 32));
    }

    private static class Node<T> {
        long key = -1;
        T value = null;
        Node<T> next = null;
        PoolNode<Node<T>> self = null;

        @SuppressWarnings("unchecked")
        public static <T> Node<T>[] createArray(int size) {
            return new Node[size];
        }
    }
}
