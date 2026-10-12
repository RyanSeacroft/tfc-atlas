package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SmoothScroll;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Pixel-scrolled native buttons, clipped to a viewport with a draggable side scrollbar. */
final class MinecraftScrollPanel extends AbstractWidget {
    private record Cell(
            int x,
            int y,
            int width,
            int height,
            Button button,
            java.util.function.Supplier<String> label,
            Tooltip tooltip,
            java.util.function.DoubleFunction<Integer> colour) {}

    private final List<Cell> cells = new ArrayList<>();
    private final SmoothScroll scroll;
    private final MinecraftScrollbar bar = new MinecraftScrollbar();
    private int contentHeight;
    private boolean singleLine;

    MinecraftScrollPanel singleLine() {
        singleLine = true;
        return this;
    }

    private long previous = System.nanoTime();

    MinecraftScrollPanel(int x, int y, int width, int height, SmoothScroll scroll) {
        super(x, y, width, height, Component.literal("Scrollable list"));
        this.scroll = scroll;
    }

    int contentWidth() {
        return getWidth() - 16;
    }

    void cell(
            int x,
            int y,
            int width,
            int height,
            java.util.function.Supplier<String> label,
            Runnable action,
            String help,
            java.util.function.DoubleFunction<Integer> colour) {
        var button =
                Button.builder(Component.empty(), ignored -> action.run())
                        .bounds(0, 0, width, height)
                        .build();
        cells.add(
                new Cell(
                        x,
                        y,
                        width,
                        height,
                        button,
                        label,
                        Tooltip.create(Component.literal(help)),
                        colour));
        contentHeight = Math.max(contentHeight, y + height + 2);
    }

    private void layout(Cell cell) {
        cell.button().setX(getX() + cell.x());
        cell.button().setY(getY() + cell.y() - (int) Math.round(scroll.position()));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        // Set the range after every row is present, preserving the position on return.
        scroll.range(contentHeight, getHeight());
        long now = System.nanoTime();
        scroll.tick((now - previous) / 1e9);
        previous = now;
        g.enableScissor(getX(), getY(), getX() + contentWidth(), getY() + getHeight());
        var font = Minecraft.getInstance().font;
        setTooltip(null);
        for (var cell : cells) {
            layout(cell);
            var button = cell.button();
            int y = button.getY();
            if (y + cell.height() <= getY() || y >= getY() + getHeight()) {
                continue;
            }
            button.render(
                    g, isMouseOver(mx, my) ? mx : -1000, isMouseOver(mx, my) ? my : -1000, delta);
            int inset = cell.colour() == null ? 6 : 34;
            if (cell.colour() != null) {
                for (int pixel = 0; pixel < 10; pixel++) {
                    g.fill(
                            button.getX() + 5 + pixel,
                            y + (cell.height() - 10) / 2,
                            button.getX() + 6 + pixel,
                            y + (cell.height() + 10) / 2,
                            0xFF000000 | cell.colour().apply((pixel + .5) / 10));
                }
            }
            if (singleLine) {
                String text = cell.label().get();
                float scale =
                        Math.min(
                                .75f,
                                Math.max(1, cell.width() - inset - 6)
                                        / (float) Math.max(1, font.width(text)));
                g.pose().pushPose();
                g.pose()
                        .translate(
                                button.getX() + cell.width() / 2.,
                                y + (cell.height() - 9 * scale) / 2.,
                                0);
                g.pose().scale(scale, scale, 1);
                g.drawCenteredString(font, text, 0, 0, 0xFFFFFF);
                g.pose().popPose();
            } else {
                var lines =
                        font.split(Component.literal(cell.label().get()), cell.width() - inset - 6);
                int textY = y + (cell.height() - lines.size() * 10) / 2 + 1;
                for (var line : lines) {
                    g.drawString(
                            font,
                            line,
                            button.getX() + (cell.width() - font.width(line)) / 2,
                            textY,
                            0xFFFFFF);
                    textY += 10;
                }
            }
            if (isMouseOver(mx, my) && button.isMouseOver(mx, my)) {
                setTooltip(cell.tooltip());
            }
        }
        g.disableScissor();
        bar.layout(getX() + getWidth() - 12, getY(), getHeight(), contentHeight, scroll);
        bar.render(g, scroll);
    }

    @Override
    public void onClick(double x, double y) {
        if (bar.click(x, y, scroll)) {
            return;
        }
        if (x >= getX() + contentWidth()) {
            return;
        }
        for (var cell : cells) {
            layout(cell);
            if (cell.button().mouseClicked(x, y, 0)) {
                return;
            }
        }
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return bar.drag(y, scroll);
    }

    @Override
    public void onRelease(double x, double y) {
        bar.release();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        if (!isMouseOver(x, y) || scroll.maximum() <= 0 || getHeight() < 4) {
            return false;
        }
        scroll.wheel(vertical);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (scroll.maximum() <= 0 || getHeight() < 4) {
            return false;
        }
        if (key == 264 || key == 267) {
            scroll.wheel(key == 264 ? -1 : -5);
            return true;
        }
        if (key == 265 || key == 266) {
            scroll.wheel(key == 265 ? 1 : 5);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
