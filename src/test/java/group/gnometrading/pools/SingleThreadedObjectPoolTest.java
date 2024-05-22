package group.gnometrading.pools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SingleThreadedObjectPoolTest {

    @Test
    void testAcquire() {
        var pool = new SingleThreadedObjectPool<Holder>(() -> new Holder(), 5);
        for (int i = 0; i < 5; i++) {
            var node = pool.acquire();
            assertEquals(0, node.getItem().value);
            node.getItem().value = 1;
        }

        var node = pool.acquire();
        assertEquals(1, pool.getAdditionalNodesCreated());
        assertEquals(0, node.getItem().value);
    }

    @Test
    void testRelease() {
        var pool = new SingleThreadedObjectPool<Holder>(() -> new Holder(), 1);
        var node = pool.acquire();
        node.getItem().value = 10;
        pool.release(node);

        node = pool.acquire();
        assertEquals(10, node.getItem().value);
        assertEquals(0, pool.getAdditionalNodesCreated());
    }

    @Test
    void testReleaseAll() {
        var pool = new SingleThreadedObjectPool<Holder>(() -> new Holder(), 5);
        for (int i = 0; i < 5; i++) {
            var node = pool.acquire();
            assertEquals(0, node.getItem().value);
            node.getItem().value = 1;
        }

        pool.releaseAll();
        for (int i = 0; i < 5; i++) {
            var node = pool.acquire();
            assertEquals(1, node.getItem().value);
        }

        assertEquals(0, pool.getAdditionalNodesCreated());
    }

    static class Holder {
        int value = 0;
    }

}