package bwta;

import bwapi.Position;
import bwapi.TilePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Legacy compatibility polygon for older bot code paths.
 * This uses the BWEM area bounding box as an approximate region polygon.
 */
public final class RegionPolygon implements Iterable<Position> {
    private final List<Position> vertices;

    RegionPolygon(final TilePosition topLeft, final TilePosition bottomRight) {
        final int left = topLeft.x * TilePosition.SIZE_IN_PIXELS;
        final int top = topLeft.y * TilePosition.SIZE_IN_PIXELS;
        final int right = (bottomRight.x + 1) * TilePosition.SIZE_IN_PIXELS;
        final int bottom = (bottomRight.y + 1) * TilePosition.SIZE_IN_PIXELS;

        final List<Position> out = new ArrayList<>(4);
        out.add(new Position(left, top));
        out.add(new Position(right, top));
        out.add(new Position(right, bottom));
        out.add(new Position(left, bottom));
        this.vertices = Collections.unmodifiableList(out);
    }

    public List<Position> getVertices() {
        return vertices;
    }

    public boolean isInside(final Position p) {
        final Position v0 = vertices.get(0);
        final Position v2 = vertices.get(2);
        return p.x >= v0.x && p.x <= v2.x && p.y >= v0.y && p.y <= v2.y;
    }

    public Position getNearestPoint(final Position p) {
        final Position v0 = vertices.get(0);
        final Position v2 = vertices.get(2);
        final int x = Math.max(v0.x, Math.min(v2.x, p.x));
        final int y = Math.max(v0.y, Math.min(v2.y, p.y));
        return new Position(x, y);
    }

    @Override
    public Iterator<Position> iterator() {
        return vertices.iterator();
    }
}
