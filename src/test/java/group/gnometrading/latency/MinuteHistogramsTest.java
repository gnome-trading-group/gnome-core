package group.gnometrading.latency;

import static group.gnometrading.latency.MinuteHistograms.MINUTE_NANOS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MinuteHistogramsTest {

    private static final long T0 = 1_700_000_040L * 1_000_000_000L;

    @Test
    void nothingIsClosedUntilAMinuteEnds() {
        MinuteHistograms histograms = new MinuteHistograms(4);
        int key = histograms.key(new LatencyKey(1, "oms.process", 0, "KALSHI", 0, "NEW", 0, "MARKET_DATA"));
        histograms.record(key, 500, T0 + 1);
        histograms.roll(T0 + MINUTE_NANOS - 1);

        assertEquals(MinuteHistograms.NONE, histograms.closedMinute());
        assertFalse(histograms.summarize(new MinuteSummary(4)));
    }

    @Test
    void aClosedMinuteIsSummarizedWhileTheNextFills() {
        MinuteHistograms histograms = new MinuteHistograms(4);
        int fast = histograms.key(new LatencyKey(1, "oms.process", 0, "KALSHI", 0, "NEW", 0, "MARKET_DATA"));
        int slow = histograms.key(new LatencyKey(1, "oms.process", 0, "KALSHI", 0, "CANCEL", 0, "MARKET_DATA"));
        for (int i = 1; i <= 100; i++) {
            histograms.record(fast, i * 10, T0 + i);
        }
        histograms.record(fast, 1_000_000, T0 + MINUTE_NANOS + 5);

        MinuteSummary summary = new MinuteSummary(4);
        assertTrue(histograms.summarize(summary));
        assertEquals(T0, summary.minute());
        assertEquals(2, summary.keys());
        assertEquals(100, summary.count(fast));
        assertEquals(1_000, summary.max(fast));
        assertTrue(summary.p50(fast) >= 500 && summary.p50(fast) <= 500 * 9 / 8, "p50 " + summary.p50(fast));
        assertEquals(0, summary.count(slow));
        assertTrue(summary.any());
    }

    @Test
    void aSetIsEmptiedBeforeItIsUsedAgain() {
        MinuteHistograms histograms = new MinuteHistograms(1);
        int key = histograms.key(new LatencyKey(1, "strat.decision", 0, "KALSHI", 0, "", 0, "MARKET_DATA"));
        histograms.record(key, 7, T0);
        histograms.roll(T0 + MINUTE_NANOS);
        histograms.roll(T0 + 2 * MINUTE_NANOS);

        MinuteSummary summary = new MinuteSummary(1);
        assertTrue(histograms.summarize(summary));
        assertEquals(T0 + MINUTE_NANOS, summary.minute());
        assertEquals(0, summary.count(key));
        assertFalse(summary.any());
    }

    @Test
    void aQuietStretchClosesTheMinuteThatWasOpen() {
        MinuteHistograms histograms = new MinuteHistograms(1);
        int key = histograms.key(new LatencyKey(1, "strat.queue", 0, "KALSHI", 0, "", 0, "EXEC_REPORT"));
        histograms.record(key, 3, T0 + 10);
        histograms.record(key, 4, T0 + 5 * MINUTE_NANOS + 10);

        MinuteSummary summary = new MinuteSummary(1);
        assertTrue(histograms.summarize(summary));
        assertEquals(T0, summary.minute());
        assertEquals(1, summary.count(key));
    }

    @Test
    void keysPastCapacityAreRefused() {
        MinuteHistograms histograms = new MinuteHistograms(1);
        histograms.key(new LatencyKey(1, "a", 0, "", 0, "", 0, ""));

        assertThrows(IllegalStateException.class, () -> histograms.key(new LatencyKey(1, "b", 0, "", 0, "", 0, "")));
    }

    @Test
    void aSummaryNeverMixesMinutes() throws InterruptedException {
        // Every value recorded in minute m is m, so a summary of minute m that saw another minute's values is torn.
        MinuteHistograms histograms = new MinuteHistograms(8);
        for (int i = 0; i < 8; i++) {
            histograms.key(new LatencyKey(1, "m" + i, 0, "", 0, "", 0, ""));
        }
        AtomicBoolean done = new AtomicBoolean();
        AtomicLong minutes = new AtomicLong();
        Thread owner = new Thread(() -> {
            long minute = 1;
            while (!done.get()) {
                long now = T0 + minute * MINUTE_NANOS;
                for (int r = 0; r < 200; r++) {
                    histograms.record(r & 7, minute, now + r);
                }
                minute++;
                minutes.set(minute);
            }
        });
        owner.start();
        MinuteSummary summary = new MinuteSummary(8);
        int checked = 0;
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline && checked < 20_000) {
            if (!histograms.summarize(summary)) {
                continue;
            }
            long minute = (summary.minute() - T0) / MINUTE_NANOS;
            for (int key = 0; key < 8; key++) {
                assertEquals(25, summary.count(key), "minute " + minute + " key " + key);
                assertEquals(minute, summary.max(key), "minute " + minute + " key " + key);
            }
            checked++;
        }
        done.set(true);
        owner.join();
        assertTrue(checked > 0, "no summary got through in " + minutes.get() + " minutes");
    }

    @Test
    void recordingAndSummarizingAllocateNothing() {
        MinuteHistograms histograms = new MinuteHistograms(16);
        for (int i = 0; i < 16; i++) {
            histograms.key(new LatencyKey(1, "m" + i, 0, "EX", 0, "NEW", 0, "MARKET_DATA"));
        }
        MinuteSummary summary = new MinuteSummary(16);
        long now = T0;
        for (int i = 0; i < 50_000; i++) {
            now += 10_000;
            histograms.record(i & 15, i, now);
            histograms.summarize(summary);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 50_000; i++) {
            now += 10_000;
            histograms.record(i & 15, i, now);
            histograms.summarize(summary);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 2_048, "allocated " + allocated + " bytes");
    }
}
