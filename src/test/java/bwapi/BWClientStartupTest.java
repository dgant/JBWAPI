package bwapi;

import org.junit.Test;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Guards startup ordering at the connection boundary without a BWAPI server or
 * elapsed-time threshold. Async game buffers and metrics must be created only
 * after connecting, so they cannot delay the first discovery attempt.
 */
public class BWClientStartupTest {
    private static class StopAfterConnectionAttempt extends RuntimeException {
    }

    @Test
    public void attemptsConnectionBeforeAllocatingFrameBuffersOrMetrics() {
        BWClient bwClient = new BWClient(new DefaultBWListener());
        BufferPoolMXBean directBuffers = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream()
                .filter(pool -> "direct".equals(pool.getName()))
                .findFirst()
                .get();
        long bytesBeforeStart = directBuffers.getMemoryUsed();
        bwClient.setClient(new Client(bwClient) {
            @Override
            void reconnect() {
                assertTrue("Connection attempts must precede allocation of even one game-state buffer",
                        directBuffers.getMemoryUsed() - bytesBeforeStart < ClientData.GameData.SIZE);
                assertNull(bwClient.getPerformanceMetrics());
                throw new StopAfterConnectionAttempt();
            }
        });
        BWClientConfiguration configuration = new BWClientConfiguration()
                .withAsync(true)
                .withAsyncFrameBufferCapacity(3);
        assertThrows(StopAfterConnectionAttempt.class, () -> bwClient.startGame(configuration));
    }
}
