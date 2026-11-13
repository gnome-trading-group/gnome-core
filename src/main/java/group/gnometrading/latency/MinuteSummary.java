package group.gnometrading.latency;

/**
 * A closed minute of {@link MinuteHistograms}, as the reader keeps it: per key the count, p50, p90, p99, p99.9 and max,
 * in the unit recorded. Sized once for the histograms' capacity; filled again each minute without allocating.
 */
public final class MinuteSummary {

    private static final double P50 = 0.5;
    private static final double P90 = 0.9;
    private static final double P99 = 0.99;
    private static final double P999 = 0.999;

    private final long[] counts;
    private final long[] p50;
    private final long[] p90;
    private final long[] p99;
    private final long[] p999;
    private final long[] max;
    private long minute = MinuteHistograms.NONE;
    private int keys;

    public MinuteSummary(final int capacity) {
        this.counts = new long[capacity];
        this.p50 = new long[capacity];
        this.p90 = new long[capacity];
        this.p99 = new long[capacity];
        this.p999 = new long[capacity];
        this.max = new long[capacity];
    }

    void reset(final long minuteStart, final int keyCount) {
        this.minute = minuteStart;
        this.keys = keyCount;
    }

    void set(final int key, final LatencyHistogram histogram) {
        counts[key] = histogram.count();
        p50[key] = histogram.percentile(P50);
        p90[key] = histogram.percentile(P90);
        p99[key] = histogram.percentile(P99);
        p999[key] = histogram.percentile(P999);
        max[key] = histogram.max();
    }

    /** Start (epoch nanos) of the minute summarized, or {@link MinuteHistograms#NONE}. */
    public long minute() {
        return minute;
    }

    public int keys() {
        return keys;
    }

    /** Whether any key recorded anything in the minute. */
    public boolean any() {
        for (int key = 0; key < keys; key++) {
            if (counts[key] > 0) {
                return true;
            }
        }
        return false;
    }

    public long count(final int key) {
        return counts[key];
    }

    public long p50(final int key) {
        return p50[key];
    }

    public long p90(final int key) {
        return p90[key];
    }

    public long p99(final int key) {
        return p99[key];
    }

    public long p999(final int key) {
        return p999[key];
    }

    public long max(final int key) {
        return max[key];
    }
}
