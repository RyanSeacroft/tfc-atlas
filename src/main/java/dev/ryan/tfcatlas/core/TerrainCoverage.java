package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/** Exact runs of known native terrain pixels; no expansion to prediction cells. */
public final class TerrainCoverage {
    /** Unknown pixels inside the viewport, including holes in overlapping saved textures. */
    public static List<Rect> complement(List<Rect> known, int x0, int z0, int x1, int z1) {
        if (x0 >= x1 || z0 >= z1) {
            return List.of();
        }
        record Edge(int x0, int x1, int change) {}
        var events = new java.util.TreeMap<Integer, List<Edge>>();
        events.put(z0, new ArrayList<>());
        events.put(z1, new ArrayList<>());
        for (Rect r : known) {
            Rect clipped = r.intersect(x0, z0, x1, z1);
            if (clipped != null) {
                events.computeIfAbsent(clipped.z0(), k -> new ArrayList<>())
                        .add(new Edge(clipped.x0(), clipped.x1(), 1));
                events.computeIfAbsent(clipped.z1(), k -> new ArrayList<>())
                        .add(new Edge(clipped.x0(), clipped.x1(), -1));
            }
        }
        var edges = new java.util.TreeMap<Integer, Integer>();
        List<Rect> unknown = new ArrayList<>();
        int previousZ = z0;
        for (var event : events.entrySet()) {
            int z = event.getKey();
            if (z > previousZ) {
                int count = 0, start = x0;
                for (var edge : edges.entrySet()) {
                    if (count == 0 && edge.getKey() > start) {
                        unknown.add(new Rect(start, previousZ, edge.getKey(), z));
                    }
                    count += edge.getValue();
                    start = edge.getKey();
                }
                if (count == 0 && start < x1) {
                    unknown.add(new Rect(start, previousZ, x1, z));
                }
            }
            for (Edge edge : event.getValue()) {
                edges.merge(edge.x0(), edge.change(), Integer::sum);
                edges.merge(edge.x1(), -edge.change(), Integer::sum);
            }
            edges.values().removeIf(count -> count == 0);
            previousZ = z;
        }
        return List.copyOf(unknown);
    }

    public record Rect(int x0, int z0, int x1, int z1) {
        public Rect intersect(int a, int b, int c, int d) {
            int x = Math.max(x0, a),
                    z = Math.max(z0, b),
                    xx = Math.min(x1, c),
                    zz = Math.min(z1, d);
            return x < xx && z < zz ? new Rect(x, z, xx, zz) : null;
        }
    }

    public static List<Rect> read(IntBinaryOperator height, IntBinaryOperator topHeight) {
        List<Rect> out = new ArrayList<>();
        Map<Long, Rect> active = new HashMap<>();
        for (int z = 0; z < 64; z++) {
            Map<Long, Rect> next = new HashMap<>();
            int x = 0;
            while (x < 64) {
                if (height.applyAsInt(x, z) == Short.MAX_VALUE
                        && topHeight.applyAsInt(x, z) == Short.MAX_VALUE) {
                    x++;
                    continue;
                }
                int start = x++;
                while (x < 64
                        && (height.applyAsInt(x, z) != Short.MAX_VALUE
                                || topHeight.applyAsInt(x, z) != Short.MAX_VALUE)) {
                    x++;
                }
                long key = ((long) start << 32) | x;
                Rect prev = active.remove(key);
                next.put(key, new Rect(start, prev == null ? z : prev.z0(), x, z + 1));
            }
            out.addAll(active.values());
            active = next;
        }
        out.addAll(active.values());
        return List.copyOf(out);
    }
}
