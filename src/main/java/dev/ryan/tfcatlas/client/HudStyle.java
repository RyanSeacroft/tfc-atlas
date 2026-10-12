package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.HudLayout;
import net.minecraft.client.gui.GuiGraphics;

/** One GUI pixel of grey trim, or one and a half for a selected toolbar option. */
final class HudStyle {
    static final int TITLE = 0xE8D9B6, TEXT = 0xE9E2CF;
    static final int BORDER = 1, GREY = 0xA0888888, SELECTED = 0xFFE8D9B6;

    private HudStyle() {}

    /** Keep text inside the physical border even when a panel is scaled down. */
    static int padding(float scale) {
        return Math.max(4, (int) Math.ceil(BORDER / scale) + 1);
    }

    static void panel(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xCD161B1D);
    }

    static void frame(GuiGraphics g, HudLayout.Box box, boolean selected) {
        int w = box.width() * 2, h = box.height() * 2;
        int border = Math.min(selected ? 3 : 2, Math.min(w, h) / 2);
        int colour = selected ? SELECTED : GREY;
        // Half-pixel coordinates give selected options their 1.5-pixel outline.
        g.pose().pushPose();
        g.pose().translate(box.x(), box.y(), 0);
        g.pose().scale(.5f, .5f, 1);
        g.fill(0, 0, w, border, colour);
        g.fill(0, h - border, w, h, colour);
        g.fill(0, border, border, h - border, colour);
        g.fill(w - border, border, w, h - border, colour);
        g.pose().popPose();
    }
}
