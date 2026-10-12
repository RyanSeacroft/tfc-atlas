package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SearchLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.RiverEdge;

/**
 * Bounded regional guidance, not a scan of placed water blocks. Rivers use TFC's real fractal
 * paths; coasts use its 128-block regional grid. Owned by one worker.
 */
final class NearbyWater {
    record Distances(double river, double ocean) {}

    private record Coast(int x, int z) {}

    private record Window(List<RiverEdge> rivers, List<Coast> coast) {}

    private static final int WINDOW = 2048, GRID = 128;
    private final RegionGenerator generator;
    private final OverviewSampler overview;
    private final Map<Long, Window> windows = new LinkedHashMap<>(16, .75f, true);

    NearbyWater(RegionGenerator generator) {
        this.generator = generator;
        overview = new OverviewSampler(generator);
    }

    Distances sample(int x, int z, boolean ocean, boolean river) {
        int wx = Math.floorDiv(x, WINDOW), wz = Math.floorDiv(z, WINDOW);
        long key = ((long) wx << 32) | (wz & 0xffffffffL);
        Window window = windows.get(key);
        if (window == null) {
            window = collect(wx * WINDOW, wz * WINDOW);
            windows.put(key, window);
            while (windows.size() > 8) {
                windows.remove(windows.keySet().iterator().next());
            }
        }
        double nearestRiver = river ? 0 : Double.POSITIVE_INFINITY;
        if (!river) {
            for (RiverEdge edge : window.rivers()) {
                double distance =
                        Math.sqrt(
                                        edge.fractal()
                                                .intersectDistance(
                                                        x / (double) GRID, z / (double) GRID))
                                * GRID;
                // TFC's regional river width is a radius in blocks. Final banks vary with terrain.
                nearestRiver = Math.min(nearestRiver, Math.max(0, distance - edge.width));
            }
        }
        double nearestOcean = ocean ? 0 : Double.POSITIVE_INFINITY;
        if (!ocean) {
            for (Coast coast : window.coast()) {
                // Distance to the regional ocean square, rather than to its centre.
                double dx = Math.max(0, Math.max(coast.x() - x, x - (coast.x() + GRID)));
                double dz = Math.max(0, Math.max(coast.z() - z, z - (coast.z() + GRID)));
                nearestOcean = Math.min(nearestOcean, Math.hypot(dx, dz));
            }
        }
        return new Distances(bounded(nearestRiver), bounded(nearestOcean));
    }

    private static double bounded(double value) {
        return value <= SearchLimits.WATER_REACH ? value : Double.POSITIVE_INFINITY;
    }

    private Window collect(int x, int z) {
        int reach = SearchLimits.WATER_REACH + GRID;
        int minX = Math.floorDiv(x - reach, GRID), minZ = Math.floorDiv(z - reach, GRID);
        int maxX = Math.floorDiv(x + WINDOW + reach, GRID);
        int maxZ = Math.floorDiv(z + WINDOW + reach, GRID);
        var rivers = new HashSet<RiverEdge>();
        List<Coast> coast = new ArrayList<>();
        for (int gz = minZ; gz <= maxZ; gz++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException();
            }
            for (int gx = minX; gx <= maxX; gx++) {
                if (!overview.point(gx, gz).land()
                        && (overview.point(gx - 1, gz).land()
                                || overview.point(gx + 1, gz).land()
                                || overview.point(gx, gz - 1).land()
                                || overview.point(gx, gz + 1).land())) {
                    coast.add(new Coast(gx * GRID, gz * GRID));
                }
                // Each partition contains three by three regional grid points.
                if (Math.floorMod(gx, 3) == 0 && Math.floorMod(gz, 3) == 0) {
                    rivers.addAll(generator.getOrCreatePartitionPoint(gx, gz).rivers());
                }
            }
        }
        return new Window(List.copyOf(rivers), List.copyOf(coast));
    }
}
