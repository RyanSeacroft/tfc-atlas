package dev.ryan.tfcatlas.core;

/** Continuous regional altitude bands; fine biome boundaries define land and mountains. */
public final class TerrainDetail {
    public static int altitude(int x, int z, int a, int b, int c, int d) {
        double u = Math.floorMod(x, 128) / 128., v = Math.floorMod(z, 128) / 128.;
        return (int) Math.round((a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v);
    }

    public static boolean mountain(String biome) {
        return biome.substring(biome.indexOf(':') + 1).contains("mountain");
    }
}
