package group.gnometrading.concurrent;

/**
 * How latency-sensitive an agent's thread is. Only takes effect once an {@link AgentRuntime} is installed;
 * otherwise every agent backs off when idle.
 */
public enum ThreadProfile {
    /** On the tick-to-trade path: eligible for a dedicated isolated core. */
    HOT_PATH,
    /** Supervisory or periodic work that should give the CPU back when idle. */
    BACKGROUND
}
