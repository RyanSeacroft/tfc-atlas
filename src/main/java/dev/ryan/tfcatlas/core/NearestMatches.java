package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Select nearest separated samples from the complete coverage, without a truncated candidate pool.
 */
public final class NearestMatches {
    public record Point(int x, int z, double distance, int layerMask, Tile.Key key) {}

    public static List<Point> select(
            Map<Tile.Key, byte[]> matches, int centerX, int centerZ, int limit, int spacing) {
        List<Point> selected = new ArrayList<>();
        double separation = (double) spacing * spacing;
        for (int n = 0; n < limit; n++) {
            Point best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (var entry : matches.entrySet()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException();
                }
                Tile.Key key = entry.getKey();
                int resolution = Tile.GRID * key.step();
                byte[] cells = entry.getValue();
                for (int i = 0; i < cells.length; i++) {
                    if (cells[i] != 0) {
                        int x = key.blockX() + (i % 32) * resolution + resolution / 2,
                                z = key.blockZ() + (i / 32) * resolution + resolution / 2;
                        double dx = (double) x - centerX,
                                dz = (double) z - centerZ,
                                distance = dx * dx + dz * dz;
                        if (distance > bestDistance
                                || distance == bestDistance
                                        && best != null
                                        && (x > best.x() || x == best.x() && z >= best.z())) {
                            continue;
                        }
                        boolean close = false;
                        for (Point p : selected) {
                            double sx = (double) x - p.x(), sz = (double) z - p.z();
                            if (x == p.x() && z == p.z() || sx * sx + sz * sz < separation) {
                                close = true;
                                break;
                            }
                        }
                        if (!close) {
                            best = new Point(x, z, Math.sqrt(distance), cells[i] & 15, key);
                            bestDistance = distance;
                        }
                    }
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
        }
        return List.copyOf(selected);
    }
}
