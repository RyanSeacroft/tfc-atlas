package dev.ryan.tfcatlas.core;

import java.util.Map;

/** Refines a discovered pipe to eight-block coverage, independently of search discovery spacing. */
public final class DikeCoverage {
    @FunctionalInterface
    public interface Match {
        boolean at(int x, int z) throws Exception;
    }

    public static long add(
            int x,
            int z,
            int reach,
            int centerX,
            int centerZ,
            int radius,
            Map<Tile.Key, byte[]> matched,
            Match match)
            throws Exception {
        return add(x, z, reach, centerX, centerZ, radius, matched, match, key -> {});
    }

    public static long add(
            int x,
            int z,
            int reach,
            int centerX,
            int centerZ,
            int radius,
            Map<Tile.Key, byte[]> matched,
            Match match,
            java.util.function.Consumer<Tile.Key> changed)
            throws Exception {
        long added = 0;
        for (int bz = Math.floorDiv(z - reach, 8) * 8 + 4; bz <= z + reach; bz += 8) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException();
            }
            for (int bx = Math.floorDiv(x - reach, 8) * 8 + 4; bx <= x + reach; bx += 8) {
                if (Math.abs((long) bx) > 29_999_984
                        || Math.abs((long) bz) > 29_999_984
                        || Math.hypot((double) bx - centerX, (double) bz - centerZ) > radius) {
                    continue;
                }
                Tile.Key key = Tile.Key.at(bx, bz, 1);
                int i = Math.floorMod(bx, 256) / 8 + 32 * (Math.floorMod(bz, 256) / 8);
                byte[] bits = matched.get(key);
                if (bits != null && bits[i] != 0) {
                    continue;
                }
                if (!match.at(bx, bz)) {
                    continue;
                }
                if (bits == null) {
                    bits = new byte[1024];
                    matched.put(key, bits);
                }
                bits[i] = 8;
                added++;
                changed.accept(key);
            }
        }
        return added;
    }
}
