package group.gnometrading.utils;

public class ArrayCopy {

    private static final int MIN_BYTES_TO_MEMCPY = 1 << 13;

    public static void arraycopy(byte[] src, int srcOffset, byte[] dest, int destOffset, int len) {
        if (len >= MIN_BYTES_TO_MEMCPY) {
            System.arraycopy(src, srcOffset, dest, destOffset, len);
        } else {
            int bytes = 0;
            while(bytes++ < len) {
                dest[destOffset++] = src[srcOffset++];
            }
        }
    }
}
