package bwapi;

import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SynchronizationTest {

    private void sleepUnchecked(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch(InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    private String describeApproximateExpectation(double expected, double actual, double margin) {
        return "Expected " + expected + " == " + actual + " +/- " + margin;
    }

    private boolean measureApproximateEquality(double expected, double actual, double margin) {
        return expected + margin >= actual && expected - margin <= actual;
    }

    private void assertWithin(String message, double expected, double actual, double margin) {
        assertTrue(
                message + ": " + describeApproximateExpectation(expected, actual, margin),
                measureApproximateEquality(expected, actual, margin));
    }

    @Test
    public void sync_IfException_ThrowException() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = false;
        environment.onFrame(0, () -> { throw new RuntimeException("Simulated bot exception"); });
        assertThrows(RuntimeException.class, environment::runGame);
    }

    @Test
    public void async_IfException_ThrowException() {
        // An exception in the bot thread must be re-thrown by the main thread.
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.asyncFrameBufferSize = 3;
        environment.onFrame(0, () -> { throw new RuntimeException("Simulated bot exception"); });
        assertThrows(RuntimeException.class, environment::runGame);
    }

    @Test
    public void sync_IfDelay_ThenNoBuffer() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = false;
        environment.configuration.maxFrameDurationMs = 1;
        environment.configuration.asyncFrameBufferSize = 3;

        IntStream.range(0, 5).forEach(frame -> {
            environment.onFrame(frame, () -> {
                sleepUnchecked(5);
                assertEquals(0, environment.bwClient.framesBehind());
                assertEquals(frame, environment.bwClient.getGame().getFrameCount());
                assertEquals(frame, environment.liveGameData().getFrameCount());
            });
        });

        environment.runGame();
    }

    @Test
    public void async_IfBotDelay_ThenClientBuffers() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.maxFrameDurationMs = 10;
        environment.configuration.asyncFrameBufferSize = 4;

        environment.onFrame(1, () -> {
            sleepUnchecked(50);
            assertEquals("Bot should be observing an old frame", 1, environment.bwClient.getGame().getFrameCount());
            assertEquals("Client should be as far ahead as the frame buffer allows", 5, environment.liveGameData().getFrameCount());
            assertEquals("Bot should be behind the live game", 4, environment.bwClient.framesBehind());
        });

        environment.onFrame(6, () -> { // Maybe it should be possible to demand that these assertions pass a frame earlier?
            assertEquals("Bot should be observing the live frame", 6, environment.bwClient.getGame().getFrameCount());
            assertEquals("Client should not be ahead of the bot", 6, environment.liveGameData().getFrameCount());
            assertEquals("Bot should not be behind the live game", 0, environment.bwClient.framesBehind());
        });

        environment.runGame();
    }

    @Test
    public void async_IfBotDelay_ThenClientStalls() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.maxFrameDurationMs = 50;
        environment.configuration.asyncFrameBufferSize = 5;

        environment.onFrame(1, () -> {
            sleepUnchecked(125);
            assertEquals("3: Bot should be observing an old frame", 1, environment.bwClient.getGame().getFrameCount());
            assertEquals("3: Client should have progressed as slowly as possible", 3, environment.liveGameData().getFrameCount());
            assertEquals("3: Bot should be behind the live game by as little as possible", 2, environment.bwClient.framesBehind());
            sleepUnchecked(50);
            assertEquals("4: Bot should be observing an old frame", 1, environment.bwClient.getGame().getFrameCount());
            assertEquals("4: Client should have progressed as slowly as possible", 4, environment.liveGameData().getFrameCount());
            assertEquals("4: Bot should be behind the live game by as little as possible", 3, environment.bwClient.framesBehind());
            sleepUnchecked(50);
            assertEquals("5: Bot should be observing an old frame", 1, environment.bwClient.getGame().getFrameCount());
            assertEquals("5: Client should have progressed as slowly as possible", 5, environment.liveGameData().getFrameCount());
            assertEquals("5: Bot should be behind the live game by as little as possible", 4, environment.bwClient.framesBehind());
        });

        environment.runGame();
    }

    @Test
    public void async_IfFrameZeroWaitsEnabled_ThenAllowInfiniteTime() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.unlimitedFrameZero = true;
        environment.configuration.maxFrameDurationMs = 5;
        environment.configuration.asyncFrameBufferSize = 2;

        environment.onFrame(0, () -> {
            sleepUnchecked(50);
            assertEquals("Bot should still be on frame zero", 0, environment.bwClient.getGame().getFrameCount());
            assertEquals("Client should still be on frame zero", 0, environment.liveGameData().getFrameCount());
            assertEquals("Bot should not be behind the live game", 0, environment.bwClient.framesBehind());
        });

        environment.runGame();
    }

    @Test
    public void async_IfFrameZeroWaitsDisabled_ThenClientBuffers() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.unlimitedFrameZero = false;
        environment.configuration.maxFrameDurationMs = 5;
        environment.configuration.asyncFrameBufferSize = 2;

        environment.onFrame(0, () -> {
            sleepUnchecked(50);
            assertEquals("Bot should still be on frame zero", 0, environment.bwClient.getGame().getFrameCount());
            assertEquals("Client should have advanced to the next frame", 2, environment.liveGameData().getFrameCount());
            assertEquals("Bot should be behind the live game", 2, environment.bwClient.framesBehind());
        });

        environment.runGame();
    }

    @Test
    public void async_MeasurePerformance_TotalFrameDuration() {
        final int frames = 10;
        final int frameSleep = 20;
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.unlimitedFrameZero = true;
        environment.configuration.maxFrameDurationMs = frameSleep + 20;
        IntStream.range(0, frames).forEach(i -> environment.onFrame(i, () -> {
            sleepUnchecked(frameSleep);
        }));
        environment.runGame(frames);

        // Assume copying accounts for almost all the frame time except what the bot uses
        double meanCopy = environment.metrics().copyingToBuffer.avgValue;
        assertWithin("Total frame duration: Average", environment.metrics().totalFrameDuration.avgValue, meanCopy + frameSleep, MS_MARGIN);
    }

    @Test
    public void async_MeasurePerformance_CopyingToBuffer() {
        // Somewhat lazy test; just verify that we're getting sane values
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.runGame(20);
        final double minObserved = 2;
        final double maxObserved = 15;
        final double meanObserved = (minObserved + maxObserved) / 2;
        final double rangeObserved = (maxObserved - minObserved) / 2;
        assertWithin("Copy to buffer: minimum", environment.metrics().copyingToBuffer.minValue, meanObserved, rangeObserved);
        assertWithin("Copy to buffer: maximum", environment.metrics().copyingToBuffer.maxValue, meanObserved, rangeObserved);
        assertWithin("Copy to buffer: average", environment.metrics().copyingToBuffer.avgValue, meanObserved, rangeObserved);
        assertWithin("Copy to buffer: previous", environment.metrics().copyingToBuffer.lastValue, meanObserved, rangeObserved);
    }

    @Test
    public void async_MeasurePerformance_FrameBufferSizeAndFramesBehind() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.unlimitedFrameZero = true;
        environment.configuration.asyncFrameBufferSize = 3;
        environment.configuration.maxFrameDurationMs = 20;

        environment.onFrame(5, () -> {
            assertWithin("5: Frame buffer average", 0, environment.metrics().frameBufferSize.avgValue, 0.1);
            assertWithin("5: Frame buffer minimum", 0, environment.metrics().frameBufferSize.minValue, 0.1);
            assertWithin("5: Frame buffer maximum", 0, environment.metrics().frameBufferSize.maxValue, 0.1);
            assertWithin("5: Frame buffer previous", 0, environment.metrics().frameBufferSize.lastValue, 0.1);
            assertWithin("5: Frames behind average", 0, environment.metrics().framesBehind.avgValue, 0.1);
            assertWithin("5: Frames behind minimum", 0, environment.metrics().framesBehind.minValue, 0.1);
            assertWithin("5: Frames behind maximum", 0, environment.metrics().framesBehind.maxValue, 0.1);
            assertWithin("5: Frames behind previous", 0, environment.metrics().framesBehind.lastValue, 0.1);
            sleepUnchecked(200);
        });
        environment.onFrame(6, () -> {
            assertWithin("6: Frame buffer average", 1 / 6.0 + 2 / 7.0, environment.metrics().frameBufferSize.avgValue, 0.1);
            assertWithin("6: Frame buffer minimum", 0, environment.metrics().frameBufferSize.minValue, 0.1);
            assertWithin("6: Frame buffer maximum", 2, environment.metrics().frameBufferSize.maxValue, 0.1);
            assertWithin("6: Frame buffer previous", 2, environment.metrics().frameBufferSize.lastValue, 0.1);
            assertWithin("6: Frames behind average", 1 / 6.0, environment.metrics().framesBehind.avgValue, 0.1);
            assertWithin("6: Frames behind minimum", 0, environment.metrics().framesBehind.minValue, 0.1);
            assertWithin("6: Frames behind maximum", 1, environment.metrics().framesBehind.maxValue, 0.1);
            assertWithin("6: Frames behind previous", 1, environment.metrics().framesBehind.lastValue, 0.1);
        });

        environment.runGame(8);
    }

    /**
     * Number of milliseconds of leeway to give in potentially noisy performance metrics.
     * Increase if tests are flaky due to variance in execution speed.
     */
    private final static long MS_MARGIN = 10;

    @Test
    public void MeasurePerformance_BotResponse() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();

        // Frame zero appears to take an extra 60ms, so let's disable timing for it
        // (and also verify that we omit frame zero from performance metrics)
        environment.configuration.unlimitedFrameZero = true;

        environment.onFrame(1, () -> {
            sleepUnchecked(100);
        });
        environment.onFrame(2, () -> {
            assertWithin("2: Bot response average", 100, environment.metrics().botResponse.avgValue, MS_MARGIN);
            assertWithin("2: Bot response minimum", 100, environment.metrics().botResponse.minValue, MS_MARGIN);
            assertWithin("2: Bot response maximum", 100, environment.metrics().botResponse.maxValue, MS_MARGIN);
            assertWithin("2: Bot response previous", 100, environment.metrics().botResponse.lastValue, MS_MARGIN);
            sleepUnchecked(300);
        });
        environment.onFrame(3, () -> {
            assertWithin("3: Bot response average", 200, environment.metrics().botResponse.avgValue, MS_MARGIN);
            assertWithin("3: Bot response minimum", 100, environment.metrics().botResponse.minValue, MS_MARGIN);
            assertWithin("3: Bot response maximum", 300, environment.metrics().botResponse.maxValue, MS_MARGIN);
            assertWithin("3: Bot response previous", 300, environment.metrics().botResponse.lastValue, MS_MARGIN);
            sleepUnchecked(200);
        });

        environment.runGame(4);

        assertWithin("Final: Bot response average", 200, environment.metrics().botResponse.avgValue, MS_MARGIN);
        assertWithin("Final: Bot response minimum", 100, environment.metrics().botResponse.minValue, MS_MARGIN);
        assertWithin("Final: Bot response maximum", 300, environment.metrics().botResponse.maxValue, MS_MARGIN);
        assertWithin("Final: Bot response previous", 200, environment.metrics().botResponse.lastValue, MS_MARGIN);
    }

    @Test
    public void MeasurePerformance_BwapiResponse() {
        final long bwapiDelayMs = 50;
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.setBwapiDelayMs(bwapiDelayMs);
        environment.runGame();
        System.out.println(environment.metrics());
        assertWithin("BWAPI Response: Average", environment.metrics().bwapiResponse.avgValue, bwapiDelayMs, MS_MARGIN);
    }

    @Test
    public void MeasurePerformance_BotIdle() {
        final long bwapiDelayMs = 10;
        final int frames = 10;
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.asyncFrameBufferSize = 3;
        environment.configuration.unlimitedFrameZero = true;
        environment.setBwapiDelayMs(bwapiDelayMs);
        environment.runGame(frames);
        double expected = environment.metrics().copyingToBuffer.avgValue + bwapiDelayMs;
        assertWithin("Bot Idle: Average", environment.metrics().botIdle.avgValue, expected, MS_MARGIN);
    }

    @Test
    public void async_MeasurePerformance_IntentionallyBlocking() {
        SynchronizationEnvironment environment = new SynchronizationEnvironment();
        environment.configuration.async = true;
        environment.configuration.unlimitedFrameZero = true;
        environment.configuration.asyncFrameBufferSize = 2;
        environment.configuration.maxFrameDurationMs = 20;
        final int frameDelayMs = 100;
        environment.onFrame(1, () -> {
            sleepUnchecked(100);
        });
        environment.onFrame(2, () -> {
            assertWithin(
                    "2: Intentionally blocking previous",
                    environment.metrics().intentionallyBlocking.lastValue,
                    frameDelayMs - environment.configuration.asyncFrameBufferSize * environment.configuration.maxFrameDurationMs,
                    MS_MARGIN);
            sleepUnchecked(100);
        });
        environment.runGame(3);
    }
}