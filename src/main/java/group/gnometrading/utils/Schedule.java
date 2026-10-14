package group.gnometrading.utils;

import java.util.concurrent.atomic.AtomicLong;
import org.agrona.concurrent.EpochClock;

public final class Schedule {
    private final EpochClock clock;
    private final long repeatMillis;
    private final Runnable task;

    private volatile long nextFireTime;
    // Kept apart from the timer so nothing check() writes can erase a request: a force made while the task runs,
    // even one forced itself, is still pending at the next check.
    private final AtomicLong forceRequests = new AtomicLong();
    private volatile long forceRequestsHandled;

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
        this.forceRequests.incrementAndGet();
    }

    /**
     * Checks if the schedule should fire now. If so, runs the task and
     * reschedules for the next interval.
     */
    public void check() {
        final long now = clock.time();
        final long requests = this.forceRequests.get();
        if (now >= this.nextFireTime || requests != this.forceRequestsHandled) {
            this.forceRequestsHandled = requests;
            try {
                task.run();
            } finally {
                this.nextFireTime = now + repeatMillis;
            }
        }
    }

    /**
     * @return time in millis until the next scheduled run
     */
    public long millisUntilNext() {
        if (this.forceRequests.get() != this.forceRequestsHandled) {
            return 0;
        }
        final long now = clock.time();
        return Math.max(0, nextFireTime - now);
    }
}
