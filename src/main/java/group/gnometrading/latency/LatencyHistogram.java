package group.gnometrading.latency;

import java.util.Arrays;

/**
 * Non-negative durations counted in log-linear buckets: 8 per power of two, so a reported value is within 12.5% of the
 * true one. Unit-free (the caller's), and covering every long, so nanoseconds fit as well as microseconds. Fixed size;
 * recording and reading allocate nothing. One thread at a time.
 */
public final class LatencyHistogram {

    private static final int SUB_BUCKETS = 8;
    private static final int SUB_BUCKET_BITS = 3;
    static final int BUCKETS = (Long.SIZE - SUB_BUCKET_BITS) * SUB_BUCKETS;

    private final long[] counts = new long[BUCKETS];
    private long count;
    private long max;

    /** Negative values count as 0. */
    public void record(final long value) {
        final long clamped = Math.max(0, value);
        counts[bucketOf(clamped)]++;
        count++;
        max = Math.max(max, clamped);
    }

    public long count() {
        return count;
    }

    public long max() {
        return max;
    }

    /** The upper edge of the bucket holding the quantile, capped at the largest value seen; 0 when empty. */
    public long percentile(final double quantile) {
        return count == 0 ? 0 : atRank(Math.max(1, (long) Math.ceil(quantile * count)));
    }

    /** The upper edge of the bucket holding the {@code rank}-th smallest value (from 1), capped at the largest seen. */
    public long atRank(final long rank) {
        long seen = 0;
        for (int i = 0; i < BUCKETS; i++) {
            seen += counts[i];
            if (seen >= rank) {
                return Math.min(upper(i), max);
            }
        }
        return max;
    }

    public void reset() {
        Arrays.fill(counts, 0);
        count = 0;
        max = 0;
    }

    static int bucketOf(final long value) {
        if (value < SUB_BUCKETS) {
            return (int) value;
        }
        final int exponent = 63 - Long.numberOfLeadingZeros(value);
        final int sub = (int) (value >> (exponent - SUB_BUCKET_BITS)) & (SUB_BUCKETS - 1);
        return (exponent - SUB_BUCKET_BITS + 1) * SUB_BUCKETS + sub;
    }

    /** Exclusive; the top bucket's edge is past {@code Long.MAX_VALUE}, so it saturates there. */
    static long upper(final int bucket) {
        if (bucket < SUB_BUCKETS) {
            return bucket + 1;
        }
        final int exponent = bucket / SUB_BUCKETS + SUB_BUCKET_BITS - 1;
        final int sub = bucket % SUB_BUCKETS;
        final long edge = (long) (SUB_BUCKETS + sub + 1) << (exponent - SUB_BUCKET_BITS);
        return edge <= 0 ? Long.MAX_VALUE : edge;
    }
}
