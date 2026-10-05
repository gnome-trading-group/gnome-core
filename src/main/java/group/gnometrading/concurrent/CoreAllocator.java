package group.gnometrading.concurrent;

/**
 * Decides which CPUs an agent's thread runs on.
 */
@FunctionalInterface
public interface CoreAllocator {

    CoreAllocator NONE = agent -> Placement.UNPINNED;

    /**
     * Chooses the placement for an agent. Called once from each agent's own thread as it starts, so
     * implementations must be thread-safe.
     */
    Placement allocate(GnomeAgent agent);
}
