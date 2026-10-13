package group.gnometrading.utils;

import java.nio.ByteBuffer;

/**
 * Reads US-ASCII text in place, from a range of a {@link ByteBuffer} or byte array, without allocating or
 * throwing on bad input. Ranges are {@code [start, end)} in absolute indexes; a buffer's position is never moved.
 */
public final class AsciiParsing {

    private static final byte LOWERCASE_BIT = 0x20;
    private static final int RADIX = 10;

    private AsciiParsing() {}

    public static boolean isDigit(final byte value) {
        return value >= '0' && value <= '9';
    }

    /** Lowercases A-Z only, so that bytes like {@code '-'} and {@code '\r'}, which differ by the case bit, stay apart. */
    public static byte toLowerCase(final byte value) {
        return value >= 'A' && value <= 'Z' ? (byte) (value | LOWERCASE_BIT) : value;
    }

    /** Space or tab: the optional whitespace HTTP allows around a header value. */
    public static boolean isSpaceOrTab(final byte value) {
        return value == ' ' || value == '\t';
    }

    /** Whether the range holds {@code lowercase}, ignoring the range's case; {@code lowercase} must be lowercase. */
    public static boolean equalsIgnoreCase(
            final ByteBuffer buffer, final int start, final int end, final byte[] lowercase) {
        if (end - start != lowercase.length) {
            return false;
        }
        for (int i = 0; i < lowercase.length; i++) {
            if (toLowerCase(buffer.get(start + i)) != lowercase[i]) {
                return false;
            }
        }
        return true;
    }

    /** As {@link #equalsIgnoreCase(ByteBuffer, int, int, byte[])}. */
    public static boolean equalsIgnoreCase(final byte[] bytes, final int start, final int end, final byte[] lowercase) {
        if (end - start != lowercase.length) {
            return false;
        }
        for (int i = 0; i < lowercase.length; i++) {
            if (toLowerCase(bytes[start + i]) != lowercase[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where {@code lowercase} first appears in the range, ignoring the range's case; -1 if it doesn't.
     * {@code lowercase} must be lowercase.
     */
    public static int indexOfIgnoreCase(
            final ByteBuffer buffer, final int start, final int end, final byte[] lowercase) {
        for (int i = start; i + lowercase.length <= end; i++) {
            if (equalsIgnoreCase(buffer, i, i + lowercase.length, lowercase)) {
                return i;
            }
        }
        return -1;
    }

    /** Where {@code pattern} first appears, byte for byte, in the range; -1 if it doesn't. */
    public static int indexOf(final ByteBuffer buffer, final int start, final int end, final byte[] pattern) {
        for (int i = start; i + pattern.length <= end; i++) {
            if (matchesAt(buffer, i, pattern)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(final ByteBuffer buffer, final int at, final byte[] pattern) {
        for (int j = 0; j < pattern.length; j++) {
            if (buffer.get(at + j) != pattern[j]) {
                return false;
            }
        }
        return true;
    }

    /** The first index in the range that isn't a digit; {@code end} if there is none. */
    public static int skipDigits(final ByteBuffer buffer, final int start, final int end) {
        int index = start;
        while (index < end && isDigit(buffer.get(index))) {
            index++;
        }
        return index;
    }

    /** The first index in the range that isn't a space or tab; {@code end} if there is none. */
    public static int skipSpacesAndTabs(final ByteBuffer buffer, final int start, final int end) {
        int index = start;
        while (index < end && isSpaceOrTab(buffer.get(index))) {
            index++;
        }
        return index;
    }

    /** The end of the range once trailing spaces and tabs are dropped; {@code start} if it is all whitespace. */
    public static int trimSpacesAndTabs(final ByteBuffer buffer, final int start, final int end) {
        int index = end;
        while (index > start && isSpaceOrTab(buffer.get(index - 1))) {
            index--;
        }
        return index;
    }

    /**
     * The range as a whole number of decimal digits.
     *
     * @return the value, or {@code invalid} if the range is empty, holds anything but digits (a sign included) or
     *     overflows a long
     */
    public static long parseNaturalLong(final ByteBuffer buffer, final int start, final int end, final long invalid) {
        if (start >= end) {
            return invalid;
        }
        long value = 0;
        for (int i = start; i < end; i++) {
            final byte at = buffer.get(i);
            if (!isDigit(at)) {
                return invalid;
            }
            final int digit = at - '0';
            if (value > (Long.MAX_VALUE - digit) / RADIX) {
                return invalid;
            }
            value = value * RADIX + digit;
        }
        return value;
    }

    /** As {@link #parseNaturalLong(ByteBuffer, int, int, long)}. */
    public static long parseNaturalLong(final byte[] bytes, final int start, final int end, final long invalid) {
        if (start >= end) {
            return invalid;
        }
        long value = 0;
        for (int i = start; i < end; i++) {
            final byte at = bytes[i];
            if (!isDigit(at)) {
                return invalid;
            }
            final int digit = at - '0';
            if (value > (Long.MAX_VALUE - digit) / RADIX) {
                return invalid;
            }
            value = value * RADIX + digit;
        }
        return value;
    }

    /**
     * The range as a non-negative decimal, such as {@code 5}, {@code 5.} or {@code 5.25}, times {@code scale}: with a
     * scale of 100, {@code 5.25} is 525. Digits finer than the scale can hold are dropped, not rounded.
     *
     * @param scale a power of ten
     * @return the scaled value, or {@code invalid} if the range has no whole digits, holds anything but digits and one
     *     point, or overflows a long
     */
    public static long parseNaturalFixedPoint(
            final ByteBuffer buffer, final int start, final int end, final long scale, final long invalid) {
        final int point = skipDigits(buffer, start, end);
        if (point == start || (point < end && buffer.get(point) != '.')) {
            return invalid;
        }
        final long whole = parseNaturalLong(buffer, start, point, -1);
        if (whole < 0 || whole > Long.MAX_VALUE / scale) {
            return invalid;
        }
        long fraction = 0;
        long place = scale / RADIX;
        for (int i = point + 1; i < end; i++) {
            final byte at = buffer.get(i);
            if (!isDigit(at)) {
                return invalid;
            }
            fraction += (at - '0') * place;
            place /= RADIX;
        }
        final long scaled = whole * scale;
        return scaled > Long.MAX_VALUE - fraction ? invalid : scaled + fraction;
    }
}
