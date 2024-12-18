package group.gnometrading.strings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ViewStringTest {

    private static Stream<Arguments> testConstructorsArguments() {
        return Stream.of(
                Arguments.of((Supplier<ViewString>) (ViewString::new), ""),
                Arguments.of((Supplier<ViewString>) () -> new ViewString("whats up"), "whats up"),
                Arguments.of((Supplier<ViewString>) () -> new ViewString(new ViewString("whats up")), "whats up"),
                Arguments.of((Supplier<ViewString>) () -> new ViewString(new byte[] {'h', 'i'}), "hi")
        );
    }

    @ParameterizedTest
    @MethodSource("testConstructorsArguments")
    void testConstructors(Supplier<ViewString> supplier, String other) {
        ViewString viewString = supplier.get();
        assertTrue(viewString.equals(other));
    }

    private static Stream<Arguments> testStringEqualsIgnoreCaseArguments() {
        return Stream.of(
                Arguments.of(new byte[] {}, 0, 0, null, false),
                Arguments.of(new byte[] {'a'}, 0, 0, "", true),
                Arguments.of(new byte[] {'a'}, 0, 1, "a", true),
                Arguments.of(new byte[] {'a'}, 0, 1, "A", true),
                Arguments.of(new byte[] {'a'}, 1, 1, "", false),
                Arguments.of(new byte[] {'a', 'A', 'a', 'b'}, 0, 4, "AaAb", true),
                Arguments.of(new byte[] {'a', 'A', 'a', 'b'}, 0, 4, "AaAc", false),
                Arguments.of(new byte[] {'b', 'C', 'd', 'e'}, 1, 3, "cDE", true)
        );
    }

    @ParameterizedTest
    @MethodSource("testStringEqualsIgnoreCaseArguments")
    void testStringEqualsIgnoreCase(byte[] bytes, int offset, int len, String other, boolean equals) {
        ViewString viewString = new ViewString(bytes, offset, len);
        assertEquals(equals, viewString.equalsIgnoreCase(other));
    }

    private static Stream<Arguments> testGnomeStringEqualsIgnoreCaseArguments() {
        return Stream.of(
                Arguments.of(new byte[] {}, 0, 0, null, false),
                Arguments.of(new byte[] {'a'}, 0, 0, new ViewString(), true),
                Arguments.of(new byte[] {'a'}, 0, 1, new ViewString("a"), true),
                Arguments.of(new byte[] {'a'}, 0, 1, new ViewString("A"), true),
                Arguments.of(new byte[] {'a'}, 1, 1, new ViewString(""), false),
                Arguments.of(new byte[] {'a', 'A', 'a', 'b'}, 0, 4, new ViewString("AaAb"), true),
                Arguments.of(new byte[] {'a', 'A', 'a', 'b'}, 0, 4, new ViewString("AaAc"), false),
                Arguments.of(new byte[] {'b', 'C', 'd', 'e'}, 1, 3, new ViewString("cDE"), true)
        );
    }

    @ParameterizedTest
    @MethodSource("testGnomeStringEqualsIgnoreCaseArguments")
    void testGnomeStringEqualsIgnoreCase(byte[] bytes, int offset, int len, GnomeString other, boolean equals) {
        ViewString viewString = new ViewString(bytes, offset, len);
        assertEquals(equals, viewString.equalsIgnoreCase(other));
    }

    private static Stream<Arguments> testViewStringEqualsArguments() {
        return Stream.of(
                Arguments.of(new ViewString(), null, false),
                Arguments.of(new ViewString(""), new ViewString(""), true),
                Arguments.of(new ViewString(""), new ViewString("1"), false),
                Arguments.of(new ViewString("1"), new ViewString("1"), true),
                Arguments.of(new ViewString("1"), new ViewString("12"), false)

        );
    }

    @ParameterizedTest
    @MethodSource("testViewStringEqualsArguments")
    void testViewStringEquals(ViewString v1, ViewString v2, boolean equals) {
        assertEquals(equals, v1.equals(v2));
    }

    private static Stream<Arguments> testStringEqualsArguments() {
        return Stream.of(
                Arguments.of(new ViewString(), null, false),
                Arguments.of(new ViewString(""), "", true),
                Arguments.of(new ViewString(""), "1", false),
                Arguments.of(new ViewString("1"), "1", true),
                Arguments.of(new ViewString("1"), "12", false)

        );
    }

    @ParameterizedTest
    @MethodSource("testStringEqualsArguments")
    void testStringEquals(ViewString v1, String v2, boolean equals) {
        assertEquals(equals, v1.equals(v2));
    }

    @ParameterizedTest
    @MethodSource("testViewStringEqualsArguments")
    void testGnomeStringEquals(ViewString v1, GnomeString v2, boolean equals) {
        assertEquals(equals, v1.equals(v2));
    }

    private static Stream<Arguments> testObjectEqualsArguments() {
        return Stream.of(
                Arguments.of(new ViewString(), null, false),
                Arguments.of(new ViewString(""), "", true),
                Arguments.of(new ViewString(""), "1", false),
                Arguments.of(new ViewString("1"), "1", true),
                Arguments.of(new ViewString("1"), new ViewString("1"), true),
                Arguments.of(new ViewString("1"), 1, false)
        );
    }

    @ParameterizedTest
    @MethodSource("testObjectEqualsArguments")
    void testObjectEquals(ViewString v1, Object v2, boolean equals) {
        assertEquals(equals, v1.equals(v2));
    }

    private static Stream<Arguments> testCompareToArguments() {
        return Stream.of(
                Arguments.of(new ViewString(), new ViewString(), 0),
                Arguments.of(new ViewString("a"), new ViewString("a"), 0),
                Arguments.of(new ViewString("a"), new ViewString("b"), -1),
                Arguments.of(new ViewString("b"), new ViewString("a"), 1),
                Arguments.of(new ViewString("ba"), new ViewString("ab"), 1),
                Arguments.of(new ViewString("abc"), new ViewString("ab"), 1),
                Arguments.of(new ViewString("ab"), new ViewString("abc"), -1),
                Arguments.of(new ViewString("Ab"), new ViewString("abc"), -1),
                Arguments.of(new ViewString("ab"), new ViewString("Abc"), 1)
        );
    }

    @ParameterizedTest
    @MethodSource("testCompareToArguments")
    void testCompareTo(ViewString v1, ViewString v2, int result) {
        if (result == 0) {
            assertEquals(0, v1.compareTo(v2));
        } else if (result == -1) {
            assertTrue(v1.compareTo(v2) < 0);
        } else {
            assertTrue(v1.compareTo(v2) > 0);
        }
    }

    private static Stream<Arguments> testToIntArguments() {
        return Stream.of(
                Arguments.of(new ViewString("1"), 1),
                Arguments.of(new ViewString("101"), 101),
                Arguments.of(new ViewString("000050"), 50),
                Arguments.of(new ViewString("-000050"), -50),
                Arguments.of(new ViewString("" + Integer.MAX_VALUE), Integer.MAX_VALUE),
                Arguments.of(new ViewString("" + Integer.MIN_VALUE), Integer.MIN_VALUE)
        );
    }

    @ParameterizedTest
    @MethodSource("testToIntArguments")
    void testToInt(ViewString str, int result) {
        assertEquals(result, str.toInt());
    }

    @Test
    void testToIntExceptions() {
        assertThrows(NumberFormatException.class, () -> new ViewString().toInt());
        assertThrows(NumberFormatException.class, () -> new ViewString("aaa").toInt());
    }

    private static Stream<Arguments> testToFixedPointLongArguments() {
        return Stream.of(
                Arguments.of(new ViewString("0"), 0, 0),
                Arguments.of(new ViewString("1"), 0, 0),
                Arguments.of(new ViewString("1"), 1_000, 1_000),
                Arguments.of(new ViewString("-1.0"), -1_000, 1_000),
                Arguments.of(new ViewString("-1.1234"), -1_123, 1_000),
                Arguments.of(new ViewString("3452.134"), 34521340, 1_000_0)
        );
    }

    @ParameterizedTest
    @MethodSource("testToFixedPointLongArguments")
    void testToFixedPointLong(ViewString str, long result, long scalingFactor) {
        assertEquals(result, str.toFixedPointLong(scalingFactor));
    }

    @Test
    void testToFixedPointLongExceptions() {
        assertThrows(NumberFormatException.class, () -> new ViewString().toFixedPointLong(0));
        assertThrows(NumberFormatException.class, () -> new ViewString("aaa").toFixedPointLong(0));
    }
}