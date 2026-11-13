package group.gnometrading.utils;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.EpochNanoClock;

/**
 * Epoch time in nanoseconds as {@link System#nanoTime()} plus an offset to the system clock, so a read allocates
 * nothing: Agrona's {@code SystemEpochNanoClock} builds an {@link Instant} on every read, and its {@code
 * OffsetEpochNanoClock} takes its offset from {@link System#currentTimeMillis()}, up to a millisecond off.
 *
 * <p>The offset is sampled from {@link Instant#now()} (the system clock to the nanosecond) between two {@code nanoTime}
 * reads, keeping the tightest of a few tries, so it is off by at most half that bracket: tens of nanoseconds. chrony
 * slews both clocks alike, so the offset holds; it is sampled again every {@link #DEFAULT_RESAMPLE_INTERVAL_NANOS} on
 * whichever thread reads, which also catches a clock step. A resample whose every try was interrupted (the thread
 * descheduled mid-bracket, on a busy host) is wider than {@link #MAX_SAMPLE_WIDTH_NANOS} and is dropped, keeping the
 * offset already held until the next interval. A resample allocates its {@code Instant}s, and may move the clock by the
 * offset's error, tens of nanoseconds either way: durations across a resample are good to that.
 *
 * <p>Thread-safe. The offset and when it was sampled are separate volatile fields rather than one published object, so
 * a resample allocates no holder: a reader may pair a new offset with an old sample time, which at worst resamples once
 * more, and two threads resampling at once each write values that are good on their own.
 */
public final class SampledEpochNanoClock implements EpochNanoClock {

    public static final long DEFAULT_RESAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
    /** The widest bracket a resample may replace the offset with: an error of at most half of it. */
    public static final long MAX_SAMPLE_WIDTH_NANOS = TimeUnit.MICROSECONDS.toNanos(100);

    private static final int TRIES = 5;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    // After the constants it reads: static fields initialize in order.
    /** One shared clock for a process: nothing about it is per caller. */
    public static final SampledEpochNanoClock INSTANCE = new SampledEpochNanoClock();

    private final long resampleIntervalNanos;
    private volatile long offsetNanos;
    private volatile long sampledAtNanoTime;

    public SampledEpochNanoClock() {
        this(DEFAULT_RESAMPLE_INTERVAL_NANOS);
    }

    public SampledEpochNanoClock(final long resampleIntervalNanos) {
        this.resampleIntervalNanos = resampleIntervalNanos;
        // The first sample is kept however wide: there is no offset to fall back on.
        sample(true);
    }

    @Override
    public long nanoTime() {
        final long now = System.nanoTime();
        if (now - sampledAtNanoTime >= resampleIntervalNanos) {
            sample(false);
        }
        return now + offsetNanos;
    }

    /** Whether a resample's bracket is tight enough to replace the offset held. */
    static boolean tightEnough(final long widthNanos) {
        return widthNanos <= MAX_SAMPLE_WIDTH_NANOS;
    }

    private void sample(final boolean first) {
        long bestOffset = 0;
        long bestWidth = Long.MAX_VALUE;
        long sampledAt = 0;
        for (int i = 0; i < TRIES; i++) {
            final long before = System.nanoTime();
            final Instant wall = Instant.now();
            final long after = System.nanoTime();
            final long width = after - before;
            if (width < bestWidth) {
                bestWidth = width;
                final long midpoint = before + width / 2;
                bestOffset = wall.getEpochSecond() * NANOS_PER_SECOND + wall.getNano() - midpoint;
                sampledAt = after;
            }
        }
        if (first || tightEnough(bestWidth)) {
            offsetNanos = bestOffset;
        }
        sampledAtNanoTime = sampledAt;
    }
}
