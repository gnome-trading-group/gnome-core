package group.gnometrading.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AsciiParsingTest {

    private static final byte[] RETRY_AFTER = ascii("retry-after");

    @Test
    void lowercasesLettersOnly() {
        assertEquals('a', AsciiParsing.toLowerCase((byte) 'A'));
        assertEquals('z', AsciiParsing.toLowerCase((byte) 'Z'));
        assertEquals('a', AsciiParsing.toLowerCase((byte) 'a'));
        assertEquals('@', AsciiParsing.toLowerCase((byte) '@'));
        assertEquals('[', AsciiParsing.toLowerCase((byte) '['));
        assertEquals('\r', AsciiParsing.toLowerCase((byte) '\r'));
    }

    @Test
    void equalsIgnoreCaseMatchesAnyCase() {
        final ByteBuffer buffer = buffer("xxRetry-AFTERyy");

        assertTrue(AsciiParsing.equalsIgnoreCase(buffer, 2, 13, RETRY_AFTER));
        assertTrue(AsciiParsing.equalsIgnoreCase(buffer.array(), 2, 13, RETRY_AFTER));
        assertEquals(0, buffer.position());
    }

    @Test
    void equalsIgnoreCaseKeepsBytesThatOnlyDifferByTheCaseBitApart() {
        // '\r' is '-' without the case bit, and '@' is '`' without it.
        assertFalse(AsciiParsing.equalsIgnoreCase(buffer("retry\rafter"), 0, 11, RETRY_AFTER));
        assertFalse(AsciiParsing.equalsIgnoreCase(buffer("@"), 0, 1, ascii("`")));
        assertFalse(AsciiParsing.equalsIgnoreCase(ascii("@"), 0, 1, ascii("`")));
    }

    @Test
    void equalsIgnoreCaseNeedsTheSameLength() {
        assertFalse(AsciiParsing.equalsIgnoreCase(buffer("retry-afterx"), 0, 12, RETRY_AFTER));
        assertFalse(AsciiParsing.equalsIgnoreCase(buffer("retry-afte"), 0, 10, RETRY_AFTER));
    }

    @Test
    void indexOfIgnoreCaseFindsTheFirstMatchWithinTheRange() {
        final ByteBuffer buffer = buffer("gzip, CHUNKED, chunked");

        assertEquals(6, AsciiParsing.indexOfIgnoreCase(buffer, 0, buffer.limit(), ascii("chunked")));
        assertEquals(15, AsciiParsing.indexOfIgnoreCase(buffer, 7, buffer.limit(), ascii("chunked")));
        assertEquals(-1, AsciiParsing.indexOfIgnoreCase(buffer, 0, 12, ascii("chunked")));
    }

    @Test
    void skipsAndTrimsSpacesAndTabsOnly() {
        final ByteBuffer buffer = buffer(" \t 42 \t\r");

        assertEquals(3, AsciiParsing.skipSpacesAndTabs(buffer, 0, buffer.limit()));
        assertEquals(8, AsciiParsing.trimSpacesAndTabs(buffer, 0, buffer.limit()));
        assertEquals(5, AsciiParsing.trimSpacesAndTabs(buffer, 0, 7));
        assertEquals(2, AsciiParsing.skipSpacesAndTabs(buffer, 0, 2));
        assertEquals(1, AsciiParsing.trimSpacesAndTabs(buffer, 1, 3));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "7, 7",
        "1700000000123, 1700000000123",
        "9223372036854775807, 9223372036854775807",
        "007, 7",
    })
    void parsesNaturalLongs(final String text, final long expected) {
        assertEquals(expected, AsciiParsing.parseNaturalLong(buffer(text), 0, text.length(), -1));
        assertEquals(expected, AsciiParsing.parseNaturalLong(ascii(text), 0, text.length(), -1));
    }

    @ParameterizedTest
    @CsvSource({
        "''",
        "-1",
        "+1",
        "1.5",
        "1 2",
        "12a",
        "9223372036854775808",
        "99999999999999999999",
        "'Wed, 21 Oct 2026 07:28:00 GMT'",
    })
    void returnsInvalidForAnythingButDigits(final String text) {
        assertEquals(-7, AsciiParsing.parseNaturalLong(buffer(text), 0, text.length(), -7));
        assertEquals(-7, AsciiParsing.parseNaturalLong(ascii(text), 0, text.length(), -7));
    }

    @Test
    void parsesOnlyTheRange() {
        assertEquals(23, AsciiParsing.parseNaturalLong(buffer("x123y"), 2, 4, -1));
        assertEquals(23, AsciiParsing.parseNaturalLong(ascii("x123y"), 2, 4, -1));
    }

    @Test
    void indexOfFindsAnExactMatchWithinTheRange() {
        final ByteBuffer buffer = buffer("ab\"count\":\"5\" \"Count\":\"6\"");

        assertEquals(2, AsciiParsing.indexOf(buffer, 0, buffer.limit(), ascii("\"count\":")));
        assertEquals(-1, AsciiParsing.indexOf(buffer, 3, buffer.limit(), ascii("\"count\":")), "case matters");
        assertEquals(-1, AsciiParsing.indexOf(buffer, 0, 8, ascii("\"count\":")), "the match must fit the range");
        assertEquals(0, buffer.position());
    }

    @Test
    void skipDigitsStopsAtTheFirstNonDigit() {
        final ByteBuffer buffer = buffer("x1234,");

        assertEquals(5, AsciiParsing.skipDigits(buffer, 1, buffer.limit()));
        assertEquals(0, AsciiParsing.skipDigits(buffer, 0, buffer.limit()));
        assertEquals(3, AsciiParsing.skipDigits(buffer, 1, 3));
    }

    @ParameterizedTest
    @CsvSource({
        "5, 500",
        "5., 500",
        "5.25, 525",
        "5.2, 520",
        "0.01, 1",
        "5.259, 525",
        "007.5, 750",
        "92233720368547758.07, 9223372036854775807",
    })
    void parsesNaturalFixedPointDecimals(final String text, final long expected) {
        assertEquals(expected, AsciiParsing.parseNaturalFixedPoint(buffer(text), 0, text.length(), 100, -1));
    }

    @ParameterizedTest
    @CsvSource({"''", ".5", "-5", "+5", "'5,25'", "5.2.5", "5.a", "1e3", "92233720368547758.08", "99999999999999999999"
    })
    void returnsInvalidForAnythingButANaturalDecimal(final String text) {
        assertEquals(-7, AsciiParsing.parseNaturalFixedPoint(buffer(text), 0, text.length(), 100, -7));
    }

    @Test
    void fixedPointParsesOnlyTheRange() {
        final ByteBuffer buffer = buffer("\"12.50\"");

        assertEquals(12_500_000, AsciiParsing.parseNaturalFixedPoint(buffer, 1, 6, 1_000_000, -1));
    }

    @Test
    void parsingAndSearchingDoNotAllocate() {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().getId();
        final ByteBuffer buffer = buffer("{\"remaining_count_fp\":\"12.50\",\"exchange_index\":3}");
        final byte[] marker = ascii("\"remaining_count_fp\":\"");

        long sink = parseMany(buffer, marker, 10_000);
        final long before = threads.getThreadAllocatedBytes(threadId);
        sink += parseMany(buffer, marker, 10_000);
        final long allocated = threads.getThreadAllocatedBytes(threadId) - before;

        assertEquals(20_000L * (12_500_000 + 3), sink);
        assertTrue(allocated < 1024, "allocated " + allocated + " bytes over 10,000 parses");
    }

    private static long parseMany(final ByteBuffer buffer, final byte[] marker, final int count) {
        long sink = 0;
        for (int i = 0; i < count; i++) {
            final int start = AsciiParsing.indexOf(buffer, 0, buffer.limit(), marker) + marker.length;
            final int end = AsciiParsing.skipDigits(buffer, start, buffer.limit());
            sink += AsciiParsing.parseNaturalFixedPoint(buffer, start, end + 3, 1_000_000, -1);
            final int index = buffer.limit() - 2;
            sink += AsciiParsing.parseNaturalLong(
                    buffer, index, AsciiParsing.skipDigits(buffer, index, buffer.limit()), -1);
        }
        return sink;
    }

    private static ByteBuffer buffer(final String text) {
        return ByteBuffer.wrap(ascii(text));
    }

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
