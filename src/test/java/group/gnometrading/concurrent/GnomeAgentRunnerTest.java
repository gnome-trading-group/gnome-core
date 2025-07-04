package group.gnometrading.concurrent;

import org.agrona.ErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GnomeAgentRunnerTest {

    @Mock
    private GnomeAgent mockAgent;

    @Mock
    private ErrorHandler mockErrorHandler;

    private GnomeAgentRunner runner;

    @BeforeEach
    void setUp() {
        when(mockAgent.roleName()).thenReturn("TestAgent");
        runner = new GnomeAgentRunner(mockAgent, mockErrorHandler);
    }

    @Test
    void testConstructor() {
        assertNotNull(runner);
        assertFalse(runner.isClosed());
        assertNull(runner.getThread());
        assertEquals(mockAgent, runner.getAgent());
    }

    @Test
    void testStartOnThread() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        Thread thread = GnomeAgentRunner.startOnThread(runner);
        
        assertNotNull(thread);
        assertEquals("TestAgent", thread.getName());

        Thread.sleep(100);
        assertTrue(thread.isAlive());

        assertNotNull(runner.getThread());
        assertEquals(thread, runner.getThread());
        
        runner.close();
    }

    @Test
    void testRunLifecycle() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the agent to start
        Thread.sleep(100);
        
        verify(mockAgent).onStart();
        verify(mockAgent, atLeastOnce()).doWork();
        
        runner.close();

        verify(mockAgent).onClose();
        assertTrue(runner.isClosed());
    }

    @Test
    void testRunWithOnStartException() throws Exception {
        RuntimeException startException = new RuntimeException("Start failed");
        doThrow(startException).when(mockAgent).onStart();
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the exception to be handled
        Thread.sleep(100);
        
        verify(mockErrorHandler).onError(startException);
        assertTrue(runner.isClosed());
    }

    @Test
    void testRunWithOnCloseException() throws Exception {
        when(mockAgent.doWork()).thenReturn(0); // Stop after first iteration
        RuntimeException closeException = new RuntimeException("Close failed");
        doThrow(closeException).when(mockAgent).onClose();
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the agent to finish
        Thread.sleep(100);

        runner.close();
        
        verify(mockErrorHandler).onError(closeException);
        assertTrue(runner.isClosed());
    }

    @Test
    void testDoWorkException() throws Exception {
        RuntimeException workException = new RuntimeException("Work failed");
        when(mockAgent.doWork()).thenThrow(workException);
        
        Thread thread = new Thread(runner);
        thread.start();

        // Wait for the exception to be handled
        Thread.sleep(100);
        
        verify(mockErrorHandler, atLeastOnce()).onError(workException);
        assertFalse(runner.isClosed());

        thread.interrupt();
        thread.join(1000);

        assertTrue(runner.isClosed());
    }

    @Test
    void testInterruption() throws Exception {
        when(mockAgent.doWork()).thenReturn(0);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait a bit for the thread to start
        Thread.sleep(100);
        
        // Interrupt the thread
        thread.interrupt();
        
        // Wait for the interruption to be handled
        Thread.sleep(100);
        
        assertTrue(runner.isClosed());
        thread.join(1000);
    }

    @Test
    void testInterruptionDuringWork() throws Exception {
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch workInterrupted = new CountDownLatch(1);
        
        when(mockAgent.doWork()).thenAnswer(invocation -> {
            workStarted.countDown();
            workInterrupted.await();
            return 1;
        });
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for work to start
        assertTrue(workStarted.await(1000, TimeUnit.MILLISECONDS));
        
        // Interrupt during work
        thread.interrupt();
        workInterrupted.countDown();
        
        // Wait for the interruption to be handled
        Thread.sleep(100);
        
        assertTrue(runner.isClosed());
        thread.join(1000);
    }

    @Test
    void testCloseBeforeStart() throws Exception {
        // Close before the runner has started
        runner.close();
        
        verify(mockAgent).onClose();
        assertTrue(runner.isClosed());
        assertEquals(GnomeAgentRunner.TOMBSTONE, runner.getThread());
    }

    @Test
    void testCloseAfterStart() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Close the runner
        runner.close();
        
        // Wait for the thread to finish
        thread.join(1000);
        
        verify(mockAgent).onClose();
        assertTrue(runner.isClosed());
    }

    @Test
    void testCloseMultipleTimes() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Close multiple times
        runner.close();
        runner.close();
        runner.close();
        
        // Should only call onClose once
        verify(mockAgent, times(1)).onClose();
        
        thread.join(1000);
    }

    @Test
    void testWorkCountZeroWithInterruption() throws Exception {
        when(mockAgent.doWork()).thenReturn(0);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Interrupt the thread
        thread.interrupt();
        
        // Wait for the interruption to be handled
        Thread.sleep(100);
        
        assertTrue(runner.isClosed());
        thread.join(1000);
    }

    @Test
    void testWorkCountPositive() throws Exception {
        when(mockAgent.doWork()).thenReturn(5);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Verify work was called
        verify(mockAgent, atLeastOnce()).doWork();
        
        // Stop the runner
        runner.close();
        thread.join(1000);
    }

    @Test
    void testNullErrorHandler() {
        GnomeAgentRunner runnerWithNullHandler = new GnomeAgentRunner(mockAgent, null);
        
        // Should not throw exception when error handler is null
        assertDoesNotThrow(() -> {
            Thread thread = new Thread(runnerWithNullHandler);
            thread.start();
            Thread.sleep(100);
            runnerWithNullHandler.close();
            thread.join(1000);
        });
    }

    @Test
    void testConcurrentAccess() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Test concurrent access to getter methods
        assertNotNull(runner.getAgent());
        assertFalse(runner.isClosed());
        assertNotNull(runner.getThread());
        
        runner.close();
        thread.join(1000);
    }

    @Test
    void testThreadReuse() throws Exception {
        when(mockAgent.doWork()).thenReturn(1);
        
        // Try to run the same runner twice
        Thread thread1 = new Thread(runner);
        thread1.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Try to run again - should not set the thread
        Thread thread2 = new Thread(runner);
        thread2.start();
        
        // Wait a bit
        Thread.sleep(100);
        
        // The thread should still be the first one
        assertEquals(thread1, runner.getThread());
        
        runner.close();
        thread1.join(1000);
        thread2.join(1000);
    }

    @Test
    void testInterruptedExceptionDuringWork() throws Exception {
        when(mockAgent.doWork()).thenThrow(new InterruptedException("Interrupted during work"));
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the exception to be handled
        Thread.sleep(100);
        
        assertTrue(runner.isClosed());
    }

    @Test
    void testErrorDuringWorkWithInterruption() throws Exception {
        RuntimeException workException = new RuntimeException("Work failed");
        when(mockAgent.doWork()).thenThrow(workException);
        
        Thread thread = new Thread(runner);
        thread.start();
        
        // Wait for the thread to start
        Thread.sleep(100);
        
        // Interrupt the thread
        thread.interrupt();
        
        // Wait for the exception to be handled
        Thread.sleep(100);
        
        verify(mockErrorHandler, atLeastOnce()).onError(workException);
        assertTrue(runner.isClosed());
        
        thread.join(1000);
    }

    @Test
    void testCloseWithNullThread() throws Exception {
        // Create a runner that hasn't been started
        GnomeAgentRunner newRunner = new GnomeAgentRunner(mockAgent, mockErrorHandler);
        
        // Close should handle null thread gracefully
        newRunner.close();
        
        verify(mockAgent).onClose();
        assertTrue(newRunner.isClosed());
    }

}