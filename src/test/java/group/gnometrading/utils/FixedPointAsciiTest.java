package group.gnometrading.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FixedPointAsciiTest {

    private static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of(0L, 1_000_000_000L, "0"),
                Arguments.of(505_000_000L, 1_000_000_000L, "0.505"),
                Arguments.of(12_300_000_000L, 1_000_000_000L, "12.3"),
                Arguments.of(10_000_000L, 1_000_000L, "10"),
                Arguments.of(1L, 1_000_000L, "0.000001"),
                Arguments.of(1_000_000_001L, 1_000_000_000L, "1.000000001"),
                Arguments.of(-5L, 1_000_000_000L, "-0.000000005"),
                Arguments.of(-2_500_000L, 1_000_000L, "-2.5"),
                Arguments.of(-1_000_000_000L, 1_000_000_000L, "-1"),
                Arguments.of(123L, 1L, "123"),
                Arguments.of(Long.MAX_VALUE, 1_000_000_000L, "9223372036.854775807"),
                Arguments.of(Long.MIN_VALUE, 1_000_000_000L, "-9223372036.854775808"),
                Arguments.of(Long.MIN_VALUE, 1L, "-9223372036854775808"));
    }

    @ParameterizedTest
    @MethodSource("values")
    void writesTheShortestPlainDecimalToADirectBuffer(long value, long scale, String expected) {
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(64);
        buffer.putByte(0, (byte) '#');

        final int length = FixedPointAscii.put(buffer, 1, value, scale);

        assertEquals(expected, buffer.getStringWithoutLengthAscii(1, length));
        assertEquals(plain(value, scale), expected);
    }

    @ParameterizedTest
    @MethodSource("values")
    void writesTheShortestPlainDecimalToAByteBuffer(long value, long scale, String expected) {
        final ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.put((byte) '#');

        final int length = FixedPointAscii.put(buffer, value, scale);

        assertEquals(1 + length, buffer.position());
        assertEquals(expected, new String(buffer.array(), 1, length, StandardCharsets.US_ASCII));
    }

    /** What BigDecimal makes of it, so the expectations above are checked against a reference too. */
    private static String plain(long value, long scale) {
        return BigDecimal.valueOf(value, AsciiEncoding.digitCount(scale) - 1)
                .stripTrailingZeros()
                .toPlainString();
    }
}
