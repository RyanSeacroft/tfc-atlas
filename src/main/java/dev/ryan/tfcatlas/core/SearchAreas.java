package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Four-connected matching areas, independent of candidate separation and rock highlight colours.
 */
public final class SearchAreas {
    private int[] parent = new int[1024];
    private long[] area = new long[1024];
    private final Map<Tile.Key, int[]> savedLabels = new HashMap<>();
    private boolean retainLabels;
    private int size = 1, count;

    private int create() {
        if (size == parent.length) {
            parent = Arrays.copyOf(parent, parent.length * 2);
            area = Arrays.copyOf(area, parent.length);
        }
        parent[size] = size;
        count++;
        return size++;
    }

    private int root(int n) {
        while (parent[n] != n) {
            parent[n] = parent[parent[n]];
            n = parent[n];
        }
        return n;
    }

    private void join(int a, int b) {
        if (a == 0 || b == 0) {
            return;
        }
        int x = root(a), y = root(b);
        if (x != y) {
            parent[Math.max(x, y)] = Math.min(x, y);
            area[Math.min(x, y)] += area[Math.max(x, y)];
            count--;
        }
    }

    public static int count(Map<Tile.Key, byte[]> masks) {
        return new SearchAreas().scan(masks);
    }

    /** Filter whole four-connected components, including those crossing tile boundaries. */
    public static void retainMinimum(
            Map<Tile.Key, byte[]> masks,
            double minimum,
            java.util.function.Consumer<Tile.Key> changed) {
        retainRange(masks, minimum, Double.MAX_VALUE, changed);
    }

    /** Keep complete connected regions whose sampled area is within inclusive bounds. */
    public static void retainRange(
            Map<Tile.Key, byte[]> masks,
            double minimum,
            double maximum,
            java.util.function.Consumer<Tile.Key> changed) {
        if (minimum <= 0 && maximum == Double.MAX_VALUE) {
            return;
        }
        SearchAreas regions = new SearchAreas();
        regions.retainLabels = true;
        regions.scan(masks);
        for (var entry : regions.savedLabels.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            byte[] cells = masks.get(entry.getKey());
            int[] labels = entry.getValue();
            boolean removed = false;
            for (int i = 0; i < cells.length; i++) {
                if (labels[i] != 0
                        && (regions.area[regions.root(labels[i])] < minimum
                                || regions.area[regions.root(labels[i])] > maximum)) {
                    cells[i] = 0;
                    removed = true;
                }
            }
            if (removed) {
                changed.accept(entry.getKey());
            }
        }
    }

    private int scan(Map<Tile.Key, byte[]> masks) {
        List<Tile.Key> keys = new ArrayList<>(masks.keySet());
        keys.sort(Comparator.comparingInt(Tile.Key::z).thenComparingInt(Tile.Key::x));
        Map<Integer, int[]> previous = new HashMap<>(), row = new HashMap<>();
        int currentZ = Integer.MIN_VALUE, lastX = Integer.MIN_VALUE;
        int[] right = null;
        for (Tile.Key key : keys) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            if (key.z() != currentZ) {
                previous = key.z() == currentZ + 1 ? row : new HashMap<>();
                row = new HashMap<>();
                currentZ = key.z();
                lastX = Integer.MIN_VALUE;
                right = null;
            }
            byte[] cells = masks.get(key);
            int[] labels = new int[1024], queue = new int[1024];
            for (int start = 0; start < 1024; start++) {
                if (cells[start] != 0 && labels[start] == 0) {
                    int label = create(), head = 0, tail = 0;
                    queue[tail++] = start;
                    labels[start] = label;
                    while (head < tail) {
                        int i = queue[head++];
                        if (i % 32 > 0) {
                            tail = visit(i - 1, label, cells, labels, queue, tail);
                        }
                        if (i % 32 < 31) {
                            tail = visit(i + 1, label, cells, labels, queue, tail);
                        }
                        if (i >= 32) {
                            tail = visit(i - 32, label, cells, labels, queue, tail);
                        }
                        if (i < 992) {
                            tail = visit(i + 32, label, cells, labels, queue, tail);
                        }
                    }
                }
            }
            long cellArea = (long) Tile.GRID * key.step() * Tile.GRID * key.step();
            for (int label : labels) {
                if (label != 0) {
                    area[root(label)] += cellArea;
                }
            }
            if (retainLabels) {
                savedLabels.put(key, labels);
            }
            int[] above = previous.get(key.x()), bottom = new int[32], nextRight = new int[32];
            for (int i = 0; i < 32; i++) {
                if (above != null) {
                    join(labels[i], above[i]);
                }
                if (right != null && key.x() == lastX + 1) {
                    join(labels[i * 32], right[i]);
                }
                bottom[i] = labels[992 + i];
                nextRight[i] = labels[i * 32 + 31];
            }
            row.put(key.x(), bottom);
            right = nextRight;
            lastX = key.x();
        }
        return count;
    }

    private static int visit(int i, int label, byte[] cells, int[] labels, int[] queue, int tail) {
        if (cells[i] != 0 && labels[i] == 0) {
            labels[i] = label;
            queue[tail++] = i;
        }
        return tail;
    }
}
