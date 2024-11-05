package group.gnometrading.strings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MutableStringTest {

    private static Stream<Arguments> testConstructorsArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) (MutableString::new), ""),
                Arguments.of((Supplier<MutableString>) () -> new MutableString(0), ""),
                Arguments.of((Supplier<MutableString>) () -> new MutableString(new ViewString("whats up")), "whats up"),
                Arguments.of((Supplier<MutableString>) () -> new MutableString("whats up 2.0"), "whats up 2.0")
        );
    }

    @ParameterizedTest
    @MethodSource("testConstructorsArguments")
    void testConstructors(Supplier<MutableString> supplier, String other) {
        MutableString viewString = supplier.get();
        assertEquals(other, viewString.toString());
    }

    @Test
    void testCopiesConstructor() {
        MutableString other = new MutableString(100);
        for (byte b : "This is my string!".getBytes()) {
            other.append(b);
        }
        GnomeString tester = new MutableString(other);

        assertEquals("This is my string!", tester.toString());
        other.append((byte) 'a');
        assertEquals("This is my string!a", other.toString());
        assertEquals("This is my string!", tester.toString());
        assertNotEquals(other.hashCode(), tester.hashCode());
    }

    private static Stream<Arguments> testByteAppendArguments() {
        return Stream.of(
                Arguments.of((Supplier<MutableString>) () -> new MutableString(1), (byte) 'a', "a"),
                Arguments.of((Supplier<MutableString>) () -> {
                    var s = new MutableString(2);
                    return s.append((byte) 'b');
                }, (byte) 'a', "ba")
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
                Arguments.of((Supplier<MutableString>) () -> new MutableString(1), new ViewString("a"), "a"),
                Arguments.of((Supplier<MutableString>) () -> new MutableString("My replacing string"), new ViewString("Replaced!"), "Replaced!")
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

    @Test
    void testReset() {
        MutableString string = new MutableString("Goodbye world.");
        assertEquals("Goodbye world.", string.toString());
        string.reset();
        assertEquals("", string.toString());
    }

    @Test
    void testLength() {
        MutableString string = new MutableString("123456789");
        assertEquals("123456789", string.toString());

        string.setLength(5);
        assertEquals("12345", string.toString());

        string.setLength(1);
        assertEquals("1", string.toString());

        string.setLength(0);
        assertEquals("", string.toString());
        assertEquals(0, string.length());
    }
}