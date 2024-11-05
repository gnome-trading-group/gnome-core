package group.gnometrading.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ArrayCopyTest {

    private static Stream<Arguments> testArrayCopyArguments() {
        return Stream.of(
                Arguments.of("1111".getBytes(), 0, "    ".getBytes(), 0, 4, "1111".getBytes()),
                Arguments.of("0111".getBytes(), 1, "   ".getBytes(), 0, 3, "111".getBytes()),
                Arguments.of("0111".getBytes(), 1, "   ".getBytes(), 1, 1, " 1 ".getBytes()),
                Arguments.of("0111".getBytes(), 0, "   ".getBytes(), 0, 0, "   ".getBytes())
        );
    }

    @ParameterizedTest
    @MethodSource("testArrayCopyArguments")
    void testArrayCopy(byte[] src, int srcOffset, byte[] dest, int destOffset, int len, byte[] result) {
        ArrayCopy.arraycopy(src, srcOffset, dest, destOffset, len);
        assertArrayEquals(result, dest);
    }
}