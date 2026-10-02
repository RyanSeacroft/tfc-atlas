package dev.ryan.tfcatlas.client;

import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Finds the upper boundary of each of TFC's first three strata in a reference column. Using TFC's
 * thickness noise avoids hard-coded Y slices. generateRock then supplies its own lateral skew and
 * geological sequence. The surface reference is zero: this predicts strata, not absolute mining
 * heights.
 */
final class RockStrata {
    // TFC 3.2.18 RegionChunkDataGenerator's stable layer-offset sequence.
    private static final int[] OFFSETS = new int[16];

    static {
        var random = new XoroshiroRandomSource(1923874192341L);
        for (int i = 0; i < OFFSETS.length; i++) {
            OFFSETS[i] = random.nextInt(0, 100_000);
        }
    }

    static int surfaceRegion(RegionChunkDataGenerator chunks, int x, int z) {
        // Same y=0, surfaceY=0 reference and DELTA_Y_OFFSET=12 as TFC's surface rock query.
        float skewX = (float) chunks.layerSkewXNoise().noise(x + OFFSETS[0], z + OFFSETS[1]);
        float skewZ = (float) chunks.layerSkewZNoise().noise(x + OFFSETS[0], z + OFFSETS[1]);
        return chunks.rockLayerArea().get().get(x + (int) (skewX * 12), z + (int) (skewZ * 12))
                & net.dries007.tfc.world.region.ChooseRocks.TYPE_MASK;
    }

    static int referenceY(RegionChunkDataGenerator chunks, int x, int z, int layer) {
        if (layer < 0 || layer > 2) {
            throw new IllegalArgumentException("Only the first three strata are mapped");
        }
        float depth = 0;
        for (int i = 0; i < layer; i++) {
            depth +=
                    (float)
                            chunks.layerHeightNoise()
                                    .noise(x + OFFSETS[2 * i], z + OFFSETS[2 * i + 1]);
        }
        // TFC includes the lower boundary in the layer above it. Step below it.
        return layer == 0 ? 0 : -(int) Math.floor(depth) - 1;
    }
}
