package dev.ryan.tfcatlas.mixin;

import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.layer.framework.Area;
import net.dries007.tfc.world.noise.Noise2D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RegionChunkDataGenerator.class, remap = false)
public interface RockNoiseAccessor {
    @Accessor("layerHeightNoise")
    Noise2D tfcatlas$height();

    @Accessor("layerSkewXNoise")
    Noise2D tfcatlas$skewX();

    @Accessor("layerSkewZNoise")
    Noise2D tfcatlas$skewZ();

    @Accessor("rockLayerArea")
    ThreadLocal<Area> tfcatlas$rocks();
}
