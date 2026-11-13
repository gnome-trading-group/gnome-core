package group.gnometrading.metrics;

import group.gnometrading.codecs.json.JsonEncoder;
import java.util.List;

/**
 * Turns named blocks of {@link MetricCounters} into one sample kind, every interval: {@code {"<block>": {"<counter>":
 * value, ...}, ...}}. A count is reported as its change since the previous sample (left out of the first, which has
 * nothing to compare with), a gauge as it stands, a peak as the largest since the previous sample, then reset. Runs on
 * the sending thread; allocates nothing once constructed.
 */
public final class CounterSampler {

    private final SampleBuffer samples;
    private final String kind;
    private final String[] blockNames;
    private final MetricCounters[] blocks;
    private final long[][] previous;
    private final long intervalMs;
    private boolean first = true;
    private long nextAtMs;

    /** @param blocks each block's name and counters, written by its own thread */
    public CounterSampler(
            final SampleBuffer samples,
            final String kind,
            final List<String> blockNames,
            final List<MetricCounters> blocks,
            final long intervalMs) {
        if (blockNames.size() != blocks.size()) {
            throw new IllegalArgumentException("A name for each block of counters");
        }
        this.samples = samples;
        this.kind = kind;
        this.blockNames = blockNames.toArray(new String[0]);
        this.blocks = blocks.toArray(new MetricCounters[0]);
        this.previous = new long[this.blocks.length][];
        for (int b = 0; b < this.blocks.length; b++) {
            this.previous[b] = new long[this.blocks[b].count()];
        }
        this.intervalMs = intervalMs;
    }

    /** Takes a sample if one is due at {@code nowMs}; @return whether it did */
    public boolean sampleIfDue(final long nowMs) {
        if (nowMs < nextAtMs) {
            return false;
        }
        nextAtMs = nowMs + intervalMs;
        sample(nowMs);
        return true;
    }

    /** Takes a sample now. */
    public void sample(final long nowMs) {
        final JsonEncoder json = samples.begin(kind, nowMs);
        try {
            json.writeObjectEntry("intervalMs", intervalMs);
            for (int b = 0; b < blocks.length; b++) {
                json.writeComma().writeString(blockNames[b]).writeColon().writeObjectStart();
                writeBlock(json, blocks[b], previous[b]);
                json.writeObjectEnd();
            }
            samples.commit();
        } catch (RuntimeException e) {
            samples.abandon();
            throw e;
        } finally {
            first = false;
        }
    }

    private void writeBlock(final JsonEncoder json, final MetricCounters counters, final long[] before) {
        boolean written = false;
        for (int c = 0; c < before.length; c++) {
            final MetricCounters.Type type = counters.type(c);
            final long value;
            if (type == MetricCounters.Type.PEAK) {
                value = counters.getAndResetMax(c);
            } else {
                value = counters.get(c);
            }
            final boolean report = type != MetricCounters.Type.COUNTER || !first;
            if (report) {
                if (written) {
                    json.writeComma();
                }
                json.writeObjectEntry(
                        counters.name(c), type == MetricCounters.Type.COUNTER ? value - before[c] : value);
                written = true;
            }
            before[c] = value;
        }
    }
}
