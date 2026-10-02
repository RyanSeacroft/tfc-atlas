package dev.ryan.tfcatlas.mixin;

import dev.ryan.tfcatlas.client.AtlasClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class GuiMapMixin {
    @Inject(
            method = {
                "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                "m_88315_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lxaero/map/element/MapElementRenderHandler;render(Lxaero/map/gui/GuiMap;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;DDIIDDDDDFZLxaero/map/element/HoveredMapElementHolder;Lnet/minecraft/client/Minecraft;F)Lxaero/map/element/HoveredMapElementHolder;",
                            remap = false),
            require = 0,
            remap = false)
    private void tfcatlas$overlay(
            GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        AtlasClient.overlay((Screen) (Object) this, g, mouseX, mouseY);
    }
}
