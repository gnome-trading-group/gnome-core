package group.gnometrading.strings;

public interface GnomeString extends Comparable<GnomeString> {
    boolean equalsIgnoreCase(final String other);

    boolean equalsIgnoreCase(final GnomeString other);

    boolean equals(final String other);

    boolean equals(final GnomeString other);

    int length();

    int capacity();

    int offset();

    byte byteAt(final int index);

    byte[] getBytes();

    void copyBytes(final byte[] dest);

    int toInt();

    long toFixedPointLong(final long scalingFactor);
}
