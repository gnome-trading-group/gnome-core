package group.gnometrading.collections.buffer;

import group.gnometrading.utils.Resettable;

public interface RingBuffer<T> extends Resettable {

    /**
     * Claims a slot in the ring buffer. Called by the producer.
     * The slot is reserved but not yet visible to consumers until commit() is called.
     *
     * @return index of the claimed slot, or -1 if the buffer is full
     */
    int tryClaim();

    /**
     * Returns the message at the given index. Called by the producer.
     *
     * @param index the index returned by tryClaim()
     * @return the message at the given index
     */
    T indexAt(int index);

    /**
     * Commits the claimed slot, making it visible to consumers. Called by the producer.
     * Must be called after tryClaim() and after the data has been written to the buffer.
     *
     * @param index the index returned by tryClaim()
     */
    void commit(int index);

    /**
     * Reads messages from the ring buffer. Called by the consumer.
     *
     * @param consumer consumer to accept the messages
     */
    default void read(MessageConsumer<T> consumer) {
        read(consumer, Integer.MAX_VALUE);
    }

    /**
     * Reads messages from the ring buffer. Called by the consumer.
     *
     * @param consumer consumer to accept the messages
     * @param limit maximum number of messages to read
     */
    void read(MessageConsumer<T> consumer, int limit);
}
