package dev.ryan.tfcatlas.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.util.RandomSource;

/** Validate cached regions before exposing them to Atlas or TFC's finer samplers. */
class AtlasRegionGenerator extends RegionGenerator {
    private final Map<Long, Region> recovered = new LinkedHashMap<>(16, .75f, true);
    private volatile boolean hasRecovered;

    AtlasRegionGenerator(Settings settings, RandomSource random) {
        super(settings, random);
    }

    protected Region cachedRegion(int x, int z) {
        return super.getOrCreateRegion(x, z);
    }

    protected Region freshRegion(int x, int z) {
        // TFC's public diagnostic hook runs the complete original generation pipeline without its
        // region cache.
        Region[] out = new Region[1];
        visualizeRegion(x, z, (task, region) -> out[0] = region);
        return out[0];
    }

    private static boolean contains(Region region, int x, int z) {
        return region != null && region.maybeAt(x, z) != null;
    }

    private long key(int x, int z) {
        var cell = sampleCell(x, z);
        return ((long) Float.floatToIntBits((float) cell.x()) << 32)
                | (Float.floatToIntBits((float) cell.y()) & 0xffffffffL);
    }

    @Override
    public Region getOrCreateRegion(int x, int z) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException();
        }
        if (hasRecovered) {
            synchronized (recovered) {
                Region found = recovered.get(key(x, z));
                if (contains(found, x, z)) {
                    return found;
                }
            }
        }
        RuntimeException failure = null;
        try {
            Region region = cachedRegion(x, z);
            if (contains(region, x, z)) {
                return region;
            }
        } catch (RuntimeException ex) {
            if (ex instanceof java.util.concurrent.CancellationException) {
                throw ex;
            }
            failure = ex;
        }
        synchronized (recovered) {
            long key = key(x, z);
            Region found = recovered.get(key);
            if (contains(found, x, z)) {
                return found;
            }
            try {
                Region fresh = freshRegion(x, z);
                if (!contains(fresh, x, z)) {
                    throw new IllegalStateException(
                            "Missing TFC region sample at grid X "
                                    + x
                                    + ", Z "
                                    + z
                                    + " after regeneration");
                }
                recovered.put(key, fresh);
                while (recovered.size() > 16) {
                    recovered.remove(recovered.keySet().iterator().next());
                }
                hasRecovered = true;
                com.mojang.logging.LogUtils.getLogger()
                        .warn(
                                "Atlas regenerated an invalid cached TFC region at grid X {}, Z {}",
                                x,
                                z);
                return fresh;
            } catch (RuntimeException ex) {
                if (failure != null && failure != ex) {
                    ex.addSuppressed(failure);
                }
                throw ex;
            }
        }
    }
}
