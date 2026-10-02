package dev.ryan.tfcatlas.core;

/** Lazy, radially ordered search bounds clipped to Minecraft's world border. */
public final class SearchWindow {
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
