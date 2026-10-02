package dev.ryan.tfcatlas.core;

import java.util.Arrays;
import java.util.BitSet;

/** Keeps background 32-block patches authoritative when coarse tiles arrive later. */
public final class DetailCoverage {
    private final int ratio, side;
    private final BitSet fine;
    private final long[] revisions;

    public DetailCoverage(int resolution) {
        if (resolution != 32 && resolution != 64 && resolution != 128 && resolution != 256) {
            throw new IllegalArgumentException("Invalid overview resolution");
        }
        ratio = resolution / 32;
        side = 8 * ratio;
        fine = new BitSet(side * side);
        revisions = new long[side * side];
    }

    public boolean needs(int x, int z, long revision) {
        return revisions[x + z * side] != revision;
    }

    public void mark(int x, int z, long revision) {
        fine.set(x + z * side);
        revisions[x + z * side] = revision;
    }

    public boolean coarsePixelAllowed(int x, int z, int pixelsPerTile) {
        int unit = pixelsPerTile / ratio;
        return !fine.get(x / unit + (z / unit) * side);
    }

    public boolean complete(int tileX, int tileZ) {
        for (int z = tileZ * ratio; z < (tileZ + 1) * ratio; z++) {
            for (int x = tileX * ratio; x < (tileX + 1) * ratio; x++) {
                if (!fine.get(x + z * side)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void invalidateRevisions() {
        Arrays.fill(revisions, 0);
    }
}
