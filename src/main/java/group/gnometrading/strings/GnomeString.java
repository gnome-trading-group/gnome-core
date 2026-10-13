package group.gnometrading.strings;

public interface GnomeString extends Comparable<GnomeString> {
    boolean equalsIgnoreCase(String other);

    boolean equalsIgnoreCase(GnomeString other);

    boolean equals(String other);

    boolean equals(GnomeString other);

    int length();

    int capacity();

    int offset();

    byte byteAt(int index);

    byte[] getBytes();

    void copyBytes(byte[] dest);

    int toInt();

    /**
     * This string as a decimal times {@code scalingFactor}, a power of ten.
     *
     * @throws NumberFormatException if it isn't one; see {@link #toFixedPointLong(long, long)}
     */
    long toFixedPointLong(long scalingFactor);

    /**
     * This string as a decimal times {@code scalingFactor}, a power of ten, or {@code invalid} if it isn't one: for
     * numbers a venue sends, where a malformed one must not be fatal. Accepts what
     * {@link group.gnometrading.utils.AsciiParsing#parseFixedPoint} does.
     */
    long toFixedPointLong(long scalingFactor, long invalid);
}
