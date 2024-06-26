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
}