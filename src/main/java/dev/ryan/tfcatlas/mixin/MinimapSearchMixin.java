package dev.ryan.tfcatlas.mixin;

import dev.ryan.tfcatlas.client.MinimapSearchOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;

/** Optional render-only hook, before Xaero draws its own map symbols. */
@Pseudo
@Mixin(
        targets = "xaero.hud.minimap.element.render.map.MinimapElementMapRendererHandler",
        remap = false)
public abstract class MinimapSearchMixin {
    @Shadow private double ps;
    @Shadow private double pc;
    @Shadow private double zoom;
    @Shadow private float halfWView;

    @Inject(method = "beforeRender", at = @At("HEAD"), require = 0, remap = false)
    private void tfcatlas$search(
            GuiGraphics graphics,
            MinimapElementRenderInfo info,
            MultiBufferSource.BufferSource buffers,
            CallbackInfo callback) {
        MinimapSearchOverlay.render(
                graphics, info.renderPos, info.mapDimension, ps, pc, zoom, halfWView);
    }
}
