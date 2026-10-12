package dev.ryan.tfcatlas.core;

import java.util.function.IntBinaryOperator;

/** Maximum local grade over a fixed 32-block baseline, independent of search sampling. */
public final class TerrainSlope {
    private TerrainSlope() {}

    public static double sample(int x, int z, IntBinaryOperator heights) {
        int center = heights.applyAsInt(x, z);
        double grade = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int y = heights.applyAsInt(x + dx * 32, z + dz * 32);
                grade = Math.max(grade, Math.abs(y - center) / (32 * Math.hypot(dx, dz)));
            }
        }
        return Math.toDegrees(Math.atan(grade));
    }
}
