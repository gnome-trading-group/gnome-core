package group.gnometrading.strings;

import group.gnometrading.utils.ArrayCopy;
import group.gnometrading.utils.ByteBufferUtils;

public class MutableString extends ViewString {

    protected static final int DEFAULT_CAPACITY = 100;

    public MutableString() {
        this(DEFAULT_CAPACITY);
    }

    public MutableString(final int capacity) {
        super(new byte[capacity], 0, 0);
    }

    public MutableString(final GnomeString other) {
        super(new byte[other.capacity()], 0, other.length());
        other.copyBytes(this.bytes);
    }

    public MutableString(final String other) {
        super(other);
    }

    public void reset() {
        length = 0;
        hash = 0;
        offset = 0;
    }

    public void copy(final GnomeString other) {
        reset();
        if (other != null) {
            this.length = other.length();
            ArrayCopy.arraycopy(other.getBytes(), other.offset(), this.bytes, 0, this.length);
        }
    }

    public void setLength(final int length) {
        hash = 0;
        this.length = length;
    }

    public MutableString append(final byte b) {
        hash = 0;
        this.bytes[this.offset + this.length++] = b;
        return this;
    }

    public MutableString appendString(final String other) {
        hash = 0;
        ArrayCopy.arraycopy(other.getBytes(), 0, this.bytes, this.length + this.offset, other.length());
        this.length += other.length();
        return this;
    }

    public MutableString appendString(final GnomeString other) {
        hash = 0;
        ArrayCopy.arraycopy(other.getBytes(), other.offset(), this.bytes, this.length + this.offset, other.length());
        this.length += other.length();
        return this;
    }

    public MutableString appendNaturalIntAscii(final int i) {
        hash = 0;
        int digits = ByteBufferUtils.putNaturalIntAscii(this.bytes, this.offset + this.length, i);
        this.length += digits;
        return this;
    }
}
