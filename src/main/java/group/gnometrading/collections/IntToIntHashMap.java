package group.gnometrading.collections;

import group.gnometrading.pools.Pool;
import group.gnometrading.pools.PoolNode;
import group.gnometrading.pools.SingleThreadedObjectPool;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.IntConsumer;

/**
 * Single-threaded int-to-int hash map with no boxing on keys or values.
 * Returns {@link #MISSING} from {@link #get} and {@link #remove} when a key is absent.
 */
public final class IntToIntHashMap implements IntToIntMap {

    public static final int MISSING = Integer.MIN_VALUE;

    static final int MAX_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_CAPACITY = 1 << 7;
    private static final int DEFAULT_NODE_POOL_CAPACITY = 100;

    private final Pool<Node> nodePool;
    private final float loadFactor;
    private Node[] hashTable;
    private int count;
    private int loadThreshold;

    public IntToIntHashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public IntToIntHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public IntToIntHashMap(int initialCapacity, float loadFactor) {
        this.nodePool = new SingleThreadedObjectPool<>(Node::new, DEFAULT_NODE_POOL_CAPACITY);

        int capacity = 1;
        while (capacity < initialCapacity) {
            capacity <<= 1;
        }

        this.loadFactor = loadFactor;
        this.count = 0;
        this.hashTable = new Node[capacity];
        setTable(this.hashTable);
    }

    @Override
    public int get(final int key) {
        final int hash = hash(key);
        Node node = this.hashTable[hash & (this.hashTable.length - 1)];
        while (node != null) {
            if (node.key == key) {
                return node.value;
            }
            node = node.next;
        }
        return MISSING;
    }

    @Override
    public void put(final int key, final int value) {
        final int hash = hash(key);
        if (count == loadThreshold) {
            rehash();
        }

        int idx = hash & (this.hashTable.length - 1);
        Node node = this.hashTable[idx];
        while (node != null && node.key != key) {
            node = node.next;
        }

        if (node != null) {
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
    }

    @Override
    public boolean containsKey(final int key) {
        return get(key) != MISSING;
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
    public int remove(final int key) {
        final int hash = hash(key);
        int idx = hash & (this.hashTable.length - 1);

        var node = this.hashTable[idx];
        var prev = this.hashTable[idx];
        while (node != null && node.key != key) {
            prev = node;
            node = node.next;
        }

        if (node == null) {
            return MISSING;
        }

        int prevValue = node.value;
        if (node == this.hashTable[idx]) {
            this.hashTable[idx] = node.next;
        } else {
            prev.next = node.next;
        }

        nodePool.release(node.self);
        this.count--;
        return prevValue;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.hashTable.length; i++) {
            Node at = this.hashTable[i];
            while (at != null) {
                nodePool.release(at.self);
                at = at.next;
            }
            this.hashTable[i] = null;
        }
        this.count = 0;
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalType")
    public Collection<Integer> keys() {
        Collection<Integer> keys = new HashSet<>(size());
        for (int idx = 0; idx < this.hashTable.length; idx++) {
            Node current = this.hashTable[idx];
            while (current != null) {
                keys.add(current.key);
                current = current.next;
            }
        }
        return keys;
    }

    @Override
    public void forEachValue(final IntConsumer consumer) {
        for (int i = 0; i < this.hashTable.length; i++) {
            Node node = this.hashTable[i];
            while (node != null) {
                consumer.accept(node.value);
                node = node.next;
            }
        }
    }

    @Override
    public void forEachKey(final IntConsumer consumer) {
        for (int i = 0; i < this.hashTable.length; i++) {
            Node node = this.hashTable[i];
            while (node != null) {
                consumer.accept(node.key);
                node = node.next;
            }
        }
    }

    private void setTable(final Node[] newHashTable) {
        this.hashTable = newHashTable;
        this.loadThreshold = (int) (this.hashTable.length * loadFactor);
    }

    private void rehash() {
        final var oldTable = this.hashTable;
        if (oldTable.length >= MAX_CAPACITY) {
            return;
        }

        final Node[] newTable = new Node[oldTable.length << 1];
        for (int tableIdx = 0; tableIdx < oldTable.length; tableIdx++) {
            Node node = oldTable[tableIdx];
            if (node == null) {
                continue;
            }

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

    private static int hash(final int key) {
        return key;
    }

    private static class Node {
        int key = -1;
        int value = -1;
        Node next = null;
        PoolNode<Node> self = null;
    }
}
