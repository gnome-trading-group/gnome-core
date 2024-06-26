package group.gnometrading.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AsciiEncodingTest {

    private static Stream<Arguments> testDigitCountArguments() {
        return Stream.of(
                Arguments.of(0, 1),
                Arguments.of(1, 1),
                Arguments.of(9, 1),
                Arguments.of(10, 2),
                Arguments.of(123456, 6)
        );
    }

    @ParameterizedTest
    @MethodSource("testDigitCountArguments")
    void testDigitCount(int value, int expectedDigits) {
        assertEquals(expectedDigits, AsciiEncoding.digitCount(value));
    }

}