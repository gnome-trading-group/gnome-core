package group.gnometrading.utils;

import org.agrona.concurrent.EpochClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleTest {

    @Mock
    private EpochClock mockClock;

    @Mock
    private Runnable mockTask;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(mockClock, mockTask);
    }

    @Test
    void testConstructorWithValidRepeatMillis() {
        Schedule schedule = new Schedule(mockClock, 1000, mockTask);
        assertNotNull(schedule);
    }

    @Test
    void testConstructorWithZeroRepeatMillisThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Schedule(mockClock, 0, mockTask)
        );
        assertEquals("repeatMillis must be > 0", exception.getMessage());
    }

    @Test
    void testConstructorWithNegativeRepeatMillisThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Schedule(mockClock, -1, mockTask)
        );
        assertEquals("repeatMillis must be > 0", exception.getMessage());
    }

    @Test
    void testConstructorWithLargeNegativeRepeatMillisThrowsException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new Schedule(mockClock, -1000000, mockTask)
        );
        assertEquals("repeatMillis must be > 0", exception.getMessage());
    }

    @Test
    void testStartSetsNextFireTime() {
        when(mockClock.time()).thenReturn(1000L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        verify(mockClock).time();
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testStartWithZeroTime() {
        when(mockClock.time()).thenReturn(0L);
        
        Schedule schedule = new Schedule(mockClock, 100, mockTask);
        schedule.start();
        
        assertEquals(100, schedule.millisUntilNext());
    }

    @Test
    void testCheckDoesNotFireBeforeTime() {
        when(mockClock.time()).thenReturn(1000L, 1000L, 1400L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        schedule.check();
        
        verify(mockTask, never()).run();
    }

    @Test
    void testCheckFiresAtExactTime() {
        when(mockClock.time()).thenReturn(1000L, 1500L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        schedule.check();
        
        verify(mockTask, times(1)).run();
    }

    @Test
    void testCheckFiresAfterTime() {
        when(mockClock.time()).thenReturn(1000L, 1600L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        schedule.check();
        
        verify(mockTask, times(1)).run();
    }

    @Test
    void testCheckReschedulesAfterFiring() {
        when(mockClock.time()).thenReturn(1000L, 1500L, 1500L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        schedule.check();
        
        verify(mockTask, times(1)).run();
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testMultipleFireCycles() {
        when(mockClock.time()).thenReturn(
                1000L,  // start
                1500L,  // first check - fires
                1500L,  // millisUntilNext after first fire
                1900L,  // second check - doesn't fire
                2000L,  // third check - fires
                2000L   // millisUntilNext after second fire
        );
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        schedule.check();
        verify(mockTask, times(1)).run();
        assertEquals(500, schedule.millisUntilNext());
        
        schedule.check();
        verify(mockTask, times(1)).run();
        
        schedule.check();
        verify(mockTask, times(2)).run();
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testCheckWithTaskException() {
        when(mockClock.time()).thenReturn(1000L, 1500L, 1500L);
        doThrow(new RuntimeException("Task failed")).when(mockTask).run();
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        assertThrows(RuntimeException.class, schedule::check);
        
        // Verify that nextFireTime was still updated despite the exception
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testCheckWithTaskExceptionStillReschedulesCorrectly() {
        AtomicInteger callCount = new AtomicInteger(0);
        when(mockClock.time()).thenReturn(1000L, 1500L, 1500L, 2000L);
        
        doAnswer(invocation -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("First call fails");
            }
            return null;
        }).when(mockTask).run();
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        // First check throws exception but reschedules
        assertThrows(RuntimeException.class, schedule::check);
        assertEquals(500, schedule.millisUntilNext());
        
        // Second check should work normally
        schedule.check();
        verify(mockTask, times(2)).run();
    }

    @Test
    void testMillisUntilNextBeforeStart() {
        when(mockClock.time()).thenReturn(1000L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        
        // Before start, nextFireTime is 0, so millisUntilNext should return 0 (not negative)
        assertEquals(0, schedule.millisUntilNext());
    }

    @Test
    void testMillisUntilNextImmediatelyAfterStart() {
        when(mockClock.time()).thenReturn(1000L, 1000L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testMillisUntilNextPartiallyElapsed() {
        when(mockClock.time()).thenReturn(1000L, 1300L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        assertEquals(200, schedule.millisUntilNext());
    }

    @Test
    void testMillisUntilNextWhenOverdue() {
        when(mockClock.time()).thenReturn(1000L, 1600L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        // When overdue, should return 0 (not negative)
        assertEquals(0, schedule.millisUntilNext());
    }

    @Test
    void testMillisUntilNextReturnsZeroNotNegative() {
        when(mockClock.time()).thenReturn(1000L, 2000L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        // Even when significantly overdue, should return 0
        assertEquals(0, schedule.millisUntilNext());
    }

    @Test
    void testScheduleWithVeryShortInterval() {
        when(mockClock.time()).thenReturn(1000L, 1001L);
        
        Schedule schedule = new Schedule(mockClock, 1, mockTask);
        schedule.start();
        schedule.check();
        
        verify(mockTask, times(1)).run();
    }

    @Test
    void testScheduleWithVeryLongInterval() {
        when(mockClock.time()).thenReturn(1000L, 1000L);
        
        Schedule schedule = new Schedule(mockClock, Long.MAX_VALUE / 2, mockTask);
        schedule.start();
        
        assertTrue(schedule.millisUntilNext() > 0);
    }

    @Test
    void testMultipleStartCalls() {
        when(mockClock.time()).thenReturn(1000L, 1000L, 2000L, 2000L);

        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        assertEquals(500, schedule.millisUntilNext());

        // Start again - should reset the schedule
        schedule.start();
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testCheckMultipleTimesWithoutFiring() {
        when(mockClock.time()).thenReturn(1000L, 1100L, 1200L, 1300L);
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        
        schedule.check();
        schedule.check();
        schedule.check();
        
        verify(mockTask, never()).run();
    }

    @Test
    void testRapidFireScenario() {
        // Simulate a scenario where check is called multiple times
        // After first fire at 1500, nextFireTime becomes 2000
        // Second check at 1900 shouldn't fire (1900 < 2000)
        // Third check at 2001 should fire (2001 >= 2000)
        when(mockClock.time()).thenReturn(
                1000L,  // start
                1500L,  // first check - fires (1500 >= 1500)
                1500L,  // millisUntilNext after first fire
                1900L,  // second check - doesn't fire (1900 < 2000)
                2001L,  // third check - fires again (2001 >= 2000)
                2001L   // millisUntilNext after second fire
        );

        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();

        schedule.check();
        verify(mockTask, times(1)).run();
        assertEquals(500, schedule.millisUntilNext());

        schedule.check();
        verify(mockTask, times(1)).run();

        schedule.check();
        verify(mockTask, times(2)).run();
        assertEquals(500, schedule.millisUntilNext());
    }

    @Test
    void testTaskExecutionOrder() {
        AtomicInteger executionOrder = new AtomicInteger(0);
        when(mockClock.time()).thenReturn(1000L, 1500L, 1500L);
        
        doAnswer(invocation -> {
            executionOrder.set(1);
            return null;
        }).when(mockTask).run();
        
        Schedule schedule = new Schedule(mockClock, 500, mockTask);
        schedule.start();
        schedule.check();
        
        assertEquals(1, executionOrder.get());
        verify(mockTask, times(1)).run();
    }
}

