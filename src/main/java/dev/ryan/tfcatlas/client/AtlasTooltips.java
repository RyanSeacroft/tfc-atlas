package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.TooltipLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Vector2i;

/** One hover-only tooltip pass, independent of focus and recreated toggle widgets. */
final class AtlasTooltips {
    static void render(Screen screen, GuiGraphics g, int mx, int my) {
        var children = screen.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof AbstractWidget widget
                    && widget.visible
                    && widget.isMouseOver(mx, my)
                    && widget.getTooltip() != null) {
                draw(g, widget.getTooltip(), mx, my, screen.width, screen.height);
                return;
            }
        }
    }

    static void draw(GuiGraphics g, Tooltip tooltip, int mx, int my, int width, int height) {
        var mc = Minecraft.getInstance();
        var lines = tooltip.toCharSequence(mc);
        if (lines.isEmpty()) {
            return;
        }
        int w = lines.stream().mapToInt(mc.font::width).max().orElse(0),
                h = lines.size() * 10 - (lines.size() == 1 ? 2 : 0);
        var layout = TooltipLayout.fit(width, height, mx, my, w, h);
        g.flush();
        g.pose().pushPose();
        g.pose().scale(layout.scale(), layout.scale(), 1);
        try {
            g.renderTooltip(
                    mc.font,
                    lines,
                    (sw, sh, x, y, tw, th) -> new Vector2i(layout.x(), layout.y()),
                    (int) (mx / layout.scale()),
                    (int) (my / layout.scale()));
            g.flush();
        } finally {
            g.pose().popPose();
        }
    }
}
