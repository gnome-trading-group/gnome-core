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

    // /proc/stat's intr line has a count per interrupt, tens of KB on a large host, and ctxt comes after it.
    private static final int BUFFER_BYTES = 64 * 1024;
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
    private static final byte[] CTXT = "\nctxt ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] READ_BYTES = "read_bytes:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WRITE_BYTES = "write_bytes:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TCP = "\nTcp: ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LOOPBACK = "lo".getBytes(StandardCharsets.US_ASCII);
    // /proc/net/snmp's Tcp values: RtoAlgorithm RtoMin RtoMax MaxConn ActiveOpens PassiveOpens AttemptFails
    // EstabResets CurrEstab InSegs OutSegs RetransSegs ...
    private static final int TCP_OUT_SEGS = 10;
    private static final int TCP_RETRANS_SEGS = 11;
    private static final byte[] UDP = "\nUdp: ".getBytes(StandardCharsets.US_ASCII);
    // /proc/net/snmp's Udp values: InDatagrams NoPorts InErrors OutDatagrams RcvbufErrors SndbufErrors ...
    private static final int UDP_IN_DATAGRAMS = 0;
    private static final int UDP_IN_ERRORS = 2;
    private static final int UDP_OUT_DATAGRAMS = 3;
    private static final int UDP_RCVBUF_ERRORS = 4;
    private static final int UDP_SNDBUF_ERRORS = 5;
    // /proc/net/dev after "iface:": rx bytes packets errs drop fifo frame compressed multicast, then tx bytes packets
    // errs drop ...
    private static final int RX_BYTES = 0;
    private static final int RX_PACKETS = 1;
    private static final int RX_DROP = 3;
    private static final int TX_BYTES = 8;
    private static final int TX_PACKETS = 9;
    private static final int TX_DROP = 11;
    private static final int NET_FIELDS = 12;
    private static final int HUNDREDTHS = 100;

    private final FileChannel stat;
    private final FileChannel meminfo;
    private final FileChannel status;
    private final FileChannel loadavg;
    private final FileChannel netDev;
    private final FileChannel snmp;
    private final FileChannel io;
    private final File disk;
    private final Runtime runtime = Runtime.getRuntime();
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_BYTES);
    private final long[] cpu = new long[CPU_FIELDS];
    private final long[] net = new long[NET_FIELDS];
    private final long[] tcp = new long[TCP_RETRANS_SEGS + 1];
    private final long[] udp = new long[UDP_SNDBUF_ERRORS + 1];

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
    private long contextSwitches = UNAVAILABLE;
    private long loadHundredths = UNAVAILABLE;
    private long netRxBytes = UNAVAILABLE;
    private long netRxPackets = UNAVAILABLE;
    private long netRxDrops = UNAVAILABLE;
    private long netTxBytes = UNAVAILABLE;
    private long netTxPackets = UNAVAILABLE;
    private long netTxDrops = UNAVAILABLE;
    private long tcpOutSegments = UNAVAILABLE;
    private long tcpRetransmits = UNAVAILABLE;
    private long udpInDatagrams = UNAVAILABLE;
    private long udpOutDatagrams = UNAVAILABLE;
    private long udpInErrors = UNAVAILABLE;
    private long udpRcvbufErrors = UNAVAILABLE;
    private long udpSndbufErrors = UNAVAILABLE;
    private long ioReadBytes = UNAVAILABLE;
    private long ioWriteBytes = UNAVAILABLE;

    /**
     * @param proc where {@code /proc} is: {@code stat}, {@code meminfo}, {@code loadavg}, {@code net/dev},
     *     {@code net/snmp}, {@code self/status} and {@code self/io} are read under it
     * @param disk a path on the filesystem whose free space to report
     */
    public HostReadings(final Path proc, final File disk) {
        this.stat = open(proc.resolve("stat"));
        this.meminfo = open(proc.resolve("meminfo"));
        this.status = open(proc.resolve("self").resolve("status"));
        this.loadavg = open(proc.resolve("loadavg"));
        this.netDev = open(proc.resolve("net").resolve("dev"));
        this.snmp = open(proc.resolve("net").resolve("snmp"));
        this.io = open(proc.resolve("self").resolve("io"));
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
        sampleLoad();
        sampleNetwork();
        sampleSnmp();
        sampleIo();
    }

    private void sampleLoad() {
        loadHundredths = UNAVAILABLE;
        if (!read(loadavg)) {
            return;
        }
        final int end = buffer.limit();
        final int wholeEnd = AsciiParsing.skipDigits(buffer, 0, end);
        final long whole = AsciiParsing.parseNaturalLong(buffer, 0, wholeEnd, UNAVAILABLE);
        if (whole == UNAVAILABLE || wholeEnd >= end || buffer.get(wholeEnd) != '.') {
            return;
        }
        final int fractionEnd = AsciiParsing.skipDigits(buffer, wholeEnd + 1, end);
        final long fraction = AsciiParsing.parseNaturalLong(buffer, wholeEnd + 1, fractionEnd, UNAVAILABLE);
        if (fraction != UNAVAILABLE && fractionEnd - wholeEnd - 1 == 2) {
            loadHundredths = whole * HUNDREDTHS + fraction;
        }
    }

    // Every interface but loopback, summed: what crossed the host's network.
    private void sampleNetwork() {
        if (!read(netDev)) {
            netRxBytes = UNAVAILABLE;
            netRxPackets = UNAVAILABLE;
            netRxDrops = UNAVAILABLE;
            netTxBytes = UNAVAILABLE;
            netTxPackets = UNAVAILABLE;
            netTxDrops = UNAVAILABLE;
            return;
        }
        final int end = buffer.limit();
        long rxBytes = 0;
        long rxPackets = 0;
        long rxDrops = 0;
        long txBytes = 0;
        long txPackets = 0;
        long txDrops = 0;
        int line = 0;
        while (line < end) {
            final int lineEnd = lineEnd(line, end);
            final int colon = indexOf(line, lineEnd, (byte) ':');
            if (colon > 0 && !loopback(line, colon) && parseFields(colon + 1, lineEnd, net)) {
                rxBytes += net[RX_BYTES];
                rxPackets += net[RX_PACKETS];
                rxDrops += net[RX_DROP];
                txBytes += net[TX_BYTES];
                txPackets += net[TX_PACKETS];
                txDrops += net[TX_DROP];
            }
            line = lineEnd + 1;
        }
        netRxBytes = rxBytes;
        netRxPackets = rxPackets;
        netRxDrops = rxDrops;
        netTxBytes = txBytes;
        netTxPackets = txPackets;
        netTxDrops = txDrops;
    }

    private boolean loopback(final int start, final int colon) {
        final int name = AsciiParsing.skipSpacesAndTabs(buffer, start, colon);
        return colon - name == LOOPBACK.length && AsciiParsing.indexOf(buffer, name, colon, LOOPBACK) == name;
    }

    private void sampleSnmp() {
        final boolean read = read(snmp);
        final boolean tcpRead = read && snmpValues(TCP, tcp);
        tcpOutSegments = tcpRead ? tcp[TCP_OUT_SEGS] : UNAVAILABLE;
        tcpRetransmits = tcpRead ? tcp[TCP_RETRANS_SEGS] : UNAVAILABLE;
        final boolean udpRead = read && snmpValues(UDP, udp);
        udpInDatagrams = udpRead ? udp[UDP_IN_DATAGRAMS] : UNAVAILABLE;
        udpOutDatagrams = udpRead ? udp[UDP_OUT_DATAGRAMS] : UNAVAILABLE;
        udpInErrors = udpRead ? udp[UDP_IN_ERRORS] : UNAVAILABLE;
        udpRcvbufErrors = udpRead ? udp[UDP_RCVBUF_ERRORS] : UNAVAILABLE;
        udpSndbufErrors = udpRead ? udp[UDP_SNDBUF_ERRORS] : UNAVAILABLE;
    }

    /**
     * The first {@code into.length} values of a protocol's second line (the first names them); false if missing. A
     * value that isn't a natural number (Tcp's MaxConn is -1) reads as {@link #UNAVAILABLE}.
     */
    private boolean snmpValues(final byte[] label, final long[] into) {
        final int end = buffer.limit();
        final int names = AsciiParsing.indexOf(buffer, 0, end, label);
        final int values = names < 0 ? -1 : AsciiParsing.indexOf(buffer, names + label.length, end, label);
        if (values < 0) {
            return false;
        }
        final int lineEnd = lineEnd(values + 1, end);
        int index = values + label.length;
        for (int field = 0; field < into.length; field++) {
            index = AsciiParsing.skipSpacesAndTabs(buffer, index, lineEnd);
            final int fieldEnd = tokenEnd(index, lineEnd);
            if (index == fieldEnd) {
                return false;
            }
            into[field] = AsciiParsing.parseNaturalLong(buffer, index, fieldEnd, UNAVAILABLE);
            index = fieldEnd;
        }
        return true;
    }

    private void sampleIo() {
        if (read(io)) {
            ioReadBytes = numberAfter(READ_BYTES);
            ioWriteBytes = numberAfter(WRITE_BYTES);
        } else {
            ioReadBytes = UNAVAILABLE;
            ioWriteBytes = UNAVAILABLE;
        }
    }

    private long numberAfter(final byte[] label) {
        final int end = buffer.limit();
        final int at = AsciiParsing.indexOf(buffer, 0, end, label);
        if (at < 0) {
            return UNAVAILABLE;
        }
        final int start = AsciiParsing.skipSpacesAndTabs(buffer, at + label.length, end);
        return AsciiParsing.parseNaturalLong(buffer, start, AsciiParsing.skipDigits(buffer, start, end), UNAVAILABLE);
    }

    private boolean parseFields(final int start, final int end, final long[] into) {
        int index = start;
        for (int field = 0; field < into.length; field++) {
            index = AsciiParsing.skipSpacesAndTabs(buffer, index, end);
            final int digitsEnd = AsciiParsing.skipDigits(buffer, index, end);
            final long value = AsciiParsing.parseNaturalLong(buffer, index, digitsEnd, UNAVAILABLE);
            if (value == UNAVAILABLE) {
                return false;
            }
            into[field] = value;
            index = digitsEnd;
        }
        return true;
    }

    private int lineEnd(final int start, final int end) {
        final int newline = indexOf(start, end, (byte) '\n');
        return newline < 0 ? end : newline;
    }

    private int tokenEnd(final int start, final int end) {
        int index = start;
        while (index < end && !AsciiParsing.isSpaceOrTab(buffer.get(index))) {
            index++;
        }
        return index;
    }

    private int indexOf(final int start, final int end, final byte value) {
        for (int i = start; i < end; i++) {
            if (buffer.get(i) == value) {
                return i;
            }
        }
        return -1;
    }

    private void sampleCpu() {
        if (!read(stat) || !parseCpuLine()) {
            cpuBusyPermille = UNAVAILABLE;
            cpuStealPermille = UNAVAILABLE;
            contextSwitches = UNAVAILABLE;
            return;
        }
        contextSwitches = numberAfter(CTXT);
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

    /** Context switches on the host since boot. */
    public long contextSwitches() {
        return contextSwitches;
    }

    /** The one-minute load average, in hundredths. */
    public long loadHundredths() {
        return loadHundredths;
    }

    /** Bytes received on every interface but loopback since boot; the rest of the network readings likewise. */
    public long netRxBytes() {
        return netRxBytes;
    }

    public long netRxPackets() {
        return netRxPackets;
    }

    public long netRxDrops() {
        return netRxDrops;
    }

    public long netTxBytes() {
        return netTxBytes;
    }

    public long netTxPackets() {
        return netTxPackets;
    }

    public long netTxDrops() {
        return netTxDrops;
    }

    /** TCP segments sent since boot, and those sent again: the host's retransmission rate. */
    public long tcpOutSegments() {
        return tcpOutSegments;
    }

    public long tcpRetransmits() {
        return tcpRetransmits;
    }

    /** Bytes this process read from and wrote to storage since it started (not page-cache hits). */
    /** UDP datagrams the host received, all sockets, since boot. */
    public long udpInDatagrams() {
        return udpInDatagrams;
    }

    public long udpOutDatagrams() {
        return udpOutDatagrams;
    }

    /** UDP datagrams the host received but couldn't deliver, receive-buffer overflows included. */
    public long udpInErrors() {
        return udpInErrors;
    }

    /** UDP datagrams dropped because a socket's receive buffer was full: a reader not keeping up. */
    public long udpRcvbufErrors() {
        return udpRcvbufErrors;
    }

    /** UDP datagrams dropped because a socket's send buffer was full. */
    public long udpSndbufErrors() {
        return udpSndbufErrors;
    }

    public long ioReadBytes() {
        return ioReadBytes;
    }

    public long ioWriteBytes() {
        return ioWriteBytes;
    }

    @Override
    public void close() {
        closeQuietly(stat);
        closeQuietly(meminfo);
        closeQuietly(status);
        closeQuietly(loadavg);
        closeQuietly(netDev);
        closeQuietly(snmp);
        closeQuietly(io);
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
