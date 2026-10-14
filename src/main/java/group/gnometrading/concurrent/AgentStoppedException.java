package group.gnometrading.concurrent;

/**
 * Thrown from inside an agent's work when what it was waiting on has been stopped, so the agent can't go on. The
 * runner treats it like an interrupt: it leaves the work loop and closes the agent, without reporting an error. A stop
 * is how shutdown reaches a thread blocked on something no one will ever release, and reporting it as an error could
 * make an error handler that exits the process do so in the middle of shutdown.
 *
 * <p>Thrown on paths that must not allocate, so subclasses are expected to be shared instances without a stack trace.
 */
public class AgentStoppedException extends RuntimeException {

    protected AgentStoppedException(final String message) {
        super(message, null, false, false);
    }
}
