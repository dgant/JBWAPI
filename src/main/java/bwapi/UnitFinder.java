package bwapi;

import java.util.*;
import java.util.stream.Stream;

/**
 * Lazily indexes unit centers once per frame. Bounds and filters are evaluated
 * at query time, including any latency-compensated type changes.
 */
class UnitFinder {
    private static final int CELL_SHIFT = 7;
    private static final int MAX_TILE_X = Arrays.stream(UnitType.values()).mapToInt(UnitType::tileWidth).max().getAsInt() * 16;
    private static final int MAX_TILE_Y = Arrays.stream(UnitType.values()).mapToInt(UnitType::tileHeight).max().getAsInt() * 16;

    private final Game game;
    private final Map<Long, List<Integer>> cells = new HashMap<>();
    private List<Unit> indexedUnits;
    private List<Unit> queriedUnits;

    UnitFinder(final Game game) {
        this.game = game;
    }

    void reset() {
        indexedUnits = null;
        queriedUnits = null;
    }

    Stream<Unit> inRectangle(final long left, final long top, final long right, final long bottom) {
        final List<Unit> units = game.getAllUnits();
        if (left > right || top > bottom) {
            return Stream.empty();
        }
        // A broad query is cheaper as a single pass, and needs no index.
        if (cellCount(left, top, right, bottom) >= units.size() || !prepare(units)) {
            return units.stream();
        }
        final BitSet candidates = new BitSet();
        addCandidates(candidates, left, top, right, bottom);
        // Preserve getAllUnits order, including closest-unit ties and filter order.
        return candidates.stream().mapToObj(units::get);
    }

    Stream<Unit> onTile(final int tileX, final int tileY) {
        if (tileX < 0 || tileY < 0) {
            return Stream.empty();
        }
        final List<Unit> units = game.getAllUnits();
        final long left = tileX * 32L - MAX_TILE_X;
        final long top = tileY * 32L - MAX_TILE_Y;
        final long right = tileX * 32L + 31 + MAX_TILE_X;
        final long bottom = tileY * 32L + 31 + MAX_TILE_Y;
        if (4 * cellCount(left, top, right, bottom) >= units.size() || !prepare(units)) {
            return units.stream();
        }
        final BitSet candidates = new BitSet();
        // getTilePosition uses absolute coordinates; include reflected centers.
        addCandidates(candidates, left, top, right, bottom);
        addCandidates(candidates, -right, top, -left, bottom);
        addCandidates(candidates, left, -bottom, right, -top);
        addCandidates(candidates, -right, -bottom, -left, -top);
        return candidates.stream().mapToObj(units::get);
    }

    private boolean prepare(final List<Unit> units) {
        if (indexedUnits == units) {
            return true;
        }
        // Do not build an index for a single local query in a frame.
        if (queriedUnits != units) {
            queriedUnits = units;
            return false;
        }
        cells.clear();
        for (int i = 0; i < units.size(); i++) {
            final Position position = units.get(i).getPosition();
            cells.computeIfAbsent(key(position.x >> CELL_SHIFT, position.y >> CELL_SHIFT), unused -> new ArrayList<>()).add(i);
        }
        indexedUnits = units;
        return true;
    }

    private void addCandidates(final BitSet candidates, final long left, final long top, final long right, final long bottom) {
        for (long x = left >> CELL_SHIFT; x <= right >> CELL_SHIFT; x++) {
            for (long y = top >> CELL_SHIFT; y <= bottom >> CELL_SHIFT; y++) {
                final List<Integer> cell = cells.get(key(x, y));
                if (cell != null) {
                    for (final int i : cell) {
                        candidates.set(i);
                    }
                }
            }
        }
    }

    private static long cellCount(final long left, final long top, final long right, final long bottom) {
        return ((right >> CELL_SHIFT) - (left >> CELL_SHIFT) + 1) * ((bottom >> CELL_SHIFT) - (top >> CELL_SHIFT) + 1);
    }

    private static long key(final long x, final long y) {
        // Mix the axes to avoid diagonal cells sharing the same Long.hashCode.
        return (x << 32) ^ ((y ^ (x * 0x9e3779b9L)) & 0xffffffffL);
    }
}
