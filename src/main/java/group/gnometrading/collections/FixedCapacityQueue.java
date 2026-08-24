package group.gnometrading.collections;

import group.gnometrading.collections.buffer.ArrayFactory;
import group.gnometrading.utils.Resettable;
import java.util.function.Supplier;

/**
 * A fixed-capacity, single-threaded FIFO queue backed by a pre-allocated circular array.
 *
 * <p>All slots are pre-allocated at construction time via the supplied factory — no GC allocation
 * occurs on {@link #offer()}, {@link #peek()}, or {@link #poll()}. Callers write into the slot
 * returned by {@link #offer()} in-place, then inspect the slot returned by {@link #peek()} and
 * advance the head with {@link #poll()} when ready to discard it.
 *
 * <p>Not thread-safe. Capacity must be a positive power of two.
 */
public final class FixedCapacityQueue<T> implements Resettable {

    private final T[] buffer;
    private final int capacity;
    private final int mask;
    private int head;
    private int tail;

    public FixedCapacityQueue(final ArrayFactory<T> arrayFactory, final Supplier<T> supplier, final int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a positive power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = arrayFactory.createArray(capacity);
        for (int i = 0; i < capacity; i++) {
            this.buffer[i] = supplier.get();
        }
    }

    /**
     * Returns the pre-allocated slot at the tail and advances the tail.
     *
     * @throws IllegalStateException if the queue is full
     */
    public T offer() {
        if (tail - head >= capacity) {
            throw new IllegalStateException("Queue is full");
        }
        return buffer[tail++ & mask];
    }

    /**
     * Returns the pre-allocated slot at the head without advancing it, or {@code null} if empty.
     */
    public T peek() {
        if (head == tail) {
            return null;
        }
        return buffer[head & mask];
    }

    /**
     * Advances the head, discarding the current front element. Must be called after {@link #peek()}.
     */
    public void poll() {
        head++;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public int size() {
        return tail - head;
    }

    @Override
    public void reset() {
        head = 0;
        tail = 0;
    }
}
