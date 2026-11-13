package group.gnometrading.host;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class JvmReadingsTest {

    private final JvmReadings jvm = new JvmReadings();

    @Test
    void collectorsAreNamedAndCountUp() {
        assertTrue(jvm.collectors() > 0);
        long before = jvm.collections(0);
        for (int i = 0; i < 2_000; i++) {
            byte[][] garbage = new byte[64][];
            for (int j = 0; j < garbage.length; j++) {
                garbage[j] = new byte[16_384];
            }
        }
        System.gc();
        long after = 0;
        for (int c = 0; c < jvm.collectors(); c++) {
            assertTrue(!jvm.collectorName(c).isEmpty());
            after += jvm.collections(c);
        }
        assertTrue(after > before, "no collection seen");
    }

    @Test
    void heapAndDirectMemoryAreRead() {
        ByteBuffer held = ByteBuffer.allocateDirect(1 << 20);
        assertTrue(jvm.directBytes() >= held.capacity());
        assertTrue(jvm.heapUsedBytes() > 0);
        assertTrue(jvm.heapCommittedBytes() >= jvm.heapUsedBytes());
        assertTrue(jvm.heapMaxBytes() >= jvm.heapCommittedBytes());
        assertTrue(jvm.threadCount() > 0);
    }

    @Test
    void aThreadsCpuTimeGrowsWhileItWorks() {
        long id = Thread.currentThread().getId();
        long before = jvm.threadCpuNanos(id);
        long spin = 0;
        for (int i = 0; i < 50_000_000; i++) {
            spin += i ^ spin;
        }
        assertTrue(spin != 1);
        assertTrue(jvm.threadCpuNanos(id) > before);
    }

    @Test
    void readingAllocatesNothing() {
        long id = Thread.currentThread().getId();
        // Long enough for the MXBean calls to compile: interpreted, a few of them allocate.
        for (int i = 0; i < 200_000; i++) {
            readAll(id);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 20_000; i++) {
            readAll(id);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 2_048, "allocated " + allocated + " bytes");
    }

    private long readAll(long threadId) {
        long sum = jvm.heapUsedBytes()
                + jvm.directBytes()
                + jvm.mappedBytes()
                + jvm.threadCount()
                + jvm.threadCpuNanos(threadId);
        for (int c = 0; c < jvm.collectors(); c++) {
            sum += jvm.collections(c) + jvm.collectionMs(c);
        }
        return sum;
    }
}
