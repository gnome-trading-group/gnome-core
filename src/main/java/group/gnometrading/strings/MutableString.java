package group.gnometrading.strings;

import group.gnometrading.utils.ArrayCopy;

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

    public MutableString append(final byte b) {
        hash = 0;
        this.bytes[this.offset + this.length++] = b;
        return this;
    }
}
