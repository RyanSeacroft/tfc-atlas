package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Keep a moving centre-first prefetch window with an ordered display-upload barrier. */
public final class TileLoadFrontier {
    private final TileWindow window;
    private Iterator<Tile.Key> cursor;
    private final List<Tile.Key> waiting = new ArrayList<>(64);
    private double reachedRadius = -1;

    public TileLoadFrontier(TileWindow window) {
        this.window = window;
        cursor = window.iterator();
    }

    public Set<Tile.Key> readyPrefix(Predicate<Tile.Key> available) {
        Set<Tile.Key> ready = new HashSet<>();
        for (Tile.Key key : waiting) {
            if (!available.test(key)) {
                break;
            }
            ready.add(key);
        }
        return ready;
    }

    /** Resident outer tiles must not jump ahead of central tiles still on disk. */
    public boolean allowsUpload(Tile.Key key) {
        if (key.step() == window.step()) {
            return waiting.contains(key);
        }
        double span = Tile.SIDE * Tile.GRID * (double) key.step();
        double dx =
                Math.max(
                        0,
                        Math.max(
                                key.blockX() - window.focusX(),
                                window.focusX() - key.blockX() - span));
        double dz =
                Math.max(
                        0,
                        Math.max(
                                key.blockZ() - window.focusZ(),
                                window.focusZ() - key.blockZ() - span));
        return dx * dx + dz * dz <= reachedRadius;
    }

    public List<Tile.Key> next(Predicate<Tile.Key> displayedOrFailed) {
        waiting.removeIf(displayedOrFailed);
        // Refill behind the upload barrier so the worker never drains at a 64-tile boundary.
        for (int checked = 0;
                checked < Math.min(256, window.count()) && waiting.size() < 64;
                checked++) {
            if (!cursor.hasNext()) {
                cursor = window.iterator();
            }
            if (!cursor.hasNext()) {
                break;
            }
            Tile.Key key = cursor.next();
            reachedRadius = Math.max(reachedRadius, window.distanceSquared(key));
            if (!displayedOrFailed.test(key) && !waiting.contains(key)) {
                waiting.add(key);
            }
        }
        return List.copyOf(waiting);
    }
}
