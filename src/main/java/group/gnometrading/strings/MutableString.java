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

    public final void reset() {
        length = 0;
        hash = 0;
        offset = 0;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public void copy(final GnomeString other) {
        reset();
        if (other != null) {
            this.length = other.length();
            ArrayCopy.arraycopy(other.getBytes(), other.offset(), this.bytes, 0, this.length);
        }
    }

    public final void setLength(final int length) {
        hash = 0;
        this.length = length;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public MutableString append(final byte value) {
        hash = 0;
        this.bytes[this.offset + this.length++] = value;
        return this;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public MutableString appendString(final String other) {
        hash = 0;
        ArrayCopy.arraycopy(other.getBytes(), 0, this.bytes, this.length + this.offset, other.length());
        this.length += other.length();
        return this;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public MutableString appendString(final GnomeString other) {
        hash = 0;
        ArrayCopy.arraycopy(other.getBytes(), other.offset(), this.bytes, this.length + this.offset, other.length());
        this.length += other.length();
        return this;
    }

    @SuppressWarnings("checkstyle:DesignForExtension")
    public MutableString appendNaturalIntAscii(final int value) {
        hash = 0;
        int digits = ByteBufferUtils.putNaturalIntAscii(this.bytes, this.offset + this.length, value);
        this.length += digits;
        return this;
    }
}
