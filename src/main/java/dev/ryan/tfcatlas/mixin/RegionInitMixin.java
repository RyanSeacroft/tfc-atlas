package dev.ryan.tfcatlas.mixin;

import dev.ryan.tfcatlas.client.AtlasRegionRecovery;
import net.dries007.tfc.world.region.Init;
import net.dries007.tfc.world.region.RegionGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Init.class, remap = false)
public abstract class RegionInitMixin {
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, require = 1)
    private void tfcatlas$completeRegion(RegionGenerator.Context context, CallbackInfo ci) {
        if (AtlasRegionRecovery.initialize(context)) {
            ci.cancel();
        }
    }
}
