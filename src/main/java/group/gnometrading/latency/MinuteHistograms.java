package group.gnometrading.latency;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

/**
 * One owner thread's latency histograms, by key, a minute at a time, read by another thread once each minute closes.
 *
 * <p>Keys (a metric and its labels) are all made at wiring, so recording never allocates. The owner records into the
 * open minute; at the first record or {@link #roll} past its end, the open set becomes the closed one, and the set
 * closed before it is emptied and opened. A reader {@link #summarize}s the closed set, checking a version the owner
 * moves around each flip, so a summary that raced one is refused rather than torn: flips are a minute apart and a
 * summary takes well under a millisecond, so the next try succeeds.
 *
 * <p>A minute with nothing recorded and no roll is never closed; the next one that is says which minute it was.
 */
public final class MinuteHistograms {

    public static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);

    /** {@link #closedMinute()} before any minute has closed. */
    public static final long NONE = Long.MIN_VALUE;

    private static final VarHandle VERSION;

    static {
        try {
            VERSION = MethodHandles.lookup().findVarHandle(MinuteHistograms.class, "version", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final int capacity;
    private final LatencyKey[] labels;
    private final LatencyHistogram[][] sets;
    private int keys;

    private int open;
    private long openMinute = Long.MIN_VALUE;
    private long closesAt = Long.MIN_VALUE;
    // Odd while a flip is under way; read with an acquire, so a summary sees the closed set as the flip left it.
    private long version;
    private volatile long closedMinute = NONE;

    public MinuteHistograms(final int capacity) {
        this.capacity = capacity;
        this.labels = new LatencyKey[capacity];
        this.sets = new LatencyHistogram[2][capacity];
    }

    /** A key for these labels; at wiring, before the owner records. */
    public int key(final LatencyKey label) {
        if (keys == capacity) {
            throw new IllegalStateException("No room for latency key " + label + " beyond " + capacity);
        }
        final int key = keys++;
        labels[key] = label;
        sets[0][key] = new LatencyHistogram();
        sets[1][key] = new LatencyHistogram();
        return key;
    }

    /** Records into the minute holding {@code nowEpochNanos}, closing the open one first if it has ended. */
    public void record(final int key, final long value, final long nowEpochNanos) {
        roll(nowEpochNanos);
        sets[open][key].record(value);
    }

    /** Closes the open minute once {@code nowEpochNanos} is past it; the owner calls it each pass, records or not. */
    public void roll(final long nowEpochNanos) {
        if (nowEpochNanos < closesAt) {
            return;
        }
        final long minute = nowEpochNanos - Math.floorMod(nowEpochNanos, MINUTE_NANOS);
        if (openMinute != Long.MIN_VALUE) {
            final long before = (long) VERSION.getOpaque(this);
            VERSION.setOpaque(this, before + 1);
            VarHandle.storeStoreFence();
            final int next = 1 - open;
            final LatencyHistogram[] reopened = sets[next];
            for (int key = 0; key < keys; key++) {
                reopened[key].reset();
            }
            open = next;
            closedMinute = openMinute;
            VERSION.setRelease(this, before + 2);
        }
        openMinute = minute;
        closesAt = minute + MINUTE_NANOS;
    }

    /** Start (epoch nanos) of the latest closed minute, or {@link #NONE}. */
    public long closedMinute() {
        return closedMinute;
    }

    /**
     * Copies the closed minute's counts and percentiles into {@code out}; false when none has closed or a flip raced the
     * copy, leaving {@code out} to be ignored. Any thread; allocates nothing.
     */
    public boolean summarize(final MinuteSummary out) {
        final long before = (long) VERSION.getAcquire(this);
        if ((before & 1) != 0) {
            return false;
        }
        final long minute = closedMinute;
        if (minute == NONE) {
            return false;
        }
        final LatencyHistogram[] closed = sets[1 - open];
        out.reset(minute, keys);
        for (int key = 0; key < keys; key++) {
            out.set(key, closed[key]);
        }
        VarHandle.loadLoadFence();
        return (long) VERSION.getAcquire(this) == before;
    }

    public int keys() {
        return keys;
    }

    public int capacity() {
        return capacity;
    }

    public LatencyKey label(final int key) {
        return labels[key];
    }
}
