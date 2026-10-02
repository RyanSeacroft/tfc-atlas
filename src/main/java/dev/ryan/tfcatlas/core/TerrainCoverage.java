package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/** Exact runs of known native terrain pixels; no expansion to prediction cells. */
public final class TerrainCoverage {
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
