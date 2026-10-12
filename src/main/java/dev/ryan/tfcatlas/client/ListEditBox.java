package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SearchCompletion;
import dev.ryan.tfcatlas.core.SmoothScroll;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Vanilla multiline editing and selection, with Atlas completion and unchanged text scale. */
final class ListEditBox extends AbstractWidget {
    private final Font font;
    private final Field input, preview;
    private final List<String> options;
    private final SmoothScroll scroll = new SmoothScroll();
    private final MinecraftScrollbar bar = new MinecraftScrollbar();
    private long previous = System.nanoTime();
    private String hint = "Any";
    private Field displayed;
    private String completionState = "";

    ListEditBox(
            Font font, int x, int y, int width, int height, Component label, List<String> options) {
        super(x, y, width, height, label);
        this.font = font;
        this.options = List.copyOf(options);
        input = new Field(font, Math.max(8, (int) ((width - 24) / CompactEditBox.SCALE)));
        preview = new Field(font, Math.max(8, (int) ((width - 24) / CompactEditBox.SCALE)));
        input.setCharacterLimit(8192);
        input.setCursorListener(this::reveal);
        displayed = input;
    }

    void hint(String text) {
        hint = text;
    }

    String value() {
        return input.value();
    }

    void value(String value) {
        input.setValue(value);
    }

    void responder(Consumer<String> action) {
        input.setValueListener(action);
    }

    private SearchCompletion.Match suggestion() {
        return input.hasSelection()
                ? null
                : SearchCompletion.suggest(value(), input.cursor(), options, isFocused() && active);
    }

    private void reveal() {
        scroll.range(input.getLineCount() * 10 * CompactEditBox.SCALE, getHeight() - 8);
        double y = input.getLineAtCursor() * 10 * CompactEditBox.SCALE;
        scroll.reveal(y, y + 10 * CompactEditBox.SCALE, getHeight() - 8);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        long now = System.nanoTime();
        scroll.tick((now - previous) / 1e9);
        previous = now;
        var match = suggestion();
        displayed = input;
        if (match != null && input.cursor() == value().length()) {
            preview.setValue(value() + match.suffix());
            preview.seekCursor(Whence.ABSOLUTE, input.cursor());
            displayed = preview;
        }
        double content = displayed.getLineCount() * 10 * CompactEditBox.SCALE;
        scroll.range(content, getHeight() - 8);
        String state = displayed.value() + ":" + input.cursor();
        if (isFocused() && !state.equals(completionState)) {
            double caretY = displayed.getLineAtCursor() * 10 * CompactEditBox.SCALE;
            scroll.reveal(caretY, caretY + 10 * CompactEditBox.SCALE, getHeight() - 8);
        }
        completionState = state;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF000000);
        g.renderOutline(
                getX(), getY(), getWidth(), getHeight(), isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        g.enableScissor(getX() + 2, getY() + 2, getX() + getWidth() - 16, getY() + getHeight() - 2);
        g.pose().pushPose();
        g.pose().translate(getX() + 4, getY() + 4 - scroll.position(), 0);
        g.pose().scale(CompactEditBox.SCALE, CompactEditBox.SCALE, 1);
        if (value().isEmpty() && match == null && !isFocused()) {
            g.drawString(font, hint, 0, 0, active ? 0xE0E0E0 : 0x777777);
        }
        int row = 0;
        var selection = input.selection();
        for (var line : displayed.lines()) {
            int start = line.start(), end = line.end(), y = row++ * 10;

            if (input.hasSelection()) {
                int a = Math.max(start, selection.start()), b = Math.min(end, selection.end());
                if (a < b) {
                    int l = font.width(displayed.value().substring(start, a)),
                            r = font.width(displayed.value().substring(start, b));
                    g.fill(l, y - 1, r, y + 9, 0xFF3355AA);
                }
            }
            int typedEnd = Math.min(end, value().length());
            if (typedEnd > start) {
                g.drawString(
                        font,
                        displayed.value().substring(start, typedEnd),
                        0,
                        y,
                        active ? 0xE0E0E0 : 0x777777);
            }
            if (end > value().length()) {
                int greyStart = Math.max(start, value().length());
                g.drawString(
                        font,
                        displayed.value().substring(greyStart, end),
                        font.width(displayed.value().substring(start, greyStart)),
                        y,
                        0x777777);
            }
            if (isFocused()
                    && row - 1 == displayed.getLineAtCursor()
                    && (System.currentTimeMillis() / 500) % 2 == 0) {
                int caret = font.width(displayed.value().substring(start, input.cursor()));
                g.fill(caret, y - 1, caret + 1, y + 9, 0xFFFFFFFF);
            }
        }
        g.pose().popPose();
        g.disableScissor();
        bar.layout(getX() + getWidth() - 14, getY() + 2, getHeight() - 4, content + 4, scroll);
        bar.render(g, scroll);
    }

    private void seek(double x, double y) {
        // Vanilla hit testing uses nine-pixel rows; our rows have one pixel of spacing.
        displayed.seekCursorToPoint(
                (x - getX() - 4) / CompactEditBox.SCALE,
                (y - getY() - 4 + scroll.position()) / CompactEditBox.SCALE * 9 / 10);
        input.seekCursor(Whence.ABSOLUTE, Math.min(value().length(), displayed.cursor()));
    }

    @Override
    public void onClick(double x, double y) {
        if (!bar.click(x, y, scroll)) {
            input.setSelecting(net.minecraft.client.gui.screens.Screen.hasShiftDown());
            seek(x, y);
        }
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (bar.drag(y, scroll)) {
            return true;
        }
        input.setSelecting(true);
        seek(x, y);
        return true;
    }

    @Override
    public void onRelease(double x, double y) {
        bar.release();
        input.setSelecting(false);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isMouseOver(x, y)) {
            return false;
        }
        scroll.wheel(vertical);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB && modifiers == 0) {
            var match = suggestion();
            if (match != null) {
                input.setValue(match.apply(value()));
                input.seekCursor(Whence.ABSOLUTE, match.cursor());
                reveal();
                return true;
            }
        }
        return input.keyPressed(key);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!active || Character.isISOControl(character)) {
            return false;
        }
        input.insertText(String.valueOf(character));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    private record Line(int start, int end) {}

    /** Exposes vanilla line ranges without leaking its protected StringView type. */
    private static final class Field extends MultilineTextField {
        Field(Font font, int width) {
            super(font, width);
        }

        List<Line> lines() {
            var result = new java.util.ArrayList<Line>();
            for (var line : iterateLines()) {
                result.add(new Line(line.beginIndex(), line.endIndex()));
            }
            return result;
        }

        Line selection() {
            var selected = getSelected();
            return new Line(selected.beginIndex(), selected.endIndex());
        }
    }
}
