package group.gnometrading.collections.buffer;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ManyToOneRingBuffer<T> implements RingBuffer<T> {

    private final T[] buffer;
    private final int capacity;
    private final int mask;

    private final AtomicLong head = new AtomicLong(0); // next sequence to consume
    private final AtomicLong tail = new AtomicLong(0); // next sequence to claim

    private final AtomicLongArray slotStates;

    private static final long EMPTY = 0L;
    private static final long CLAIMED = 1L;
    private static final long PUBLISHED = 2L;

    public ManyToOneRingBuffer(ArrayFactory<T> arrayFactory, Supplier<T> supplier, int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a positive power of 2");
        }

        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = arrayFactory.createArray(capacity);
        for (int i = 0; i < capacity; i++) {
            this.buffer[i] = supplier.get();
        }

        this.slotStates = new AtomicLongArray(capacity);
        for (int i = 0; i < capacity; i++) {
            this.slotStates.set(i, EMPTY);
        }
    }

    @Override
    public int tryClaim() {
        while (true) {
            final long currentTail = tail.get();

            if (currentTail - head.get() >= capacity) {
                return -1;
            }

            if (tail.compareAndSet(currentTail, currentTail + 1)) {
                final int index = (int) (currentTail & mask);
                slotStates.set(index, CLAIMED);
                return index;
            }

            // If CAS failed, another thread claimed it, retry
        }
    }

    @Override
    public void commit(final int index) {
        slotStates.set(index, PUBLISHED);
    }



    @Override
    public void read(final MessageConsumer<T> consumer, final int limit) {
        long currentHead = this.head.get();
        final long currentTail = this.tail.get();

        final long available = currentTail - currentHead;

        final int toRead = (int) Math.min(available, limit);

        for (int i = 0; i < toRead; i++) {
            final int index = (int) (currentHead & mask);
            if (slotStates.get(index) != PUBLISHED) {
                break;
            }

            consumer.accept(buffer[index]);
            slotStates.set(index, EMPTY);
            currentHead++;
        }

        this.head.set(currentHead);
    }

    @Override
    public T indexAt(int index) {
        return buffer[index];
    }

    @Override
    public void reset() {
        this.head.set(0);
        this.tail.set(0);
        for (int i = 0; i < capacity; i++) {
            this.slotStates.set(i, EMPTY);
        }
    }
}
