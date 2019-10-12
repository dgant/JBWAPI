package bwta;

import static java.util.stream.Collectors.collectingAndThen;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwem.Base;

import bwem.Neutral;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BaseLocation {
    private final Base base;
    private final Position position;
    private final TilePosition tilePosition;
    private final int minerals;
    private final int gas;
    private final List<Unit> mineralSet;
    private final List<Unit> geyserSet;
    private final boolean island;
    private final boolean mineralOnly;
    private final boolean startLocation;


    BaseLocation(final Base base) {
        this.base = base;
        this.position = base.getCenter();
        this.tilePosition = base.getLocation();
        this.minerals = 1;
        this.gas = 1;
        this.mineralSet = base.getMinerals().stream().map(Neutral::getUnit).collect(Collectors.toList());
        this.geyserSet =  base.getGeysers().stream().map(Neutral::getUnit).collect(Collectors.toList());
        this.island = base.getArea().getAccessibleNeighbors().isEmpty();
        this.mineralOnly = !mineralSet.isEmpty() && geyserSet.isEmpty();
        this.startLocation = base.isStartingLocation();
    }

    public Position getPosition() {
        return position;
    }

    public TilePosition getTilePosition() {
        return tilePosition;
    }

    public Region getRegion() {
        return BWTA.regionMap.get(base.getArea());
    }

    public int minerals() {
        return minerals;
    }

    public int gas() {
        return gas;
    }

    public List<Unit> getMinerals() {
        return new ArrayList<>(mineralSet);
    }

    public List<Unit> getGeysers() {
        return new ArrayList<>(geyserSet);
    }

    public double getGroundDistance(final BaseLocation other) {
        return BWTA.getGroundDistance(tilePosition, other.tilePosition);
    }

    public double getAirDistance(final BaseLocation other) {
        return position.getDistance(other.position);
    }

    public boolean isIsland() {
        return island;
    }

    public boolean isMineralOnly() {
        return mineralOnly;
    }

    public boolean isStartLocation() {
        return startLocation;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof BaseLocation)) {
            return false;
        }
        return base.equals(((BaseLocation) o).base);
    }

    @Override
    public int hashCode() {
        return base.hashCode();
    }
}
