package dev.ryan.tfcatlas.client;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.dries007.tfc.world.ChunkBiomeSampler;
import net.dries007.tfc.world.biome.BiomeBlendType;

/**
 * Computes only the four biome-weight corners used by TFC's height lookup. Wide kernel samples are
 * shared between neighbouring chunks. No height interpolation or coarser approximation is
 * introduced; regression tests compare the stock API.
 */
final class HeightWeights<T> {
    private final net.dries007.tfc.world.Sampler<T> source;
    private final Function<T, BiomeBlendType> group;
    private final Map<Long, Object2DoubleMap<T>> wide = new LinkedHashMap<>(256, .75f, true),
            quart = new LinkedHashMap<>(256, .75f, true);
    private final Object2DoubleMap<T>[] column;

    @SuppressWarnings("unchecked")
    HeightWeights(net.dries007.tfc.world.Sampler<T> source, Function<T, BiomeBlendType> group) {
        this.source = source;
        this.group = group;
        column = (Object2DoubleMap<T>[]) new Object2DoubleMap[49];
    }

    Object2DoubleMap<T>[] at(int x, int z) {
        int qx = Math.floorDiv(x, 4) * 4,
                qz = Math.floorDiv(z, 4) * 4,
                ix = ((x & 15) >> 2) + 1,
                iz = ((z & 15) >> 2) + 1;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                if (dx == 1 && (x & 3) == 0 || dz == 1 && (z & 3) == 0) {
                    continue;
                } // zero-weight corners are never read by TFC
                column[ix + dx + 7 * (iz + dz)] = quart(qx + dx * 4, qz + dz * 4);
            }
        }
        return column;
    }

    Object2DoubleMap<T> point(int x, int z) {
        int qx = Math.floorDiv(x, 4) * 4, qz = Math.floorDiv(z, 4) * 4;
        if (x == qx && z == qz) {
            return quart(qx, qz);
        }
        var result = new Object2DoubleOpenHashMap<T>();
        double u = (x - qx) / 4., v = (z - qz) / 4.;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                double fraction = (dx == 0 ? 1 - u : u) * (dz == 0 ? 1 - v : v);
                if (fraction == 0) {
                    continue;
                }
                for (var e : quart(qx + 4 * dx, qz + 4 * dz).object2DoubleEntrySet()) {
                    result.mergeDouble(e.getKey(), e.getDoubleValue() * fraction, Double::sum);
                }
            }
        }
        return result;
    }

    private Object2DoubleMap<T> quart(int x, int z) {
        long key = key(x, z);
        var old = quart.get(key);
        if (old != null) {
            return old;
        }
        var near = kernel(x, z, 4);
        var far = new Object2DoubleOpenHashMap<T>();
        int bx = Math.floorDiv(x, 16) * 16, bz = Math.floorDiv(z, 16) * 16;
        double tx = (x - bx) / 16., tz = (z - bz) / 16.;
        for (int dz = 0; dz < 2; dz++) {
            for (int dx = 0; dx < 2; dx++) {
                double fraction = (dx == 0 ? 1 - tx : tx) * (dz == 0 ? 1 - tz : tz);
                if (fraction == 0) {
                    continue;
                }
                for (var e : wide(bx + dx * 16, bz + dz * 16).object2DoubleEntrySet()) {
                    far.mergeDouble(e.getKey(), e.getDoubleValue() * fraction, Double::sum);
                }
            }
        }
        double[] nearTotals = new double[BiomeBlendType.SIZE],
                farTotals = new double[BiomeBlendType.SIZE];
        for (var e : near.object2DoubleEntrySet()) {
            nearTotals[group.apply(e.getKey()).ordinal()] += e.getDoubleValue();
        }
        for (var e : far.object2DoubleEntrySet()) {
            farTotals[group.apply(e.getKey()).ordinal()] += e.getDoubleValue();
        }
        var result = new Object2DoubleOpenHashMap<T>();
        for (var e : far.object2DoubleEntrySet()) {
            int g = group.apply(e.getKey()).ordinal();
            if (nearTotals[g] > 0 && farTotals[g] > 0) {
                result.put(e.getKey(), e.getDoubleValue() * nearTotals[g] / farTotals[g]);
            }
        }
        remember(quart, key, result, 4096);
        return result;
    }

    private Object2DoubleMap<T> wide(int x, int z) {
        long key = key(x, z);
        var old = wide.get(key);
        if (old != null) {
            return old;
        }
        var value = kernel(x, z, 16);
        remember(wide, key, value, 4096);
        return value;
    }

    private Object2DoubleMap<T> kernel(int x, int z, int stride) {
        var kernel = ChunkBiomeSampler.KERNEL_9x9;
        var values = new Object2DoubleOpenHashMap<T>();
        for (int dx = -kernel.radius(); dx <= kernel.radius(); dx++) {
            for (int dz = -kernel.radius(); dz <= kernel.radius(); dz++) {
                double w =
                        kernel.values()[
                                dx + kernel.radius() + (dz + kernel.radius()) * kernel.width()];
                values.mergeDouble(source.get(x + dx * stride, z + dz * stride), w, Double::sum);
            }
        }
        return values;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static <T> void remember(Map<Long, T> cache, long key, T value, int limit) {
        cache.put(key, value);
        if (cache.size() > limit) {
            cache.remove(cache.keySet().iterator().next());
        }
    }
}
