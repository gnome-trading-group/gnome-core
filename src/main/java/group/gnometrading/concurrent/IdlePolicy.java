package group.gnometrading.concurrent;

import java.util.concurrent.TimeUnit;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingIdleStrategy;

/**
 * Chooses how an agent's thread behaves when a {@code doWork()} pass finds nothing to do.
 */
@FunctionalInterface
public interface IdlePolicy {

    /**
     * The default when no runtime is installed: every agent backs off when idle (spin, then yield, then park up to
     * 1ms). Busy-spinning burns a core per thread, so only a process that has dedicated cores to spend opts into it.
     */
    IdlePolicy BACKOFF = (agent, placement) -> new BackoffIdleStrategy();

    /**
     * Returns the idle strategy for one agent thread. Idle strategies can be stateful, so implementations
     * must not share a stateful instance between threads.
     */
    IdleStrategy idleStrategyFor(GnomeAgent agent, Placement placement);

    /**
     * Only a hot agent that owns its core spins. A hot agent without one backs off rather than spin on a
     * shared core, and background agents sleep so the shared cores stay free for the OS and the GC.
     */
    static IdlePolicy lowLatency() {
        return (agent, placement) -> {
            if (agent.threadProfile() == ThreadProfile.HOT_PATH) {
                return placement.dedicated() ? NoOpIdleStrategy.INSTANCE : new BackoffIdleStrategy();
            }
            return newBackgroundIdleStrategy();
        };
    }

    /**
     * For strategies that are not latency-sensitive: nothing owns a core, so nothing spins.
     */
    static IdlePolicy standard() {
        return (agent, placement) -> agent.threadProfile() == ThreadProfile.HOT_PATH
                ? new BackoffIdleStrategy()
                : newBackgroundIdleStrategy();
    }

    private static IdleStrategy newBackgroundIdleStrategy() {
        return new SleepingIdleStrategy(1, TimeUnit.MILLISECONDS);
    }
}
