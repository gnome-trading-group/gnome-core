package group.gnometrading.latency;

import org.agrona.collections.Int2IntHashMap;

/**
 * One metric in {@link MinuteHistograms}, keyed by exchange, action and kind, every combination made at wiring so a
 * record is an index lookup. Actions and kinds are the caller's small ordinals, their names indexed by them (an empty
 * name, a dimension the metric doesn't have, codes as {@link LatencyKey#NONE}); an exchange id the grid wasn't given,
 * or none, goes under exchange 0, unnamed.
 */
public final class LatencyGrid {

    private static final int MISSING = -1;

    private final MinuteHistograms histograms;
    private final Int2IntHashMap exchangeIndex = new Int2IntHashMap(MISSING);
    private final int actions;
    private final int kinds;
    private final int[] keys;

    public LatencyGrid(
            final MinuteHistograms histograms,
            final int metricCode,
            final String metric,
            final int[] exchangeIds,
            final String[] exchangeNames,
            final String[] actionLabels,
            final String[] kindLabels) {
        if (exchangeIds.length != exchangeNames.length) {
            throw new IllegalArgumentException("Exchange ids and names differ in length");
        }
        this.histograms = histograms;
        this.actions = actionLabels.length;
        this.kinds = kindLabels.length;
        final int exchanges = exchangeIds.length + 1;
        this.keys = new int[exchanges * actions * kinds];
        for (int e = 0; e < exchanges; e++) {
            final int exchangeId = e == 0 ? 0 : exchangeIds[e - 1];
            final String exchange = e == 0 ? "" : exchangeNames[e - 1];
            if (e > 0) {
                exchangeIndex.put(exchangeId, e);
            }
            for (int a = 0; a < actions; a++) {
                for (int k = 0; k < kinds; k++) {
                    keys[(e * actions + a) * kinds + k] = histograms.key(new LatencyKey(
                            metricCode,
                            metric,
                            exchangeId,
                            exchange,
                            code(a, actionLabels[a]),
                            actionLabels[a],
                            code(k, kindLabels[k]),
                            kindLabels[k]));
                }
            }
        }
    }

    private static int code(final int index, final String name) {
        return name.isEmpty() ? LatencyKey.NONE : index;
    }

    /** How many keys a grid over these takes, to size its {@link MinuteHistograms}. */
    public static int size(final int exchanges, final int actions, final int kinds) {
        return (exchanges + 1) * actions * kinds;
    }

    /** An action or kind outside the labels is dropped. */
    public void record(final int exchangeId, final int action, final int kind, final long value, final long nowNanos) {
        if (action < 0 || action >= actions || kind < 0 || kind >= kinds) {
            return;
        }
        final int found = exchangeIndex.get(exchangeId);
        final int exchange = found == MISSING ? 0 : found;
        histograms.record(keys[(exchange * actions + action) * kinds + kind], value, nowNanos);
    }

    public MinuteHistograms histograms() {
        return histograms;
    }
}
