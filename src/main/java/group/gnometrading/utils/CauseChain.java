package group.gnometrading.utils;

/** Formats an exception with its causes, since the outermost one alone often hides what failed. */
public final class CauseChain {

    private static final int MAX_DEPTH = 8;

    private CauseChain() {}

    /** Each cause's type and message, outermost first, e.g. {@code RuntimeException: x <- IOException: y}. */
    public static String of(final Throwable error) {
        final StringBuilder chain = new StringBuilder();
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_DEPTH; depth++) {
            if (depth > 0) {
                chain.append(" <- ");
            }
            chain.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                chain.append(": ").append(cause.getMessage());
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return chain.toString();
    }
}
