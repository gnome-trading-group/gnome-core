package group.gnometrading.strings;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ExpandingMutableStringTest {

    private static Stream<Arguments> testByteAppendArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(1), (byte) 'a', "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(2);
                    return s.append((byte) 'b');
                }, (byte) 'a', "ba"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(0), (byte) 'a', "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(0);
                    return s.append((byte) 'b');
                }, (byte) 'a', "ba"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(5);
                    for (int i = 0; i < 5; i++) {
                        s.append((byte) 'a');
                    }
                    return s;
                }, (byte) 'b', "aaaaab")
        );
    }

    @ParameterizedTest
    @MethodSource("testByteAppendArguments")
    void testByteAppend(Supplier<MutableString> subject, byte b, String expected) {
        var s = subject.get();
        int oldHash = s.hashCode();
        assertEquals(expected, s.append(b).toString());
        assertNotEquals(oldHash, s.hashCode());
    }

    private static Stream<Arguments> testJavaStringAppendArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(1), "a", "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(2);
                    return s.appendString("b");
                }, "a", "ba"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(0), "a", "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(0);
                    return s.appendString("b");
                }, "a", "ba"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(5);
                    for (int i = 0; i < 5; i++) {
                        s.appendString("a");
                    }
                    return s;
                }, "bbb", "aaaaabbb")
        );
    }

    @ParameterizedTest
    @MethodSource("testJavaStringAppendArguments")
    void testJavaStringAppend(Supplier<MutableString> subject, String other, String expected) {
        var s = subject.get();
        int oldHash = s.hashCode();
        assertEquals(expected, s.appendString(other).toString());
        assertNotEquals(oldHash, s.hashCode());
    }

    private static Stream<Arguments> testGnomeStringAppendArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(1), new ViewString("a"), "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(2);
                    return s.appendString(new ViewString("b"));
                }, new ViewString("a"), "ba"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(0), new ViewString("a"), "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(0);
                    return s.appendString(new ViewString("b"));
                }, new ViewString("a"), "ba"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(5);
                    for (int i = 0; i < 5; i++) {
                        s.appendString(new ViewString("a"));
                    }
                    return s;
                }, new ViewString("bbb"), "aaaaabbb")
        );
    }

    @ParameterizedTest
    @MethodSource("testGnomeStringAppendArguments")
    void testGnomeStringAppend(Supplier<MutableString> subject, GnomeString other, String expected) {
        var s = subject.get();
        int oldHash = s.hashCode();
        assertEquals(expected, s.appendString(other).toString());
        assertNotEquals(oldHash, s.hashCode());
    }

    private static Stream<Arguments> testIntAppendArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(1), 1, "1"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(2);
                    return s.appendNaturalIntAscii(1);
                }, 3, "13"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(0), 123, "123"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(0);
                    return s.appendNaturalIntAscii(56);
                }, 99, "5699"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(5);
                    for (int i = 0; i < 5; i++) {
                        s.appendNaturalIntAscii(1);
                    }
                    return s;
                }, 99, "1111199")
        );
    }

    @ParameterizedTest
    @MethodSource("testIntAppendArguments")
    void testIntAppend(Supplier<MutableString> subject, int i, String expected) {
        var s = subject.get();
        int oldHash = s.hashCode();
        assertEquals(expected, s.appendNaturalIntAscii(i).toString());
        assertNotEquals(oldHash, s.hashCode());
    }

    private static Stream<Arguments> testCopyArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(1), new ViewString("a"), "a"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString("My replacing string"), new ViewString("Replaced!"), "Replaced!"),
                Arguments.of((Supplier<MutableString>) () -> new ExpandingMutableString(0), new ViewString("Long string"), "Long string"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new ExpandingMutableString(5);
                    for (int i = 0; i < 5; i++) {
                        s.append((byte) 'a');
                    }
                    return s;
                }, new ViewString("This is a longer string!"), "This is a longer string!")
        );
    }

    @ParameterizedTest
    @MethodSource("testCopyArguments")
    void testCopy(Supplier<MutableString> subject, GnomeString other, String expected) {
        var s = subject.get();
        s.copy(other);
        assertEquals(expected, s.toString());
        assertEquals(s.hashCode(), other.hashCode());
    }

}