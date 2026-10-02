package dev.ryan.tfcatlas.mixin;

import java.util.Map;
import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.Seed;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.chunkdata.ChunkDataGenerator;
import net.dries007.tfc.world.river.RiverBlendType;
import net.dries007.tfc.world.river.RiverNoiseSampler;
import net.dries007.tfc.world.shore.ShoreBlendType;
import net.dries007.tfc.world.shore.ShoreNoiseSampler;
import net.dries007.tfc.world.volcano.CenteredFeatureBlendType;
import net.dries007.tfc.world.volcano.CenteredFeatureNoiseSampler;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = TFCChunkGenerator.class, remap = false)
public interface TfcGeneratorAccessor {
    @Accessor("seed")
    void tfcatlas$seed(Seed seed);

    @Accessor("chunkDataGenerator")
    void tfcatlas$chunks(ChunkDataGenerator chunks);

    @Invoker("createBiomeSamplersForChunk")
    Map<BiomeExtension, BiomeNoiseSampler> tfcatlas$biomes(ChunkAccess chunk);

    @Invoker("createRiverSamplersForChunk")
    Map<RiverBlendType, RiverNoiseSampler> tfcatlas$rivers();

    @Invoker("createShoreSamplersForChunk")
    Map<ShoreBlendType, ShoreNoiseSampler> tfcatlas$shores();

    @Invoker("createVolcanoSamplersForChunk")
    Map<CenteredFeatureBlendType, CenteredFeatureNoiseSampler> tfcatlas$volcanoes();
}
