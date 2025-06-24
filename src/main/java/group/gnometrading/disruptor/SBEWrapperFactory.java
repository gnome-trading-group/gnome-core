package group.gnometrading.disruptor;

import com.lmax.disruptor.EventFactory;

public class SBEWrapperFactory implements EventFactory<SBEWrapper> {
    @Override
    public SBEWrapper newInstance() {
        return new SBEWrapper();
    }
}
