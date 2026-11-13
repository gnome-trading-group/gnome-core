package group.gnometrading.metrics;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * One thread's counters, named at wiring and read by another thread: how a hot-path agent reports what it did without
 * paying for it. The owner thread is the only writer; each write is a plain read and a release store (no lock, no CAS,
 * no allocation), so a reader sees a value no older than the owner's last write and never a torn one.
 *
 * <p>Each is declared as what it is, so a reader knows how to report it ({@link CounterSampler}): a {@link #counter}
 * only grows ({@link #increment}, {@link #add}) and is reported as its change; a {@link #gauge} holds the latest value
 * ({@link #set}); a {@link #peak} the largest since a reader last took it ({@link #max}, {@link #getAndResetMax}).
 * One block per writer thread: two threads writing one block would lose increments.
 */
public final class MetricCounters {

    private static final VarHandle VALUES = MethodHandles.arrayElementVarHandle(long[].class);
    // Cache lines either side, so a block's counters never share a line with another thread's data.
    private static final int PAD = 8;

    /** What a counter is, which says how a reader reports it. */
    public enum Type {
        COUNTER,
        GAUGE,
        PEAK
    }

    private final long[] values;
    private final String[] names;
    private final Type[] types;
    private int count;

    public MetricCounters(final int capacity) {
        this.values = new long[capacity + 2 * PAD];
        this.names = new String[capacity];
        this.types = new Type[capacity];
    }

    /** A count named {@code name}; at wiring, before the owner writes. */
    public int counter(final String name) {
        return declare(name, Type.COUNTER);
    }

    /** A value named {@code name}, reported as it stands. */
    public int gauge(final String name) {
        return declare(name, Type.GAUGE);
    }

    /** A high-water mark named {@code name}, reported and reset by each read. */
    public int peak(final String name) {
        return declare(name, Type.PEAK);
    }

    private int declare(final String name, final Type type) {
        if (count == names.length) {
            throw new IllegalStateException("No room for counter " + name + " beyond " + names.length);
        }
        names[count] = name;
        types[count] = type;
        return count++;
    }

    /** Owner thread only. */
    public void increment(final int counter) {
        add(counter, 1);
    }

    /** Owner thread only. */
    public void add(final int counter, final long delta) {
        final int index = PAD + counter;
        VALUES.setRelease(values, index, values[index] + delta);
    }

    /** Owner thread only: the counter holds {@code value} until set again. */
    public void set(final int counter, final long value) {
        VALUES.setRelease(values, PAD + counter, value);
    }

    /** Owner thread only: the counter holds the largest value given since a reader last took it. */
    public void max(final int counter, final long value) {
        final int index = PAD + counter;
        if (value > values[index]) {
            VALUES.setRelease(values, index, value);
        }
    }

    /** Any thread. */
    public long get(final int counter) {
        return (long) VALUES.getAcquire(values, PAD + counter);
    }

    /**
     * Any thread, for a {@link #max} counter only: its value, then zero. A larger value the owner writes between the two
     * is kept for the next read only if it lands after the reset, so a peak may be missed by one read; never invented.
     */
    public long getAndResetMax(final int counter) {
        return (long) VALUES.getAndSet(values, PAD + counter, 0L);
    }

    public int count() {
        return count;
    }

    public String name(final int counter) {
        return names[counter];
    }

    public Type type(final int counter) {
        return types[counter];
    }
}
