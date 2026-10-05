package group.gnometrading.concurrent;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

/**
 * Process-wide placement and idle settings for agent threads, installed once at startup before any agent
 * starts. Global so that runners created deep inside gateways pick it up without threading it through
 * every constructor.
 */
public final class AgentRuntime {

    private static final Settings DEFAULTS =
            new Settings(ThreadPinner.NONE, CoreAllocator.NONE, IdlePolicy.BACKOFF, Listener.NONE);

    private static volatile Settings settings = DEFAULTS;

    private AgentRuntime() {}

    /**
     * Installs the placement and idle settings used by every agent thread started afterwards.
     */
    public static void install(
            final ThreadPinner pinner,
            final CoreAllocator allocator,
            final IdlePolicy idlePolicy,
            final Listener listener) {
        settings = new Settings(pinner, allocator, idlePolicy, listener);
    }

    /**
     * Restores the defaults: no pinning and backing off when idle, as if nothing had been installed.
     */
    public static void reset() {
        settings = DEFAULTS;
    }

    /**
     * Places the calling thread for the given agent and returns the idle strategy it should use. Never
     * throws: an agent left unpinned is slower, whereas an agent that fails to start stops trading.
     */
    public static IdleStrategy attach(final GnomeAgent agent) {
        final Settings current = settings;
        Placement placement = Placement.UNPINNED;
        try {
            placement = current.allocator().allocate(agent);
            if (placement.isPinned()) {
                current.pinner().pinCurrentThread(placement.cpus());
            }
            current.listener().onPlacement(agent, placement);
        } catch (Throwable e) {
            current.listener().onPlacementFailure(agent, placement, e);
            placement = Placement.UNPINNED;
        }

        try {
            return current.idlePolicy().idleStrategyFor(agent, placement);
        } catch (Throwable e) {
            current.listener().onPlacementFailure(agent, placement, e);
            return new BackoffIdleStrategy();
        }
    }

    /**
     * Observes placement decisions, mainly so the host process can log them; gnome-core has no logger.
     */
    public interface Listener {

        Listener NONE = new Listener() {};

        /**
         * Called on the agent's thread once it has been placed.
         */
        default void onPlacement(GnomeAgent agent, Placement placement) {}

        /**
         * Called on the agent's thread when placement fails; the thread then runs unpinned.
         */
        default void onPlacementFailure(GnomeAgent agent, Placement placement, Throwable error) {}
    }

    private record Settings(ThreadPinner pinner, CoreAllocator allocator, IdlePolicy idlePolicy, Listener listener) {}
}
