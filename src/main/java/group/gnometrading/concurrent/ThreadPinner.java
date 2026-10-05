package group.gnometrading.concurrent;

/**
 * Restricts the calling thread to a set of CPUs. Implementations live outside gnome-core so that the native
 * dependency only reaches processes that actually pin threads.
 */
@FunctionalInterface
public interface ThreadPinner {

    ThreadPinner NONE = cpus -> {};

    /**
     * Restricts the calling thread to the given logical CPUs.
     *
     * @param cpus logical CPU ids, never empty
     */
    void pinCurrentThread(int[] cpus);
}
