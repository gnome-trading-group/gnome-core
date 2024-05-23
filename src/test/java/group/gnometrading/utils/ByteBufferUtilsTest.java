package group.gnometrading.utils;

import group.gnometrading.strings.GnomeString;
import group.gnometrading.strings.ViewString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
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
}