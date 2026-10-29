package bwapi;

import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UnitFinderTest {
    @Test
    public void indexedQueriesMatchLinearQueries() {
        final Game game = new Game();
        final Random random = new Random(98);
        final List<Unit> units = new ArrayList<>();
        final UnitType[] types = UnitType.values();
        for (int i = 0; i < types.length; i++) {
            units.add(unit(types[i], random.nextInt(4096) - 256, random.nextInt(4096) - 256));
        }
        units.add(unit(UnitType.Terran_Marine, Position.Unknown.x, Position.Unknown.y));
        game.setAllUnits(units);
        final UnitFilter filter = u -> !u.getType().isFlyer();

        for (int i = 0; i < 40; i++) {
            final Position center = units.get(i).getPosition();
            final int radius = random.nextInt(160);
            final int left = center.x - radius;
            final int top = center.y - radius;
            final int right = center.x + radius;
            final int bottom = center.y + radius;
            final List<Unit> rectangle = units.stream()
                    .filter(u -> left <= u.getRight() && top <= u.getBottom() && right >= u.getLeft() && bottom >= u.getTop() && filter.test(u))
                    .collect(Collectors.toList());
            final List<Unit> circle = units.stream()
                    .filter(u -> center.getApproxDistance(u.getPosition()) <= radius && filter.test(u))
                    .collect(Collectors.toList());
            final TilePosition tile = units.get(i).getTilePosition();
            final List<Unit> onTile = units.stream()
                    .filter(u -> tile.equals(u.getTilePosition()) && filter.test(u))
                    .collect(Collectors.toList());

            assertEquals(rectangle, game.getUnitsInRectangle(left, top, right, bottom, filter));
            assertEquals(circle, game.getUnitsInRadius(center, radius, filter));
            assertEquals(onTile, game.getUnitsOnTile(tile.x, tile.y, filter));
            assertEquals(rectangle.stream().min(Comparator.comparingInt(u -> u.getDistance(center))).orElse(null),
                    game.getClosestUnitInRectangle(center, left, top, right, bottom, filter));
            assertEquals(circle.stream().min(Comparator.comparingInt(u -> u.getDistance(center))).orElse(null),
                    game.getClosestUnit(center, radius, filter));
        }
    }

    @Test
    public void localQueriesDoNotRescanDistantUnits() {
        final Game game = new Game();
        final List<Unit> units = distantUnits();
        final Unit nearby = unit(UnitType.Terran_Marine, 127, 128);
        units.add(nearby);
        game.setAllUnits(units);
        assertThat(game.getUnitsInRectangle(127, 128, 127, 128)).containsExactly(nearby);
        assertThat(game.getUnitsInRectangle(127, 128, 127, 128)).containsExactly(nearby);
        final Unit distant = units.get(0);
        clearInvocations(distant);

        assertThat(game.getUnitsInRectangle(127, 128, 127, 128)).containsExactly(nearby);
        assertThat(game.getUnitsInRadius(nearby.getPosition(), 0)).containsExactly(nearby);
        assertSame(nearby, game.getClosestUnit(nearby.getPosition(), 0));
        assertThat(game.getUnitsOnTile(nearby.getTilePosition())).containsExactly(nearby);
        verifyNoInteractions(distant);
    }

    @Test
    public void tiesAndPredicatesKeepOriginalUnitOrder() {
        final Game game = new Game();
        final Unit first = unit(UnitType.Terran_Marine, 128, 128);
        final Unit second = unit(UnitType.Terran_Marine, 128, 128);
        final List<Unit> units = distantUnits();
        units.add(0, first);
        units.add(second);
        game.setAllUnits(units);
        final List<Unit> visited = new ArrayList<>();

        assertThat(game.getUnitsInRectangle(128, 128, 128, 128, u -> { visited.add(u); return true; }))
                .containsExactly(first, second);
        assertThat(visited).containsExactly(first, second);
        assertSame(first, game.getClosestUnit(new Position(128, 128), 0));
        assertSame(first, game.getClosestUnitInRectangle(new Position(128, 128), 128, 128, 128, 128));
    }

    @Test
    public void boundsAndTileOriginsFollowTypeChangesWithinAFrame() {
        final Game game = new Game();
        final Unit unit = unit(UnitType.Terran_Marine, 256, 256);
        final List<Unit> units = distantUnits();
        units.add(unit);
        game.setAllUnits(units);
        assertThat(game.getUnitsInRectangle(200, 256, 200, 256)).isEmpty();
        final TilePosition oldTile = unit.getTilePosition();
        assertThat(game.getUnitsOnTile(oldTile)).containsExactly(unit);

        when(unit.getType()).thenReturn(UnitType.Terran_Command_Center);

        assertThat(game.getUnitsInRectangle(200, 256, 200, 256)).containsExactly(unit);
        assertThat(game.getUnitsOnTile(unit.getTilePosition())).containsExactly(unit);
        assertThat(game.getUnitsOnTile(oldTile)).isEmpty();
    }

    @Test
    public void changedUnitSnapshotsDiscardOldPositionsAndMembership() {
        final Game game = new Game();
        final List<Unit> units = distantUnits();
        final Unit unit = unit(UnitType.Terran_Marine, 128, 128);
        units.add(unit);
        game.setAllUnits(units);
        assertThat(game.getUnitsInRadius(new Position(128, 128), 0)).containsExactly(unit);
        assertThat(game.getUnitsInRadius(new Position(128, 128), 0)).containsExactly(unit);

        when(unit.getPosition()).thenReturn(new Position(512, 512));
        game.setAllUnits(units);
        assertThat(game.getUnitsInRadius(new Position(128, 128), 0)).isEmpty();
        assertThat(game.getUnitsInRadius(new Position(512, 512), 0)).containsExactly(unit);

        game.setAllUnits(Collections.emptyList());
        assertThat(game.getUnitsInRadius(new Position(512, 512), 0)).isEmpty();
    }

    @Test
    public void eventPositionUpdatesInvalidateTheIndex() throws IOException {
        final Game game = GameBuilder.createGame();
        final Unit unit = game.getAllUnits().get(0);
        final Position oldPosition = unit.getPosition();
        assertThat(game.getUnitsInRadius(oldPosition, 0, u -> u == unit)).containsExactly(unit);
        assertThat(game.getUnitsInRadius(oldPosition, 0, u -> u == unit)).containsExactly(unit);
        final ClientData.UnitData data = game.botClientData().gameData().getUnits(unit.getID());
        data.setPositionX(1024);
        data.setPositionY(1024);
        unit.updatePosition(1);

        assertThat(game.getUnitsInRadius(new Position(1024, 1024), 0, u -> u == unit)).containsExactly(unit);
        assertThat(game.getUnitsInRadius(oldPosition, 0, u -> u == unit)).isEmpty();
    }

    @Test
    public void broadQueriesAndCoordinateLimitsDoNotOverflow() {
        final Game game = new Game();
        final List<Unit> units = distantUnits();
        game.setAllUnits(units);
        assertEquals(units, game.getUnitsInRectangle(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(units, game.getUnitsInRadius(new Position(4096, 4096), Integer.MAX_VALUE));
        assertThat(game.getUnitsInRadius(new Position(4096, 4096), -1)).isEmpty();
        assertThat(game.getUnitsOnTile(Integer.MAX_VALUE, Integer.MAX_VALUE)).isEmpty();
    }

    private List<Unit> distantUnits() {
        final List<Unit> units = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            units.add(unit(UnitType.Terran_Marine, 2048 + (i % 20) * 128, 2048 + (i / 20) * 128));
        }
        return units;
    }

    private Unit unit(final UnitType type, final int x, final int y) {
        final Unit unit = mock(Unit.class);
        when(unit.getType()).thenReturn(type);
        when(unit.getPosition()).thenReturn(new Position(x, y));
        when(unit.exists()).thenReturn(true);
        when(unit.getLeft()).thenCallRealMethod();
        when(unit.getRight()).thenCallRealMethod();
        when(unit.getTop()).thenCallRealMethod();
        when(unit.getBottom()).thenCallRealMethod();
        when(unit.getX()).thenCallRealMethod();
        when(unit.getY()).thenCallRealMethod();
        when(unit.getTilePosition()).thenCallRealMethod();
        when(unit.getDistance(any(Position.class))).thenCallRealMethod();
        return unit;
    }
}
