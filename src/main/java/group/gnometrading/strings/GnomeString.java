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

    long toFixedPointLong(long scalingFactor);
}
