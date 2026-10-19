package group.gnometrading.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostReadingsTest {

    // As Linux writes them, trimmed; the intr line is what makes the real /proc/stat long.
    private static final String STAT_HEAD = "cpu  %d 20 %d %d 50 0 30 %d 0 0\n"
            + "cpu0 100 10 50 400 25 0 15 5 0 0\n"
            + "intr 123456 1 2 3 4 5 6 7 8 9\n"
            + "ctxt 987654\n";
    private static final String MEMINFO = "MemTotal:        2010000 kB\n"
            + "MemFree:          120000 kB\n"
            + "MemAvailable:     812000 kB\n"
            + "Buffers:           30000 kB\n";
    private static final String STATUS =
            "Name:\tpython\n" + "VmPeak:\t 900000 kB\n" + "VmRSS:\t  463000 kB\n" + "Threads:\t31\n";

    @TempDir
    Path proc;

    @BeforeEach
    void writeProc() throws IOException {
        writeStat(1_000, 300, 8_000, 0);
        Files.writeString(proc.resolve("meminfo"), MEMINFO);
        Files.createDirectories(proc.resolve("self"));
        Files.writeString(proc.resolve("self").resolve("status"), STATUS);
    }

    private void writeStat(final long user, final long system, final long idle, final long steal) throws IOException {
        Files.writeString(proc.resolve("stat"), String.format(STAT_HEAD, user, system, idle, steal));
    }

    @Test
    void memoryIsReadFromMeminfoAndTheProcessStatus() {
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();

            assertEquals(2_010_000L * 1024, readings.memTotalBytes());
            assertEquals(812_000L * 1024, readings.memAvailableBytes());
            assertEquals(463_000L * 1024, readings.processRssBytes());
        }
    }

    @Test
    void cpuUseIsTheShareOfTimeSinceThePreviousSample() throws IOException {
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();
            assertEquals(HostReadings.UNAVAILABLE, readings.cpuBusyPermille(), "no previous sample to compare with");
            assertEquals(HostReadings.UNAVAILABLE, readings.cpuStealPermille());

            // Over 1,000 ticks: 300 user, 100 system and 100 stolen are busy; 500 idle isn't.
            writeStat(1_300, 400, 8_500, 100);
            readings.sample();

            assertEquals(500, readings.cpuBusyPermille());
            assertEquals(100, readings.cpuStealPermille());
        }
    }

    @Test
    void anUnchangedStatReportsNoCpuRatherThanDividingByZero() {
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();
            readings.sample();

            assertEquals(HostReadings.UNAVAILABLE, readings.cpuBusyPermille());
        }
    }

    @Test
    void withoutProcOnlyTheHeapAndDiskRead() {
        try (HostReadings readings = new HostReadings(proc.resolve("missing"), proc.toFile())) {
            readings.sample();
            readings.sample();

            assertEquals(HostReadings.UNAVAILABLE, readings.cpuBusyPermille());
            assertEquals(HostReadings.UNAVAILABLE, readings.memTotalBytes());
            assertEquals(HostReadings.UNAVAILABLE, readings.memAvailableBytes());
            assertEquals(HostReadings.UNAVAILABLE, readings.processRssBytes());
            assertTrue(readings.heapUsedBytes() > 0);
            assertTrue(readings.heapMaxBytes() >= readings.heapUsedBytes());
            assertTrue(readings.diskFreeBytes() > 0);
        }
    }

    @Test
    void aFileMissingALineReportsThatReadingUnavailable() throws IOException {
        Files.writeString(proc.resolve("meminfo"), "MemTotal:        2010000 kB\n");
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();

            assertEquals(2_010_000L * 1024, readings.memTotalBytes());
            assertEquals(HostReadings.UNAVAILABLE, readings.memAvailableBytes());
        }
    }

    @Test
    void theRealProcParsesOnLinux() {
        if (!Files.exists(Path.of("/proc/stat"))) {
            return;
        }
        try (HostReadings readings = HostReadings.ofThisProcess(Path.of("/").toFile())) {
            readings.sample();

            assertTrue(readings.memTotalBytes() > 0);
            assertTrue(readings.memAvailableBytes() > 0 && readings.memAvailableBytes() <= readings.memTotalBytes());
            assertTrue(readings.processRssBytes() > 0);
        }
    }
}
