package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.ScrollbarGeometry;
import dev.ryan.tfcatlas.core.SmoothScroll;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft-like bevelled scrollbar; the thumb and track both accept the mouse. */
final class MinecraftScrollbar {
    private boolean dragging;
    private double grab;
    private int x, y, width = 12, height, thumb, top, travel;

    void layout(int x, int y, int height, double content, SmoothScroll scroll) {
        layout(x, y, 12, height, content, 18, scroll);
    }

    void layout(
            int x,
            int y,
            int width,
            int height,
            double content,
            int minimumThumb,
            SmoothScroll scroll) {
        this.x = x;
        this.y = y;
        this.width = Math.max(3, width);
        this.height = Math.max(0, height);
        var geometry =
                ScrollbarGeometry.of(
                        this.height, content, minimumThumb, scroll.position(), scroll.maximum());
        thumb = geometry.thumb();
        travel = geometry.travel();
        top = y + geometry.offset();
        if (scroll.maximum() <= 0 || travel == 0) {
            release();
        }
    }

    boolean hovered(double mx, double my, SmoothScroll scroll) {
        return scroll.maximum() > 0
                && travel > 0
                && mx >= x
                && mx < x + width
                && my >= y
                && my < y + height;
    }

    void render(GuiGraphics g, SmoothScroll scroll) {
        if (scroll.maximum() <= 0 || travel == 0) {
            return;
        }
        g.fill(x, y, x + width, y + height, 0xFF101010);
        g.fill(x + 1, top, x + width - 1, top + thumb, 0xFF888888);
        g.fill(x + 1, top, x + width - 1, top + 1, 0xFFCCCCCC);
        if (thumb > 1 && width > 3) {
            g.fill(x + 1, top, x + 2, top + thumb - 1, 0xFFCCCCCC);
            g.fill(x + width - 2, top + 1, x + width - 1, top + thumb, 0xFF444444);
            g.fill(x + 2, top + thumb - 1, x + width - 1, top + thumb, 0xFF444444);
        }
    }

    boolean click(double mx, double my, SmoothScroll scroll) {
        if (!hovered(mx, my, scroll)) {
            return false;
        }
        dragging = true;
        grab = my >= top && my < top + thumb ? my - top : thumb / 2.;
        drag(my, scroll);
        return true;
    }

    boolean drag(double my, SmoothScroll scroll) {
        if (!dragging) {
            return false;
        }
        scroll.move((my - y - 1 - grab) * scroll.maximum() / Math.max(1, travel));
        return true;
    }

    boolean release() {
        boolean captured = dragging;
        dragging = false;
        return captured;
    }
}
