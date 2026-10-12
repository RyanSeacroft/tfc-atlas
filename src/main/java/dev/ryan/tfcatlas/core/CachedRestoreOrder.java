package dev.ryan.tfcatlas.core;

import java.util.Iterator;
import java.util.function.Predicate;

/** Restore new overview colours from the focus outward, without exposing a detached cache ring. */
public final class CachedRestoreOrder {
    private final TileWindow window;
    private final Iterator<Tile.Key> cursor;
    private Tile.Key next;
    private double radius = -1;

    public CachedRestoreOrder(TileWindow window) {
        this.window = window;
        cursor = window.iterator();
        next = cursor.hasNext() ? cursor.next() : null;
    }

    public void advance(Predicate<Tile.Key> readyOrFailed) {
        for (int checked = 0;
                checked < 1024 && next != null && readyOrFailed.test(next);
                checked++) {
            radius = window.distanceSquared(next);
            next = cursor.hasNext() ? cursor.next() : null;
        }
        if (next == null) {
            radius = Double.POSITIVE_INFINITY;
        }
    }

    public boolean allows(Tile.Key key) {
        // Fine detail always appears immediately; only newly restored overviews need ordering.
        return key.step() <= 32 / Tile.GRID || window.distanceSquared(key) <= radius;
    }
}
