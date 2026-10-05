package group.gnometrading.concurrent;

/**
 * Where an agent's thread runs.
 *
 * @param cpus logical CPUs the thread is restricted to; empty means it keeps the affinity it inherited.
 *     Shared between placements, so callers must not mutate it.
 * @param dedicated whether the thread owns its core, which is what makes busy-spinning safe
 */
public record Placement(int[] cpus, boolean dedicated) {

    public static final Placement UNPINNED = new Placement(new int[0], false);

    /**
     * Whether this placement restricts the thread's CPUs at all.
     */
    public boolean isPinned() {
        return cpus.length > 0;
    }
}
