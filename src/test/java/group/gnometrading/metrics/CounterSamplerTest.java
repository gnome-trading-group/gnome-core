package group.gnometrading.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CounterSamplerTest {

    private final SampleBuffer samples = new SampleBuffer(8192, 16, 1024);
    private final MetricCounters oms = new MetricCounters(3);
    private final int orders = oms.counter("orders");
    private final int working = oms.gauge("workingOrders");
    private final int lag = oms.peak("lagPeak");
    private final MetricCounters strategy = new MetricCounters(1);
    private final int intents = strategy.counter("intents");
    private final CounterSampler sampler =
            new CounterSampler(samples, "session.activity", List.of("oms", "strategy"), List.of(oms, strategy), 60_000);

    private String take() {
        ByteBuffer out = ByteBuffer.allocate(8192);
        int written = samples.writeTo(out, 16);
        samples.acknowledge(written);
        return new String(out.array(), 0, out.position(), StandardCharsets.UTF_8);
    }

    @Test
    void countsAreReportedAsTheirChangeGaugesAsTheyStandAndPeaksThenReset() {
        oms.add(orders, 5);
        oms.set(working, 3);
        oms.max(lag, 40);
        sampler.sample(1_000);
        assertEquals(
                "{\"kind\":\"session.activity\",\"tsMs\":1000,\"data\":{\"intervalMs\":60000,"
                        + "\"oms\":{\"workingOrders\":3,\"lagPeak\":40},\"strategy\":{}}}",
                take(),
                "the first sample has no change to report");

        oms.add(orders, 2);
        oms.max(lag, 7);
        strategy.increment(intents);
        sampler.sample(61_000);
        assertEquals(
                "{\"kind\":\"session.activity\",\"tsMs\":61000,\"data\":{\"intervalMs\":60000,"
                        + "\"oms\":{\"orders\":2,\"workingOrders\":3,\"lagPeak\":7},\"strategy\":{\"intents\":1}}}",
                take());
    }

    @Test
    void itSamplesOncePerInterval() {
        assertTrue(sampler.sampleIfDue(0));
        assertFalse(sampler.sampleIfDue(59_999));
        assertTrue(sampler.sampleIfDue(60_000));
    }

    @Test
    void samplingAllocatesNothing() {
        ByteBuffer out = ByteBuffer.allocate(8192);
        for (int i = 0; i < 100_000; i++) {
            cycle(out, i);
        }
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 10_000; i++) {
            cycle(out, i);
        }
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertTrue(allocated < 2_048, "allocated " + allocated + " bytes");
    }

    private void cycle(ByteBuffer out, long i) {
        oms.increment(orders);
        oms.max(lag, i);
        sampler.sample(i);
        out.clear();
        samples.acknowledge(samples.writeTo(out, 16));
    }
}
