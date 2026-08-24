package group.gnometrading.collections;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FixedCapacityQueueTest {

    private static class Slot {
        int value;
    }

    private FixedCapacityQueue<Slot> queue(int capacity) {
        return new FixedCapacityQueue<>(Slot[]::new, Slot::new, capacity);
    }

    // ========== construction ==========

    @Test
    void constructor_invalidCapacity_notPowerOfTwo_throws() {
        assertThrows(IllegalArgumentException.class, () -> queue(3));
        assertThrows(IllegalArgumentException.class, () -> queue(7));
    }

    @Test
    void constructor_invalidCapacity_zero_throws() {
        assertThrows(IllegalArgumentException.class, () -> queue(0));
    }

    @Test
    void constructor_invalidCapacity_negative_throws() {
        assertThrows(IllegalArgumentException.class, () -> queue(-1));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256})
    void constructor_validPowerOfTwo_doesNotThrow(int capacity) {
        assertDoesNotThrow(() -> queue(capacity));
    }

    // ========== isEmpty / size ==========

    @Test
    void isEmpty_newQueue_true() {
        assertTrue(queue(4).isEmpty());
    }

    @Test
    void isEmpty_afterOffer_false() {
        var q = queue(4);
        q.offer();
        assertFalse(q.isEmpty());
    }

    @Test
    void size_newQueue_zero() {
        assertEquals(0, queue(4).size());
    }

    @Test
    void size_afterOffers_correct() {
        var q = queue(8);
        q.offer();
        q.offer();
        q.offer();
        assertEquals(3, q.size());
    }

    @Test
    void size_afterOfferAndPoll_correct() {
        var q = queue(4);
        q.offer();
        q.offer();
        q.poll();
        assertEquals(1, q.size());
    }

    // ========== offer ==========

    @Test
    void offer_returnsPreallocatedSlot() {
        var q = queue(4);
        Slot s = q.offer();
        assertNotNull(s);
    }

    @Test
    void offer_sameSlotReturnedOnPeek() {
        var q = queue(4);
        Slot offered = q.offer();
        offered.value = 42;
        Slot peeked = q.peek();
        assertSame(offered, peeked);
        assertEquals(42, peeked.value);
    }

    @Test
    void offer_returnsDifferentSlotsInSequence() {
        var q = queue(4);
        Slot s0 = q.offer();
        Slot s1 = q.offer();
        assertNotSame(s0, s1);
    }

    @Test
    void offer_whenFull_throws() {
        var q = queue(4);
        q.offer();
        q.offer();
        q.offer();
        q.offer();
        assertThrows(IllegalStateException.class, q::offer);
    }

    // ========== peek ==========

    @Test
    void peek_emptyQueue_returnsNull() {
        assertNull(queue(4).peek());
    }

    @Test
    void peek_doesNotAdvanceHead() {
        var q = queue(4);
        Slot s = q.offer();
        assertSame(s, q.peek());
        assertSame(s, q.peek()); // still the same element
        assertEquals(1, q.size());
    }

    // ========== poll ==========

    @Test
    void poll_advancesHead() {
        var q = queue(4);
        Slot s0 = q.offer();
        Slot s1 = q.offer();
        assertSame(s0, q.peek());
        q.poll();
        assertSame(s1, q.peek());
    }

    @Test
    void poll_afterDraining_isEmpty() {
        var q = queue(4);
        q.offer();
        q.offer();
        q.poll();
        q.poll();
        assertTrue(q.isEmpty());
        assertNull(q.peek());
    }

    // ========== FIFO ordering ==========

    @Test
    void fifoOrder_maintainedAcrossOfferAndPoll() {
        var q = queue(8);
        for (int i = 0; i < 5; i++) {
            q.offer().value = i;
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(i, q.peek().value);
            q.poll();
        }
        assertTrue(q.isEmpty());
    }

    // ========== wrap-around ==========

    @Test
    void wrapAround_acrossMultipleFillDrainCycles() {
        var q = queue(4);
        for (int cycle = 0; cycle < 10; cycle++) {
            for (int i = 0; i < 4; i++) {
                q.offer().value = i;
            }
            assertThrows(IllegalStateException.class, q::offer);
            for (int i = 0; i < 4; i++) {
                assertEquals(i, q.peek().value);
                q.poll();
            }
            assertTrue(q.isEmpty());
        }
    }

    @Test
    void wrapAround_slotReuseCorrect() {
        var q = queue(4);
        // Capture slot identities from first fill
        Slot[] firstSlots = new Slot[4];
        for (int i = 0; i < 4; i++) {
            firstSlots[i] = q.offer();
        }
        for (int i = 0; i < 4; i++) {
            q.poll();
        }
        // Second fill should reuse the same slot objects
        for (int i = 0; i < 4; i++) {
            assertSame(firstSlots[i], q.offer());
        }
    }

    // ========== reset ==========

    @Test
    void reset_clearsState() {
        var q = queue(4);
        q.offer();
        q.offer();
        q.reset();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertNull(q.peek());
    }

    @Test
    void reset_allowsReuse() {
        var q = queue(4);
        for (int i = 0; i < 4; i++) {
            q.offer();
        }
        q.reset();
        // Should be able to offer again from slot 0
        Slot s = q.offer();
        assertNotNull(s);
        assertEquals(1, q.size());
    }
}
