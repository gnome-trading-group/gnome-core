package group.gnometrading.utils;

public final class ArrayCopy {

    private static final int MIN_BYTES_TO_MEMCPY = 1 << 13;

    private ArrayCopy() {}

    public static void arraycopy(byte[] src, int srcOffset, byte[] dest, int destOffset, int len) {
        if (len >= MIN_BYTES_TO_MEMCPY) {
            System.arraycopy(src, srcOffset, dest, destOffset, len);
        } else {
            int srcIdx = srcOffset;
            int destIdx = destOffset;
            for (int bytes = 0; bytes < len; bytes++) {
                dest[destIdx++] = src[srcIdx++];
            }
        }
    }
}
