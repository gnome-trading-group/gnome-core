package group.gnometrading.pools;

import group.gnometrading.annotations.VisibleForTesting;

import java.util.function.Supplier;

public class SingleThreadedObjectPool<T> implements Pool<T> {

    private static final int DEFAULT_CAPACITY = 50;

    private final PoolNodeImpl<T> freeNodes = new PoolNodeImpl<>();
    private final PoolNodeImpl<T> usedNodes = new PoolNodeImpl<>();
    private int additionalNodes = 0;
    private final Supplier<T> supplier;

    public SingleThreadedObjectPool(final Class<T> clazz) {
        this(clazz, DEFAULT_CAPACITY);
    }

    public SingleThreadedObjectPool(final Class<T> clazz, final int defaultCapacity) {
        this(() -> newInstance(clazz), defaultCapacity);
    }

    public SingleThreadedObjectPool(final Supplier<T> supplier) {
        this(supplier, DEFAULT_CAPACITY);
    }

    public SingleThreadedObjectPool(final Supplier<T> supplier, final int defaultCapacity) {
        this.supplier = supplier;
        PoolNodeImpl<T> current = freeNodes;
        for (int i = 0; i < defaultCapacity; i++) {
            final var node = new PoolNodeImpl<T>();
            node.item = this.supplier.get();
            node.insertAfter(current);
            current = node;
        }
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Class " + clazz.getSimpleName() + " needs a default constructor for a pool.", e);
        }
    }

    @Override
    public PoolNode<T> acquire() {
        if (freeNodes.next == null) {
            freeNodes.next = new PoolNodeImpl<>();
            freeNodes.next.prev = freeNodes;
            freeNodes.next.item = this.supplier.get();
            additionalNodes++;
        }

        final var free = freeNodes.next;
        free.removeMyself();
        free.insertAfter(usedNodes);

        return free;
    }

    @Override
    public void release(final PoolNode<T> node) {
        PoolNodeImpl<T> casted = (PoolNodeImpl<T>) node;

        casted.removeMyself();
        casted.insertAfter(freeNodes);
    }

    @Override
    public void releaseAll() {
        PoolNodeImpl<T> current = usedNodes.next;
        while (current != null) {
            final var next = current.next;
            current.removeMyself();
            current.insertAfter(freeNodes);
            current = next;
        }
    }

    @VisibleForTesting
    public int getAdditionalNodesCreated() {
        return additionalNodes;
    }

    private static class PoolNodeImpl<T> implements PoolNode<T> {
        PoolNodeImpl<T> next, prev = null;

        T item = null;

        @Override
        public T getItem() {
            return item;
        }

        public void removeMyself() {
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
        }

        public void insertAfter(final PoolNodeImpl<T> other) {
            next = other.next;
            if (next != null) {
                next.prev = this;
            }

            other.next = this;
            prev = other;
        }
    }
}
