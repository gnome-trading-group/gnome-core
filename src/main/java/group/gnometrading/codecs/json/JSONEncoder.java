package group.gnometrading.codecs.json;

import group.gnometrading.strings.GnomeString;
import group.gnometrading.utils.ByteBufferUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class JSONEncoder {

    private static final byte[] NULL = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE = "false".getBytes(StandardCharsets.UTF_8);

    private ByteBuffer buffer;

    public JSONEncoder() {}

    public void wrap(final ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public JSONEncoder writeObjectStart() {
        this.buffer.put((byte) '{');
        return this;
    }

    public JSONEncoder writeObjectEnd() {
        this.buffer.put((byte) '}');
        return this;
    }

    public JSONEncoder writeArrayStart() {
        this.buffer.put((byte) '[');
        return this;
    }

    public JSONEncoder writeArrayEnd() {
        this.buffer.put((byte) ']');
        return this;
    }

    public JSONEncoder writeComma() {
        this.buffer.put((byte) ',');
        return this;
    }

    public JSONEncoder writeColon() {
        this.buffer.put((byte) ':');
        return this;
    }

    public JSONEncoder writeObjectEntry(final String key, final int value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final GnomeString key, final int value) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final String key, final String value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final GnomeString key, final String value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final String key, final GnomeString value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final GnomeString key, final GnomeString value) {
        this.writeString(key);
        this.writeColon();
        this.writeString(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final String key, final double value, final int scale) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value, scale);
        return this;
    }

    public JSONEncoder writeObjectEntry(final GnomeString key, final double value, final int scale) {
        this.writeString(key);
        this.writeColon();
        this.writeNumber(value, scale);
        return this;
    }

    public JSONEncoder writeObjectEntry(final String key, final boolean value) {
        this.writeString(key);
        this.writeColon();
        this.writeBoolean(value);
        return this;
    }

    public JSONEncoder writeObjectEntry(final GnomeString key, final boolean value) {
        this.writeString(key);
        this.writeColon();
        this.writeBoolean(value);
        return this;
    }

    public JSONEncoder writeString(final String value) {
        this.buffer.put((byte) '"');
        for (int i = 0; i < value.length(); i++) {
            byte at = (byte) value.charAt(i);
            if (at == '"' || at == '\\') {
                this.buffer.put((byte) '\\');
            }
            this.buffer.put(at);
        }
        this.buffer.put((byte) '"');
        return this;
    }

    public JSONEncoder writeString(final GnomeString value) {
        this.buffer.put((byte) '"');
        for (int i = 0; i < value.length(); i++) {
            byte at = value.byteAt(i);
            if (at == '"' || at == '\\') {
                this.buffer.put((byte) '\\');
            }
            this.buffer.put(at);
        }
        this.buffer.put((byte) '"');
        return this;
    }

    public JSONEncoder writeNumber(final int number) {
        ByteBufferUtils.putIntAscii(this.buffer, number);
        return this;
    }

    public JSONEncoder writeNumber(final double number, final int scale) {
        ByteBufferUtils.putDoubleAscii(this.buffer, number, scale);
        return this;
    }

    public JSONEncoder writeBoolean(final boolean value) {
        if (value) {
            return writeBytes(TRUE);
        } else {
            return writeBytes(FALSE);
        }
    }

    public JSONEncoder writeNull() {
        return writeBytes(NULL);
    }

    private JSONEncoder writeBytes(final byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            this.buffer.put(bytes[i]);
        }
        return this;
    }

}
