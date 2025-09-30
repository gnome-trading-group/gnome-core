package group.gnometrading.concurrent;

public interface GnomeAgent {
    /**
     * Called once before the agent starts processing.
     */
    default void onStart() throws Exception {}

    /**
     * Called once per loop iteration. Return number of work items processed.
     */
    int doWork() throws Exception;

    /**
     * Called once after the loop ends.
     */
    default void onClose() {}

    /**
     * Used for diagnostics/logging/thread management.
     */
    default String roleName() {
        return this.getClass().getSimpleName();
    }
}
