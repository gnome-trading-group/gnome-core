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
 * reads, keeping the tightest of a few tries, so it is off by tens of nanoseconds at most. chrony slews both clocks
 * alike, so the offset holds; it is sampled again every {@link #DEFAULT_RESAMPLE_INTERVAL_NANOS} on whichever thread
 * reads, which also catches a clock step. That resample allocates a few small objects, and may move the clock by the
 * offset's error, tens of nanoseconds either way: durations across a resample are good to that. Thread-safe.
 */
public final class SampledEpochNanoClock implements EpochNanoClock {

    public static final long DEFAULT_RESAMPLE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final int TRIES = 5;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    // After the constants it reads: static fields initialize in order.
    /** One shared clock for a process: nothing about it is per caller. */
    public static final SampledEpochNanoClock INSTANCE = new SampledEpochNanoClock();

    private final long resampleIntervalNanos;
    private volatile Sample sample;

    public SampledEpochNanoClock() {
        this(DEFAULT_RESAMPLE_INTERVAL_NANOS);
    }

    public SampledEpochNanoClock(final long resampleIntervalNanos) {
        this.resampleIntervalNanos = resampleIntervalNanos;
        this.sample = take();
    }

    @Override
    public long nanoTime() {
        final long now = System.nanoTime();
        Sample current = sample;
        if (now - current.sampledAtNanoTime >= resampleIntervalNanos) {
            current = take();
            sample = current;
        }
        return now + current.offsetNanos;
    }

    private static Sample take() {
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
        return new Sample(bestOffset, sampledAt);
    }

    private record Sample(long offsetNanos, long sampledAtNanoTime) {}
}
