package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class HoverEditBox extends EditBox {
    HoverEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
        setCanLoseFocus(true);
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float delta) {
        HoverTooltips.render(this, x, y, () -> super.render(g, x, y, delta));
    }
}
