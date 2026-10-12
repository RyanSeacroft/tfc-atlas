package dev.ryan.tfcatlas.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import net.dries007.tfc.world.noise.Cellular2D;
import net.dries007.tfc.world.noise.FastNoiseLite;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;

/**
 * Atlas-only recovery for a cellular region extending outside TFC 3.x's initial 100-grid radius. A
 * narrowly scoped compatibility adapter runs TFC's tasks with their original context. This helper
 * never touches the live world's generator, chunks, or Xaero data.
 */
final class AtlasRegionRecovery {
    private static final Constructor<RegionGenerator.Context> CONTEXT;
    private static final Method RUN;

    static {
        try {
            CONTEXT =
                    RegionGenerator.Context.class.getDeclaredConstructor(
                            RegionGenerator.class,
                            BiConsumer.class,
                            Cellular2D.Cell.class,
                            long.class);
            RUN =
                    RegionGenerator.Context.class.getDeclaredMethod(
                            "run", RegionGenerator.Task.class);
            CONTEXT.setAccessible(true);
            RUN.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private AtlasRegionRecovery() {}

    private static RegionGenerator.Context context(
            RegionGenerator generator, Cellular2D.Cell cell) {
        try {
            return CONTEXT.newInstance(
                    generator,
                    (BiConsumer<RegionGenerator.Task, Region>) (task, region) -> {},
                    cell,
                    generator.seed());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TFC region recovery context is unavailable", failure);
        }
    }

    private static void run(RegionGenerator.Context context, RegionGenerator.Task task) {
        try {
            RUN.invoke(context, task);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (failure.getCause() instanceof Error cause) {
                throw cause;
            }
            throw new IllegalStateException("TFC region task failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TFC region task is unavailable", failure);
        }
    }

    public static Region generate(RegionGenerator generator, int x, int z) {
        var cell = generator.sampleCell(x, z);
        var context = context(generator, cell);
        int centreX = FastNoiseLite.FastRound(cell.x()),
                centreZ = FastNoiseLite.FastRound(cell.y());
        // Jitter can move both neighbouring centres. This bounds the complete owning cell,
        // including points that fall outside the stock diagnostic window.
        int radius =
                Math.max(
                        Units.REGION_RADIUS_IN_GRID,
                        (int) Math.ceil(Units.CELL_WIDTH_IN_GRID * (.5 + 2 * Cellular2D.JITTER))
                                + 2);
        radius = Math.max(radius, Math.max(Math.abs(x - centreX), Math.abs(z - centreZ)) + 2);
        int side = radius * 2 + 1;
        var region = context.region;
        region.setRegionArea(
                new Region.Point[side * side],
                centreX - radius,
                centreZ - radius,
                centreX + radius,
                centreZ + radius);
        for (var task : RegionGenerator.Task.values()) {
            if (task == RegionGenerator.Task.ADD_CONTINENTS) {
                // Same ownership and land test used by TFC; only the checked bounds grow.
                for (int gridZ = region.minZ(); gridZ <= region.maxZ(); gridZ++) {
                    for (int gridX = region.minX(); gridX <= region.maxX(); gridX++) {
                        var owner = generator.sampleCell(gridX, gridZ);
                        if (owner.x() != cell.x() || owner.y() != cell.y()) {
                            continue;
                        }
                        var point = region.atInit(gridX, gridZ);
                        if (generator.continentNoise.noise(gridX, gridZ) > 4.4) {
                            point.setLand();
                        }
                        context.minX = Math.min(context.minX, gridX);
                        context.maxX = Math.max(context.maxX, gridX);
                        context.minZ = Math.min(context.minZ, gridZ);
                        context.maxZ = Math.max(context.maxZ, gridZ);
                    }
                }
            } else {
                run(context, task);
            }
        }
        return region;
    }
}
