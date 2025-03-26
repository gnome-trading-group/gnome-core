package group.gnometrading.ipc;

import io.aeron.Aeron;
import io.aeron.ConcurrentPublication;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IPCManagerTest {

    @Mock
    private Aeron aeron;

    @InjectMocks
    private IPCManager ipcManager;

    @Test
    void testAddSubscription() {
        Subscription sub = mock();
        when(aeron.addSubscription("aeron:ipc", 0)).thenReturn(sub);

        final var result = ipcManager.addSubscription("test");
        assertEquals(sub, result);
        verify(aeron, times(1)).addSubscription("aeron:ipc", 0);
    }

    @Test
    void testAddPublication() {
        ConcurrentPublication pub = mock();
        when(aeron.addPublication("aeron:ipc", 0)).thenReturn(pub);

        final var result = ipcManager.addPublication("test");
        assertEquals(pub, result);
        verify(aeron, times(1)).addPublication("aeron:ipc", 0);
    }

    @Test
    void testAddExclusivePublication() {
        ExclusivePublication pub = mock();
        when(aeron.addExclusivePublication("aeron:ipc", 0)).thenReturn(pub);

        final var result = ipcManager.addExclusivePublication("test");
        assertEquals(pub, result);
        verify(aeron, times(1)).addExclusivePublication("aeron:ipc", 0);
    }

    @Test
    void testIncrementsCounter() {
        when(aeron.addPublication(eq("aeron:ipc"), anyInt())).thenReturn(null);
        when(aeron.addSubscription(eq("aeron:ipc"), anyInt())).thenReturn(null);

        ipcManager.addPublication("test");
        ipcManager.addSubscription("test");

        ipcManager.addPublication("test1");
        ipcManager.addPublication("test1");
        ipcManager.addSubscription("test1");

        ipcManager.addSubscription("test2");

        verify(aeron, times(1)).addPublication("aeron:ipc", 0);
        verify(aeron, times(1)).addSubscription("aeron:ipc", 0);

        verify(aeron, times(2)).addPublication("aeron:ipc", 1);
        verify(aeron, times(1)).addSubscription("aeron:ipc", 1);

        verify(aeron, times(1)).addSubscription("aeron:ipc", 2);
    }
}