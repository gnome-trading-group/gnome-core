package group.gnometrading.codecs.json;

import group.gnometrading.strings.GnomeString;
import group.gnometrading.utils.ByteBufferUtils;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class JsonEncoder {

    private static final byte[] NULL = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE = "false".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final int NIBBLE_BITS = 4;
    private static final int NIBBLE_MASK = 0xF;
    private static final char ASCII_LIMIT = 0x80;
    private static final char FIRST_PRINTABLE = 0x20;
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;
    // UTF-8: a lead byte, then 6 bits in each continuation byte.
    private static final int TWO_BYTE_LIMIT = 0x800;
    private static final int THREE_BYTE_LIMIT = 0x10000;
    private static final int LEAD_OF_TWO = 0xC0;
    private static final int LEAD_OF_THREE = 0xE0;
    private static final int LEAD_OF_FOUR = 0xF0;
    private static final int CONTINUATION = 0x80;
    private static final int CONTINUATION_BITS = 6;
    private static final int CONTINUATION_MASK = 0x3F;

    private ByteBuffer buffer;

    public JsonEncoder() {}

    public void wrap(final ByteBuffer newBuffer) {
        this.buffer = newBuffer;
    }

    public JsonEncoder writeObjectStart() {
        this.buffer.put((byte) '{');
        return this;
    }

    public JsonEncoder writeObjectEnd() {
        this.buffer.put((byte) '}');
        return this;
    }

    public JsonEncoder writeArrayStart() {
        this.buffer.put((byte) '[');
        return this;
    }

    public JsonEncoder writeArrayEnd() {
        this.buffer.put((byte) ']');
        return this;
    }

    public JsonEncoder writeComma() {
        this.buffer.put((byte) ',');
        return this;
    }

    public JsonEncoder writeColon() {
        this.buffer.put((byte) ':');
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final int value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final int value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final String value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final String value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final GnomeString value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final GnomeString value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final double value, final int scale) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value, scale);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final double value, final int scale) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value, scale);
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final long value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final long value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final String key, final boolean value) {
        this.writeString(key);
        this.writeColon();
        this.writeBoolean(value);
        return this;
    }

    public JsonEncoder writeObjectEntry(final GnomeString key, final boolean value) {
        this.writeString(key);
        this.writeColon();
        this.writeBoolean(value);
        return this;
    }

    /** A JSON string: quotes, backslashes and control characters escaped, anything beyond ASCII as UTF-8. */
    public JsonEncoder writeString(final String value) {
        this.buffer.put((byte) '"');
        int index = 0;
        while (index < value.length()) {
            final char at = value.charAt(index++);
            if (at < ASCII_LIMIT) {
                writeAscii(at);
            } else if (Character.isHighSurrogate(at)
                    && index < value.length()
                    && Character.isLowSurrogate(value.charAt(index))) {
                writeUtf8(Character.toCodePoint(at, value.charAt(index++)));
            } else if (Character.isSurrogate(at)) {
                // An unpaired surrogate has no UTF-8 encoding; the replacement character stands in for it.
                writeUtf8(REPLACEMENT_CHARACTER);
            } else {
                writeUtf8(at);
            }
        }
        this.buffer.put((byte) '"');
        return this;
    }

    /** As {@link #writeString(String)}; the value's bytes are UTF-8 already, so only ASCII ones need escaping. */
    public JsonEncoder writeString(final GnomeString value) {
        this.buffer.put((byte) '"');
        for (int i = 0; i < value.length(); i++) {
            final byte at = value.byteAt(i);
            if (at >= 0) {
                writeAscii((char) at);
            } else {
                this.buffer.put(at);
            }
        }
        this.buffer.put((byte) '"');
        return this;
    }

    private void writeAscii(final char at) {
        if (at == '"' || at == '\\') {
            this.buffer.put((byte) '\\').put((byte) at);
        } else if (at >= FIRST_PRINTABLE) {
            this.buffer.put((byte) at);
        } else if (at == '\n') {
            this.buffer.put((byte) '\\').put((byte) 'n');
        } else if (at == '\r') {
            this.buffer.put((byte) '\\').put((byte) 'r');
        } else if (at == '\t') {
            this.buffer.put((byte) '\\').put((byte) 't');
        } else {
            this.buffer.put((byte) '\\').put((byte) 'u').put((byte) '0').put((byte) '0');
            this.buffer.put(HEX[at >> NIBBLE_BITS]).put(HEX[at & NIBBLE_MASK]);
        }
    }

    private void writeUtf8(final int codePoint) {
        if (codePoint < TWO_BYTE_LIMIT) {
            this.buffer.put((byte) (LEAD_OF_TWO | codePoint >> CONTINUATION_BITS));
        } else if (codePoint < THREE_BYTE_LIMIT) {
            this.buffer.put((byte) (LEAD_OF_THREE | codePoint >> 2 * CONTINUATION_BITS));
            this.buffer.put(continuation(codePoint >> CONTINUATION_BITS));
        } else {
            this.buffer.put((byte) (LEAD_OF_FOUR | codePoint >> 3 * CONTINUATION_BITS));
            this.buffer.put(continuation(codePoint >> 2 * CONTINUATION_BITS));
            this.buffer.put(continuation(codePoint >> CONTINUATION_BITS));
        }
        this.buffer.put(continuation(codePoint));
    }

    private static byte continuation(final int bits) {
        return (byte) (CONTINUATION | bits & CONTINUATION_MASK);
    }

    public JsonEncoder writeNumber(final int number) {
        ByteBufferUtils.putIntAscii(this.buffer, number);
        return this;
    }

    public JsonEncoder writeNumber(final long number) {
        ByteBufferUtils.putLongAscii(this.buffer, number);
        return this;
    }

    public JsonEncoder writeNumber(final double number, final int scale) {
        ByteBufferUtils.putDoubleAscii(this.buffer, number, scale);
        return this;
    }

    public JsonEncoder writeBoolean(final boolean value) {
        if (value) {
            return writeBytes(TRUE);
        } else {
            return writeBytes(FALSE);
        }
    }

    public JsonEncoder writeNull() {
        return writeBytes(NULL);
    }

    private JsonEncoder writeBytes(final byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            this.buffer.put(bytes[i]);
        }
        return this;
    }
}
