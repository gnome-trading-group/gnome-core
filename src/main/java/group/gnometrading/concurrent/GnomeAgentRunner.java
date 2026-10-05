package group.gnometrading.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import org.agrona.ErrorHandler;
import org.agrona.concurrent.IdleStrategy;

public final class GnomeAgentRunner implements Runnable, AutoCloseable {

    public static final Thread TOMBSTONE = new Thread();

    private volatile boolean running = true;
    private volatile boolean closed = false;

    private final GnomeAgent agent;
    private final ErrorHandler errorHandler;
    private final AtomicReference<Thread> thread = new AtomicReference<>();
    // Assigned on the agent's own thread when run() starts, before the work loop reads it.
    private IdleStrategy idleStrategy;

    public GnomeAgentRunner(GnomeAgent agent, ErrorHandler errorHandler) {
        this.agent = agent;
        this.errorHandler = errorHandler;
    }

    public static Thread startOnThread(final GnomeAgentRunner agentRunner) {
        Thread thread = new Thread(agentRunner);
        thread.setName(agentRunner.agent.roleName());
        thread.start();
        return thread;
    }

    public Thread getThread() {
        return this.thread.get();
    }

    public GnomeAgent getAgent() {
        return this.agent;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void run() {
        try {
            if (!this.thread.compareAndSet(null, Thread.currentThread())) {
                return;
            }

            this.idleStrategy = AgentRuntime.attach(this.agent);

            try {
                this.agent.onStart();
            } catch (Throwable e) {
                this.errorHandler.onError(e);
                this.running = false;
            }

            this.workLoop();

            try {
                this.agent.onClose();
            } catch (Throwable e) {
                this.errorHandler.onError(e);
            }
        } finally {
            this.closed = true;
        }
    }

    private void workLoop() {
        while (this.running) {
            this.idleStrategy.idle(this.doWork());
        }
    }

    private int doWork() {
        try {
            int workCount = agent.doWork();
            if (workCount <= 0 && Thread.currentThread().isInterrupted()) {
                this.running = false;
            }
            return workCount;
        } catch (InterruptedException e) {
            this.running = false;
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            if (Thread.currentThread().isInterrupted()) {
                this.running = false;
            }

            this.handleError(e);

            if (this.running && Thread.currentThread().isInterrupted()) {
                this.running = false;
            }
        }
        return 0;
    }

    private void handleError(Throwable error) {
        if (this.errorHandler != null) {
            this.errorHandler.onError(error);
        }
    }

    @Override
    public void close() throws Exception {
        this.running = false;

        Thread currentThread = this.thread.getAndSet(TOMBSTONE);
        if (currentThread == null) { // We never started running
            try {
                this.agent.onClose();
            } catch (Throwable e) {
                this.errorHandler.onError(e);
            } finally {
                this.closed = true;
            }
        } else if (TOMBSTONE != currentThread) {
            if (this.closed) {
                return;
            }

            // Closed is set to true in the work loop
            currentThread.join();
        }
    }
}
