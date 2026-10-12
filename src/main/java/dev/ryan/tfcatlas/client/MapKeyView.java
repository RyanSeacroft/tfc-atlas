package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SmoothScroll;
import net.minecraft.client.gui.GuiGraphics;

/** Only the key's scrollbar captures input; the rest of the map keeps its usual controls. */
final class MapKeyView {
    private final SmoothScroll scroll = new SmoothScroll();
    private final MinecraftScrollbar scrollbar = new MinecraftScrollbar();
    private String layer = "";
    private boolean visible;
    private long lastFrame;

    void layout(String layer, int x, int y, int width, int height, double content, float scale) {
        if (!this.layer.equals(layer)) {
            scroll.move(0);
            scrollbar.release();
            this.layer = layer;
        }
        scroll.range(content, height / (double) scale);
        long now = System.nanoTime();
        scroll.tick(lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000.);
        lastFrame = now;
        visible = content > height / (double) scale && height >= 4;
        if (!visible) {
            scrollbar.release();
        }
        scrollbar.layout(
                x,
                y,
                width,
                height,
                content * scale,
                Math.max(2, (int) Math.ceil(12 * scale)),
                scroll);
    }

    double offset() {
        return scroll.position();
    }

    void render(GuiGraphics g) {
        if (visible) {
            scrollbar.render(g, scroll);
        }
    }

    boolean click(double x, double y) {
        return visible && scrollbar.click(x, y, scroll);
    }

    boolean drag(double y) {
        return visible && scrollbar.drag(y, scroll);
    }

    boolean release() {
        return scrollbar.release();
    }

    boolean wheel(double x, double y, double amount) {
        if (!visible || !scrollbar.hovered(x, y, scroll)) {
            return false;
        }
        scroll.wheel(amount);
        return true;
    }

    void hide() {
        visible = false;
        lastFrame = 0;
        scrollbar.release();
    }
}
