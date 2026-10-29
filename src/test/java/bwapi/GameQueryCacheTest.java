package bwapi;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GameQueryCacheTest {
    private Game game;
    private ClientData.GameData data;

    @Before
    public void setup() throws IOException {
        game = GameBuilder.createGame();
        game.setLatCom(false);
        data = game.botClientData().gameData();
    }

    @Test
    public void unitListsAndPowerShareOneScanPerFrame() {
        final Unit pylon = unit(UnitType.Protoss_Pylon, game.self());
        final Unit mineral = unit(UnitType.Resource_Mineral_Field, game.neutral());
        final Unit geyser = unit(UnitType.Resource_Vespene_Geyser, game.neutral());
        final List<Unit> units = Arrays.asList(pylon, mineral, geyser);
        game.setAllUnits(units);
        clearInvocations(pylon, mineral, geyser);

        for (int i = 0; i < 3; i++) {
            assertThat(game.self().getUnits()).containsExactly(pylon);
            assertThat(game.getMinerals()).containsExactly(mineral);
            assertThat(game.getGeysers()).containsExactly(geyser);
            assertThat(game.getNeutralUnits()).containsExactly(mineral, geyser);
            assertTrue(game.hasPowerPrecise(1024, 1024, UnitType.Protoss_Gateway));
        }
        for (final Unit unit : units) {
            verify(unit, times(1)).getType();
            verify(unit, times(1)).getPlayer();
        }
    }

    @Test
    public void callersCanModifyAndRetainListResults() {
        final Unit mineral = unit(UnitType.Resource_Mineral_Field, game.neutral());
        final Unit geyser = unit(UnitType.Resource_Vespene_Geyser, game.neutral());
        game.setAllUnits(Arrays.asList(mineral, geyser));
        final List<Unit> previous = game.getMinerals();
        game.getMinerals().clear();
        game.getGeysers().clear();
        game.getNeutralUnits().clear();
        game.neutral().getUnits().clear();

        assertThat(game.getMinerals()).containsExactly(mineral);
        assertThat(game.getGeysers()).containsExactly(geyser);
        assertThat(game.getNeutralUnits()).containsExactly(mineral, geyser);
        assertThat(game.neutral().getUnits()).containsExactly(mineral, geyser);

        game.setAllUnits(Collections.emptyList());
        assertThat(game.getMinerals()).isEmpty();
        assertThat(game.getGeysers()).isEmpty();
        assertThat(game.neutral().getUnits()).isEmpty();
        assertThat(previous).containsExactly(mineral);
    }

    @Test
    public void ownershipAndMorphChangesRefreshBeforeTheFrameCallback() {
        final Unit pylon = unit(UnitType.Protoss_Pylon, game.self());
        final Unit geyser = unit(UnitType.Resource_Vespene_Geyser, game.neutral());
        game.setAllUnits(Arrays.asList(pylon, geyser));
        assertTrue(game.hasPowerPrecise(1024, 1024));
        assertThat(game.getGeysers()).containsExactly(geyser);

        when(pylon.getPlayer()).thenReturn(game.enemy());
        when(geyser.getPlayer()).thenReturn(game.self());
        when(geyser.getType()).thenReturn(UnitType.Protoss_Assimilator);
        data.setFrameCount(data.getFrameCount() + 1);

        assertFalse(game.hasPowerPrecise(1024, 1024));
        assertThat(game.getGeysers()).isEmpty();
        assertThat(game.getNeutralUnits()).isEmpty();
        assertThat(game.self().getUnits()).containsExactly(geyser);
        assertThat(game.enemy().getUnits()).containsExactly(pylon);
    }

    @Test
    public void powerChecksStillObservePylonCompletionAndExistence() {
        final Unit pylon = unit(UnitType.Protoss_Pylon, game.self());
        game.setAllUnits(Collections.singletonList(pylon));
        when(pylon.isCompleted()).thenReturn(false);
        assertFalse(game.hasPowerPrecise(1024, 1024));
        when(pylon.isCompleted()).thenReturn(true);
        assertTrue(game.hasPowerPrecise(1024, 1024));
        when(pylon.exists()).thenReturn(false);
        assertFalse(game.hasPowerPrecise(1024, 1024));
    }

    @Test
    public void bulletsRefreshEachFrameAndAfterInitialization() {
        for (int i = 0; i < 100; i++) {
            data.getBullets(i).setExists(false);
        }
        data.getBullets(0).setExists(true);
        data.getBullets(1).setId(1);
        final List<Bullet> previous = game.getBullets();
        assertEquals(1, previous.size());
        game.getBullets().clear();
        data.getBullets(1).setExists(true);
        assertEquals(1, game.getBullets().size());

        data.setFrameCount(data.getFrameCount() + 1);
        assertEquals(2, game.getBullets().size());
        assertEquals(1, previous.size());

        data.getBullets(0).setExists(false);
        game.init();
        assertEquals(1, game.getBullets().size());
        assertEquals(1, game.getBullets().get(0).getID());
    }

    @Test
    public void initializationDiscardsPreviousUnitLists() {
        final Unit pylon = unit(UnitType.Protoss_Pylon, game.self());
        game.setAllUnits(Collections.singletonList(pylon));
        assertThat(game.self().getUnits()).containsExactly(pylon);
        assertTrue(game.hasPowerPrecise(1024, 1024));

        game.init();

        assertThat(game.self().getUnits()).doesNotContain(pylon);
        assertThat(game.getNeutralUnits()).isNotEmpty();
        assertFalse(game.hasPowerPrecise(1024, 1024));
    }

    @Test
    public void connectedUnitsAndCapacityAlreadyShareFrameCaches() {
        final Unit carrier = game.getAllUnits().get(0);
        final Unit transport = game.getAllUnits().get(1);
        final Unit hatchery = game.getAllUnits().get(2);
        data.getUnits(carrier.getID()).setType(UnitType.Protoss_Carrier.id);
        data.getUnits(transport.getID()).setType(UnitType.Protoss_Shuttle.id);
        data.getUnits(hatchery.getID()).setType(UnitType.Zerg_Hatchery.id);
        final Unit interceptor = unit(UnitType.Protoss_Interceptor, game.self());
        final Unit passenger = unit(UnitType.Terran_Marine, game.self());
        final Unit larva = unit(UnitType.Zerg_Larva, game.self());
        when(interceptor.getCarrier()).thenReturn(carrier);
        when(passenger.getTransport()).thenReturn(transport);
        when(larva.getHatchery()).thenReturn(hatchery);
        game.setAllUnits(Arrays.asList(carrier, transport, hatchery, interceptor, passenger, larva));
        clearInvocations(interceptor, passenger, larva);

        for (int i = 0; i < 3; i++) {
            assertThat(carrier.getInterceptors()).containsExactly(interceptor);
            assertThat(transport.getLoadedUnits()).containsExactly(passenger);
            assertEquals(7, transport.getSpaceRemaining());
            assertThat(hatchery.getLarva()).containsExactly(larva);
        }
        verify(interceptor, times(1)).getCarrier();
        verify(passenger, times(1)).getTransport();
        verify(larva, times(1)).getHatchery();

        when(passenger.getTransport()).thenReturn(null);
        data.setFrameCount(data.getFrameCount() + 1);
        assertThat(transport.getLoadedUnits()).isEmpty();
        assertEquals(8, transport.getSpaceRemaining());
    }

    private Unit unit(final UnitType type, final Player player) {
        final Unit unit = mock(Unit.class);
        when(unit.getType()).thenReturn(type);
        when(unit.getPlayer()).thenReturn(player);
        when(unit.getPosition()).thenReturn(new Position(1024, 1024));
        when(unit.exists()).thenReturn(true);
        when(unit.isCompleted()).thenReturn(true);
        return unit;
    }
}
