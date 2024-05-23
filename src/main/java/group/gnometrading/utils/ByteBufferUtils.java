package group.gnometrading.utils;

import group.gnometrading.strings.GnomeString;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    public static void putString(final ByteBuffer buffer, final String string) {
        for (int i = 0; i < string.length(); i++) {
            buffer.put((byte) string.charAt(i));
        }
    }

    public static void putString(final ByteBuffer buffer, final GnomeString string) {
        for (int i = 0; i < string.length(); i++) {
            buffer.put(string.byteAt(i));
        }
    }

}
