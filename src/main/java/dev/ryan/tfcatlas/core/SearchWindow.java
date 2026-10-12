package dev.ryan.tfcatlas.core;

/** Lazy, radially ordered search bounds clipped to Minecraft's world border. */
public final class SearchWindow {
    /** Exclude square-window corners without dropping any sample inside the circular radius. */
    public static boolean intersectsRadius(Tile.Key key, int x, int z, int radius) {
        long nearestX =
                Math.max((long) key.blockX(), Math.min((long) key.blockX() + key.span() - 1, x));
        long nearestZ =
                Math.max((long) key.blockZ(), Math.min((long) key.blockZ() + key.span() - 1, z));
        double dx = nearestX - x, dz = nearestZ - z;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public static TileWindow of(int x, int z, int radius, int resolution) {
        int span = 32 * resolution;
        int minX = (int) Math.floorDiv(Math.max(-29_999_984L, (long) x - radius), span),
                maxX = (int) Math.floorDiv(Math.min(29_999_984L, (long) x + radius), span);
        int minZ = (int) Math.floorDiv(Math.max(-29_999_984L, (long) z - radius), span),
                maxZ = (int) Math.floorDiv(Math.min(29_999_984L, (long) z + radius), span);
        return new TileWindow(
                minX,
                minZ,
                maxX,
                maxZ,
                Math.floorDiv(x, span),
                Math.floorDiv(z, span),
                resolution / Tile.GRID,
                x,
                z);
    }
}
