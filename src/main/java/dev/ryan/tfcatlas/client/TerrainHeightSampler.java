package dev.ryan.tfcatlas.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.ChunkHeightFiller;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.RegionBiomeSource;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.noise.OpenSimplex2D;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.river.RiverBlendType;
import net.dries007.tfc.world.river.RiverNoiseSampler;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;

/**
 * Search-worker-only sampler using TFC's base-height API, including biome blending, shores and
 * rivers.
 */
final class TerrainHeightSampler {
    final RegionBiomeSource source;
    private final HeightWeights<BiomeExtension> weights;
    private final Map<BiomeExtension, BiomeNoiseSampler> biomes = new HashMap<>();
    private final Map<RiverBlendType, RiverNoiseSampler> rivers =
            new EnumMap<>(RiverBlendType.class);
    private final Noise2D shore;
    private final Map<Long, Integer> cache = new LinkedHashMap<>(256, .75f, true);

    TerrainHeightSampler(
            long seed,
            long biomeLayerSeed,
            RegionGenerator generator,
            HolderGetter<Biome> registry) {
        source = new RegionBiomeSource(registry);
        source.initRandomState(
                generator,
                new ConcurrentArea<>(
                        TFCLayers.createRegionBiomeLayer(generator, biomeLayerSeed),
                        TFCLayers::getFromLayerId));
        for (BiomeExtension extension : TFCBiomes.getExtensions()) {
            BiomeNoiseSampler sampler = extension.createNoiseSampler(seed);
            if (sampler != null) {
                biomes.put(extension, sampler);
            }
        }
        for (RiverBlendType blend : RiverBlendType.ALL) {
            rivers.put(blend, blend.createNoiseSampler(seed));
        }
        weights =
                new HeightWeights<>(
                        (x, z) ->
                                source.getBiomeExtensionNoRiver(
                                        QuartPos.fromBlock(x), QuartPos.fromBlock(z)),
                        BiomeExtension::biomeBlendType);
        shore = new OpenSimplex2D(seed).octaves(2).spread(.003f).scaled(-.1, 1.1);
    }

    int sample(int x, int z) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        Integer old = cache.get(key);
        if (old != null) {
            return old;
        }
        double height =
                new ChunkHeightFiller(
                                weights.at(x, z),
                                source,
                                biomes,
                                rivers,
                                shore,
                                TFCChunkGenerator.SEA_LEVEL_Y)
                        .sampleHeight(x, z);
        if (!Double.isFinite(height)) {
            throw new IllegalStateException("TFC returned an invalid terrain height");
        }
        int y = Math.max(-64, Math.min(319, (int) height));
        cache.put(key, y);
        if (cache.size() > 65536) {
            cache.remove(cache.keySet().iterator().next());
        }
        return y;
    }
}
