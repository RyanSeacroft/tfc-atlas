package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.TerrainDetail;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.RegionBiomeSource;
import net.minecraft.core.QuartPos;

/** Mountain dominance in the same blended biome weights TFC uses for its terrain heights. */
final class TerrainShapeSampler {
    private final HeightWeights<BiomeExtension> weights;

    TerrainShapeSampler(RegionBiomeSource source) {
        weights =
                new HeightWeights<>(
                        (x, z) ->
                                source.getBiomeExtensionNoRiver(
                                        QuartPos.fromBlock(x), QuartPos.fromBlock(z)),
                        BiomeExtension::biomeBlendType);
    }

    boolean mountain(int x, int z) {
        double mountain = 0, total = 0;
        for (var entry : weights.point(x, z).object2DoubleEntrySet()) {
            double weight = entry.getDoubleValue();
            total += weight;
            if (TerrainDetail.mountain(entry.getKey().key().location().toString())) {
                mountain += weight;
            }
        }
        return total > 0 && mountain >= total * .5;
    }
}
