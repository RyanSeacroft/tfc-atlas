package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.SearchCompletion;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A real smaller hitbox with an independently scaled, fully keyboard-accessible edit field. */
final class CompactEditBox extends AbstractWidget {
    static final float SCALE = .8f;
    private final EditBox input;
    private final Font font;
    private boolean previewScrolled;
    private final List<String> options;

    CompactEditBox(
            Font font, int x, int y, int width, int height, Component label, List<String> options) {
        super(x, y, width, height, label);
        this.options = List.copyOf(options);
        this.font = font;
        input =
                new EditBox(
                        font,
                        1,
                        1,
                        Math.max(8, (int) (width / SCALE) - 2),
                        Math.max(10, (int) (height / SCALE) - 2),
                        label);
        input.setMaxLength(256);
        input.setCanLoseFocus(true);
    }

    void hint(String text) {
        input.setHint(Component.literal(text));
    }

    String value() {
        return input.getValue();
    }

    void value(String value) {
        input.setValue(value);
    }

    void responder(Consumer<String> responder) {
        input.setResponder(responder);
    }

    void tick() {
        input.tick();
    }

    private SearchCompletion.Match suggestion() {
        return input.getHighlighted().isEmpty()
                ? SearchCompletion.suggest(
                        value(), input.getCursorPosition(), options, isFocused() && active)
                : null;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float delta) {
        HoverTooltips.render(this, x, y, () -> super.render(g, x, y, delta));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        input.setEditable(active);
        input.setTextColor(active ? 0xE0E0E0 : 0x777777);
        var suggestion = suggestion();
        String suffix =
                suggestion != null && input.getCursorPosition() == value().length()
                        ? suggestion.suffix()
                        : null;
        var viewport = (dev.ryan.tfcatlas.mixin.EditBoxAccessor) (Object) input;
        if (suffix != null) {
            // Scroll as though the completion is already present, without changing the value or
            // caret.
            String preview = value() + suffix;
            int start =
                    preview.length()
                            - font.plainSubstrByWidth(preview, input.getInnerWidth() - 2, true)
                                    .length();
            viewport.tfcatlas$displayPos(Math.min(value().length(), start));
            previewScrolled = true;
        } else if (previewScrolled) {
            viewport.tfcatlas$displayPos(0);
            input.setCursorPosition(input.getCursorPosition());
            previewScrolled = false;
        }
        input.setSuggestion(suffix);
        g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        g.pose().pushPose();
        g.pose().translate(getX(), getY(), 0);
        g.pose().scale(SCALE, SCALE, 1);
        input.render(g, (int) ((mx - getX()) / SCALE), (int) ((my - getY()) / SCALE), delta);
        g.pose().popPose();
        g.disableScissor();
    }

    @Override
    public void onClick(double x, double y) {
        input.onClick((x - getX()) / SCALE, (y - getY()) / SCALE);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        input.setFocused(focused);
        if (!focused) {
            input.setSuggestion(null);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB && modifiers == 0 && isFocused()) {
            var match = suggestion();
            if (match != null) {
                String completed = match.apply(value());
                input.setValue(completed);
                input.moveCursorTo(match.cursor());
                return true;
            }
        }
        return input.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return input.charTyped(character, modifiers);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        input.updateWidgetNarration(out);
    }
}
