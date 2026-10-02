package dev.ryan.tfcatlas.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** A finite, centre-first, layer-independent plan. Small moves don't restart it. */
public record PrecacheArea(int tileX, int tileZ) implements Iterable<Tile.Key> {
    public static final int RADIUS = 512_000, STEP = 256 / Tile.GRID, SPAN = Tile.SIDE * 256;

    public static PrecacheArea at(int x, int z) {
        return new PrecacheArea(Math.floorDiv(x, SPAN), Math.floorDiv(z, SPAN));
    }

    public int centerX() {
        return tileX * SPAN + SPAN / 2;
    }

    public int centerZ() {
        return tileZ * SPAN + SPAN / 2;
    }

    // Padding covers the full requested circle anywhere inside the player's anchor tile.
    private static final int PADDED_RADIUS = RADIUS + SPAN;

    public boolean contains(Tile.Key key) {
        if (key.step() != STEP) {
            return false;
        }
        long dx =
                Math.max(
                        0,
                        Math.max(
                                (long) key.blockX() - centerX(),
                                (long) centerX() - key.blockX() - SPAN));
        long dz =
                Math.max(
                        0,
                        Math.max(
                                (long) key.blockZ() - centerZ(),
                                (long) centerZ() - key.blockZ() - SPAN));
        return dx * dx + dz * dz <= (long) PADDED_RADIUS * PADDED_RADIUS;
    }

    public Iterator<Tile.Key> iterator() {
        var bounds =
                TileWindow.visible(
                        centerX(), centerZ(), 1, 2 * PADDED_RADIUS, 2 * PADDED_RADIUS, STEP);
        return new Iterator<>() {
            final Iterator<Tile.Key> source = bounds.iterator();
            Tile.Key next;

            public boolean hasNext() {
                while (next == null && source.hasNext()) {
                    var key = source.next();
                    if (contains(key)) {
                        next = key;
                    }
                }
                return next != null;
            }

            public Tile.Key next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                var found = next;
                next = null;
                return found;
            }
        };
    }
}
