package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared focus behavior for Atlas settings and its preset/colour submenus. */
abstract class AtlasMenuScreen extends Screen {
    protected AtlasMenuScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(net.minecraft.client.gui.GuiGraphics g, int x, int y, float delta) {
        super.render(g, x, y, delta);
        renderContent(g, x, y, delta);
        AtlasTooltips.render(this, g, x, y);
    }

    protected void renderContent(
            net.minecraft.client.gui.GuiGraphics g, int x, int y, float delta) {}

    // Suppress Minecraft's deferred focus tooltip. It alternates mouse/focus positions after
    // toggles.
    @Override
    public void setTooltipForNextRenderPass(
            java.util.List<net.minecraft.util.FormattedCharSequence> lines,
            net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
            boolean override) {}

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                        || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                && (getFocused() instanceof CompactEditBox
                        || getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            setFocused(null);
            setDragging(false);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled && button == 0) {
            setFocused(null);
            setDragging(false);
        }
        return handled;
    }
}
