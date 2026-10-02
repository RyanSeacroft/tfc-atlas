package dev.ryan.tfcatlas.client;

import net.minecraft.client.gui.components.AbstractWidget;

/** Retain keyboard focus and completion without Minecraft's focus-pinned tooltip. */
final class HoverTooltips {
    static void render(AbstractWidget widget, int mouseX, int mouseY, Runnable draw) {
        var tooltip = widget.getTooltip();
        boolean hover =
                widget.visible
                        && mouseX >= widget.getX()
                        && mouseY >= widget.getY()
                        && mouseX < widget.getX() + widget.getWidth()
                        && mouseY < widget.getY() + widget.getHeight();
        if (!hover) {
            widget.setTooltip(null);
        }
        try {
            draw.run();
        } finally {
            if (!hover) {
                widget.setTooltip(tooltip);
            }
        }
    }
}
