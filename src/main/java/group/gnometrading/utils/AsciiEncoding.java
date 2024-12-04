package group.gnometrading.utils;

import java.math.BigInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;

public class AsciiEncoding {
    public static final byte ZERO = '0';

    /**
     * US-ASCII-encoded byte representation of the {@link Integer#MIN_VALUE}.
     */
    public static final byte[] MIN_INTEGER_VALUE = String.valueOf(Integer.MIN_VALUE).getBytes(US_ASCII);

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
            1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L,
            10_000_000_000L, 100_000_000_000L, 1_000_000_000_000L, 10_000_000_000_000L, 100_000_000_000_000L,
            1_000_000_000_000_000L, 10_000_000_000_000_000L, 100_000_000_000_000_000L, 1_000_000_000_000_000_000L
    };

    private static final long[] INT_DIGITS = new long[32];
    private static final long[] LONG_DIGITS = new long[64];

    static {
        for (int i = 1; i < 33; i++) {
            final int smallest = 1 << (i - 1);
            final long smallestLog10 = (long) Math.ceil(Math.log10(smallest) / Math.log10(10));
            if (1 == i) {
                INT_DIGITS[i - 1] = 1L << 32;
            } else if (i < 31) {
                INT_DIGITS[i - 1] = (1L << 32) - LONG_POW_10[(int) smallestLog10] + (smallestLog10 << 32);
            } else {
                INT_DIGITS[i - 1] = smallestLog10 << 32;
            }
        }

        final BigInteger tenToNineteen = BigInteger.TEN.pow(19);
        for (int i = 0; i < 64; i++) {
            if (0 == i) {
                LONG_DIGITS[i] = 1L << 52;
            } else {
                final int upper = ((i * 1262611) >> 22) + 1;
                final long correction = upper < LONG_MAX_DIGITS ? LONG_POW_10[upper] >> (i >> 2) :
                        tenToNineteen.shiftRight(i >> 2).longValueExact();
                final long value = ((long)(upper + 1) << 52) - correction;
                LONG_DIGITS[i] = value;
            }
        }
    }

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
        return (int)((value + INT_DIGITS[31 - Integer.numberOfLeadingZeros(value | 1)]) >> 32);
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
        final int floorLog2 = 63 ^ Long.numberOfLeadingZeros(value | 1);
        return (int)((LONG_DIGITS[floorLog2] + (value >> (floorLog2 >> 2))) >> 52);
    }
}
