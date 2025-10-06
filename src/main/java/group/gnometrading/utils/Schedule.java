package group.gnometrading.utils;

import org.agrona.concurrent.EpochClock;

public final class Schedule {
    private final EpochClock clock;
    private final long repeatMillis;
    private final Runnable task;

    private long nextFireTime;

    public Schedule(EpochClock clock, long repeatMillis, Runnable task) {
        if (repeatMillis <= 0) {
            throw new IllegalArgumentException("repeatMillis must be > 0");
        }
        this.clock = clock;
        this.repeatMillis = repeatMillis;
        this.task = task;
    }

    /**
     * Starts the schedule by setting the next fire time.
     */
    public void start() {
        final long now = clock.time();
        this.nextFireTime = now + repeatMillis;
    }

    /**
     * Forces the schedule to fire on the next check.
     */
    public void forceTrigger() {
        this.nextFireTime = 0;
    }

    /**
     * Checks if the schedule should fire now. If so, runs the task and
     * reschedules for the next interval.
     */
    public void check() {
        final long now = clock.time();
        if (now >= nextFireTime) {
            try {
                task.run();
            } finally {
                // Ensure we always move forward, even if the task threw
                nextFireTime = now + repeatMillis;
            }
        }
    }

    /**
     * @return time in millis until the next scheduled run
     */
    public long millisUntilNext() {
        final long now = clock.time();
        return Math.max(0, nextFireTime - now);
    }
}