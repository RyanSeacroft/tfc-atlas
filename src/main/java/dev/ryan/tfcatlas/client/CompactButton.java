package dev.ryan.tfcatlas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small physical hitbox and matching scaled text, with normal keyboard navigation. */
final class CompactButton extends Button {
    private float scale;
    private Button skin;

    CompactButton minecraftStyle() {
        skin = Button.builder(getMessage(), b -> {}).bounds(0, 0, 100, 20).build();
        return this;
    }

    CompactButton(String text, int x, int y, int width, float scale, OnPress action) {
        super(
                x,
                y,
                width,
                Math.max(8, Math.round(18 * scale)),
                Component.literal(text),
                action,
                DEFAULT_NARRATION);
        this.scale = scale;
    }

    void layout(int x, int y, int w, int h, float scale) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        this.scale = scale;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        if (skin != null) {
            float factor = Math.min(scale, getHeight() / 20f);
            skin.setWidth(Math.max(1, Math.round(width / factor)));
            skin.setMessage(getMessage());
            skin.active = active;
            skin.setFocused(isFocused());
            g.pose().pushPose();
            g.pose().translate(getX(), getY(), 0);
            g.pose().scale(factor, factor, 1);
            skin.render(g, (int) ((mx - getX()) / factor), (int) ((my - getY()) / factor), delta);
            g.pose().popPose();
            return;
        }
        int color = isHoveredOrFocused() ? 0xED40494B : 0xD0161B1D;
        g.fill(getX(), getY(), getX() + width, getY() + height, color);
        if (isFocused()) {
            g.renderOutline(getX(), getY(), width, height, 0xFFE8D9B6);
        }
        var font = Minecraft.getInstance().font;
        float textScale = Math.min(scale, (width - 4f) / Math.max(1, font.width(getMessage())));
        g.pose().pushPose();
        g.pose().translate(getX() + width / 2., getY() + (height - 8 * textScale) / 2., 0);
        g.pose().scale(textScale, textScale, 1);
        g.drawCenteredString(font, getMessage(), 0, 0, active ? 0xE8D9B6 : 0x888888);
        g.pose().popPose();
    }
}
