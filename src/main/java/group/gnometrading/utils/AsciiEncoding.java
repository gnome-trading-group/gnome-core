package group.gnometrading.utils;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.math.BigInteger;

public final class AsciiEncoding {

    private static final int BITS_PER_INT = 32;
    private static final int INT_ARRAY_SIZE = 32;
    private static final int BITS_PER_LONG = 64;
    private static final int INT_BIT_COUNT_PLUS_ONE = 33;
    private static final int INT_LEADING_ZERO_OFFSET = 31;
    private static final int LONG_MAX_INDEX = 63;
    private static final int LONG_MANTISSA_BITS = 52;
    private static final int LOG10_MULTIPLIER = 1262611;
    private static final int LOG10_SHIFT = 22;
    private static final int QUARTER_SHIFT = 2;

    public static final byte ZERO = '0';

    /**
     * US-ASCII-encoded byte representation of the {@link Integer#MIN_VALUE}.
     */
    public static final byte[] MIN_INTEGER_VALUE =
            String.valueOf(Integer.MIN_VALUE).getBytes(US_ASCII);

    /**
     * US-ASCII-encoded byte representation of the {@link Long#MIN_VALUE}.
     */
    public static final byte[] MIN_LONG_VALUE = String.valueOf(Long.MIN_VALUE).getBytes(US_ASCII);

    /**
     * Lookup table used for encoding ints/longs as ASCII characters.
     */
    public static final byte[] ASCII_DIGITS = new byte[] {
        '0', '0', '0', '1', '0', '2', '0', '3', '0', '4', '0', '5', '0', '6', '0', '7', '0', '8', '0', '9',
        '1', '0', '1', '1', '1', '2', '1', '3', '1', '4', '1', '5', '1', '6', '1', '7', '1', '8', '1', '9',
        '2', '0', '2', '1', '2', '2', '2', '3', '2', '4', '2', '5', '2', '6', '2', '7', '2', '8', '2', '9',
        '3', '0', '3', '1', '3', '2', '3', '3', '3', '4', '3', '5', '3', '6', '3', '7', '3', '8', '3', '9',
        '4', '0', '4', '1', '4', '2', '4', '3', '4', '4', '4', '5', '4', '6', '4', '7', '4', '8', '4', '9',
        '5', '0', '5', '1', '5', '2', '5', '3', '5', '4', '5', '5', '5', '6', '5', '7', '5', '8', '5', '9',
        '6', '0', '6', '1', '6', '2', '6', '3', '6', '4', '6', '5', '6', '6', '6', '7', '6', '8', '6', '9',
        '7', '0', '7', '1', '7', '2', '7', '3', '7', '4', '7', '5', '7', '6', '7', '7', '7', '8', '7', '9',
        '8', '0', '8', '1', '8', '2', '8', '3', '8', '4', '8', '5', '8', '6', '8', '7', '8', '8', '8', '9',
        '9', '0', '9', '1', '9', '2', '9', '3', '9', '4', '9', '5', '9', '6', '9', '7', '9', '8', '9', '9'
    };

    /**
     * Maximum number of digits in a US-ASCII-encoded long.
     */
    public static final int LONG_MAX_DIGITS = 19;

    /**
     * Power of ten for long values.
     */
    public static final long[] LONG_POW_10 = {
        1L,
        10L,
        100L,
        1_000L,
        10_000L,
        100_000L,
        1_000_000L,
        10_000_000L,
        100_000_000L,
        1_000_000_000L,
        10_000_000_000L,
        100_000_000_000L,
        1_000_000_000_000L,
        10_000_000_000_000L,
        100_000_000_000_000L,
        1_000_000_000_000_000L,
        10_000_000_000_000_000L,
        100_000_000_000_000_000L,
        1_000_000_000_000_000_000L
    };

    private static final long[] INT_DIGITS = new long[INT_ARRAY_SIZE];
    private static final long[] LONG_DIGITS = new long[BITS_PER_LONG];

    static {
        for (int idx = 1; idx < INT_BIT_COUNT_PLUS_ONE; idx++) {
            final int smallest = 1 << (idx - 1);
            final long smallestLog10 = (long) Math.ceil(Math.log10(smallest) / Math.log10(10));
            if (1 == idx) {
                INT_DIGITS[idx - 1] = 1L << BITS_PER_INT;
            } else if (idx < INT_LEADING_ZERO_OFFSET) {
                INT_DIGITS[idx - 1] =
                        (1L << BITS_PER_INT) - LONG_POW_10[(int) smallestLog10] + (smallestLog10 << BITS_PER_INT);
            } else {
                INT_DIGITS[idx - 1] = smallestLog10 << BITS_PER_INT;
            }
        }

        final BigInteger tenToNineteen = BigInteger.TEN.pow(LONG_MAX_DIGITS);
        for (int idx = 0; idx < BITS_PER_LONG; idx++) {
            if (0 == idx) {
                LONG_DIGITS[idx] = 1L << LONG_MANTISSA_BITS;
            } else {
                final int upper = ((idx * LOG10_MULTIPLIER) >> LOG10_SHIFT) + 1;
                final long correction = upper < LONG_MAX_DIGITS
                        ? LONG_POW_10[upper] >> (idx >> QUARTER_SHIFT)
                        : tenToNineteen.shiftRight(idx >> QUARTER_SHIFT).longValueExact();
                final long value = ((long) (upper + 1) << LONG_MANTISSA_BITS) - correction;
                LONG_DIGITS[idx] = value;
            }
        }
    }

    private AsciiEncoding() {}

    /**
     * Count number of digits in a positive {@code int} value.
     *
     * <p>Implementation is based on the Kendall Willets' idea as presented in the
     * <a href="https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/"
     * target="_blank">Computing the number of digits of an integer even faster</a> blog post.
     *
     * @param value to count number of digits int.
     * @return number of digits in a number, e.g. if input value is {@code 123} then the result will be {@code 3}.
     */
    public static int digitCount(final int value) {
        return (int) ((value + INT_DIGITS[INT_LEADING_ZERO_OFFSET - Integer.numberOfLeadingZeros(value | 1)])
                >> BITS_PER_INT);
    }

    /**
     * Count number of digits in a positive {@code long} value.
     *
     * <p>Implementation is based on the Kendall Willets' idea as presented in the
     * <a href="https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/"
     * target="_blank">Computing the number of digits of an integer even faster</a> blog post.
     *
     * @param value to count number of digits int.
     * @return number of digits in a number, e.g. if input value is {@code 12345678909876} then the result will be
     * {@code 14}.
     */
    public static int digitCount(final long value) {
        final int floorLog2 = LONG_MAX_INDEX ^ Long.numberOfLeadingZeros(value | 1);
        return (int) ((LONG_DIGITS[floorLog2] + (value >> (floorLog2 >> QUARTER_SHIFT))) >> LONG_MANTISSA_BITS);
    }
}
