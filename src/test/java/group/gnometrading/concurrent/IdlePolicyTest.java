package group.gnometrading.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;
import org.junit.jupiter.api.Test;

class IdlePolicyTest {

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

    private static final GnomeAgent BACKGROUND = () -> 0;

    private static final Placement DEDICATED = new Placement(new int[] {2}, true);
    private static final Placement SHARED = new Placement(new int[] {0, 1}, false);

    @Test
    void testDefaultNeverSpins() {
        assertInstanceOf(BackoffIdleStrategy.class, IdlePolicy.BACKOFF.idleStrategyFor(HOT, DEDICATED));
        assertInstanceOf(BackoffIdleStrategy.class, IdlePolicy.BACKOFF.idleStrategyFor(BACKGROUND, SHARED));
        assertNotSame(IdlePolicy.BACKOFF.idleStrategyFor(HOT, SHARED), IdlePolicy.BACKOFF.idleStrategyFor(HOT, SHARED));
    }

    @Test
    void testLowLatencySpinsOnlyOnDedicatedCore() {
        IdlePolicy policy = IdlePolicy.lowLatency();

        assertInstanceOf(NoOpIdleStrategy.class, policy.idleStrategyFor(HOT, DEDICATED));
        assertInstanceOf(BackoffIdleStrategy.class, policy.idleStrategyFor(HOT, SHARED));
        assertInstanceOf(BackoffIdleStrategy.class, policy.idleStrategyFor(HOT, Placement.UNPINNED));
        assertInstanceOf(SleepingIdleStrategy.class, policy.idleStrategyFor(BACKGROUND, SHARED));
    }

    @Test
    void testStandardNeverSpins() {
        IdlePolicy policy = IdlePolicy.standard();

        assertInstanceOf(BackoffIdleStrategy.class, policy.idleStrategyFor(HOT, Placement.UNPINNED));
        assertInstanceOf(SleepingIdleStrategy.class, policy.idleStrategyFor(BACKGROUND, Placement.UNPINNED));
    }

    @Test
    void testStatefulStrategiesAreNotShared() {
        IdlePolicy policy = IdlePolicy.lowLatency();

        assertNotSame(policy.idleStrategyFor(HOT, SHARED), policy.idleStrategyFor(HOT, SHARED));
        assertNotSame(policy.idleStrategyFor(BACKGROUND, SHARED), policy.idleStrategyFor(BACKGROUND, SHARED));
    }
}
