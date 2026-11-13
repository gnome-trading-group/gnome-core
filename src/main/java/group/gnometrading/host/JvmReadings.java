package group.gnometrading.host;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * This JVM's readings: each garbage collector's collections and time, heap, direct and mapped buffers, threads, and a
 * given thread's CPU time. Every reading is a cumulative count or a current value from the platform MXBeans, all of
 * which answer with a primitive: nothing allocates after construction. Rates are the reader's (a difference between
 * two reads).
 */
public final class JvmReadings {

    public static final long UNAVAILABLE = -1;
    private static final String DIRECT = "direct";
    private static final String MAPPED = "mapped";

    private final GarbageCollectorMXBean[] collectors;
    private final String[] collectorNames;
    private final BufferPoolMXBean direct;
    private final BufferPoolMXBean mapped;
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final Runtime runtime = Runtime.getRuntime();
    private final boolean threadCpu;

    public JvmReadings() {
        final List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        this.collectors = gcs.toArray(new GarbageCollectorMXBean[0]);
        this.collectorNames = new String[collectors.length];
        for (int i = 0; i < collectors.length; i++) {
            collectorNames[i] = collectors[i].getName();
        }
        BufferPoolMXBean directPool = null;
        BufferPoolMXBean mappedPool = null;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (DIRECT.equals(pool.getName())) {
                directPool = pool;
            } else if (MAPPED.equals(pool.getName())) {
                mappedPool = pool;
            }
        }
        this.direct = directPool;
        this.mapped = mappedPool;
        boolean cpu = threads.isThreadCpuTimeSupported();
        if (cpu && !threads.isThreadCpuTimeEnabled()) {
            threads.setThreadCpuTimeEnabled(true);
        }
        this.threadCpu = cpu;
    }

    /** How many collectors this JVM runs (ZGC has two: cycles and pauses; G1 young and old). */
    public int collectors() {
        return collectors.length;
    }

    public String collectorName(final int collector) {
        return collectorNames[collector];
    }

    /** Collections by this collector since the JVM started. */
    public long collections(final int collector) {
        return collectors[collector].getCollectionCount();
    }

    /** Milliseconds this collector has spent since the JVM started (for a concurrent one, cycle time, not pauses). */
    public long collectionMs(final int collector) {
        return collectors[collector].getCollectionTime();
    }

    public long heapUsedBytes() {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public long heapCommittedBytes() {
        return runtime.totalMemory();
    }

    public long heapMaxBytes() {
        return runtime.maxMemory();
    }

    /** Off-heap memory held by direct buffers (rings, journal and Aeron buffers among them). */
    public long directBytes() {
        return direct == null ? UNAVAILABLE : direct.getMemoryUsed();
    }

    /** Memory-mapped files in use (journal segments, the ownership log). */
    public long mappedBytes() {
        return mapped == null ? UNAVAILABLE : mapped.getMemoryUsed();
    }

    public int threadCount() {
        return threads.getThreadCount();
    }

    /** CPU time, in nanoseconds, the thread with this id has used; UNAVAILABLE if unsupported or it has ended. */
    public long threadCpuNanos(final long threadId) {
        if (!threadCpu) {
            return UNAVAILABLE;
        }
        final long nanos = threads.getThreadCpuTime(threadId);
        return nanos < 0 ? UNAVAILABLE : nanos;
    }
}
