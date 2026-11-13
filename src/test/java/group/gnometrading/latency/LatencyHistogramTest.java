package group.gnometrading.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

    @Test
    void everyValueFallsInsideItsBucket() {
        for (long value = 0; value > 0 || value == 0; value += value < 100 ? 1 : value / 7) {
            int bucket = LatencyHistogram.bucketOf(value);
            long upper = LatencyHistogram.upper(bucket);
            long lower = bucket == 0 ? 0 : LatencyHistogram.upper(bucket - 1);
            assertTrue(bucket < LatencyHistogram.BUCKETS, value + " past the last bucket");
            assertTrue(lower <= value && (value < upper || upper == Long.MAX_VALUE), value + " not in its bucket");
            assertTrue(upper - lower <= Math.max(1, lower / 8), "bucket " + bucket + " is wider than 12.5%");
        }
    }

    @Test
    void theLargestLongHasABucket() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(Long.MAX_VALUE);

        assertEquals(LatencyHistogram.BUCKETS - 1, LatencyHistogram.bucketOf(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, histogram.percentile(0.5));
    }

    @Test
    void ranksCountFromTheSmallest() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(3);
        histogram.record(400);
        histogram.record(9_000);

        assertEquals(4, histogram.atRank(1));
        assertEquals(416, histogram.atRank(2));
        assertEquals(9_000, histogram.atRank(3));
        assertEquals(416, histogram.percentile(0.5));
        assertEquals(4, histogram.percentile(0));
    }

    @Test
    void negativeValuesCountAsZero() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(-5);

        assertEquals(1, histogram.count());
        assertEquals(0, histogram.max());
        assertEquals(0, histogram.percentile(0.99));
    }

    @Test
    void resettingEmptiesIt() {
        LatencyHistogram histogram = new LatencyHistogram();
        histogram.record(70);
        histogram.reset();

        assertEquals(0, histogram.count());
        assertEquals(0, histogram.max());
        assertEquals(0, histogram.percentile(0.99));
    }
}
