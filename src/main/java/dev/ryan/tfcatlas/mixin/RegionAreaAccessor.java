package dev.ryan.tfcatlas.mixin;

import net.dries007.tfc.world.region.Region;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = Region.class, remap = false)
public interface RegionAreaAccessor {
    @Invoker("setRegionArea")
    void tfcatlas$setRegionArea(int minX, int minZ, int maxX, int maxZ);

    @Invoker("init")
    void tfcatlas$init(int x, int z);
}
