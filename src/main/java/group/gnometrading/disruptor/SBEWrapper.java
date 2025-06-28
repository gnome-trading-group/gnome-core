package group.gnometrading.disruptor;

import org.agrona.MutableDirectBuffer;

public class SBEWrapper {

    public MutableDirectBuffer buffer;
    public int offset;
    public int length;

    public void update(final MutableDirectBuffer buffer, final int offset, final int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }
}
