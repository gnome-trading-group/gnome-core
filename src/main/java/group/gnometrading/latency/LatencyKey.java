package group.gnometrading.latency;

/**
 * What one histogram in {@link MinuteHistograms} measures: a metric and its labels, each as a code (for numeric
 * records, such as a journal's metric rows) and a name (for people). A label the metric doesn't have is {@link #NONE}
 * with an empty name; an exchange it doesn't know is exchange 0.
 */
public record LatencyKey(
        int metricCode,
        String metric,
        int exchangeId,
        String exchange,
        int actionCode,
        String action,
        int kindCode,
        String kind) {

    public static final int NONE = -1;
}
