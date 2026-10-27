package bwapi;

import org.junit.Test;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class BWClientStartupTest {
    private static class WaitingForServer extends RuntimeException {
    }

    @Test
    public void doesNotAllocateFrameBuffersWhileWaitingForServer() {
        BWClient bwClient = new BWClient(new DefaultBWListener());
        Client client = mock(Client.class);
        bwClient.setClient(client);
        BWClientConfiguration configuration = new BWClientConfiguration.Builder()
                .withAsync(true)
                .withAsyncFrameBufferCapacity(3)
                .build();
        BufferPoolMXBean directBuffers = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream().filter(pool -> "direct".equals(pool.getName())).findFirst().get();
        long bytesBeforeStart = directBuffers.getMemoryUsed();
        doAnswer(invocation -> {
            assertTrue("Connection attempts must precede allocation of even one game-state buffer",
                    directBuffers.getMemoryUsed() - bytesBeforeStart < ClientData.GameData.SIZE);
            assertNull(bwClient.getPerformanceMetrics());
            throw new WaitingForServer();
        }).when(client).reconnect();

        assertThrows(WaitingForServer.class, () -> bwClient.startGame(configuration));
    }
}
