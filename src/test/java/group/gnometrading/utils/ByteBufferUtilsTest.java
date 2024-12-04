package group.gnometrading.utils;

import group.gnometrading.strings.GnomeString;
import group.gnometrading.strings.ViewString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ByteBufferUtilsTest {

    private static Stream<Arguments> testJavaStringArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.wrap("".getBytes()), "", "".getBytes()),
                Arguments.of(ByteBuffer.allocate(5), "hello", "hello".getBytes()),
                Arguments.of(ByteBuffer.wrap("ahello".getBytes()), "hello", "helloo".getBytes()),
                Arguments.of(ByteBuffer.wrap("ahello".getBytes()).position(1), "hello", "ahello".getBytes())
        );
    }

    @ParameterizedTest
    @MethodSource("testJavaStringArguments")
    void testJavaString(ByteBuffer input, String string, byte[] result) {
        int startIdx = input.position();
        input.mark();
        ByteBufferUtils.putString(input, string);
        assertEquals(startIdx + string.length(), input.position());
        input.reset();
        for (int i = 0; i < result.length; i++) {
            assertEquals(result[i], input.get(i));
        }
    }

    private static Stream<Arguments> testGnomeStringArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.wrap("".getBytes()), new ViewString(""), "".getBytes()),
                Arguments.of(ByteBuffer.allocate(5), new ViewString("hello"), "hello".getBytes()),
                Arguments.of(ByteBuffer.wrap("ahello".getBytes()), new ViewString("hello"), "helloo".getBytes()),
                Arguments.of(ByteBuffer.wrap("ahello".getBytes()).position(1), new ViewString("hello"), "ahello".getBytes())
        );
    }

    @ParameterizedTest
    @MethodSource("testGnomeStringArguments")
    void testGnomeString(ByteBuffer input, GnomeString string, byte[] result) {
        int startIdx = input.position();
        input.mark();
        ByteBufferUtils.putString(input, string);
        assertEquals(startIdx + string.length(), input.position());
        input.reset();
        for (int i = 0; i < result.length; i++) {
            assertEquals(result[i], input.get(i));
        }
    }

    private static Stream<Arguments> testPutNaturalIntAsciiArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(1), 0, "0", 1),
                Arguments.of(ByteBuffer.allocate(1), 1, "1", 1),
                Arguments.of(ByteBuffer.allocate(6), 123456, "123456", 6),
                Arguments.of(ByteBuffer.wrap("a000000".getBytes()).position(1), 123456, "a123456", 6),
                Arguments.of(ByteBuffer.wrap("a000000aa".getBytes()).position(1), 123456, "a123456aa", 6),
                Arguments.of(ByteBuffer.wrap("a000000aaa".getBytes()).position(1), 123456789, "a123456789", 9)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutNaturalIntAsciiArguments")
    void testPutNaturalIntAscii(ByteBuffer input, int value, String result, int expectedDigits) {
        int startIdx = input.position();
        input.mark();

        int digits = ByteBufferUtils.putNaturalIntAscii(input, value);
        assertEquals(startIdx + digits, input.position());
        assertEquals(expectedDigits, digits);
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }

    private static Stream<Arguments> testPutNaturalIntAsciiArgumentsWithArray() {
        return Stream.of(
                Arguments.of("1".getBytes(), 0, 0, "0", 1),
                Arguments.of("1".getBytes(), 0, 1, "1", 1),
                Arguments.of("123".getBytes(), 0, 111, "111", 3),
                Arguments.of("321".getBytes(), 1, 12, "312", 2),
                Arguments.of("aaaaa99999".getBytes(), 3, 55555, "aaa5555599", 5)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutNaturalIntAsciiArgumentsWithArray")
    void testPutNaturalIntAscii(byte[] input, int offset, int value, String result, int expectedDigits) {
        int digits = ByteBufferUtils.putNaturalIntAscii(input, offset, value);
        assertEquals(expectedDigits, digits);
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(input))));
    }

    private static Stream<Arguments> testPutDoubleAsciiArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(1), 0, 0, "0", 1),
                Arguments.of(ByteBuffer.allocate(1), 1.0, 0, "1", 1),
                Arguments.of(ByteBuffer.allocate(3), 1.0, 1, "1.0", 3),
                Arguments.of(ByteBuffer.allocate(4), 1.01, 2, "1.01", 4),
                Arguments.of(ByteBuffer.allocate(6), 123.99, 2, "123.99", 6),
                Arguments.of(ByteBuffer.allocate(5), 123.99, 1, "124.0", 5),
                Arguments.of(ByteBuffer.allocate(6), -123.99, 1, "-124.0", 6),
                Arguments.of(ByteBuffer.wrap("aaaaaa".getBytes()).position(1), 4.56, 2, "a4.56a", 4),
                Arguments.of(ByteBuffer.wrap("aaaaaa".getBytes()).position(1), 4.56, 3, "a4.560", 5),
                Arguments.of(ByteBuffer.wrap("aaaaaa".getBytes()).position(1), 4.56, 0, "a5aaaa", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutDoubleAsciiArguments")
    void testPutDoubleAscii(ByteBuffer input, double value, int scale, String result, int expectedDigits) {
        int startIdx = input.position();
        input.mark();

        int digits = ByteBufferUtils.putDoubleAscii(input, value, scale);
        assertEquals(startIdx + digits, input.position());
        assertEquals(expectedDigits, digits);
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }

    private static Stream<Arguments> testPutNaturalPaddedIntArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(0), 0, 0, ""),
                Arguments.of(ByteBuffer.allocate(1), 0, 1, "0"),
                Arguments.of(ByteBuffer.allocate(3), 0, 3, "000"),
                Arguments.of(ByteBuffer.allocate(3), 1, 3, "001"),
                Arguments.of(ByteBuffer.allocate(4), 5, 4, "0005"),
                Arguments.of(ByteBuffer.wrap("bbbbb".getBytes()).position(0), 35, 4, "0035b"),
                Arguments.of(ByteBuffer.wrap("bbbbb".getBytes()).position(1), 35, 3, "b035b")
        );
    }

    @ParameterizedTest
    @MethodSource("testPutNaturalPaddedIntArguments")
    void testPutNaturalPaddedInt(ByteBuffer input, int value, int length, String result) {
        int startIdx = input.position();
        ByteBufferUtils.putNaturalPaddedIntAscii(input, length, value);
        assertEquals(startIdx + length, input.position());
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }

    private static Stream<Arguments> testPutNaturalPaddedLongArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(0), 0, 0, ""),
                Arguments.of(ByteBuffer.allocate(1), 0, 1, "0"),
                Arguments.of(ByteBuffer.allocate(3), 0, 3, "000"),
                Arguments.of(ByteBuffer.allocate(3), 1, 3, "001"),
                Arguments.of(ByteBuffer.allocate(4), 5, 4, "0005"),
                Arguments.of(ByteBuffer.wrap("bbbbb".getBytes()).position(0), 35, 4, "0035b"),
                Arguments.of(ByteBuffer.wrap("bbbbb".getBytes()).position(1), 35, 3, "b035b"),
                Arguments.of(ByteBuffer.allocate(20), Long.MAX_VALUE, 20, "0" + Long.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutNaturalPaddedLongArguments")
    void testPutNaturalPaddedLong(ByteBuffer input, long value, int length, String result) {
        int startIdx = input.position();
        ByteBufferUtils.putNaturalPaddedLongAscii(input, length, value);
        assertEquals(startIdx + length, input.position());
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }

    private static Stream<Arguments> testPutIntAsciiArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(1), 0, 1, "0"),
                Arguments.of(ByteBuffer.allocate(1), 1, 1, "1"),
                Arguments.of(ByteBuffer.allocate(2), -1, 2, "-1"),
                Arguments.of(ByteBuffer.allocate(4), -123, 4, "-123"),
                Arguments.of(ByteBuffer.allocate(11), Integer.MIN_VALUE, 11, "" + Integer.MIN_VALUE),
                Arguments.of(ByteBuffer.allocate(10), Integer.MAX_VALUE, 10, "" + Integer.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutIntAsciiArguments")
    void testPutIntAscii(ByteBuffer input, int value, int expectedDigits, String result) {
        int startIdx = input.position();
        int digits = ByteBufferUtils.putIntAscii(input, value);
        assertEquals(startIdx + digits, input.position());
        assertEquals(expectedDigits, digits);
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }

    private static Stream<Arguments> testPutLongAsciiArguments() {
        return Stream.of(
                Arguments.of(ByteBuffer.allocate(1), 0, 1, "0"),
                Arguments.of(ByteBuffer.allocate(1), 1, 1, "1"),
                Arguments.of(ByteBuffer.allocate(2), -1, 2, "-1"),
                Arguments.of(ByteBuffer.allocate(4), -123, 4, "-123"),
                Arguments.of(ByteBuffer.allocate(11), Integer.MIN_VALUE, 11, "" + Integer.MIN_VALUE),
                Arguments.of(ByteBuffer.allocate(10), Integer.MAX_VALUE, 10, "" + Integer.MAX_VALUE),
                Arguments.of(ByteBuffer.allocate(20), Long.MIN_VALUE, 20, "" + Long.MIN_VALUE),
                Arguments.of(ByteBuffer.allocate(19), Long.MAX_VALUE, 19, "" + Long.MAX_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testPutLongAsciiArguments")
    void testPutLongAscii(ByteBuffer input, long value, int expectedDigits, String result) {
        int startIdx = input.position();
        int digits = ByteBufferUtils.putLongAscii(input, value);
        assertEquals(startIdx + digits, input.position());
        assertEquals(expectedDigits, digits);
        input.clear();
        assertEquals(result, String.valueOf(StandardCharsets.US_ASCII.decode(input)));
    }
}