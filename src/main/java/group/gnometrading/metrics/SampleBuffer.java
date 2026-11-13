package group.gnometrading.metrics;

import group.gnometrading.codecs.json.JsonEncoder;
import java.nio.ByteBuffer;

/**
 * Samples waiting to be sent: each a JSON object {@code {"kind":..,"tsMs":..,"data":{..}}}, kept in order until a
 * post that carried them is accepted. Fixed size and allocation-free; one thread writes and sends.
 *
 * <p>A sample is written in place: {@link #begin} returns the encoder positioned inside {@code data}, the caller writes
 * the fields, and {@link #commit} closes it. When there is no room, the oldest samples are dropped to make it, and
 * counted: a long outage loses the oldest readings rather than the newest. A sample too large for the whole buffer is
 * dropped itself.
 */
public final class SampleBuffer {

    private final byte[] stored;
    private final int[] starts;
    private final int[] lengths;
    private final ByteBuffer scratch;
    private final JsonEncoder json = new JsonEncoder();

    private int first;
    private int count;
    private int used;
    private long dropped;
    private boolean open;

    /**
     * @param capacityBytes the most bytes waiting samples take
     * @param maxSamples the most samples waiting at once
     * @param maxSampleBytes the largest one sample may be
     */
    public SampleBuffer(final int capacityBytes, final int maxSamples, final int maxSampleBytes) {
        this.stored = new byte[capacityBytes];
        this.starts = new int[maxSamples];
        this.lengths = new int[maxSamples];
        this.scratch = ByteBuffer.allocate(maxSampleBytes);
    }

    /**
     * Starts a sample; write its data's fields (an object's entries, comma-separated) through the returned encoder. Data
     * past {@code maxSampleBytes} throws {@link java.nio.BufferOverflowException}: {@link #abandon} the sample then.
     */
    public JsonEncoder begin(final String kind, final long tsMs) {
        if (open) {
            throw new IllegalStateException("A sample is already being written");
        }
        open = true;
        scratch.clear();
        json.wrap(scratch);
        json.writeObjectStart()
                .writeObjectEntry("kind", kind)
                .writeComma()
                .writeObjectEntry("tsMs", tsMs)
                .writeComma()
                .writeString("data")
                .writeColon()
                .writeObjectStart();
        return json;
    }

    /** Ends the sample begun last and queues it. */
    public void commit() {
        if (!open) {
            throw new IllegalStateException("No sample is being written");
        }
        open = false;
        json.writeObjectEnd().writeObjectEnd();
        final int length = scratch.position();
        if (length > stored.length) {
            dropped++;
            return;
        }
        while (count == starts.length || stored.length - used < length) {
            dropOldest();
        }
        compactIfWrapped(length);
        final int start = count == 0 ? 0 : starts[slot(count - 1)] + lengths[slot(count - 1)];
        System.arraycopy(scratch.array(), 0, stored, start, length);
        final int slot = slot(count);
        starts[slot] = start;
        lengths[slot] = length;
        count++;
        used += length;
    }

    /** Abandons the sample begun last, as when reading for it failed. */
    public void abandon() {
        open = false;
    }

    // Samples sit end to end from the first one's start; past the end of the array, they are moved back to the front.
    private void compactIfWrapped(final int length) {
        if (count == 0) {
            return;
        }
        final int end = starts[slot(count - 1)] + lengths[slot(count - 1)];
        if (stored.length - end >= length) {
            return;
        }
        final int from = starts[slot(0)];
        System.arraycopy(stored, from, stored, 0, end - from);
        for (int i = 0; i < count; i++) {
            starts[slot(i)] -= from;
        }
    }

    private void dropOldest() {
        used -= lengths[slot(0)];
        first = slot(1);
        count--;
        dropped++;
    }

    private int slot(final int index) {
        return (first + index) % starts.length;
    }

    /** How many samples are waiting. */
    public int pending() {
        return count;
    }

    /** Samples dropped for room since the buffer was made. */
    public long dropped() {
        return dropped;
    }

    /** The waiting samples' bytes, at most this many samples. */
    public int bytes(final int samples) {
        int total = 0;
        for (int i = 0; i < Math.min(samples, count); i++) {
            total += lengths[slot(i)];
        }
        return total;
    }

    /**
     * Writes up to {@code maxSamples} of the oldest waiting samples, comma-separated, into {@code out} at its position, as
     * many as fit its remaining space; they stay queued until {@link #acknowledge}d.
     *
     * @return how many were written
     */
    public int writeTo(final ByteBuffer out, final int maxSamples) {
        int written = 0;
        while (written < Math.min(maxSamples, count)) {
            final int slot = slot(written);
            final int needed = lengths[slot] + (written > 0 ? 1 : 0);
            if (out.remaining() < needed) {
                break;
            }
            if (written > 0) {
                out.put((byte) ',');
            }
            out.put(stored, starts[slot], lengths[slot]);
            written++;
        }
        return written;
    }

    /**
     * Drops waiting samples, oldest first, that are larger than {@code maxBytes}, which no post of that size could ever
     * carry: left at the head, one would block every sample behind it. Counted as dropped.
     *
     * @return how many were dropped
     */
    public int dropLargerThan(final int maxBytes) {
        int droppedNow = 0;
        while (count > 0 && lengths[slot(0)] > maxBytes) {
            dropOldest();
            droppedNow++;
        }
        return droppedNow;
    }

    /** The oldest {@code samples} were accepted: they are gone. */
    public void acknowledge(final int samples) {
        for (int i = Math.min(samples, count); i > 0; i--) {
            used -= lengths[slot(0)];
            first = slot(1);
            count--;
        }
        if (count == 0) {
            first = 0;
        }
    }
}
