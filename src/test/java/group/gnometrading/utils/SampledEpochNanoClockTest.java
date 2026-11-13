package group.gnometrading.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SampledEpochNanoClockTest {

    private static final int WARM_UP = 50_000;
    private static final int MEASURED = 10_000;

    @Test
    void theSharedClockReadsTheSystemClock() {
        assertReadsTheSystemClock(SampledEpochNanoClock.INSTANCE);
    }

    @Test
    void readsTheSystemClock() {
        assertReadsTheSystemClock(new SampledEpochNanoClock());
    }

    @Test
    void theSharedClockAllocatesNothing() {
        assertAllocatesNothing(SampledEpochNanoClock.INSTANCE);
    }

    @Test
    void readingAllocatesNothing() {
        assertAllocatesNothing(new SampledEpochNanoClock(TimeUnit.HOURS.toNanos(1)));
    }

    private static void assertReadsTheSystemClock(final SampledEpochNanoClock clock) {
        final Instant before = Instant.now();
        final long read = clock.nanoTime();
        final Instant after = Instant.now();

        // The offset is good to tens of nanoseconds and the read is bracketed by wall reads, so a thread descheduled
        // between them only widens the bracket; the margin is for the system clock itself moving under chrony.
        final long slack = TimeUnit.MILLISECONDS.toNanos(1);
        assertTrue(read >= epochNanos(before) - slack, "read " + read + " before " + before);
        assertTrue(read <= epochNanos(after) + slack, "read " + read + " after " + after);
    }

    @Test
    void neverGoesBackwardsBetweenSamples() {
        final SampledEpochNanoClock clock = new SampledEpochNanoClock();
        long last = clock.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            final long now = clock.nanoTime();
            assertTrue(now >= last, "went from " + last + " back to " + now);
            last = now;
        }
    }

    @Test
    void resamplingKeepsReadingTheSystemClock() {
        final SampledEpochNanoClock clock = new SampledEpochNanoClock(0);

        for (int i = 0; i < 1_000; i++) {
            assertReadsTheSystemClock(clock);
        }
    }

    @Test
    void aResampleAsTightAsAllowedReplacesTheOffset() {
        assertTrue(SampledEpochNanoClock.tightEnough(SampledEpochNanoClock.MAX_SAMPLE_WIDTH_NANOS));
    }

    @Test
    void anInterruptedResampleKeepsTheOffsetHeld() {
        assertFalse(SampledEpochNanoClock.tightEnough(SampledEpochNanoClock.MAX_SAMPLE_WIDTH_NANOS + 1));
    }

    private static void assertAllocatesNothing(final SampledEpochNanoClock clock) {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().getId();

        long sink = read(clock, WARM_UP);
        final long before = threads.getThreadAllocatedBytes(threadId);
        sink += read(clock, MEASURED);
        final long allocated = threads.getThreadAllocatedBytes(threadId) - before;

        assertTrue(sink != 0);
        // Measuring can itself allocate a little; anything per read would be hundreds of kilobytes here.
        assertTrue(allocated < 2_048, "allocated " + allocated + " bytes over " + MEASURED + " reads");
    }

    private static long read(final SampledEpochNanoClock clock, final int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += clock.nanoTime();
        }
        return sum;
    }

    private static long epochNanos(final Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
