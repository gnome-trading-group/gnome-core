package group.gnometrading.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatencyGridTest {

    private static final long T0 = 1_700_000_040L * 1_000_000_000L;
    private static final String[] ACTIONS = {"NEW", "CANCEL"};
    private static final String[] KINDS = {"NONE", "MARKET_DATA", "EXEC_REPORT"};

    @Test
    void everyCombinationHasItsOwnKey() {
        MinuteHistograms histograms = new MinuteHistograms(LatencyGrid.size(2, 2, 3));
        LatencyGrid grid = new LatencyGrid(
                histograms, 3, "oms.process", new int[] {4, 9}, new String[] {"KALSHI", "PM"}, ACTIONS, KINDS);

        grid.record(9, 1, 2, 700, T0);
        grid.record(4, 0, 1, 300, T0);
        histograms.roll(T0 + MinuteHistograms.MINUTE_NANOS);

        assertEquals(LatencyGrid.size(2, 2, 3), histograms.keys());
        MinuteSummary summary = new MinuteSummary(histograms.capacity());
        assertTrue(histograms.summarize(summary));
        int recorded = 0;
        for (int key = 0; key < histograms.keys(); key++) {
            if (summary.count(key) == 0) {
                continue;
            }
            recorded++;
            LatencyKey label = histograms.label(key);
            String labels = label.exchange() + "/" + label.action() + "/" + label.kind();
            assertEquals("oms.process", label.metric());
            assertEquals(labels.startsWith("PM") ? 9 : 4, label.exchangeId());
            assertEquals(
                    labels.equals("PM/CANCEL/EXEC_REPORT") ? 700 : 300,
                    summary.max(key),
                    labels + " got another's value");
            assertTrue(labels.equals("PM/CANCEL/EXEC_REPORT") || labels.equals("KALSHI/NEW/MARKET_DATA"), labels);
        }
        assertEquals(2, recorded);
    }

    @Test
    void anUnknownExchangeGoesUnderTheEmptyOne() {
        MinuteHistograms histograms = new MinuteHistograms(LatencyGrid.size(1, 1, 1));
        LatencyGrid grid = new LatencyGrid(
                histograms, 1, "strat.queue", new int[] {4}, new String[] {"KALSHI"}, new String[] {""}, new String[] {
                    "X"
                });

        grid.record(77, 0, 0, 5, T0);
        grid.record(4, 0, 5, 5, T0);
        histograms.roll(T0 + MinuteHistograms.MINUTE_NANOS);

        MinuteSummary summary = new MinuteSummary(histograms.capacity());
        assertTrue(histograms.summarize(summary));
        assertEquals("", histograms.label(0).exchange());
        assertEquals(0, histograms.label(0).exchangeId());
        assertEquals(LatencyKey.NONE, histograms.label(0).actionCode());
        assertEquals(0, histograms.label(0).kindCode());
        assertEquals(1, summary.count(0));
        assertEquals(0, summary.count(1));
    }
}
