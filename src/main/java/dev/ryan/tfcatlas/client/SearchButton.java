package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native Minecraft button with a restrained gold border identifying the primary action. */
final class SearchButton extends Button {
    SearchButton(int x, int y, int width, OnPress action) {
        super(x, y, width, 20, Component.literal("Search"), action, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        super.renderWidget(g, mx, my, delta);
        g.renderOutline(
                getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFFFDF80 : 0xFFAA8C48);
    }
}
