package dev.ryan.tfcatlas.client;

import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;

/** Reuse nearby immutable regions without re-running cellular lookup for every overview pixel. */
final class OverviewSampler {
    private final RegionGenerator generator;
    private final Object[] locks = new Object[64];
    private final ThreadLocal<Region[]> recent = ThreadLocal.withInitial(() -> new Region[4]);

    OverviewSampler(RegionGenerator generator) {
        this.generator = generator;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    Region.Point point(int x, int z) {
        Region[] regions = recent.get();
        for (Region region : regions) {
            if (region != null) {
                Region.Point point = region.maybeAt(x, z);
                if (point != null) {
                    return point;
                }
            }
        }
        var cell = generator.sampleCell(x, z);
        int hash =
                31 * Float.floatToIntBits((float) cell.x())
                        + Float.floatToIntBits((float) cell.y());
        Region region;
        // Adjacent overview workers must not both generate the same initially cold region.
        synchronized (locks[(hash ^ (hash >>> 16)) & 63]) {
            region = generator.getOrCreateRegion(x, z);
        }
        System.arraycopy(regions, 0, regions, 1, regions.length - 1);
        regions[0] = region;
        Region.Point point = region.maybeAt(x, z);
        if (point == null) {
            throw new IllegalStateException("Missing overview sample at grid X " + x + ", Z " + z);
        }
        return point;
    }
}
