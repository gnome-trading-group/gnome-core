package group.gnometrading.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {

    private static final GnomeAgent HOT = new GnomeAgent() {
        @Override
        public int doWork() {
            return 0;
        }

        @Override
        public ThreadProfile threadProfile() {
            return ThreadProfile.HOT_PATH;
        }
    };

    @AfterEach
    void tearDown() {
        AgentRuntime.reset();
    }

    @Test
    void testDefaultsLeaveThreadUnpinnedAndBackingOff() {
        assertInstanceOf(BackoffIdleStrategy.class, AgentRuntime.attach(HOT));
    }

    @Test
    void testPinsThreadToAllocatedCpus() {
        List<int[]> pinned = new ArrayList<>();
        Placement placement = new Placement(new int[] {4}, true);
        AgentRuntime.install(pinned::add, agent -> placement, IdlePolicy.lowLatency(), AgentRuntime.Listener.NONE);

        IdleStrategy idle = AgentRuntime.attach(HOT);

        assertEquals(1, pinned.size());
        assertArrayEquals(new int[] {4}, pinned.get(0));
        assertInstanceOf(NoOpIdleStrategy.class, idle);
    }

    @Test
    void testUnpinnedPlacementDoesNotCallPinner() {
        List<int[]> pinned = new ArrayList<>();
        AgentRuntime.install(pinned::add, CoreAllocator.NONE, IdlePolicy.BACKOFF, AgentRuntime.Listener.NONE);

        AgentRuntime.attach(HOT);

        assertTrue(pinned.isEmpty());
    }

    @Test
    void testPinFailureRunsUnpinnedAndDoesNotSpin() {
        RecordingListener listener = new RecordingListener();
        ThreadPinner failing = cpus -> {
            throw new IllegalStateException("sched_setaffinity failed");
        };
        AgentRuntime.install(failing, agent -> new Placement(new int[] {4}, true), IdlePolicy.lowLatency(), listener);

        IdleStrategy idle = AgentRuntime.attach(HOT);

        assertInstanceOf(BackoffIdleStrategy.class, idle);
        assertEquals(1, listener.failures.size());
        assertTrue(listener.placements.isEmpty());
    }

    @Test
    void testIdlePolicyFailureFallsBackToBackoff() {
        RecordingListener listener = new RecordingListener();
        IdlePolicy failing = (agent, placement) -> {
            throw new IllegalStateException("boom");
        };
        AgentRuntime.install(ThreadPinner.NONE, CoreAllocator.NONE, failing, listener);

        assertInstanceOf(BackoffIdleStrategy.class, AgentRuntime.attach(HOT));
        assertEquals(1, listener.failures.size());
    }

    @Test
    void testListenerSeesPlacement() {
        RecordingListener listener = new RecordingListener();
        Placement placement = new Placement(new int[] {0, 1}, false);
        AgentRuntime.install(ThreadPinner.NONE, agent -> placement, IdlePolicy.BACKOFF, listener);

        AgentRuntime.attach(HOT);

        assertEquals(List.of(placement), listener.placements);
    }

    @Test
    void testResetRestoresDefaults() {
        AgentRuntime.install(
                ThreadPinner.NONE,
                agent -> new Placement(new int[] {4}, true),
                IdlePolicy.standard(),
                AgentRuntime.Listener.NONE);

        AgentRuntime.reset();

        assertInstanceOf(BackoffIdleStrategy.class, AgentRuntime.attach(HOT));
    }

    @Test
    void testRunnerPinsOnItsOwnThreadAndUsesIdleStrategy() throws Exception {
        List<Thread> pinnedThreads = new CopyOnWriteArrayList<>();
        List<Integer> idledWith = new CopyOnWriteArrayList<>();
        CountDownLatch idled = new CountDownLatch(3);
        IdleStrategy recording = new IdleStrategy() {
            @Override
            public void idle(int workCount) {
                idledWith.add(workCount);
                idled.countDown();
            }

            @Override
            public void idle() {}

            @Override
            public void reset() {}
        };
        AgentRuntime.install(
                cpus -> pinnedThreads.add(Thread.currentThread()),
                agent -> new Placement(new int[] {4}, true),
                (agent, placement) -> recording,
                AgentRuntime.Listener.NONE);

        int[] calls = {0};
        GnomeAgent agent = () -> calls[0]++ == 0 ? 5 : 0;
        GnomeAgentRunner runner = new GnomeAgentRunner(agent, error -> {});
        Thread thread = GnomeAgentRunner.startOnThread(runner);

        assertTrue(idled.await(5, TimeUnit.SECONDS));
        runner.close();

        assertEquals(List.of(thread), pinnedThreads);
        assertEquals(5, idledWith.get(0));
        assertEquals(0, idledWith.get(1));
    }

    private static final class RecordingListener implements AgentRuntime.Listener {
        private final List<Placement> placements = new CopyOnWriteArrayList<>();
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();

        @Override
        public void onPlacement(GnomeAgent agent, Placement placement) {
            placements.add(placement);
        }

        @Override
        public void onPlacementFailure(GnomeAgent agent, Placement placement, Throwable error) {
            failures.add(error);
        }
    }
}
