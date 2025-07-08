package group.gnometrading.utils;

import group.gnometrading.strings.GnomeString;
import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

import static group.gnometrading.utils.AsciiEncoding.*;

public final class ByteBufferUtils {

    private static final int CACHE_LINE_SIZE = 64;
    private static final int ALIGNMENT_MASK = CACHE_LINE_SIZE - 1;

    private ByteBufferUtils() {}

    /**
     * Allocates a 64-byte aligned direct ByteBuffer for optimal HFT performance.
     *
     * @param size The required buffer size
     * @return A 64-byte aligned direct ByteBuffer
     */
    public static ByteBuffer allocateAlignedBuffer(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }

        int alignedSize = size + CACHE_LINE_SIZE;
        ByteBuffer buffer = ByteBuffer.allocateDirect(alignedSize);

        long alignmentOffset = calculateAlignmentPadding(buffer);
        if (alignmentOffset > 0) {
            buffer.position((int) alignmentOffset);
            buffer.limit((int) alignmentOffset + size);
            return buffer.slice();
        }

        buffer.limit(size);
        return buffer;
    }

    /**
     * Creates an UnsafeBuffer with 64-byte alignment for optimal HFT performance.
     *
     * @param size The required buffer size
     * @return A 64-byte aligned UnsafeBuffer
     */
    public static UnsafeBuffer createAlignedUnsafeBuffer(int size) {
        return new UnsafeBuffer(allocateAlignedBuffer(size));
    }

    /**
     * Verifies that a buffer is 64-byte aligned.
     *
     * @param buffer The buffer to check
     * @return true if the buffer is 64-byte aligned
     */
    public static boolean isAligned(ByteBuffer buffer) {
        long address = BufferUtil.address(buffer);
        return (address & ALIGNMENT_MASK) == 0;
    }

    /**
     * Verifies that an UnsafeBuffer is 64-byte aligned.
     *
     * @param buffer The buffer to check
     * @return true if the buffer is 64-byte aligned
     */
    public static boolean isAligned(UnsafeBuffer buffer) {
        long address = buffer.addressOffset();
        return (address & ALIGNMENT_MASK) == 0;
    }

    /**
     * Calculates the padding needed to align a buffer to 64 bytes.
     *
     * @param buffer The buffer to calculate padding for
     * @return The number of bytes needed for alignment
     */
    public static int calculateAlignmentPadding(ByteBuffer buffer) {
        long address = BufferUtil.address(buffer);
        return (int) ((CACHE_LINE_SIZE - (address & ALIGNMENT_MASK)) & ALIGNMENT_MASK);
    }

    /**
     * Calculates the padding needed to align an UnsafeBuffer to 64 bytes.
     *
     * @param buffer The buffer to calculate padding for
     * @return The number of bytes needed for alignment
     */
    public static int calculateAlignmentPadding(UnsafeBuffer buffer) {
        long address = buffer.addressOffset();
        return (int) ((CACHE_LINE_SIZE - (address & ALIGNMENT_MASK)) & ALIGNMENT_MASK);
    }

    /**
     * Puts a string into a buffer.
     * @param buffer the buffer to write to
     * @param string the string to write
     */
    public static void putString(final ByteBuffer buffer, final String string) {
        for (int i = 0; i < string.length(); i++) {
            buffer.put((byte) string.charAt(i));
        }
    }

    /**
     * Puts a Gnome string into a buffer.
     * @param buffer the buffer to write to
     * @param string the string to write
     */
    public static void putString(final ByteBuffer buffer, final GnomeString string) {
        for (int i = 0; i < string.length(); i++) {
            buffer.put(string.byteAt(i));
        }
    }

    public static int putDoubleAscii(final ByteBuffer buffer, final double value, final int scale) {
        if (value == 0) {
            buffer.put(ZERO);
            return 1;
        }

        final long y;
        int length;
        final int digitCount;

        if (value < 0) {
            digitCount = digitCount(Math.round(-value));
            y = Math.round(LONG_POW_10[scale] * -value);
            buffer.put((byte) '-');

            length = 1 + digitCount;
        } else {
            digitCount = digitCount(Math.round(value));
            y = Math.round(LONG_POW_10[scale] * value);
            length = digitCount;
        }

        long integerPart = y / LONG_POW_10[scale];
        long fractionalPart = y % LONG_POW_10[scale];

        putPositiveLongAscii(buffer, integerPart, digitCount);
        if (scale > 0) {
            length += 1 + scale;
            buffer.put((byte) '.');
            putNaturalPaddedLongAscii(buffer, scale, fractionalPart);
        }
        return length;
    }

    public static void putNaturalPaddedIntAscii(final ByteBuffer buffer, final int length, final int value) {
        final int offset = buffer.position();
        final int end = offset + length;
        int remainder = value;
        for (int index = end - 1; index >= offset; index--) {
            final int digit = remainder % 10;
            remainder = remainder / 10;
            buffer.put(index, (byte) (ZERO + digit));
        }
        buffer.position(end);
    }

    public static void putNaturalPaddedLongAscii(final ByteBuffer buffer, final int length, final long value) {
        final int offset = buffer.position();
        final int end = offset + length;
        long remainder = value;
        for (int index = end - 1; index >= offset; index--) {
            final long digit = remainder % 10;
            remainder = remainder / 10;
            buffer.put(index, (byte) (ZERO + digit));
        }
        buffer.position(end);
    }

    public static int putLongAscii(final ByteBuffer buffer, final long value) {
        if (value == 0) {
            buffer.put(ZERO);
            return 1;
        }

        final int digitCount, length;
        long quotient = value;
        if (value < 0) {
            if (value == Long.MIN_VALUE) {
                buffer.put(MIN_LONG_VALUE);
                return MIN_LONG_VALUE.length;
            }

            quotient = -quotient;
            buffer.put((byte) '-');
            digitCount = digitCount(quotient);
            length = 1 + digitCount;
        } else {
            length = digitCount = digitCount(quotient);
        }

        putPositiveLongAscii(buffer, quotient, digitCount);
        return length;
    }

    private static void putPositiveLongAscii(final ByteBuffer buffer, final long value, int digitCount) {
        long quotient = value;
        int offset = buffer.position();
        int i = digitCount;
        while (quotient >= 100_000_000)
        {
            final int lastEightDigits = (int)(quotient % 100_000_000);
            quotient /= 100_000_000;

            final int upperPart = lastEightDigits / 10_000;
            final int lowerPart = lastEightDigits % 10_000;

            final int u1 = (upperPart / 100) << 1;
            final int u2 = (upperPart % 100) << 1;
            final int l1 = (lowerPart / 100) << 1;
            final int l2 = (lowerPart % 100) << 1;

            i -= 8;

            buffer.put(offset + i, ASCII_DIGITS[u1]);
            buffer.put(offset + i + 1, ASCII_DIGITS[u1 + 1]);
            buffer.put(offset + i + 2, ASCII_DIGITS[u2]);
            buffer.put(offset + i + 3, ASCII_DIGITS[u2 + 1]);
            buffer.put(offset + i + 4, ASCII_DIGITS[l1]);
            buffer.put(offset + i + 5, ASCII_DIGITS[l1 + 1]);
            buffer.put(offset + i + 6, ASCII_DIGITS[l2]);
            buffer.put(offset + i + 7, ASCII_DIGITS[l2 + 1]);
        }

        putPositiveIntAscii(buffer, (int) quotient, i);
        buffer.position(offset + digitCount);
    }

    public static int putIntAscii(final ByteBuffer buffer, final int value) {
        if (value == 0) {
            buffer.put(ZERO);
            return 1;
        }

        final int digitCount, length;
        int quotient = value;

        if (value < 0) {
            if (value == Integer.MIN_VALUE) {
                buffer.put(MIN_INTEGER_VALUE);
                return MIN_INTEGER_VALUE.length;
            }
            quotient = -quotient;
            buffer.put((byte) '-');
            digitCount = digitCount(quotient);
            length = digitCount + 1;
        } else {
            length = digitCount = digitCount(quotient);
        }

        putPositiveIntAscii(buffer, quotient, digitCount);
        return length;
    }

    /**
     * Puts an ASCII-encoded int sized natural number into the buffer.
     *
     * @param buffer the buffer to write to
     * @param value the int to write
     * @return the number of bytes that the int used to encode
     */
    public static int putNaturalIntAscii(final ByteBuffer buffer, final int value) {
        if (value == 0) {
            buffer.put(ZERO);
            return 1;
        }

        final int digits = digitCount(value);
        putPositiveIntAscii(buffer, value, digits);
        return digits;
    }

    /**
     * Put an ASCII-encoded int sized natural number into the buffer array.
     *
     * @param buffer the buffer array to write to
     * @param offset the offset index to start writing from
     * @param value the int to write
     * @return the number of bytes that the int used to encode
     */
    public static int putNaturalIntAscii(final byte[] buffer, final int offset, final int value) {
        if (value == 0) {
            buffer[offset] = ZERO;
            return 1;
        }

        final int digits = digitCount(value);
        putPositiveIntAscii(buffer, offset, value, digits);
        return digits;
    }


    private static void putPositiveIntAscii(final byte[] buffer, final int offset, final int value, final int digits) {
        int quotient = value;
        int idx = digits;

        while (quotient >= 10_000) {
            final int lastFourDigits = quotient % 10_000;
            quotient /= 10_000;

            final int p1 = (lastFourDigits / 100) << 1;
            final int p2 = (lastFourDigits % 100) << 1;

            idx -= 4;

            buffer[offset + idx] = ASCII_DIGITS[p1];
            buffer[offset + idx + 1] = ASCII_DIGITS[p1 + 1];
            buffer[offset + idx + 2] = ASCII_DIGITS[p2];
            buffer[offset + idx + 3] = ASCII_DIGITS[p2 + 1];
        }

        if (quotient >= 100) {
            final int position = (quotient % 100) << 1;
            quotient /= 100;
            buffer[offset + idx - 1] = ASCII_DIGITS[position + 1];
            buffer[offset + idx - 2] = ASCII_DIGITS[position];
        }

        if (quotient >= 10) {
            final int position = quotient << 1;
            buffer[offset + 1] = ASCII_DIGITS[position + 1];
            buffer[offset] = ASCII_DIGITS[position];
        } else {
            buffer[offset] = (byte) (ZERO + quotient);
        }
    }

    private static void putPositiveIntAscii(final ByteBuffer buffer, final int value, final int digits) {
        int quotient = value;
        int offset = buffer.position();
        int idx = digits;

        while (quotient >= 10_000) {
            final int lastFourDigits = quotient % 10_000;
            quotient /= 10_000;

            final int p1 = (lastFourDigits / 100) << 1;
            final int p2 = (lastFourDigits % 100) << 1;

            idx -= 4;

            buffer.put(offset + idx, ASCII_DIGITS[p1]);
            buffer.put(offset + idx + 1, ASCII_DIGITS[p1 + 1]);
            buffer.put(offset + idx + 2, ASCII_DIGITS[p2]);
            buffer.put(offset + idx + 3, ASCII_DIGITS[p2 + 1]);
        }

        if (quotient >= 100) {
            final int position = (quotient % 100) << 1;
            quotient /= 100;
            buffer.put(offset + idx - 1, ASCII_DIGITS[position + 1]);
            buffer.put(offset + idx - 2, ASCII_DIGITS[position]);
        }

        if (quotient >= 10) {
            final int position = quotient << 1;
            buffer.put(offset + 1, ASCII_DIGITS[position + 1]);
            buffer.put(offset, ASCII_DIGITS[position]);
        } else {
            buffer.put(offset, (byte) (ZERO + quotient));
        }

        buffer.position(offset + digits);
    }

}
