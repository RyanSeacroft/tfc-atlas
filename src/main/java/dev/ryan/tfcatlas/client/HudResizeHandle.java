package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Dedicated grip, outside the toolbar's buttons so Done can never swallow a resize. */
final class HudResizeHandle extends AbstractWidget {
    final Tooltip tip = Tooltip.create(Component.literal("Drag this square to resize the toolbar"));

    HudResizeHandle() {
        super(0, 0, 7, 7, Component.literal("Resize toolbar"));
    }

    void layout(int x, int y, int size) {
        setX(x);
        setY(y);
        setWidth(size);
        setHeight(size);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        int c = isHoveredOrFocused() ? 0xFFFFDA7A : 0xFF9BCABC;
        int size = Math.min(7, Math.min(width, height));
        g.fill(getX() + width - size, getY() + height - size, getX() + width, getY() + height, c);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
