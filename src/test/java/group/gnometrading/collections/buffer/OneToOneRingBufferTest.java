package group.gnometrading.collections.buffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OneToOneRingBufferTest {

    private static class TestMessage {
        int value;

        public void setValue(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    @Test
    void testConstructor() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        assertNotNull(buffer);
    }

    @Test
    void testConstructorWithInvalidCapacity() {
        // Not a power of 2
        assertThrows(IllegalArgumentException.class, () ->
                new OneToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, 7)
        );

        // Zero capacity
        assertThrows(IllegalArgumentException.class, () ->
                new OneToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, 0)
        );

        // Negative capacity
        assertThrows(IllegalArgumentException.class, () ->
                new OneToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, -1)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024})
    void testConstructorWithValidPowerOfTwoCapacities(int capacity) {
        assertDoesNotThrow(() ->
                new OneToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, capacity)
        );
    }

    @Test
    void testTryClaimSingleSlot() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index = buffer.tryClaim();
        assertEquals(0, index);
    }

    @Test
    void testTryClaimMultipleSlots() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        assertEquals(0, buffer.tryClaim());
        assertEquals(1, buffer.tryClaim());
        assertEquals(2, buffer.tryClaim());
        assertEquals(3, buffer.tryClaim());
    }

    @Test
    void testTryClaimWhenFull() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // Claim all slots
        for (int i = 0; i < 4; i++) {
            int index = buffer.tryClaim();
            assertEquals(i, index);
        }

        // Buffer is full, should return -1
        assertEquals(-1, buffer.tryClaim());
    }

    @Test
    void testCommitSingleSlot() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index = buffer.tryClaim();
        assertDoesNotThrow(() -> buffer.commit(index));
    }

    @Test
    void testCommitInOrder() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index0 = buffer.tryClaim();
        int index1 = buffer.tryClaim();
        int index2 = buffer.tryClaim();

        buffer.commit(index0);
        buffer.commit(index1);
        buffer.commit(index2);
    }

    @Test
    void testCommitOutOfOrder() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index0 = buffer.tryClaim();
        int index1 = buffer.tryClaim();
        int index2 = buffer.tryClaim();

        // Commit out of order: 1, 0, 2
        buffer.commit(index1);

        // After committing index1, nothing should be readable yet (waiting for index0)
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "No messages should be readable until index0 is committed");

        // Now commit index0
        buffer.commit(index0);

        // Now both index0 and index1 should be readable
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(2, count.get(), "Both messages should be readable after index0 is committed");

        // Commit index2
        buffer.commit(index2);

        // Now index2 should also be readable
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1, count.get(), "Third message should be readable");
    }

    @Test
    void testReadEmptyBuffer() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());

        assertEquals(0, count.get());
    }

    @Test
    void testReadSingleMessage() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index = buffer.tryClaim();
        buffer.commit(index);

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());

        assertEquals(1, count.get());
    }

    @Test
    void testReadMultipleMessages() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        for (int i = 0; i < 3; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());

        assertEquals(3, count.get());
    }

    @Test
    void testReadWithLimit() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        for (int i = 0; i < 5; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet(), 3);

        assertEquals(3, count.get());

        // Read remaining messages
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    void testClaimCommitReadCycle() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // First cycle
        for (int i = 0; i < 4; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(4, count.get());

        // Second cycle - buffer should be reusable
        for (int i = 0; i < 4; i++) {
            int index = buffer.tryClaim();
            assertEquals(i, index); // Should wrap around
            buffer.commit(index);
        }

        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(4, count.get());
    }

    @Test
    void testDataIntegrity() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Write data
        for (int i = 0; i < 5; i++) {
            int index = buffer.tryClaim();
            // Access buffer directly through a getter would be needed,
            // but we'll test through the read operation
            buffer.commit(index);
        }

        List<TestMessage> messages = new ArrayList<>();
        buffer.read(messages::add);

        assertEquals(5, messages.size());
    }

    @Test
    void testReset() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // Fill buffer
        for (int i = 0; i < 4; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        // Reset
        buffer.reset();

        // Should be able to claim from beginning
        int index = buffer.tryClaim();
        assertEquals(0, index);
    }

    @Test
    void testResetAfterPartialRead() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Write 5 messages
        for (int i = 0; i < 5; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        // Read 2 messages
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet(), 2);
        assertEquals(2, count.get());

        // Reset
        buffer.reset();

        // Should be able to claim from beginning again
        int index = buffer.tryClaim();
        assertEquals(0, index);

        // Old messages should not be readable
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get());
    }

    @Test
    void testWrapAroundBehavior() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // Fill and empty buffer multiple times
        for (int cycle = 0; cycle < 10; cycle++) {
            for (int i = 0; i < 4; i++) {
                int index = buffer.tryClaim();
                assertEquals(i, index);
                buffer.commit(index);
            }

            AtomicInteger count = new AtomicInteger(0);
            buffer.read(msg -> count.incrementAndGet());
            assertEquals(4, count.get());
        }
    }

    @Test
    void testPartialFillAndRead() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Write 3, read 2, write 2, read 3
        for (int i = 0; i < 3; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet(), 2);
        assertEquals(2, count.get());

        for (int i = 0; i < 2; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(3, count.get());
    }

    @Test
    void testClaimWithoutCommit() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // Claim but don't commit
        buffer.tryClaim();
        buffer.tryClaim();

        // Consumer should not see any messages
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get());
    }

    @Test
    void testCommitMakesDataVisible() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index0 = buffer.tryClaim();
        int index1 = buffer.tryClaim();

        // Commit only first
        buffer.commit(index0);

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1, count.get());

        // Commit second
        buffer.commit(index1);

        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1, count.get());
    }

    @Test
    void testConcurrentProducerConsumer() throws InterruptedException {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                16
        );

        final int messagesToSend = 1000;
        CountDownLatch producerLatch = new CountDownLatch(1);
        CountDownLatch consumerLatch = new CountDownLatch(1);
        AtomicInteger messagesReceived = new AtomicInteger(0);

        // Producer thread
        Thread producer = new Thread(() -> {
            try {
                producerLatch.await();
                for (int i = 0; i < messagesToSend; i++) {
                    int index;
                    while ((index = buffer.tryClaim()) == -1) {
                        Thread.yield();
                    }
                    buffer.commit(index);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer thread
        Thread consumer = new Thread(() -> {
            try {
                consumerLatch.await();
                while (messagesReceived.get() < messagesToSend) {
                    buffer.read(msg -> messagesReceived.incrementAndGet());
                    if (messagesReceived.get() < messagesToSend) {
                        Thread.yield();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        // Start both threads
        producerLatch.countDown();
        consumerLatch.countDown();

        // Wait for completion
        producer.join(5000);
        consumer.join(5000);

        assertEquals(messagesToSend, messagesReceived.get());
    }

    @Test
    void testHighThroughputConcurrency() throws InterruptedException {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                256
        );

        final int messagesToSend = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger messagesReceived = new AtomicInteger(0);
        List<Integer> receivedValues = new ArrayList<>();

        // Producer thread
        Thread producer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < messagesToSend; i++) {
                    int index;
                    while ((index = buffer.tryClaim()) == -1) {
                        // Spin until space available
                    }
                    // Note: In real usage, you'd write data here
                    buffer.commit(index);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Consumer thread
        Thread consumer = new Thread(() -> {
            try {
                startLatch.await();
                while (messagesReceived.get() < messagesToSend) {
                    buffer.read(msg -> {
                        messagesReceived.incrementAndGet();
                    }, 100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        startLatch.countDown();

        producer.join(10000);
        consumer.join(10000);

        assertFalse(producer.isAlive());
        assertFalse(consumer.isAlive());
        assertEquals(messagesToSend, messagesReceived.get());
    }

    @Test
    void testSingleCapacityBuffer() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                1
        );

        int index = buffer.tryClaim();
        assertEquals(0, index);
        assertEquals(-1, buffer.tryClaim()); // Full

        buffer.commit(index);

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1, count.get());

        // Should be able to use again
        index = buffer.tryClaim();
        assertEquals(0, index);
    }

    @Test
    void testLargeCapacityBuffer() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                1024
        );

        for (int i = 0; i < 1024; i++) {
            int index = buffer.tryClaim();
            assertEquals(i, index);
            buffer.commit(index);
        }

        assertEquals(-1, buffer.tryClaim()); // Full

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1024, count.get());
    }

    @Test
    void testOutOfOrderCommitWithMultipleGaps() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Claim 5 slots
        int index0 = buffer.tryClaim();
        int index1 = buffer.tryClaim();
        int index2 = buffer.tryClaim();
        int index3 = buffer.tryClaim();
        int index4 = buffer.tryClaim();

        // Commit in order: 2, 4, 1, 0, 3
        buffer.commit(index2);

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "No messages readable yet");

        buffer.commit(index4);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "Still no messages readable");

        buffer.commit(index1);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "Still waiting for index0");

        buffer.commit(index0);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(3, count.get(), "First 3 messages should be readable");

        buffer.commit(index3);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(2, count.get(), "Last 2 messages should be readable");
    }

    @Test
    void testOutOfOrderCommitReverseOrder() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Claim 4 slots
        int index0 = buffer.tryClaim();
        int index1 = buffer.tryClaim();
        int index2 = buffer.tryClaim();
        int index3 = buffer.tryClaim();

        // Commit in reverse order: 3, 2, 1, 0
        buffer.commit(index3);
        buffer.commit(index2);
        buffer.commit(index1);

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "No messages readable until index0 is committed");

        buffer.commit(index0);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(4, count.get(), "All 4 messages should be readable");
    }

    @Test
    void testOutOfOrderCommitWithWrapAround() {
        OneToOneRingBuffer<TestMessage> buffer = new OneToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        // Fill and drain the buffer once to get wrap-around
        for (int i = 0; i < 4; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(4, count.get());

        // Now claim and commit out of order with wrap-around
        int index0 = buffer.tryClaim(); // Should be 0
        int index1 = buffer.tryClaim(); // Should be 1
        int index2 = buffer.tryClaim(); // Should be 2

        assertEquals(0, index0);
        assertEquals(1, index1);
        assertEquals(2, index2);

        // Commit out of order: first commit index2 (nothing readable)
        buffer.commit(index2);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get(), "Waiting for index0");

        // Then commit index0 (index0 becomes readable, but not index1 or index2)
        buffer.commit(index0);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(1, count.get(), "Only index0 should be readable, waiting for index1");

        // Finally commit index1 (all 3 become readable)
        buffer.commit(index1);
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(2, count.get(), "index1 and index2 should now be readable");
    }
}
