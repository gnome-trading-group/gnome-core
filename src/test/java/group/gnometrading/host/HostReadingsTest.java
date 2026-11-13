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

    private static final String NET_DEV = "Inter-|   Receive                            |  Transmit\n"
            + " face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls"
            + " carrier compressed\n"
            + "    lo:  5000      50    0    0    0     0          0         0     5000      50    0    0    0     0"
            + "       0          0\n"
            + "  ens5: 1000000  2000    0    3    0     0          0         0   400000    1500    0    1    0     0"
            + "       0          0\n"
            + "  ens6:   20000    40    0    0    0     0          0         0     1000      10    0    0    0     0"
            + "       0          0\n";
    private static final String SNMP = "Ip: Forwarding DefaultTTL\nIp: 1 64\n"
            + "Tcp: RtoAlgorithm RtoMin RtoMax MaxConn ActiveOpens PassiveOpens AttemptFails EstabResets CurrEstab"
            + " InSegs OutSegs RetransSegs InErrs OutRsts InCsumErrors\n"
            + "Tcp: 1 200 120000 -1 300 20 5 7 12 900000 850000 42 0 9 0\n"
            + "Udp: InDatagrams NoPorts InErrors OutDatagrams RcvbufErrors SndbufErrors InCsumErrors IgnoredMulti"
            + " MemErrors\n"
            + "Udp: 70000 3 120 64000 115 2 0 0 0\n"
            + "UdpLite: InDatagrams NoPorts InErrors OutDatagrams RcvbufErrors SndbufErrors InCsumErrors\n"
            + "UdpLite: 9 9 9 9 9 9 9\n";
    private static final String IO = "rchar: 5000000\nwchar: 7000000\nsyscr: 100\nsyscw: 200\n"
            + "read_bytes: 4096\nwrite_bytes: 81920\ncancelled_write_bytes: 0\n";

    @Test
    void networkTcpLoadIoAndContextSwitchesAreRead() throws IOException {
        Files.createDirectories(proc.resolve("net"));
        Files.writeString(proc.resolve("net").resolve("dev"), NET_DEV);
        Files.writeString(proc.resolve("net").resolve("snmp"), SNMP);
        Files.writeString(proc.resolve("loadavg"), "1.37 0.80 0.52 2/345 6789\n");
        Files.writeString(proc.resolve("self").resolve("io"), IO);
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();

            assertEquals(1_020_000, readings.netRxBytes(), "loopback left out");
            assertEquals(2_040, readings.netRxPackets());
            assertEquals(3, readings.netRxDrops());
            assertEquals(401_000, readings.netTxBytes());
            assertEquals(1_510, readings.netTxPackets());
            assertEquals(1, readings.netTxDrops());
            assertEquals(850_000, readings.tcpOutSegments());
            assertEquals(42, readings.tcpRetransmits());
            assertEquals(70_000, readings.udpInDatagrams());
            assertEquals(64_000, readings.udpOutDatagrams());
            assertEquals(120, readings.udpInErrors());
            assertEquals(115, readings.udpRcvbufErrors());
            assertEquals(2, readings.udpSndbufErrors());
            assertEquals(137, readings.loadHundredths());
            assertEquals(4_096, readings.ioReadBytes());
            assertEquals(81_920, readings.ioWriteBytes());
            assertEquals(987_654, readings.contextSwitches());
        }
    }

    @Test
    void readingsWithoutTheirFilesAreUnavailable() {
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();

            assertEquals(HostReadings.UNAVAILABLE, readings.netRxBytes());
            assertEquals(HostReadings.UNAVAILABLE, readings.tcpRetransmits());
            assertEquals(HostReadings.UNAVAILABLE, readings.udpRcvbufErrors());
            assertEquals(HostReadings.UNAVAILABLE, readings.loadHundredths());
            assertEquals(HostReadings.UNAVAILABLE, readings.ioWriteBytes());
        }
    }

    @Test
    void contextSwitchesAreFoundPastALongInterruptLine() throws IOException {
        StringBuilder intr = new StringBuilder("intr 1");
        for (int i = 0; i < 6_000; i++) {
            intr.append(' ').append(i);
        }
        Files.writeString(proc.resolve("stat"), "cpu  1 2 3 4 5 6 7 8 0 0\n" + intr + "\nctxt 555\n");
        try (HostReadings readings = new HostReadings(proc, proc.toFile())) {
            readings.sample();
            assertEquals(555, readings.contextSwitches());
        }
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
