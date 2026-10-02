package dev.ryan.tfcatlas.core;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/**
 * Radial traversal about the actual map focus, with lazy row streams instead of a full tile list.
 */
public record TileWindow(
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int centerX,
        int centerZ,
        int step,
        double focusX,
        double focusZ)
        implements Iterable<Tile.Key> {
    public TileWindow(int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ, int step) {
        this(
                minX,
                minZ,
                maxX,
                maxZ,
                centerX,
                centerZ,
                step,
                (centerX + .5) * Tile.SIDE * Tile.GRID * step,
                (centerZ + .5) * Tile.SIDE * Tile.GRID * step);
    }

    public static TileWindow visible(
            double x, double z, double scale, int width, int height, int step) {
        int span = Tile.SIDE * Tile.GRID * step;
        double halfW = width / (2 * scale), halfH = height / (2 * scale);
        int minX = (int) Math.floor(Math.max(-29999900d, x - halfW) / span),
                maxX = (int) Math.floor(Math.min(29999900d, x + halfW) / span);
        int minZ = (int) Math.floor(Math.max(-29999900d, z - halfH) / span),
                maxZ = (int) Math.floor(Math.min(29999900d, z + halfH) / span);
        return new TileWindow(
                minX,
                minZ,
                maxX,
                maxZ,
                (int) Math.floor(x / span),
                (int) Math.floor(z / span),
                step,
                x,
                z);
    }

    public boolean contains(Tile.Key k) {
        return k.step() == step && k.x() >= minX && k.x() <= maxX && k.z() >= minZ && k.z() <= maxZ;
    }

    public double distanceSquared(Tile.Key key) {
        double dx = key.blockX() + key.span() / 2. - focusX,
                dz = key.blockZ() + key.span() / 2. - focusZ;
        return dx * dx + dz * dz;
    }

    public long count() {
        return Math.max(0, (long) maxX - minX + 1) * Math.max(0, (long) maxZ - minZ + 1);
    }

    public Iterator<Tile.Key> iterator() {
        return new Iterator<>() {
            final int cx = Math.max(minX, Math.min(maxX, centerX)),
                    cz = Math.max(minZ, Math.min(maxZ, centerZ));

            final class Row {
                final int z, direction;
                int left = cx - 1, right = cx + 1;
                Tile.Key next;
                boolean first = true;

                Row(int z, int direction) {
                    this.z = z;
                    this.direction = direction;
                    next = new Tile.Key(cx, z, step);
                }

                boolean advance() {
                    if (left < minX && right > maxX) {
                        return false;
                    }
                    var a = new Tile.Key(left, z, step);
                    var b = new Tile.Key(right, z, step);
                    if (right > maxX || left >= minX && distanceSquared(a) <= distanceSquared(b)) {
                        next = a;
                        left--;
                    } else {
                        next = b;
                        right++;
                    }
                    return true;
                }
            }

            final PriorityQueue<Row> rows =
                    new PriorityQueue<>(
                            Comparator.<Row>comparingDouble(r -> distanceSquared(r.next))
                                    .thenComparingInt(r -> r.next.z())
                                    .thenComparingInt(r -> r.next.x()));

            {
                if (count() > 0) {
                    rows.add(new Row(cz, 0));
                }
            }

            private void addRow(int z, int direction) {
                if (z >= minZ && z <= maxZ) {
                    rows.add(new Row(z, direction));
                }
            }

            public boolean hasNext() {
                return !rows.isEmpty();
            }

            public Tile.Key next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                Row row = rows.remove();
                Tile.Key result = row.next;
                if (row.first) {
                    row.first = false;
                    if (row.direction == 0) {
                        addRow(cz - 1, -1);
                        addRow(cz + 1, 1);
                    } else {
                        addRow(row.z + row.direction, row.direction);
                    }
                }
                if (row.advance()) {
                    rows.add(row);
                }
                return result;
            }
        };
    }
}
