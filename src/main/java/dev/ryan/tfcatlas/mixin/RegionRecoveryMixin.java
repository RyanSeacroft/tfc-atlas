package dev.ryan.tfcatlas.mixin;

import dev.ryan.tfcatlas.client.AtlasRegionRecovery;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RegionGenerator.class, remap = false)
public abstract class RegionRecoveryMixin {
    @Inject(
            method = "getOrCreateRegion(II)Lnet/dries007/tfc/world/region/Region;",
            at = @At("RETURN"),
            cancellable = true,
            require = 1)
    private void tfcatlas$validate(int x, int z, CallbackInfoReturnable<Region> cir) {
        cir.setReturnValue(
                AtlasRegionRecovery.validate(
                        (RegionGenerator) (Object) this, x, z, cir.getReturnValue()));
    }
}
