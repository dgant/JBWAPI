package bwapi;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ConnectedUnitCacheTest {
    @Test(timeout = 10000)
    public void retainedIteratorSurvivesConcurrentNextFrameRefresh() throws Exception {
        Game game = GameBuilder.createGame();
        Unit owner = mock(Unit.class), first = mock(Unit.class), second = mock(Unit.class);
        game.setAllUnits(Arrays.asList(first, second));
        AtomicBoolean pause = new AtomicBoolean();
        CountDownLatch refreshing = new CountDownLatch(1), release = new CountDownLatch(1);
        ConnectedUnitCache cache = new ConnectedUnitCache(game, unit -> {
            if (pause.compareAndSet(true, false)) {
                refreshing.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }
            return owner;
        });
        List<Unit> retained = cache.getConnected(owner);
        Iterator<Unit> iterator = retained.iterator();
        assertSame(first, iterator.next());
        game.botClientData().gameData().setFrameCount(game.getFrameCount() + 1);
        pause.set(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Unit>> next = executor.submit(() -> cache.getConnected(owner));
        try {
            assertTrue(refreshing.await(5, TimeUnit.SECONDS));
            assertSame(second, iterator.next());
            assertEquals(Arrays.asList(first, second), retained);
        } finally {
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(Arrays.asList(first, second), next.get());
        try { retained.clear(); fail("Snapshot must be read-only"); }
        catch (UnsupportedOperationException expected) { }
        cache.reset();
        game.setAllUnits(Collections.emptyList());
        assertTrue(cache.getConnected(owner).isEmpty());
        assertEquals(Arrays.asList(first, second), retained);
    }
}
