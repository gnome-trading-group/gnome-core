package group.gnometrading.disruptor;

import org.agrona.MutableDirectBuffer;

public class SBEWrapper {

    public MutableDirectBuffer buffer;

    public void update(final MutableDirectBuffer buffer) {
        this.buffer = buffer;
    }
}
