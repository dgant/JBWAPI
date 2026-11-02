package bwapi;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class UncheckedMapQueriesTest {
    @Test
    public void uncheckedQueriesReadCurrentDataAtTheRequestedTile() throws IOException {
        Game game = GameBuilder.createGame();
        ClientData.GameData data = game.botClientData().gameData();
        for (int frame = 0; frame < 2; frame++) {
            for (int x = 0; x < game.mapWidth(); x++) {
                for (int y = 0; y < game.mapHeight(); y++) {
                    boolean visible = (x + frame) % 2 == 0;
                    boolean explored = (y + frame) % 3 == 0;
                    boolean creep = (x + 2 * y + frame) % 5 == 0;
                    data.setIsVisible(x, y, visible);
                    data.setIsExplored(x, y, explored);
                    data.setHasCreep(x, y, creep);
                    assertEquals(visible, game.isVisibleUnsafe(x, y));
                    assertEquals(explored, game.isExploredUnsafe(x, y));
                    assertEquals(creep, game.hasCreepUnsafe(x, y));
                }
            }
        }
    }
}
