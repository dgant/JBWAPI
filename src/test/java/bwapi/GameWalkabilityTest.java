package bwapi;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameWalkabilityTest {
    @Test
    public void integerCoordinatesMatchPositionQueriesAcrossTheMap() throws IOException {
        Game game = GameBuilder.createGame();
        boolean foundWalkable = false;
        boolean foundBlocked = false;
        for (int x = 0; x < game.mapWidth() * 4; x++) {
            for (int y = 0; y < game.mapHeight() * 4; y++) {
                boolean expected = game.isWalkable(new WalkPosition(x, y));
                assertEquals(expected, game.isWalkable(x, y));
                foundWalkable |= expected;
                foundBlocked |= !expected;
            }
        }
        assertTrue(foundWalkable);
        assertTrue(foundBlocked);
    }

    @Test
    public void rejectsCoordinatesOutsideTheMapWithoutIntegerOverflow() throws IOException {
        Game game = GameBuilder.createGame();
        int[] invalidX = {-1, Integer.MIN_VALUE, game.mapWidth() * 4, 1 << 29, Integer.MAX_VALUE};
        int[] invalidY = {-1, Integer.MIN_VALUE, game.mapHeight() * 4, 1 << 29, Integer.MAX_VALUE};
        for (int x : invalidX) {
            assertFalse(game.isWalkable(x, 0));
        }
        for (int y : invalidY) {
            assertFalse(game.isWalkable(0, y));
        }
    }
}
