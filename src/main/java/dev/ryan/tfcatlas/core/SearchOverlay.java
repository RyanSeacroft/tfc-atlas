package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable coverage snapshots. Live updates share unchanged pages and exact outline geometry. */
public final class SearchOverlay {
    public static final SearchOverlay EMPTY = new SearchOverlay(Map.of(), List.of());
    private final Map<Tile.Key, byte[]> layers;
    private final Map<Tile.Key, List<Edge>> edgePages;
    private final Map<Long, OutlineGroup> groups;
    private final int step, stride, areaCount;
    private final long count;
    private final boolean layered, complete;
    private final Object series;
    private final List<SearchQuery.Result> candidates;

    public record Bounds(int blockX, int blockZ, int pixelWidth, int pixelHeight) {}

    public record Edge(int x0, int z0, int x1, int z1, int layerMask) {
        public Edge(int x0, int z0, int x1, int z1) {
            this(x0, z0, x1, z1, 1);
        }
    }

    /** Fixed spatial batches: geometry can remain on the GPU while the camera pans or zooms. */
    public record OutlineGroup(long key, int blockX, int blockZ, int span, List<Edge> edges) {}

    private final Bounds bounds;
    private final byte[] overviewPixels;

    private static long bucket(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public SearchOverlay(Map<Tile.Key, BitSet> cells, List<SearchQuery.Result> candidates) {
        this(masks(cells), candidates, false);
    }

    private static Map<Tile.Key, byte[]> masks(Map<Tile.Key, BitSet> cells) {
        Map<Tile.Key, byte[]> values = new HashMap<>();
        cells.forEach(
                (key, bits) -> {
                    byte[] row = new byte[1024];
                    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                        row[i] = 1;
                    }
                    values.put(key, row);
                });
        return values;
    }

    public SearchOverlay(
            Map<Tile.Key, byte[]> masks, List<SearchQuery.Result> candidates, boolean layered) {
        this(masks, candidates, layered, true, new Object(), 8, null, masks.keySet());
    }

    private SearchOverlay(
            Map<Tile.Key, byte[]> masks,
            List<SearchQuery.Result> candidates,
            boolean layered,
            boolean complete,
            Object series,
            int stride,
            SearchOverlay previous,
            Set<Tile.Key> dirty) {
        int suppliedStep = masks.keySet().stream().findFirst().map(Tile.Key::step).orElse(1);
        for (Tile.Key key : masks.keySet()) {
            if (key.step() != suppliedStep
                    || key.step() < 1
                    || key.step() > Sampling.MAX_SEARCH_RESOLUTION / Tile.GRID) {
                throw new IllegalArgumentException(
                        "Search coverage must use one resolution (8–512 blocks)");
            }
        }
        this.layered = layered;
        this.complete = complete;
        this.series = series;
        this.stride = stride;
        this.candidates = List.copyOf(candidates);
        Map<Tile.Key, byte[]> data =
                previous == null ? new HashMap<>() : new HashMap<>(previous.layers);
        long total = previous == null ? 0 : previous.count;
        for (Tile.Key key : dirty) {
            interrupted();
            byte[] values = masks.get(key), old = data.get(key);
            if (old != null) {
                for (byte b : old) {
                    if (b != 0) {
                        total--;
                    }
                }
            }
            if (values == null || values.length != 1024) {
                throw new IllegalArgumentException("Invalid layer coverage");
            }
            byte[] copy = values.clone();
            int n = 0;
            for (byte b : copy) {
                if (b < 0 || b > 15) {
                    throw new IllegalArgumentException("Invalid layer mask");
                }
                if (b != 0) {
                    n++;
                }
            }
            if (n > 0) {
                data.put(key, copy);
            } else {
                data.remove(key);
            }
            total += n;
        }
        layers = Map.copyOf(data);
        count = total;
        step = layers.keySet().stream().findFirst().map(Tile.Key::step).orElse(1);
        for (Tile.Key key : layers.keySet()) {
            if (key.step() != step
                    || step < 1
                    || step > Sampling.MAX_SEARCH_RESOLUTION / Tile.GRID) {
                throw new IllegalArgumentException(
                        "Search coverage must use one resolution (8–512 blocks)");
            }
        }
        areaCount = complete ? SearchAreas.count(layers) : -1;
        int minX = layers.keySet().stream().mapToInt(Tile.Key::x).min().orElse(0),
                maxX = layers.keySet().stream().mapToInt(Tile.Key::x).max().orElse(-1);
        int minZ = layers.keySet().stream().mapToInt(Tile.Key::z).min().orElse(0),
                maxZ = layers.keySet().stream().mapToInt(Tile.Key::z).max().orElse(-1);
        bounds =
                new Bounds(
                        minX * 32 * resolution(),
                        minZ * 32 * resolution(),
                        (maxX - minX + 1) * 32,
                        (maxZ - minZ + 1) * 32);
        boolean same = previous != null && bounds.equals(previous.bounds) && step == previous.step;
        overviewPixels =
                buildOverview(
                        same ? previous.overviewPixels : null, same ? dirty : layers.keySet());
        Set<Tile.Key> affected = new HashSet<>(dirty);
        for (Tile.Key k : dirty) {
            affected.add(new Tile.Key(k.x() - 1, k.z(), k.step()));
            affected.add(new Tile.Key(k.x() + 1, k.z(), k.step()));
            affected.add(new Tile.Key(k.x(), k.z() - 1, k.step()));
            affected.add(new Tile.Key(k.x(), k.z() + 1, k.step()));
        }
        Map<Tile.Key, List<Edge>> pages =
                previous == null ? new HashMap<>() : new HashMap<>(previous.edgePages);
        Set<Long> changedGroups = new HashSet<>();
        for (Tile.Key key : affected) {
            interrupted();
            if (layers.containsKey(key)) {
                pages.put(key, buildEdges(key));
            } else {
                pages.remove(key);
            }
            changedGroups.add(groupKey(key));
        }
        edgePages = Map.copyOf(pages);
        Map<Long, OutlineGroup> updated =
                previous == null ? new HashMap<>() : new HashMap<>(previous.groups);
        Map<Long, List<Edge>> additions = new HashMap<>();
        for (var entry : pages.entrySet()) {
            long key = groupKey(entry.getKey());
            if (changedGroups.contains(key)) {
                additions.computeIfAbsent(key, k -> new ArrayList<>()).addAll(entry.getValue());
            }
        }
        int span = stride * 32 * resolution();
        for (long key : changedGroups) {
            var edges = additions.get(key);
            if (edges == null || edges.isEmpty()) {
                updated.remove(key);
            } else {
                updated.put(
                        key,
                        new OutlineGroup(
                                key,
                                (int) (key >> 32) * span,
                                (int) key * span,
                                span,
                                List.copyOf(edges)));
            }
        }
        groups = Map.copyOf(updated);
    }

    private long groupKey(Tile.Key key) {
        return bucket(Math.floorDiv(key.x(), stride), Math.floorDiv(key.z(), stride));
    }

    private List<Edge> buildEdges(Tile.Key key) {
        List<Edge> out = new ArrayList<>();
        int x0 = key.x() * 32, z0 = key.z() * 32;
        for (int z = z0; z < z0 + 32; z++) {
            int start = x0, last = 0;
            for (int x = x0; x <= x0 + 32; x++) {
                int mask = x < x0 + 32 ? boundaryMask(maskGrid(x, z - 1), maskGrid(x, z)) : 0;
                if (mask != last) {
                    if (last != 0) {
                        out.add(
                                new Edge(
                                        start * resolution(),
                                        z * resolution(),
                                        x * resolution(),
                                        z * resolution(),
                                        last));
                    }
                    start = x;
                    last = mask;
                }
            }
        }
        for (int x = x0; x < x0 + 32; x++) {
            int start = z0, last = 0;
            for (int z = z0; z <= z0 + 32; z++) {
                int mask = z < z0 + 32 ? boundaryMask(maskGrid(x - 1, z), maskGrid(x, z)) : 0;
                if (mask != last) {
                    if (last != 0) {
                        out.add(
                                new Edge(
                                        x * resolution(),
                                        start * resolution(),
                                        x * resolution(),
                                        z * resolution(),
                                        last));
                    }
                    start = z;
                    last = mask;
                }
            }
        }
        if (!layers.containsKey(new Tile.Key(key.x(), key.z() + 1, step))) {
            appendOutside(out, x0, z0 + 32, true);
        }
        if (!layers.containsKey(new Tile.Key(key.x() + 1, key.z(), step))) {
            appendOutside(out, x0 + 32, z0, false);
        }
        return List.copyOf(out);
    }

    private void appendOutside(List<Edge> out, int x, int z, boolean horizontal) {
        int start = 0, last = 0;
        for (int i = 0; i <= 32; i++) {
            int mask =
                    i == 32
                            ? 0
                            : horizontal
                                    ? boundaryMask(maskGrid(x + i, z - 1), 0)
                                    : boundaryMask(maskGrid(x - 1, z + i), 0);
            if (mask != last) {
                if (last != 0) {
                    out.add(
                            horizontal
                                    ? new Edge(
                                            (x + start) * resolution(),
                                            z * resolution(),
                                            (x + i) * resolution(),
                                            z * resolution(),
                                            last)
                                    : new Edge(
                                            x * resolution(),
                                            (z + start) * resolution(),
                                            x * resolution(),
                                            (z + i) * resolution(),
                                            last));
                }
                start = i;
                last = mask;
            }
        }
    }

    private byte[] buildOverview(byte[] old, Set<Tile.Key> changed) {
        int scale = overviewScale(),
                w = (bounds.pixelWidth() + scale - 1) / scale,
                h = (bounds.pixelHeight() + scale - 1) / scale;
        byte[] pixels = old == null ? new byte[w * h] : old.clone();
        for (Tile.Key key : changed) {
            interrupted();
            byte[] values = layers.get(key);
            if (values == null) {
                continue;
            }
            int ox = (key.blockX() - bounds.blockX()) / resolution(),
                    oz = (key.blockZ() - bounds.blockZ()) / resolution();
            for (int i = 0; i < 1024; i++) {
                if (values[i] != 0) {
                    pixels[(ox + i % 32) / scale + w * ((oz + i / 32) / scale)] |= values[i];
                }
            }
        }
        return pixels;
    }

    private static void interrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    private int boundaryMask(int a, int b) {
        return a == b ? 0 : !layered ? (a == 0 || b == 0 ? 1 : 0) : a | b;
    }

    private int maskGrid(int x, int z) {
        return layerMask(
                new Tile.Key(Math.floorDiv(x, 32), Math.floorDiv(z, 32), step),
                Math.floorMod(x, 32) + 32 * Math.floorMod(z, 32));
    }

    public int areaCount() {
        return areaCount;
    }

    public boolean complete() {
        return complete;
    }

    public Object series() {
        return series;
    }

    public Object pageIdentity(Tile.Key key) {
        return layers.get(key);
    }

    public boolean layered() {
        return layered;
    }

    public int layerMask(Tile.Key key, int index) {
        byte[] values = layers.get(key);
        return values == null ? 0 : values[index];
    }

    public int colour(Tile.Key key, int index, int fallback) {
        return layered ? RockLayer.colour(layerMask(key, index)) : fallback;
    }

    public int resolution() {
        return Tile.GRID * step;
    }

    public int overviewScale() {
        int scale = 1;
        while ((Math.max(bounds.pixelWidth(), bounds.pixelHeight()) + scale - 1) / scale > 1024) {
            scale *= 2;
        }
        return scale;
    }

    public byte[] overview() {
        return overviewPixels;
    }

    public Set<Tile.Key> keys() {
        return layers.keySet();
    }

    public boolean matches(Tile.Key key, int index) {
        return layerMask(key, index) != 0;
    }

    public long count() {
        return count;
    }

    public List<SearchQuery.Result> candidates() {
        return candidates;
    }

    public Collection<OutlineGroup> groups() {
        return groups.values();
    }

    public List<Edge> edges() {
        return edgePages.values().stream().flatMap(List::stream).toList();
    }

    public Collection<Edge> edgesIn(double minX, double minZ, double maxX, double maxZ) {
        List<Edge> out = new ArrayList<>();
        for (var group : groups.values()) {
            if (group.blockX > maxX
                    || group.blockZ > maxZ
                    || group.blockX + (double) group.span < minX
                    || group.blockZ + (double) group.span < minZ) {
                continue;
            }
            for (Edge e : group.edges) {
                if (Math.max(e.x0, e.x1) >= minX
                        && Math.min(e.x0, e.x1) <= maxX
                        && Math.max(e.z0, e.z1) >= minZ
                        && Math.min(e.z0, e.z1) <= maxZ) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    public Bounds bounds() {
        return bounds;
    }

    /** Owned by the search worker. Coverage grows monotonically; published arrays never change. */
    public static final class Builder {
        private final Map<Tile.Key, byte[]> masks;
        private final Set<Tile.Key> dirty = new HashSet<>();
        private final Object series = new Object();
        private final boolean layered;
        private final int stride;
        private SearchOverlay previous;

        public Builder(Map<Tile.Key, byte[]> masks, boolean layered, int radius, int resolution) {
            this.masks = masks;
            this.layered = layered;
            int size = 8;
            // Bound draw calls, not precision. Every original edge remains in its batch.
            while (Math.min(60_000_000L, 2L * radius) / (32L * resolution * size) > 12) {
                size *= 2;
            }
            stride = size;
        }

        public void changed(Tile.Key key) {
            dirty.add(key);
        }

        public boolean changed() {
            return !dirty.isEmpty();
        }

        public SearchOverlay publish(List<SearchQuery.Result> candidates, boolean complete) {
            if (!complete && dirty.isEmpty() && previous != null) {
                return previous;
            }
            previous =
                    new SearchOverlay(
                            masks, candidates, layered, complete, series, stride, previous, dirty);
            dirty.clear();
            return previous;
        }
    }
}
