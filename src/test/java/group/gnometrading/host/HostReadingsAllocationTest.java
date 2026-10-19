package group.gnometrading.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostReadingsAllocationTest {

    // Until the JIT has compiled sample(), the interpreter allocates where compiled code doesn't.
    private static final int WARM_UP = 50_000;
    private static final int MEASURED = 10_000;

    @TempDir
    Path proc;

    @Test
    void samplingAllocatesNothing() throws IOException {
        // The real /proc on Linux; elsewhere, files laid out like it, read through the same channels.
        final boolean linux = Files.exists(Path.of("/proc/stat"));
        if (!linux) {
            Files.writeString(proc.resolve("stat"), "cpu  1000 20 300 8000 50 0 30 0 0 0\n");
            Files.writeString(proc.resolve("meminfo"), "MemTotal: 2010000 kB\nMemAvailable: 812000 kB\n");
            Files.createDirectories(proc.resolve("self"));
            Files.writeString(proc.resolve("self").resolve("status"), "VmRSS:\t463000 kB\n");
        }
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().getId();

        try (HostReadings readings = new HostReadings(linux ? Path.of("/proc") : proc, proc.toFile())) {
            sample(readings, WARM_UP);
            final long before = threads.getThreadAllocatedBytes(threadId);
            sample(readings, MEASURED);
            final long allocated = threads.getThreadAllocatedBytes(threadId) - before;

            assertTrue(readings.memTotalBytes() > 0, "the readings were taken");
            assertEquals(0, allocated, "allocated " + allocated + " bytes over " + MEASURED + " samples");
        }
    }

    private static void sample(final HostReadings readings, final int count) {
        for (int i = 0; i < count; i++) {
            readings.sample();
        }
    }
}
