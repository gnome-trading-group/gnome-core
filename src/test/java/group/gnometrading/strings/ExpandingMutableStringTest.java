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