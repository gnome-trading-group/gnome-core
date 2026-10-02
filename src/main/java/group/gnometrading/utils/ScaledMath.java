package group.gnometrading.utils;

/**
 * Exact arithmetic on fixed-point values whose intermediate products do not fit in a {@code long}.
 *
 * <p>Multiplying a scaled price by a scaled quantity overflows long before the true result does: at a
 * price scale of 1e9 and a quantity scale of 1e6, {@code price * qty} wraps once a notional passes about
 * $9,223, even though the notional itself fits easily in price units. These methods carry the product in
 * 128 bits so only the final result has to fit. They never allocate and never throw on overflow: a
 * result outside the {@code long} range saturates, so limit checks built on them fail closed.
 */
public final class ScaledMath {

    private static final long HALF_WORD = 1L << 32;
    private static final long LOW_WORD_MASK = 0xFFFFFFFFL;
    private static final int SIGN_SHIFT = 63;

    private ScaledMath() {}

    /**
     * Returns {@code first * second / divisor} computed exactly, truncated toward zero like Java integer division, or
     * {@link Long#MAX_VALUE} / {@link Long#MIN_VALUE} when the result does not fit in a {@code long}.
     *
     * @throws IllegalArgumentException if {@code divisor} is not positive
     */
    public static long multiplyDivide(final long first, final long second, final long divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        final boolean negative = (first < 0) != (second < 0);
        // Math.abs(Long.MIN_VALUE) is Long.MIN_VALUE, which is 2^63 when read as unsigned: still correct.
        final long absA = Math.abs(first);
        final long absB = Math.abs(second);

        final long high = unsignedMultiplyHigh(absA, absB);
        final long low = absA * absB;
        if (Long.compareUnsigned(high, divisor) >= 0) {
            return negative ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        final long quotient = high == 0 ? Long.divideUnsigned(low, divisor) : divideUnsigned128(high, low, divisor);

        if (quotient >= 0) {
            return negative ? -quotient : quotient;
        }
        // The magnitude is at least 2^63: only -2^63 itself is representable.
        return negative ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** Math.unsignedMultiplyHigh arrived in Java 18; this is its documented equivalent. */
    private static long unsignedMultiplyHigh(final long left, final long right) {
        return Math.multiplyHigh(left, right) + ((left >> SIGN_SHIFT) & right) + ((right >> SIGN_SHIFT) & left);
    }

    /**
     * Divides the unsigned 128-bit value {@code high:low} by {@code divisor}, where {@code high &lt;
     * divisor} so the quotient fits in 64 bits. Knuth's algorithm D specialised to two 32-bit digits
     * (Hacker's Delight, {@code divlu}).
     */
    private static long divideUnsigned128(final long high, final long low, final long divisor) {
        final int shift = Long.numberOfLeadingZeros(divisor);
        final long normalizedDivisor = divisor << shift;
        final long divisorHigh = normalizedDivisor >>> 32;
        final long divisorLow = normalizedDivisor & LOW_WORD_MASK;

        final long numeratorHigh = shift == 0 ? high : (high << shift) | (low >>> (64 - shift));
        final long numeratorLow = low << shift;
        final long numeratorLowHigh = numeratorLow >>> 32;
        final long numeratorLowLow = numeratorLow & LOW_WORD_MASK;

        final long quotientHigh = quotientDigit(numeratorHigh, numeratorLowHigh, divisorHigh, divisorLow);
        final long remainder = numeratorHigh * HALF_WORD + numeratorLowHigh - quotientHigh * normalizedDivisor;
        final long quotientLow = quotientDigit(remainder, numeratorLowLow, divisorHigh, divisorLow);
        return quotientHigh * HALF_WORD + quotientLow;
    }

    private static long quotientDigit(
            final long numerator, final long nextDigit, final long divisorHigh, final long divisorLow) {
        long digit = Long.divideUnsigned(numerator, divisorHigh);
        long remainder = numerator - digit * divisorHigh;
        while (Long.compareUnsigned(digit, HALF_WORD) >= 0
                || Long.compareUnsigned(digit * divisorLow, remainder * HALF_WORD + nextDigit) > 0) {
            digit--;
            remainder += divisorHigh;
            if (Long.compareUnsigned(remainder, HALF_WORD) >= 0) {
                break;
            }
        }
        return digit;
    }
}
