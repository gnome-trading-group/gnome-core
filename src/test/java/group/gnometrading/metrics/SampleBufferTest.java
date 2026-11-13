package group.gnometrading.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SampleBufferTest {

    private static void sample(SampleBuffer buffer, String kind, long tsMs, long value) {
        buffer.begin(kind, tsMs).writeObjectEntry("v", value);
        buffer.commit();
    }

    private static String sent(SampleBuffer buffer, int max) {
        ByteBuffer out = ByteBuffer.allocate(4096);
        int written = buffer.writeTo(out, max);
        return written + ":" + new String(out.array(), 0, out.position(), StandardCharsets.UTF_8);
    }

    @Test
    void samplesWaitInOrderUntilAccepted() {
        SampleBuffer buffer = new SampleBuffer(1024, 16, 256);
        sample(buffer, "runtime", 1, 10);
        sample(buffer, "latency.oms", 2, 20);

        assertEquals(
                "2:{\"kind\":\"runtime\",\"tsMs\":1,\"data\":{\"v\":10}},"
                        + "{\"kind\":\"latency.oms\",\"tsMs\":2,\"data\":{\"v\":20}}",
                sent(buffer, 10));
        assertEquals(2, buffer.pending(), "a post not yet accepted keeps them");

        buffer.acknowledge(1);
        assertEquals("1:{\"kind\":\"latency.oms\",\"tsMs\":2,\"data\":{\"v\":20}}", sent(buffer, 10));
        buffer.acknowledge(1);
        assertEquals(0, buffer.pending());
        assertEquals("0:", sent(buffer, 10));
    }

    @Test
    void aPostTakesNoMoreThanAskedOrThanFits() {
        SampleBuffer buffer = new SampleBuffer(1024, 16, 256);
        for (int i = 0; i < 5; i++) {
            sample(buffer, "runtime", i, i);
        }
        assertEquals('3', sent(buffer, 3).charAt(0));

        ByteBuffer small = ByteBuffer.allocate(buffer.bytes(2) + 1);
        assertEquals(2, buffer.writeTo(small, 10));
    }

    @Test
    void theOldestAreDroppedForRoomAndCounted() {
        SampleBuffer buffer = new SampleBuffer(200, 16, 128);
        for (int i = 0; i < 10; i++) {
            sample(buffer, "runtime", i, i);
        }
        assertTrue(buffer.dropped() > 0);
        assertEquals(10, buffer.pending() + buffer.dropped());
        String sent = sent(buffer, 100);
        assertTrue(sent.endsWith("{\"kind\":\"runtime\",\"tsMs\":9,\"data\":{\"v\":9}}"), sent);
    }

    @Test
    void theOldestAreDroppedWhenTheSlotsRunOut() {
        SampleBuffer buffer = new SampleBuffer(4096, 3, 128);
        for (int i = 0; i < 5; i++) {
            sample(buffer, "runtime", i, i);
        }
        assertEquals(3, buffer.pending());
        assertEquals(2, buffer.dropped());
        assertTrue(sent(buffer, 10).startsWith("3:{\"kind\":\"runtime\",\"tsMs\":2"));
    }

    @Test
    void keepsWorkingAcrossManyRoundsOfDropsAndAcks() {
        SampleBuffer buffer = new SampleBuffer(300, 8, 128);
        long next = 0;
        long acknowledged = 0;
        for (int round = 0; round < 1_000; round++) {
            for (int i = 0; i < 1 + round % 4; i++) {
                sample(buffer, "runtime", next, next);
                next++;
            }
            String sent = sent(buffer, 2);
            int written = sent.charAt(0) - '0';
            if (round % 3 != 0) {
                buffer.acknowledge(written);
                acknowledged += written;
            }
        }
        assertEquals(next, acknowledged + buffer.dropped() + buffer.pending());
        String last = sent(buffer, 100);
        assertTrue(last.endsWith("\"tsMs\":" + (next - 1) + ",\"data\":{\"v\":" + (next - 1) + "}}"), last);
    }

    @Test
    void aSampleNoPostCanCarryIsDroppedRatherThanBlockingTheRest() {
        SampleBuffer buffer = new SampleBuffer(4096, 16, 1024);
        buffer.begin("big", 1).writeObjectEntry("filler", "x".repeat(500));
        buffer.commit();
        sample(buffer, "runtime", 2, 2);
        ByteBuffer post = ByteBuffer.allocate(200);
        assertEquals(0, buffer.writeTo(post, 10), "the big one blocks the head");

        assertEquals(1, buffer.dropLargerThan(post.capacity()));
        assertEquals(1, buffer.dropped());
        assertEquals(1, buffer.writeTo(post, 10));
    }

    @Test
    void aSampleTooLargeForItsLimitIsAbandoned() {
        SampleBuffer buffer = new SampleBuffer(1024, 16, 64);
        assertThrows(BufferOverflowException.class, () -> buffer.begin("runtime", 1)
                .writeObjectEntry("long", "x".repeat(100)));
        buffer.abandon();

        sample(buffer, "runtime", 2, 2);
        assertEquals(1, buffer.pending());
    }

    @Test
    void writingSendingAndAcknowledgingAllocateNothing() {
        SampleBuffer buffer = new SampleBuffer(8192, 64, 256);
        ByteBuffer out = ByteBuffer.allocate(8192);
        for (int i = 0; i < 50_000; i++) {
            cycle(buffer, out, i);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 50_000; i++) {
            cycle(buffer, out, i);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 2_048, "allocated " + allocated + " bytes");
    }

    private static void cycle(SampleBuffer buffer, ByteBuffer out, long i) {
        buffer.begin("runtime", i).writeObjectEntry("v", i).writeComma().writeObjectEntry("w", i * 3);
        buffer.commit();
        out.clear();
        buffer.acknowledge(buffer.writeTo(out, 8));
    }
}
