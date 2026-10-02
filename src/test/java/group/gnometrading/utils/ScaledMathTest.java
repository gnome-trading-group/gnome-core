package group.gnometrading.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ScaledMathTest {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final long PRICE_SCALE = 1_000_000_000L;
    private static final long SIZE_SCALE = 1_000_000L;

    @Test
    void notionalAboveTheOldOverflowPointIsExact() {
        // 50,000 contracts at $0.60: price*size alone is 3e19, past Long.MAX_VALUE.
        final long price = 600_000_000L;
        final long size = 50_000L * SIZE_SCALE;

        assertEquals(30_000L * PRICE_SCALE, ScaledMath.multiplyDivide(price, size, SIZE_SCALE));
    }

    @Test
    void unitPriceFromALargeNotionalIsExact() {
        final long notional = 12_000L * PRICE_SCALE;
        final long size = 20_000L * SIZE_SCALE;

        assertEquals(600_000_000L, ScaledMath.multiplyDivide(notional, SIZE_SCALE, size));
    }

    @Test
    void truncatesTowardZeroLikeJavaDivision() {
        assertEquals(7 / 2, ScaledMath.multiplyDivide(7, 1, 2));
        assertEquals(-7 / 2, ScaledMath.multiplyDivide(-7, 1, 2));
        assertEquals(-7 / 2, ScaledMath.multiplyDivide(7, -1, 2));
        assertEquals(7 / 2, ScaledMath.multiplyDivide(-7, -1, 2));
    }

    @Test
    void resultsOutsideTheLongRangeSaturate() {
        assertEquals(Long.MAX_VALUE, ScaledMath.multiplyDivide(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        assertEquals(Long.MIN_VALUE, ScaledMath.multiplyDivide(Long.MAX_VALUE, -Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, ScaledMath.multiplyDivide(Long.MIN_VALUE, -1, 1));
        assertEquals(Long.MIN_VALUE, ScaledMath.multiplyDivide(Long.MIN_VALUE, 1, 1));
        assertEquals(Long.MIN_VALUE, ScaledMath.multiplyDivide(Long.MIN_VALUE, 2, 2));
    }

    @Test
    void nonPositiveDivisorIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScaledMath.multiplyDivide(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ScaledMath.multiplyDivide(1, 1, -5));
    }

    @Test
    void matchesBigIntegerOnSmallValues() {
        for (long a = -40; a <= 40; a++) {
            for (long b = -40; b <= 40; b++) {
                for (long c = 1; c <= 40; c++) {
                    assertEquals(expected(a, b, c), ScaledMath.multiplyDivide(a, b, c), a + "*" + b + "/" + c);
                }
            }
        }
    }

    @Test
    void matchesBigIntegerOnRandomValuesOfEveryMagnitude() {
        final SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 2_000_000; i++) {
            final long a = randomOfRandomWidth(random);
            final long b = randomOfRandomWidth(random);
            final long c = Math.max(1, Math.abs(randomOfRandomWidth(random)));
            assertEquals(expected(a, b, c), ScaledMath.multiplyDivide(a, b, c), a + "*" + b + "/" + c);
        }
    }

    @Test
    void matchesBigIntegerAtTheEdges() {
        final long[] edges = {
            0,
            1,
            -1,
            2,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            Long.MAX_VALUE - 1,
            Long.MIN_VALUE + 1,
            1L << 32,
            (1L << 32) - 1,
            (1L << 32) + 1,
            1L << 62,
            -(1L << 62),
            SIZE_SCALE,
            PRICE_SCALE
        };
        for (final long a : edges) {
            for (final long b : edges) {
                for (final long c : edges) {
                    if (c > 0) {
                        assertEquals(expected(a, b, c), ScaledMath.multiplyDivide(a, b, c), a + "*" + b + "/" + c);
                    }
                }
            }
        }
    }

    private static long randomOfRandomWidth(final SplittableRandom random) {
        final int bits = random.nextInt(1, 65);
        final long value = bits == 64 ? random.nextLong() : random.nextLong() >>> (64 - bits);
        return random.nextBoolean() ? value : -value;
    }

    private static long expected(final long a, final long b, final long c) {
        final BigInteger exact =
                BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(c));
        if (exact.compareTo(LONG_MAX) > 0) {
            return Long.MAX_VALUE;
        }
        if (exact.compareTo(LONG_MIN) < 0) {
            return Long.MIN_VALUE;
        }
        return exact.longValueExact();
    }
}
