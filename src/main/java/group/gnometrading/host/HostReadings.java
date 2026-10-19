package group.gnometrading.host;

import group.gnometrading.utils.AsciiParsing;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The host's CPU, memory and disk, and this process's memory, read without allocating, so a GC-free process can
 * report on itself.
 *
 * <p>Linux regenerates a {@code /proc} file each time it is read from the start, so the files are opened once and
 * re-read into one buffer, and their numbers parsed in place. Off Linux the {@code /proc} readings are
 * {@link #UNAVAILABLE}; the heap and disk still read.
 *
 * <p>{@link #sample()} refreshes every reading. CPU use is the share of time since the previous sample, so it is
 * {@link #UNAVAILABLE} until the second. Not thread-safe: sample and read on one thread.
 */
public final class HostReadings implements AutoCloseable {

    public static final long UNAVAILABLE = -1;

    private static final int BUFFER_BYTES = 4096;
    private static final long KILOBYTE = 1024;
    private static final long PERMILLE = 1000;
    // /proc/stat's "cpu" line: user nice system idle iowait irq softirq steal, then guest time already counted in user.
    private static final int CPU_FIELDS = 8;
    private static final int IDLE = 3;
    private static final int IOWAIT = 4;
    private static final int STEAL = 7;
    private static final byte[] CPU_LINE = "cpu ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MEM_TOTAL = "MemTotal:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MEM_AVAILABLE = "MemAvailable:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VM_RSS = "VmRSS:".getBytes(StandardCharsets.US_ASCII);

    private final FileChannel stat;
    private final FileChannel meminfo;
    private final FileChannel status;
    private final File disk;
    private final Runtime runtime = Runtime.getRuntime();
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
    private final long[] cpu = new long[CPU_FIELDS];

    private long previousTotal = UNAVAILABLE;
    private long previousBusy;
    private long previousSteal;
    private long cpuBusyPermille = UNAVAILABLE;
    private long cpuStealPermille = UNAVAILABLE;
    private long memTotalBytes = UNAVAILABLE;
    private long memAvailableBytes = UNAVAILABLE;
    private long processRssBytes = UNAVAILABLE;
    private long heapUsedBytes;
    private long heapMaxBytes;
    private long diskFreeBytes;

    /**
     * @param proc where {@code /proc} is: {@code stat}, {@code meminfo} and {@code self/status} are read under it
     * @param disk a path on the filesystem whose free space to report
     */
    public HostReadings(final Path proc, final File disk) {
        this.stat = open(proc.resolve("stat"));
        this.meminfo = open(proc.resolve("meminfo"));
        this.status = open(proc.resolve("self").resolve("status"));
        this.disk = disk;
    }

    /** Readings of this host and process. */
    public static HostReadings ofThisProcess(final File disk) {
        return new HostReadings(Path.of("/proc"), disk);
    }

    private static FileChannel open(final Path path) {
        try {
            return FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }

    /** Refreshes every reading. */
    public void sample() {
        sampleCpu();
        sampleMemory();
        processRssBytes = read(status) ? kilobytesAfter(VM_RSS) : UNAVAILABLE;
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        heapMaxBytes = runtime.maxMemory();
        diskFreeBytes = disk.getUsableSpace();
    }

    private void sampleCpu() {
        if (!read(stat) || !parseCpuLine()) {
            cpuBusyPermille = UNAVAILABLE;
            cpuStealPermille = UNAVAILABLE;
            return;
        }
        long total = 0;
        for (final long field : cpu) {
            total += field;
        }
        final long busy = total - cpu[IDLE] - cpu[IOWAIT];
        final long elapsed = total - previousTotal;
        if (previousTotal == UNAVAILABLE || elapsed <= 0) {
            cpuBusyPermille = UNAVAILABLE;
            cpuStealPermille = UNAVAILABLE;
        } else {
            cpuBusyPermille = (busy - previousBusy) * PERMILLE / elapsed;
            cpuStealPermille = (cpu[STEAL] - previousSteal) * PERMILLE / elapsed;
        }
        previousTotal = total;
        previousBusy = busy;
        previousSteal = cpu[STEAL];
    }

    private boolean parseCpuLine() {
        final int end = buffer.limit();
        if (AsciiParsing.indexOf(buffer, 0, end, CPU_LINE) != 0) {
            return false;
        }
        int index = CPU_LINE.length;
        for (int field = 0; field < CPU_FIELDS; field++) {
            index = AsciiParsing.skipSpacesAndTabs(buffer, index, end);
            final int digitsEnd = AsciiParsing.skipDigits(buffer, index, end);
            final long value = AsciiParsing.parseNaturalLong(buffer, index, digitsEnd, UNAVAILABLE);
            if (value == UNAVAILABLE) {
                return false;
            }
            cpu[field] = value;
            index = digitsEnd;
        }
        return true;
    }

    private void sampleMemory() {
        if (read(meminfo)) {
            memTotalBytes = kilobytesAfter(MEM_TOTAL);
            memAvailableBytes = kilobytesAfter(MEM_AVAILABLE);
        } else {
            memTotalBytes = UNAVAILABLE;
            memAvailableBytes = UNAVAILABLE;
        }
    }

    /** The {@code kB} value on the line starting {@code label}, in bytes. */
    private long kilobytesAfter(final byte[] label) {
        final int end = buffer.limit();
        final int at = AsciiParsing.indexOf(buffer, 0, end, label);
        if (at < 0) {
            return UNAVAILABLE;
        }
        final int start = AsciiParsing.skipSpacesAndTabs(buffer, at + label.length, end);
        final long kilobytes =
                AsciiParsing.parseNaturalLong(buffer, start, AsciiParsing.skipDigits(buffer, start, end), UNAVAILABLE);
        return kilobytes == UNAVAILABLE ? UNAVAILABLE : kilobytes * KILOBYTE;
    }

    /** Reads the start of {@code file} into the buffer, flipped for parsing; false if it can't be read. */
    private boolean read(final FileChannel file) {
        if (file == null) {
            return false;
        }
        buffer.clear();
        try {
            long position = 0;
            int read;
            while (buffer.hasRemaining() && (read = file.read(buffer, position)) > 0) {
                position += read;
            }
        } catch (IOException e) {
            return false;
        }
        buffer.flip();
        return buffer.hasRemaining();
    }

    /** CPU not idle across all cores since the previous sample, stolen time included, in tenths of a percent. */
    public long cpuBusyPermille() {
        return cpuBusyPermille;
    }

    /**
     * CPU time the hypervisor gave to someone else since the previous sample, in tenths of a percent: on a burstable
     * instance out of credits, the throttling.
     */
    public long cpuStealPermille() {
        return cpuStealPermille;
    }

    public long memTotalBytes() {
        return memTotalBytes;
    }

    /** Memory the kernel could give processes without swapping, page cache it can drop included. */
    public long memAvailableBytes() {
        return memAvailableBytes;
    }

    /** This process's resident memory: for an embedded JVM, its host process's too. */
    public long processRssBytes() {
        return processRssBytes;
    }

    public long heapUsedBytes() {
        return heapUsedBytes;
    }

    public long heapMaxBytes() {
        return heapMaxBytes;
    }

    public long diskFreeBytes() {
        return diskFreeBytes;
    }

    @Override
    public void close() {
        closeQuietly(stat);
        closeQuietly(meminfo);
        closeQuietly(status);
    }

    private static void closeQuietly(final FileChannel file) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (IOException e) {
            // Closing a read-only file can't lose anything.
        }
    }
}
