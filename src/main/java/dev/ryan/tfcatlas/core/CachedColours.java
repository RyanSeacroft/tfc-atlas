package dev.ryan.tfcatlas.core;

/** Transfer cached colour coverage without erasing finer pixels or changing overlay opacity. */
public final class CachedColours {
    public interface Pixels {
        int get(int x, int z);

        void put(int x, int z, int colour);
    }

    public record Raster(int x, int z, int span, int size, Pixels pixels) {}

    public static boolean fillMissing(Raster target, Raster source) {
        return fillMissing(
                target, source, target.x, target.z, target.x + target.span, target.z + target.span);
    }

    /** Limit hole filling to the visible footprint instead of scanning an entire packed page. */
    public static boolean fillMissing(
            Raster target, Raster source, int minX, int minZ, int maxX, int maxZ) {
        int left = Math.max(minX, Math.max(target.x, source.x)),
                top = Math.max(minZ, Math.max(target.z, source.z));
        int right = Math.min(maxX, Math.min(target.x + target.span, source.x + source.span));
        int bottom = Math.min(maxZ, Math.min(target.z + target.span, source.z + source.span));
        if (left >= right || top >= bottom) {
            return false;
        }
        int x0 = Math.max(0, (int) ((long) (left - target.x) * target.size / target.span));
        int z0 = Math.max(0, (int) ((long) (top - target.z) * target.size / target.span));
        int x1 =
                Math.min(
                        target.size,
                        (int) Math.ceil((double) (right - target.x) * target.size / target.span));
        int z1 =
                Math.min(
                        target.size,
                        (int) Math.ceil((double) (bottom - target.z) * target.size / target.span));
        boolean changed = false;
        for (int z = z0; z < z1; z++) {
            for (int x = x0; x < x1; x++) {
                if ((target.pixels.get(x, z) >>> 24) != 0) {
                    continue;
                }
                double wx = target.x + (x + .5) * target.span / target.size;
                double wz = target.z + (z + .5) * target.span / target.size;
                int sx = (int) Math.floor((wx - source.x) * source.size / source.span);
                int sz = (int) Math.floor((wz - source.z) * source.size / source.span);
                if (sx < 0 || sz < 0 || sx >= source.size || sz >= source.size) {
                    continue;
                }
                int colour = source.pixels.get(sx, sz);
                if ((colour >>> 24) != 0) {
                    target.pixels.put(x, z, colour);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private CachedColours() {}
}
