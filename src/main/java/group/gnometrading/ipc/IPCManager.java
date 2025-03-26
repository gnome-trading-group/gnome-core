package group.gnometrading.ipc;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.collections.Object2IntHashMap;

import java.util.concurrent.atomic.AtomicInteger;

public class IPCManager {

    private static final String CHANNEL = "aeron:ipc";

    private final Aeron aeron;
    private final Object2IntHashMap<String> streamMap;
    private final AtomicInteger counter;

    public IPCManager(Aeron aeron) {
        this.aeron = aeron;
        this.streamMap = new Object2IntHashMap<>(-1);
        this.counter = new AtomicInteger();
    }

    public Subscription addSubscription(final String streamName) {
        final int streamId = this.getStreamId(streamName);

        return aeron.addSubscription(CHANNEL, streamId);
    }

    public Publication addPublication(final String streamName) {
        final int streamId = this.getStreamId(streamName);

        return aeron.addPublication(CHANNEL, streamId);
    }

    public Publication addExclusivePublication(final String streamName) {
        final int streamId = this.getStreamId(streamName);

        return aeron.addExclusivePublication(CHANNEL, streamId);
    }

    private int getStreamId(final String streamName) {
        if (!streamMap.containsKey(streamName)) {
            streamMap.put(streamName, counter.getAndIncrement());
        }

        return streamMap.get(streamName);
    }

}
