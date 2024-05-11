package group.gnometrading.pools;

import java.util.Arrays;
import java.util.function.Supplier;

public class SingleThreadedObjectPool<T> implements Pool<T> {

    private static final int DEFAULT_CAPACITY = 50;

    private final PoolNodeImpl<T> freeNodes = new PoolNodeImpl<>();
    private int additionalNodes = 0;
    private final Supplier<T> supplier;

    public SingleThreadedObjectPool(final Class<T> clazz) {
        this(clazz, DEFAULT_CAPACITY);
    }

    public SingleThreadedObjectPool(final Class<T> clazz, final int defaultCapacity) {
        this(() -> newInstance(clazz), defaultCapacity);
    }

    public SingleThreadedObjectPool(final Supplier<T> supplier, final int defaultCapacity) {
        this.supplier = supplier;
        PoolNodeImpl<T> current = freeNodes;
        for (int i = 0; i < defaultCapacity; i++) {
            current.next = new PoolNodeImpl<>();
            current.next.item = this.supplier.get();
            current = current.next;
        }
    }

    private static <T> T newInstance(Class<T> clazz) {
        try {
            System.out.println(Arrays.toString(clazz.getDeclaredConstructors()));
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Class " + clazz.getSimpleName() + " needs a default constructor for a pool.", e);
        }
    }

    public PoolNode<T> acquire() {
        if (freeNodes.next == null) {
            PoolNodeImpl<T> newNode = new PoolNodeImpl<>();
            newNode.item = this.supplier.get();
            additionalNodes++;
            return newNode;
        } else {
            PoolNodeImpl<T> free = freeNodes.next;
            freeNodes.next = free.next;
            return free;
        }
    }

    public void release(PoolNode<T> node) {
        PoolNodeImpl<T> casted = (PoolNodeImpl<T>) node;
        casted.next = freeNodes.next;
        freeNodes.next = casted;
    }

    public int getAdditionalNodesCreated() {
        return additionalNodes;
    }

    private static class PoolNodeImpl<T> implements PoolNode<T> {
        PoolNodeImpl<T> next = null;

        T item = null;

        @Override
        public T getItem() {
            return item;
        }
    }
}
