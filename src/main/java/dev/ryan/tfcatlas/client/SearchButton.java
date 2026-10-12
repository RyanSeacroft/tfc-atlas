package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native Minecraft action button with a gold or red outline. */
final class SearchButton extends Button {
    private final int border;

    SearchButton(int x, int y, int width, OnPress action) {
        this("Search", x, y, width, action);
    }

    SearchButton(String text, int x, int y, int width, OnPress action) {
        this(text, x, y, width, action, 0xFFAA8C48);
    }

    SearchButton(String text, int x, int y, int width, OnPress action, int border) {
        super(x, y, width, 20, Component.literal(text), action, DEFAULT_NARRATION);
        this.border = border;
    }

    private static int brighten(int colour) {
        int r = Math.min(255, (colour >> 16 & 255) + 48);
        int g = Math.min(255, (colour >> 8 & 255) + 48);
        int b = Math.min(255, (colour & 255) + 48);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        var message = getMessage();
        setMessage(Component.empty());
        super.renderWidget(g, mx, my, delta);
        setMessage(message);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        float scale = Math.min(1, (width - 12f) / Math.max(1, font.width(message)));
        g.pose().pushPose();
        g.pose().translate(getX() + width / 2., getY() + (height - 9 * scale) / 2., 0);
        g.pose().scale(scale, scale, 1);
        g.drawCenteredString(font, message, 0, 0, active ? 0xFFFFFF : 0xA0A0A0);
        g.pose().popPose();
        g.renderOutline(
                getX(), getY(), width, height, isHoveredOrFocused() ? brighten(border) : border);
    }
}
