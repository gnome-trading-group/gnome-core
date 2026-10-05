package group.gnometrading.concurrent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IsolatedCoreAllocatorTest {

    private static final GnomeAgent HOT = new GnomeAgent() {
        @Override
        public int doWork() {
            return 0;
        }

        @Override
        public ThreadProfile threadProfile() {
            return ThreadProfile.HOT_PATH;
        }
    };

    private static final GnomeAgent BACKGROUND = () -> 0;

    @Test
    void testHotAgentsGetDistinctDedicatedCores() {
        IsolatedCoreAllocator allocator = new IsolatedCoreAllocator(new int[] {2, 3}, new int[] {0, 1});

        Placement first = allocator.allocate(HOT);
        Placement second = allocator.allocate(HOT);

        assertArrayEquals(new int[] {2}, first.cpus());
        assertArrayEquals(new int[] {3}, second.cpus());
        assertTrue(first.dedicated());
        assertTrue(second.dedicated());
    }

    @Test
    void testBackgroundAgentsShareHousekeepingCores() {
        IsolatedCoreAllocator allocator = new IsolatedCoreAllocator(new int[] {2, 3}, new int[] {0, 1});

        Placement placement = allocator.allocate(BACKGROUND);

        assertArrayEquals(new int[] {0, 1}, placement.cpus());
        assertFalse(placement.dedicated());
    }

    @Test
    void testBackgroundAgentsDoNotConsumeIsolatedCores() {
        IsolatedCoreAllocator allocator = new IsolatedCoreAllocator(new int[] {2}, new int[] {0, 1});

        allocator.allocate(BACKGROUND);
        Placement hot = allocator.allocate(HOT);

        assertArrayEquals(new int[] {2}, hot.cpus());
        assertTrue(hot.dedicated());
    }

    @Test
    void testHotOverflowFallsBackToHousekeepingWithoutDedication() {
        IsolatedCoreAllocator allocator = new IsolatedCoreAllocator(new int[] {2}, new int[] {0, 1});

        allocator.allocate(HOT);
        Placement overflow = allocator.allocate(HOT);

        assertArrayEquals(new int[] {0, 1}, overflow.cpus());
        assertFalse(overflow.dedicated());
    }

    @Test
    void testRequiresHousekeepingCores() {
        assertThrows(IllegalArgumentException.class, () -> new IsolatedCoreAllocator(new int[] {2}, new int[0]));
    }

    @Test
    void testParseCpuList() {
        assertArrayEquals(new int[] {2, 3, 4, 5}, IsolatedCoreAllocator.parseCpuList("2-5"));
        assertArrayEquals(new int[] {0, 1, 8, 9}, IsolatedCoreAllocator.parseCpuList("0-1,8-9"));
        assertArrayEquals(new int[] {1, 3, 7}, IsolatedCoreAllocator.parseCpuList(" 7, 1,3 "));
        assertArrayEquals(new int[] {0, 1}, IsolatedCoreAllocator.parseCpuList("0-1,1"));
        assertArrayEquals(new int[0], IsolatedCoreAllocator.parseCpuList(""));
        assertArrayEquals(new int[0], IsolatedCoreAllocator.parseCpuList(null));
    }

    @Test
    void testParseCpuListRejectsBackwardsRange() {
        assertThrows(IllegalArgumentException.class, () -> IsolatedCoreAllocator.parseCpuList("5-2"));
    }
}
