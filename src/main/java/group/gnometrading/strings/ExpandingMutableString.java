package group.gnometrading.strings;

import group.gnometrading.utils.ArrayCopy;
import group.gnometrading.utils.AsciiEncoding;

public class ExpandingMutableString extends MutableString {

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
        if (this.bytes.length == 0) {
            expand(DEFAULT_CAPACITY);
            return;
        }

        int remaining = this.capacity() - this.length;
        while (remaining < minimumCapacity) {
            expand(2 * this.bytes.length); // TODO: Handle overflow here?
            remaining = this.capacity() - this.length;
        }
    }

    @Override
    public void copy(GnomeString other) {
        if (this.bytes.length < other.length()) {
            ensureCapacity(other.length() - this.bytes.length);
        }
        super.copy(other);
    }

    @Override
    public MutableString append(final byte b) {
        ensureCapacity(1);
        return super.append(b);
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
    public MutableString appendNaturalIntAscii(final int i) {
        ensureCapacity(AsciiEncoding.digitCount(i));
        return super.appendNaturalIntAscii(i);
    }
}
