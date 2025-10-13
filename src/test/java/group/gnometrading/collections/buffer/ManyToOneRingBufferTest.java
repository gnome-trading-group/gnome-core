package group.gnometrading.collections.buffer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ManyToOneRingBufferTest {

    private static class TestMessage {
        long value;
        long threadId;

        public void setValue(long value) {
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        public void setThreadId(long threadId) {
            this.threadId = threadId;
        }

        public long getThreadId() {
            return threadId;
        }
    }

    // ========== Basic Constructor and Validation Tests ==========

    @Test
    void testConstructor() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
                new ManyToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, 7)
        );

        // Zero capacity
        assertThrows(IllegalArgumentException.class, () ->
                new ManyToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, 0)
        );

        // Negative capacity
        assertThrows(IllegalArgumentException.class, () ->
                new ManyToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, -1)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024})
    void testConstructorWithValidCapacities(int capacity) {
        assertDoesNotThrow(() ->
                new ManyToOneRingBuffer<>(TestMessage[]::new, TestMessage::new, capacity)
        );
    }

    // ========== Single Producer Tests (Baseline) ==========

    @Test
    void testSingleProducerTryClaim() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index = buffer.tryClaim();
        assertTrue(index >= 0 && index < 4);
    }

    @Test
    void testSingleProducerTryClaimWhenFull() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testSingleProducerCommitSingleSlot() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int index = buffer.tryClaim();
        assertDoesNotThrow(() -> buffer.commit(index));
    }

    @Test
    void testSingleProducerCommitInOrder() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testSingleProducerReadEmptyBuffer() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());

        assertEquals(0, count.get());
    }

    @Test
    void testSingleProducerReadSingleMessage() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testSingleProducerReadMultipleMessages() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testSingleProducerReadWithLimit() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testSingleProducerClaimCommitReadCycle() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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
    void testReset() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        // Write some messages
        for (int i = 0; i < 3; i++) {
            int index = buffer.tryClaim();
            buffer.commit(index);
        }

        buffer.reset();

        // Should be able to claim from beginning again
        int index = buffer.tryClaim();
        assertEquals(0, index);

        // Old messages should not be readable
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get());
    }

    @Test
    void testWrapAroundBehavior() {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
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

    // ========== Multi-Producer Basic Tests ==========

    @Test
    void testTwoProducersSimultaneousClaim() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        Set<Integer> claimedIndices = Collections.synchronizedSet(new HashSet<>());

        Runnable producer = () -> {
            try {
                startLatch.await();
                for (int i = 0; i < 4; i++) {
                    int index = buffer.tryClaim();
                    assertTrue(index >= 0);
                    assertTrue(claimedIndices.add(index), "Duplicate index claimed: " + index);
                    buffer.commit(index);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        };

        Thread t1 = new Thread(producer);
        Thread t2 = new Thread(producer);

        t1.start();
        t2.start();

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Should have claimed 8 unique indices
        assertEquals(8, claimedIndices.size());

        // Should be able to read 8 messages
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(8, count.get());
    }

    @Test
    void testMultipleProducersNoDuplicateClaims() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                64
        );

        int numProducers = 8;
        int claimsPerProducer = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        Set<Integer> claimedIndices = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger duplicates = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < claimsPerProducer; j++) {
                        int index = buffer.tryClaim();
                        if (!claimedIndices.add(index)) {
                            duplicates.incrementAndGet();
                        }
                        buffer.commit(index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        assertEquals(0, duplicates.get(), "Found duplicate claims");
        assertEquals(numProducers * claimsPerProducer, claimedIndices.size());
    }

    @Test
    void testMultipleProducersWithBarrier() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                32
        );

        int numProducers = 4;
        CyclicBarrier barrier = new CyclicBarrier(numProducers);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        AtomicInteger totalMessages = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    barrier.await(); // All start at the same time
                    for (int j = 0; j < 8; j++) {
                        int index = buffer.tryClaim();
                        assertTrue(index >= 0);
                        buffer.commit(index);
                        totalMessages.incrementAndGet();
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertEquals(numProducers * 8, totalMessages.get());

        AtomicInteger messagesRead = new AtomicInteger(0);
        buffer.read(msg -> messagesRead.incrementAndGet());
        assertEquals(numProducers * 8, messagesRead.get());
    }

    // ========== Out-of-Order Commit Tests ==========

    @Test
    void testTwoProducersOutOfOrderCommit() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch claimLatch = new CountDownLatch(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        List<Integer> indices = Collections.synchronizedList(new ArrayList<>());

        // Producer 1: Claims first, commits second
        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                int index = buffer.tryClaim();
                indices.add(index);
                claimLatch.countDown();
                claimLatch.await(); // Wait for both to claim
                Thread.sleep(100); // Let producer 2 commit first
                buffer.commit(index);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Producer 2: Claims second, commits first
        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                int index = buffer.tryClaim();
                indices.add(index);
                claimLatch.countDown();
                claimLatch.await(); // Wait for both to claim
                Thread.sleep(10); // Commit before producer 1
                buffer.commit(index);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        t1.start();
        t2.start();
        startLatch.countDown();

        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Both messages should be readable after both commits
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(2, count.get());
    }

    @Test
    void testMultipleProducersOutOfOrderCommit() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                16
        );

        int numProducers = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch claimLatch = new CountDownLatch(numProducers);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        List<Integer> claimedIndices = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numProducers; i++) {
            final int producerId = i;
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    int index = buffer.tryClaim();
                    claimedIndices.add(index);
                    claimLatch.countDown();
                    claimLatch.await(); // Wait for all to claim

                    // Commit in reverse order
                    Thread.sleep((numProducers - producerId) * 10);
                    buffer.commit(index);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // All messages should be readable
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(numProducers, count.get());
    }

    @RepeatedTest(10)
    void testRandomOutOfOrderCommits() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                32
        );

        int numProducers = 8;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch claimLatch = new CountDownLatch(numProducers);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    int index = buffer.tryClaim();
                    claimLatch.countDown();
                    claimLatch.await();

                    // Random delay before commit
                    Thread.sleep((long) (Math.random() * 50));
                    buffer.commit(index);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(numProducers, count.get());
    }

    // ========== High Concurrency Tests ==========

    @RepeatedTest(5)
    void testHighConcurrencyWithConsumer() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                128
        );

        int numProducers = 4;
        int messagesPerProducer = 5000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);

        // Start producers
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index;
                        while ((index = buffer.tryClaim()) == -1) {
                            Thread.yield();
                        }
                        buffer.commit(index);
                        totalProduced.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || totalConsumed.get() < numProducers * messagesPerProducer) {
                buffer.read(msg -> totalConsumed.incrementAndGet(), 10);
                if (totalConsumed.get() < numProducers * messagesPerProducer) {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(30, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(5000);

        assertEquals(numProducers * messagesPerProducer, totalProduced.get());
        assertEquals(numProducers * messagesPerProducer, totalConsumed.get());
    }

    @Test
    void testStressTestWithDataIntegrity() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                64
        );

        int numProducers = 4;
        int messagesPerProducer = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);
        List<Long> receivedValues = Collections.synchronizedList(new ArrayList<>());

        // Start producers - each writes unique values
        for (int i = 0; i < numProducers; i++) {
            final long producerId = i;
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index;
                        while ((index = buffer.tryClaim()) == -1) {
                            Thread.yield();
                        }
                        // Write unique value: producerId * 1000000 + j
                        TestMessage msg = buffer.indexAt(index);
                        msg.setValue(producerId * 1000000 + j);
                        msg.setThreadId(producerId);
                        buffer.commit(index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || receivedValues.size() < numProducers * messagesPerProducer) {
                buffer.read(msg -> receivedValues.add(msg.getValue()), 10);
                if (receivedValues.size() < numProducers * messagesPerProducer) {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(30, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(5000);

        // Verify all messages received
        assertEquals(numProducers * messagesPerProducer, receivedValues.size());

        // Verify no duplicates
        Set<Long> uniqueValues = new HashSet<>(receivedValues);
        assertEquals(receivedValues.size(), uniqueValues.size(), "Found duplicate messages");
    }

    // ========== Edge Cases and Buffer Full Scenarios ==========

    @Test
    void testBufferFullWithMultipleProducers() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        int numProducers = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        AtomicInteger failedClaims = new AtomicInteger(0);
        AtomicInteger successfulClaims = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 10; j++) {
                        int index = buffer.tryClaim();
                        if (index == -1) {
                            failedClaims.incrementAndGet();
                        } else {
                            successfulClaims.incrementAndGet();
                            buffer.commit(index);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Should have exactly 8 successful claims (buffer capacity)
        assertEquals(8, successfulClaims.get());
        assertEquals(numProducers * 10 - 8, failedClaims.get());
    }

    @Test
    void testProducerConsumerWithSmallBuffer() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                4
        );

        int numProducers = 2;
        int messagesPerProducer = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);

        // Start producers
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index;
                        while ((index = buffer.tryClaim()) == -1) {
                            Thread.yield();
                        }
                        buffer.commit(index);
                        totalProduced.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || totalConsumed.get() < numProducers * messagesPerProducer) {
                buffer.read(msg -> totalConsumed.incrementAndGet(), 2);
                if (totalConsumed.get() < numProducers * messagesPerProducer) {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(10, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(5000);

        assertEquals(numProducers * messagesPerProducer, totalProduced.get());
        assertEquals(numProducers * messagesPerProducer, totalConsumed.get());
    }

    @Test
    void testSingleCapacityBuffer() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                1
        );

        int numProducers = 3;
        int messagesPerProducer = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);

        // Start producers
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index;
                        while ((index = buffer.tryClaim()) == -1) {
                            Thread.yield();
                        }
                        buffer.commit(index);
                        totalProduced.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || totalConsumed.get() < numProducers * messagesPerProducer) {
                buffer.read(msg -> totalConsumed.incrementAndGet(), 1);
                if (totalConsumed.get() < numProducers * messagesPerProducer) {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(10, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(5000);

        assertEquals(numProducers * messagesPerProducer, totalProduced.get());
        assertEquals(numProducers * messagesPerProducer, totalConsumed.get());
    }

    @Test
    void testLargeCapacityBuffer() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                1024
        );

        int numProducers = 16;
        int messagesPerProducer = 64;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        AtomicInteger totalMessages = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index = buffer.tryClaim();
                        assertTrue(index >= 0);
                        buffer.commit(index);
                        totalMessages.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        assertEquals(numProducers * messagesPerProducer, totalMessages.get());

        AtomicInteger messagesRead = new AtomicInteger(0);
        buffer.read(msg -> messagesRead.incrementAndGet());
        assertEquals(numProducers * messagesPerProducer, messagesRead.get());
    }

    // ========== Wrap-Around and Multiple Cycles Tests ==========

    @Test
    void testMultipleProducersWithWrapAround() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                8
        );

        int numProducers = 2;
        int cycles = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);
        AtomicInteger totalProduced = new AtomicInteger(0);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int cycle = 0; cycle < cycles; cycle++) {
                        for (int j = 0; j < 4; j++) {
                            int index;
                            while ((index = buffer.tryClaim()) == -1) {
                                Thread.yield();
                            }
                            buffer.commit(index);
                            totalProduced.incrementAndGet();
                        }
                        // Read to make space
                        buffer.read(msg -> {}, 4);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

        assertEquals(numProducers * cycles * 4, totalProduced.get());
    }

    @Test
    void testResetWithMultipleProducers() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                16
        );

        int numProducers = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);

        // First round of production
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 4; j++) {
                        int index = buffer.tryClaim();
                        buffer.commit(index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Reset buffer
        buffer.reset();

        // Verify buffer is empty
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(0, count.get());

        // Verify we can claim from the beginning
        int index = buffer.tryClaim();
        assertEquals(0, index);
    }

    // ========== Extreme Stress Tests ==========

    @RepeatedTest(3)
    void testExtremeStressTest() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                512
        );

        int numProducers = 16;
        int messagesPerProducer = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicLong totalProduced = new AtomicLong(0);
        AtomicLong totalConsumed = new AtomicLong(0);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);

        // Start producers
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < messagesPerProducer; j++) {
                        int index;
                        while ((index = buffer.tryClaim()) == -1) {
                            // Spin
                        }
                        buffer.commit(index);
                        totalProduced.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || totalConsumed.get() < numProducers * messagesPerProducer) {
                buffer.read(msg -> totalConsumed.incrementAndGet(), 100);
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(60, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(10000);

        assertEquals(numProducers * messagesPerProducer, totalProduced.get());
        assertEquals(numProducers * messagesPerProducer, totalConsumed.get());
    }

    @Test
    void testBurstProduction() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                64
        );

        int numProducers = 8;
        int bursts = 5;
        int messagesPerBurst = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch producersDone = new CountDownLatch(numProducers);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        AtomicBoolean consumerRunning = new AtomicBoolean(true);

        // Start producers with burst pattern
        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int burst = 0; burst < bursts; burst++) {
                        for (int j = 0; j < messagesPerBurst; j++) {
                            int index;
                            while ((index = buffer.tryClaim()) == -1) {
                                Thread.yield();
                            }
                            buffer.commit(index);
                            totalProduced.incrementAndGet();
                        }
                        Thread.sleep(10); // Pause between bursts
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    producersDone.countDown();
                }
            });
            t.start();
        }

        // Start consumer
        Thread consumer = new Thread(() -> {
            while (consumerRunning.get() || totalConsumed.get() < numProducers * bursts * messagesPerBurst) {
                buffer.read(msg -> totalConsumed.incrementAndGet(), 20);
                if (totalConsumed.get() < numProducers * bursts * messagesPerBurst) {
                    Thread.yield();
                }
            }
        });
        consumer.start();

        startLatch.countDown();
        assertTrue(producersDone.await(30, TimeUnit.SECONDS));
        consumerRunning.set(false);
        consumer.join(5000);

        assertEquals(numProducers * bursts * messagesPerBurst, totalProduced.get());
        assertEquals(numProducers * bursts * messagesPerBurst, totalConsumed.get());
    }

    // ========== Partial Read Tests ==========

    @Test
    void testPartialReadWithMultipleProducers() throws InterruptedException {
        ManyToOneRingBuffer<TestMessage> buffer = new ManyToOneRingBuffer<>(
                TestMessage[]::new,
                TestMessage::new,
                16
        );

        int numProducers = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numProducers);

        for (int i = 0; i < numProducers; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 4; j++) {
                        int index = buffer.tryClaim();
                        buffer.commit(index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));

        // Read partially
        AtomicInteger count = new AtomicInteger(0);
        buffer.read(msg -> count.incrementAndGet(), 5);
        assertEquals(5, count.get());

        // Read remaining
        count.set(0);
        buffer.read(msg -> count.incrementAndGet());
        assertEquals(3, count.get());
    }
}

