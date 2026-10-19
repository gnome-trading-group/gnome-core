package group.gnometrading.utils;

import static group.gnometrading.utils.AsciiEncoding.ZERO;

import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Writes a fixed-point value, the decimal multiplied by a power-of-ten {@code scale} as prices and sizes are kept, as
 * its shortest plain decimal: {@code 505_000_000} at a scale of 1e9 is {@code 0.505}, not {@code 0.505000000} or
 * {@code 5.05E-1}. Never allocates, and handles every long, {@link Long#MIN_VALUE} included.
 */
public final class FixedPointAscii {

    private FixedPointAscii() {}

    /** @return the number of bytes written at {@code index} */
    public static int put(final MutableDirectBuffer buffer, final int index, final long value, final long scale) {
        final long whole = value / scale;
        // The whole part keeps its sign for putLongAscii to write, since it may be Long.MIN_VALUE and can't be negated.
        // Below one it is 0, which has no sign, so -0.5's minus is written here.
        long fraction = Math.abs(value % scale);
        int at = index;
        if (value < 0 && whole == 0) {
            buffer.putByte(at++, (byte) '-');
        }
        at += buffer.putLongAscii(at, whole);
        if (fraction == 0) {
            return at - index;
        }
        int digits = AsciiEncoding.digitCount(scale) - 1;
        while (fraction % 10 == 0) {
            fraction /= 10;
            digits--;
        }
        buffer.putByte(at++, (byte) '.');
        for (int i = at + digits - 1; i >= at; i--) {
            buffer.putByte(i, (byte) (ZERO + fraction % 10));
            fraction /= 10;
        }
        return at + digits - index;
    }

    /** Writes at the buffer's position and advances it. @return the number of bytes written */
    public static int put(final ByteBuffer buffer, final long value, final long scale) {
        final int start = buffer.position();
        final long whole = value / scale;
        long fraction = Math.abs(value % scale);
        if (value < 0 && whole == 0) {
            buffer.put((byte) '-');
        }
        ByteBufferUtils.putLongAscii(buffer, whole);
        if (fraction != 0) {
            int digits = AsciiEncoding.digitCount(scale) - 1;
            while (fraction % 10 == 0) {
                fraction /= 10;
                digits--;
            }
            buffer.put((byte) '.');
            ByteBufferUtils.putNaturalPaddedLongAscii(buffer, digits, fraction);
        }
        return buffer.position() - start;
    }
}
