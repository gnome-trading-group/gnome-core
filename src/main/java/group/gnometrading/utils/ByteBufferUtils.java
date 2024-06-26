package group.gnometrading.utils;

import group.gnometrading.strings.GnomeString;

import java.nio.ByteBuffer;

import static group.gnometrading.utils.AsciiEncoding.*;

public class ByteBufferUtils {

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
