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

    private net.minecraft.client.gui.components.Tooltip hoverTooltip;

    @Override
    public void setTooltip(net.minecraft.client.gui.components.Tooltip tooltip) {
        hoverTooltip = tooltip;
        super.setTooltip(tooltip);
    }

    @Override
    public void renderWidget(GuiGraphics g, int x, int y, float d) {
        super.setTooltip(isMouseOver(x, y) ? hoverTooltip : null);
        super.renderWidget(g, x, y, d);
    }
}
