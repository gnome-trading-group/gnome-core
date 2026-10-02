package group.gnometrading.strings;

import group.gnometrading.utils.ArrayCopy;
import group.gnometrading.utils.AsciiEncoding;

public final class ExpandingMutableString extends MutableString {

    public ExpandingMutableString() {
        super(DEFAULT_CAPACITY);
    }

    public ExpandingMutableString(final int capacity) {
        super(capacity);
    }

    public ExpandingMutableString(final GnomeString other) {
        super(other);
    }

    public ExpandingMutableString(final String other) {
        super(other);
    }

    private void expand(final int newSize) {
        final byte[] newBytes = new byte[newSize];
        if (this.offset > 0 || this.length > 0) {
            ArrayCopy.arraycopy(this.bytes, 0, newBytes, 0, offset + this.length);
        }
        this.bytes = newBytes;
    }

    private void ensureCapacity(final int minimumCapacity) {
        hash = 0;
        final int required = this.offset + this.length + minimumCapacity;
        if (required <= this.bytes.length) {
            return;
        }

        int newSize = Math.max(this.bytes.length, DEFAULT_CAPACITY);
        while (newSize < required) {
            newSize <<= 1;
            if (newSize <= 0) {
                newSize = required;
                break;
            }
        }
        expand(newSize);
    }

    @Override
    public void copy(GnomeString other) {
        if (other != null && this.bytes.length < other.length()) {
            reset();
            ensureCapacity(other.length());
        }
        super.copy(other);
    }

    @Override
    public MutableString append(final byte value) {
        ensureCapacity(1);
        return super.append(value);
    }

    @Override
    public MutableString appendString(final String other) {
        ensureCapacity(other.length());
        return super.appendString(other);
    }

    @Override
    public MutableString appendString(final GnomeString other) {
        ensureCapacity(other.length());
        return super.appendString(other);
    }

    @Override
    public MutableString appendNaturalIntAscii(final int value) {
        ensureCapacity(AsciiEncoding.digitCount(value));
        return super.appendNaturalIntAscii(value);
    }
}
