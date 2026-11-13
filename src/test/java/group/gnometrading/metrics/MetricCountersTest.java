package group.gnometrading.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MetricCountersTest {

    @Test
    void countsGaugesAndPeaksAreReadByName() {
        MetricCounters counters = new MetricCounters(3);
        int orders = counters.counter("orders");
        int position = counters.counter("position");
        int lag = counters.counter("lagPeak");

        counters.increment(orders);
        counters.add(orders, 4);
        counters.set(position, -7);
        counters.max(lag, 30);
        counters.max(lag, 10);

        assertEquals(5, counters.get(orders));
        assertEquals(-7, counters.get(position));
        assertEquals(30, counters.getAndResetMax(lag));
        assertEquals(0, counters.get(lag));
        assertEquals("position", counters.name(position));
        assertEquals(3, counters.count());
    }

    @Test
    void countersPastCapacityAreRefused() {
        MetricCounters counters = new MetricCounters(1);
        counters.counter("a");
        assertThrows(IllegalStateException.class, () -> counters.counter("b"));
    }

    @Test
    void aReaderOnAnotherThreadNeverSeesACountGoBackwards() throws InterruptedException {
        MetricCounters counters = new MetricCounters(1);
        int counter = counters.counter("n");
        AtomicBoolean done = new AtomicBoolean();
        Thread owner = new Thread(() -> {
            for (long i = 0; i < 5_000_000; i++) {
                counters.increment(counter);
            }
            done.set(true);
        });
        owner.start();
        long last = 0;
        while (!done.get()) {
            long now = counters.get(counter);
            assertTrue(now >= last, now + " after " + last);
            last = now;
        }
        owner.join();
        assertEquals(5_000_000, counters.get(counter));
    }

    @Test
    void writingAllocatesNothing() {
        MetricCounters counters = new MetricCounters(2);
        int a = counters.counter("a");
        int b = counters.counter("b");
        for (int i = 0; i < 100_000; i++) {
            counters.increment(a);
            counters.max(b, i);
            counters.get(a);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100_000; i++) {
            counters.increment(a);
            counters.max(b, i);
            counters.get(a);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 1_024, "allocated " + allocated + " bytes");
    }
}
