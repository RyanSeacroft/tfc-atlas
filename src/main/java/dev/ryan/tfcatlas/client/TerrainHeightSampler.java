package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.mixin.TfcGeneratorAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.ChunkHeightFiller;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeNoise;
import net.dries007.tfc.world.biome.RegionBiomeSource;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** Independent mutable height samplers, using TFC's factories and preparation callbacks. */
final class TerrainHeightSampler {
    final RegionBiomeSource source;
    private final HeightWeights<BiomeExtension> weights;
    private final Map<Long, Integer> cache = new LinkedHashMap<>(256, .75f, true);
    private final TfcGeneratorAccessor access;
    private final net.dries007.tfc.world.noise.Noise2D tide;
    private final Map<BiomeExtension, BiomeNoiseSampler> biomes;
    private final Map<
                    net.dries007.tfc.world.river.RiverBlendType,
                    net.dries007.tfc.world.river.RiverNoiseSampler>
            rivers;
    private final Map<
                    net.dries007.tfc.world.shore.ShoreBlendType,
                    net.dries007.tfc.world.shore.ShoreNoiseSampler>
            shores;
    private final Map<
                    net.dries007.tfc.world.volcano.CenteredFeatureBlendType,
                    net.dries007.tfc.world.volcano.CenteredFeatureNoiseSampler>
            volcanoes;

    TerrainHeightSampler(
            long seed,
            RegionBiomeSource source,
            Settings settings,
            RegistryAccess registry,
            ChunkDataGenerator chunks) {
        // Vanilla noise settings only satisfy the owner constructor; all heights below use TFC
        // factories.
        this.source = source;
        var owner =
                new TFCChunkGenerator(
                        source, Holder.direct(NoiseGeneratorSettings.dummy()), settings);
        access = (TfcGeneratorAccessor) (Object) owner;
        access.tfcatlas$seed(Seed.of(seed));
        access.tfcatlas$chunks(chunks);
        tide = BiomeNoise.shoreTideLevelNoise(Seed.of(seed));
        weights =
                new HeightWeights<>(
                        (x, z) ->
                                source.getBiomeExtensionNoRiver(
                                        QuartPos.fromBlock(x), QuartPos.fromBlock(z)),
                        BiomeExtension::biomeBlendType);
        biomes = access.tfcatlas$biomes(null);
        rivers = access.tfcatlas$rivers();
        shores = access.tfcatlas$shores();
        volcanoes = access.tfcatlas$volcanoes();
    }

    int sample(int x, int z) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        Integer old = cache.get(key);
        if (old != null) {
            return old;
        }
        double value =
                new ChunkHeightFiller(
                                weights.at(x, z),
                                source,
                                biomes,
                                rivers,
                                shores,
                                volcanoes,
                                TFCChunkGenerator.SEA_LEVEL_Y,
                                tide)
                        .sampleHeight(x, z);
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("TFC returned an invalid terrain height");
        }
        int y = Math.max(-64, Math.min(319, (int) value));
        cache.put(key, y);
        if (cache.size() > 65536) {
            cache.remove(cache.keySet().iterator().next());
        }
        return y;
    }
}
