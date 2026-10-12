package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Keep a bounded radial prefetch window; ready tiles can be displayed independently. */
public final class TileLoadFrontier {
    private final TileWindow window;
    private Iterator<Tile.Key> cursor;
    private final List<Tile.Key> waiting = new ArrayList<>(64);

    public TileLoadFrontier(TileWindow window) {
        this.window = window;
        cursor = window.iterator();
    }

    public List<Tile.Key> next(Predicate<Tile.Key> displayedOrFailed) {
        waiting.removeIf(displayedOrFailed);
        // Refill missing work without requiring every earlier job to finish first.
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
            if (!displayedOrFailed.test(key) && !waiting.contains(key)) {
                waiting.add(key);
            }
        }
        return List.copyOf(waiting);
    }
}
