package group.gnometrading.strings;

import group.gnometrading.utils.ArrayCopy;

public class ViewString implements GnomeString {

    private static final byte[] NULL_BUF = new byte[1];

    protected int hash;
    protected int length;
    protected int offset;
    protected byte[] bytes;

    public ViewString(final byte[] bytes, final int offset, final int length) {
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.hash = 0;
    }

    public ViewString(final ViewString other) {
        this(other.bytes, other.offset, other.length);
    }

    public ViewString(final byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public ViewString(final GnomeString other) {
        this(other.getBytes(), other.offset(), other.length());
    }

    public ViewString(final String other) {
        this(other == null ? NULL_BUF : other.getBytes(), 0, other == null ? 0 : other.length());
    }

    public ViewString() {
        this(NULL_BUF, 0, 0);
    }

    @Override
    public boolean equalsIgnoreCase(final String other) {
        if (other == null) return false;

        if (length != other.length()) return false;

        int localIdx = offset;
        for (int i = 0; i < length; i++) {
            byte b1 = this.bytes[localIdx + i];
            byte b2 = (byte) other.charAt(i);

            if (b1 != b2) {
                if ((0x20 + b1) == b2) {
                    continue;
                } else if (b1 == (0x20 + b2)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equalsIgnoreCase(final GnomeString other) {
        if (other == this) return true;
        if (other == null) return false;

        if (other.length() != length) return false;

        int localIdx = offset;
        for (int i = 0; i < length; i++) {
            byte b1 = this.bytes[localIdx + i];
            byte b2 = other.byteAt(i);

            if (b1 != b2) {
                if ((0x20 + b1) == b2) {
                    continue;
                } else if (b1 == (0x20 + b2)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    public boolean equals(final ViewString other) {
        if (other == null) return false;
        if (other == this) return true;
        if (other.length != this.length) return false;

        for (int i = 0; i < length; i++) {
            if (this.bytes[offset + i] != other.bytes[other.offset + i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(final String other) {
        if (other == null) return false;
        if (other.length() != this.length) return false;

        for (int i = 0; i < length; i++) {
            if (this.bytes[offset + i] != (byte) other.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(final GnomeString other) {
        if (other == null) return false;
        if (other.length() != this.length) return false;

        for (int i = 0; i < length; i++) {
            if (this.bytes[offset + i] != other.byteAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(final Object other) {
        if (other == this) return true;
        if (other == null) return false;

        Class<?> otherClass = other.getClass();

        if (otherClass == ViewString.class || otherClass == MutableString.class || otherClass == ExpandingMutableString.class)  {
            return equals((ViewString) other);
        } else if (other.getClass() == String.class) {
            return equals((String) other);
        }
        return false;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int capacity() {
        return bytes.length - offset;
    }

    @Override
    public int offset() {
        return offset;
    }

    @Override
    public byte byteAt(final int index) {
        return this.bytes[this.offset + index];
    }

    @Override
    public int compareTo(final GnomeString gnomeString) {
        int l1 = this.length;
        int l2 = gnomeString.length();

        int limit = Math.min(l1, l2);

        for (int i = 0; i < limit; i++) {
            byte b1 = this.byteAt(i);
            byte b2 = gnomeString.byteAt(i);
            if (b1 != b2) {
                return b1 - b2;
            }
        }
        return l1 - l2;
    }

    @Override
    public int hashCode() {
        if (hash == 0) {
            int h = 0;
            for (int i = 0; i < this.length; i++) {
                h = 31 * h + this.byteAt(i);
            }
            hash = h;
        }
        return hash;
    }

    @Override
    public byte[] getBytes() {
        return this.bytes;
    }

    @Override
    public void copyBytes(final byte[] dest) {
        ArrayCopy.arraycopy(this.bytes, this.offset, dest, 0, this.capacity());
    }

    @Override
    public int toInt() {
        if (this.length == 0) {
            throw new NumberFormatException("Empty string");
        }

        int result = 0;
        boolean isNegative = false;

        for (int i = 0; i < this.length; i++) {
            byte at = this.byteAt(i);
            if (at == '-') {
                isNegative = true;
            } else if (at <= '9' && at >= '0') {
                result = result * 10 + (at - '0');
            } else {
                throw new NumberFormatException("Invalid character: " + at);
            }
        }
        return isNegative ? -result : result;
    }

    @Override
    public long toFixedPointLong(final long scalingFactor) {
        if (this.length == 0) {
            throw new NumberFormatException("Empty string");
        }

        long result = 0;
        boolean isNegative = false;
        boolean inFraction = false;
        long fractionalMultiplier = scalingFactor;

        for (int i = 0; i < this.length; i++) {
            final byte at = this.byteAt(i);

            if (at == '-') {
                isNegative = true;
            } else if (at == '.') {
                inFraction = true;
            } else if (at >= '0' && at <= '9') {
                final int digit = at - '0';
                if (inFraction) {
                    fractionalMultiplier /= 10;
                    result += digit * fractionalMultiplier;
                } else {
                    result = result * 10 + digit * scalingFactor;
                }
            } else {
                throw new NumberFormatException("Invalid character: " + at);
            }
        }

        return isNegative ? -result : result;
    }

    @Override
    public String toString() {
        return new String(this.bytes, this.offset, this.length);
    }
}
