package bwapi;


import bwapi.ClientData.RegionData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Region objects are created by Starcraft: Broodwar to contain several tiles with the same
 * properties, and create a node in pathfinding and other algorithms. Regions may not contain
 * detailed information, but have a sufficient amount of data to identify general chokepoints,
 * accessibility to neighboring terrain, be used in general pathing algorithms, and used as
 * nodes to rally units to.
 * <p>
 * Most parameters that are available are explicitly assigned by Broodwar itself.
 *
 * @see Game#getAllRegions
 * @see Game#getRegionAt
 * @see Unit#getRegion
 */
public class Region implements Comparable<Region> {
    private final Game game;

    private final int id;
    private final int regionGroupID;
    private final Position center;
    private final boolean higherGround;
    private final int defensePriority;
    private final boolean accessible;
    private final int boundsLeft;
    private final int boundsTop;
    private final int boundsRight;
    private final int boundsBottom;
    private Region closestAccessibleRegion;
    private Region closestInaccessibleRegion;

    private List<Region> neighbours;

    Region(final int id, final Game game) {
        this.game = game;
        this.id = id;
        this.regionGroupID = getData().islandID();
        this.center = new Position(getData().getCenter_x(), getData().getCenter_y());
        this.higherGround = getData().isHigherGround();
        this.defensePriority = getData().getPriority();
        this.accessible = getData().isAccessible();
        this.boundsLeft = getData().getLeftMost();
        this.boundsTop = getData().getTopMost();
        this.boundsRight = getData().getRightMost();
        this.boundsBottom = getData().getBottomMost();
    }

    public RegionData getData() {
        return game.getData().getRegions(id);
    }

    void updateNeighbours() {
        int accessibleBestDist = Integer.MAX_VALUE;
        int inaccessibleBestDist = Integer.MAX_VALUE;

        final List<Region> neighbours = new ArrayList<>();
        for (int i = 0; i < getData().getNeighborCount(); i++) {
            final Region region = game.getRegion(getData().getNeighbors(i));
            neighbours.add(region);
            final int d = getDistance(region);
            if (region.isAccessible()) {
                if (d < accessibleBestDist) {
                    closestAccessibleRegion = region;
                    accessibleBestDist = d;
                }
            } else if (d < inaccessibleBestDist) {
                closestInaccessibleRegion = region;
                inaccessibleBestDist = d;
            }
        }
        this.neighbours = neighbours;
    }

    /**
     * Retrieves a unique identifier for this region.
     * <p>
     * This identifier is explicitly assigned by Broodwar.
     *
     * @return An integer that represents this region.
     * @see Game#getRegion
     */
    public int getID() {
        return id;
    }

    /**
     * Retrieves a unique identifier for a group of regions that are all connected and
     * accessible by each other. That is, all accessible regions will have the same
     * group ID. This function is generally used to check if a path is available between two
     * points in constant time.
     * <p>
     * This identifier is explicitly assigned by Broodwar.
     *
     * @return An integer that represents the group of regions that this one is attached to.
     */
    public int getRegionGroupID() {
        return regionGroupID;
    }

    /**
     * Retrieves the center of the region. This position is used as the node
     * of the region.
     *
     * @return A {@link Position} indicating the center location of the Region, in pixels.
     */
    public Position getCenter() {
        return center;
    }

    /**
     * Checks if this region is part of higher ground. Higher ground may be
     * used in strategic placement of units and structures.
     *
     * @return true if this region is part of strategic higher ground, and false otherwise.
     */
    public boolean isHigherGround() {
        return higherGround;
    }

    /**
     * Retrieves a value that represents the strategic advantage of this region relative
     * to other regions. A value of 2 may indicate a possible choke point, and a value
     * of 3 indicates a signficant strategic position.
     * <p>
     * This value is explicitly assigned by Broodwar.
     *
     * @return An integer indicating this region's strategic potential.
     */
    public int getDefensePriority() {
        return defensePriority;
    }

    /**
     * Retrieves the state of accessibility of the region. The region is
     * considered accessible if it can be accessed by ground units.
     *
     * @return true if ground units can traverse this region, and false if the tiles in this
     * region are inaccessible or unwalkable.
     */
    public boolean isAccessible() {
        return accessible;
    }

    /**
     * Retrieves the set of neighbor Regions that this one is connected to.
     *
     * @return A reference to a List<Region> containing the neighboring Regions.
     */
    public List<Region> getNeighbors() {
        return new ArrayList<>(neighbours);
    }

    /**
     * Retrieves the approximate left boundary of the region.
     *
     * @return The x coordinate, in pixels, of the approximate left boundary of the region.
     */
    public int getBoundsLeft() {
        return boundsLeft;
    }

    /**
     * Retrieves the approximate top boundary of the region.
     *
     * @return The y coordinate, in pixels, of the approximate top boundary of the region.
     */
    public int getBoundsTop() {
        return boundsTop;
    }

    /**
     * Retrieves the approximate right boundary of the region.
     *
     * @return The x coordinate, in pixels, of the approximate right boundary of the region.
     */
    public int getBoundsRight() {
        return boundsRight;
    }

    /**
     * Retrieves the approximate bottom boundary of the region.
     *
     * @return The y coordinate, in pixels, of the approximate bottom boundary of the region.
     */
    public int getBoundsBottom() {
        return boundsBottom;
    }

    /**
     * Retrieves the closest accessible neighbor region.
     *
     * @return The closest {@link Region} that is accessible.
     */
    public Region getClosestAccessibleRegion() {
        return closestAccessibleRegion;
    }

    /**
     * Retrieves the closest inaccessible neighbor region.
     *
     * @return The closest {@link Region} that is inaccessible.
     */
    public Region getClosestInaccessibleRegion() {
        return closestInaccessibleRegion;
    }

    /**
     * Retrieves the center-to-center distance between two regions.
     * <p>
     * Ignores all collisions.
     *
     * @param other The target {@link Region} to calculate distance to.
     * @return The integer distance from this Region to other.
     */
    public int getDistance(final Region other) {
        return getCenter().getApproxDistance(other.getCenter());
    }

    public List<Unit> getUnits() {
        return getUnits(u -> true);
    }

    /**
     * Retrieves a List<Unit> containing all the units that are in this region.
     * Also has the ability to filter the units before the creation of the List<Unit>.
     *
     * @param pred If this parameter is used, it is a UnitFilter or function predicate that will retrieve only the units whose attributes match the given criteria. If omitted, then a default value of null is used, in which case there is no filter.
     * @return A List<Unit> containing all units in this region that have met the requirements
     * of pred.
     * @see UnitFilter
     */
    public List<Unit> getUnits(final UnitFilter pred) {
        return game.getUnitsInRectangle(getBoundsLeft(), getBoundsTop(), getBoundsRight(), getBoundsBottom(),
                u -> equals(u.getRegion()) && pred.test(u));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Region region = (Region) o;
        return id == region.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public int compareTo(final Region other) {
        return id - other.id;
    }
}
