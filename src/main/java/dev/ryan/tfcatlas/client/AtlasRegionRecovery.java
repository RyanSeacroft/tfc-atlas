package dev.ryan.tfcatlas.client;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;

/** Validate only Atlas-owned generators; never change a server's world generation. */
public final class AtlasRegionRecovery {
    private static final Map<RegionGenerator, Map<Long, Region>> OWNED =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void register(RegionGenerator generator) {
        OWNED.put(generator, new LinkedHashMap<>(16, .75f, true));
    }

    /**
     * TFC 4's 101-grid radius can miss the tips of its jittered Voronoi cells. A closest lattice
     * point is at most sqrt(.5) away, plus .437016 jitter; allow rounding of the region centre as
     * well. Ownership still uses TFC noise.
     */
    public static final int SAFE_RADIUS =
            (int) Math.ceil((Math.sqrt(.5) + .437016) * Units.CELL_WIDTH_IN_GRID) + 1;

    public static boolean initialize(RegionGenerator.Context context) {
        if (!OWNED.containsKey(context.generator())) {
            return false;
        }
        int originX =
                net.dries007.tfc.world.noise.FastNoiseLite.FastRound(context.regionCell.x())
                        - SAFE_RADIUS;
        int originZ =
                net.dries007.tfc.world.noise.FastNoiseLite.FastRound(context.regionCell.y())
                        - SAFE_RADIUS;
        int side = SAFE_RADIUS * 2 + 1,
                minX = Integer.MAX_VALUE,
                minZ = Integer.MAX_VALUE,
                maxX = Integer.MIN_VALUE,
                maxZ = Integer.MIN_VALUE;
        BitSet points = new BitSet(side * side);
        for (int dz = 0; dz < side; dz++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            for (int dx = 0; dx < side; dx++) {
                int x = originX + dx, z = originZ + dz;
                var cell = context.generator().sampleCell(x, z);
                if (cell.x() == context.regionCell.x() && cell.y() == context.regionCell.y()) {
                    points.set(dx + dz * side);
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (points.isEmpty()) {
            throw new IllegalStateException("Empty Atlas region");
        }
        var area = (dev.ryan.tfcatlas.mixin.RegionAreaAccessor) (Object) context.region;
        area.tfcatlas$setRegionArea(minX, minZ, maxX, maxZ);
        for (int index = points.nextSetBit(0); index >= 0; index = points.nextSetBit(index + 1)) {
            area.tfcatlas$init(originX + index % side, originZ + index / side);
        }
        return true;
    }

    public static Region validate(RegionGenerator generator, int x, int z, Region candidate) {
        Map<Long, Region> recovered = OWNED.get(generator);
        if (recovered == null) {
            return candidate;
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
        if (candidate != null && candidate.at(x, z) != null) {
            return candidate;
        }
        var cell = generator.sampleCell(x, z);
        long key =
                ((long) Float.floatToIntBits((float) cell.x()) << 32)
                        | (Float.floatToIntBits((float) cell.y()) & 0xffffffffL);
        synchronized (recovered) {
            Region cached = recovered.get(key);
            if (cached != null && cached.at(x, z) != null) {
                return cached;
            }
            Region[] fresh = new Region[1];
            generator.visualizeRegion(x, z, (task, region) -> fresh[0] = region);
            if (fresh[0] == null || fresh[0].at(x, z) == null) {
                throw new IllegalStateException(
                        "Missing TFC region at grid X " + x + ", Z " + z + " after regeneration");
            }
            recovered.put(key, fresh[0]);
            while (recovered.size() > 16) {
                recovered.remove(recovered.keySet().iterator().next());
            }
            return fresh[0];
        }
    }

    private AtlasRegionRecovery() {}
}
