package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.HudHandles;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.HudLayout.Box;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Mouse capture belongs only to the selected Atlas panel; empty map space stays usable. */
final class HudEditor {
    enum Panel {
        TOOLBAR,
        KEY,
        INFO,
        CRITERIA
    }

    private final Map<Panel, Box> bounds = new EnumMap<>(Panel.class);
    private final Map<Panel, Double> scales = new EnumMap<>(Panel.class);
    boolean editing;
    private Panel active;
    private HudLayout.Drag drag;
    private Box toolbarGrip;
    private int infoWidth, infoHeight, keyStartWidth, keyStartHeight;
    private boolean keyHeightDrag;

    void reset() {
        finish();
        bounds.clear();
        scales.clear();
        toolbarGrip = null;
        infoWidth = infoHeight = 0;
        editing = false;
    }

    void toggle() {
        finish();
        editing = !editing;
    }

    void hide(Panel panel) {
        bounds.remove(panel);
    }

    void drawn(Panel panel, Box box, double scale) {
        bounds.put(panel, box);
        scales.put(panel, scale);
        if (panel == active && drag != null) {
            position(panel, box.x(), box.y());
            if (drag.resize() && panel != Panel.KEY) {
                scale(panel, scale);
            }
        }
    }

    Box bounds(Panel panel) {
        return bounds.get(panel);
    }

    boolean interacting() {
        return drag != null;
    }

    boolean over(double x, double y) {
        return bounds.entrySet().stream()
                .anyMatch(e -> occupied(e.getKey(), e.getValue()).contains(x, y));
    }

    Box occupied(Panel panel, Box box) {
        return box;
    }

    Box place(Box safe, int width, int height, int x, int y, List<Box> obstacles) {
        return HudLayout.place(safe, width, height, x, y, obstacles);
    }

    double scale(Panel panel) {
        Profile p = AtlasClient.profile;
        return switch (panel) {
            case TOOLBAR -> p.toolbarScale;
            case KEY -> p.keyScale();
            case INFO -> p.infoScale;
            case CRITERIA -> p.criteriaScale;
        };
    }

    void toolbarGrip(Box box) {
        toolbarGrip = box;
    }

    boolean onToolbarGrip(double x, double y) {
        return toolbarGrip != null && toolbarGrip.contains(x, y);
    }

    int stableInfoWidth(int width) {
        return infoWidth = Math.max(infoWidth, width);
    }

    int stableInfoHeight(int height) {
        return infoHeight = Math.max(infoHeight, height);
    }

    int x(Panel panel, int fallback) {
        Profile p = AtlasClient.profile;
        int x =
                switch (panel) {
                    case TOOLBAR -> p.toolbarX;
                    case KEY -> p.keyX;
                    case INFO -> p.infoX;
                    case CRITERIA -> p.criteriaX;
                };
        return x < 0 ? fallback : x;
    }

    int y(Panel panel, int fallback) {
        Profile p = AtlasClient.profile;
        int y =
                switch (panel) {
                    case TOOLBAR -> p.toolbarY;
                    case KEY -> p.keyY;
                    case INFO -> p.infoY;
                    case CRITERIA -> p.criteriaY;
                };
        return y < 0 ? fallback : y;
    }

    private void position(Panel panel, int x, int y) {
        Profile p = AtlasClient.profile;
        switch (panel) {
            case TOOLBAR -> {
                p.toolbarX = x;
                p.toolbarY = y;
            }
            case KEY -> {
                p.keyX = x;
                p.keyY = y;
            }
            case INFO -> {
                p.infoX = x;
                p.infoY = y;
            }
            case CRITERIA -> {
                p.criteriaX = x;
                p.criteriaY = y;
            }
        }
    }

    private void scale(Panel panel, double scale) {
        Profile p = AtlasClient.profile;
        switch (panel) {
            case TOOLBAR -> p.toolbarScale = scale;
            case KEY -> p.keyScale(scale);
            case INFO -> p.infoScale = scale;
            case CRITERIA -> p.criteriaScale = scale;
        }
    }

    boolean press(double x, double y) {
        if (!editing) {
            return false;
        }
        // Grips take precedence over panel bodies; their hit areas stay outside text.
        for (int pass = 0; pass < 2; pass++) {
            for (Panel panel : List.of(Panel.CRITERIA, Panel.INFO, Panel.KEY, Panel.TOOLBAR)) {
                Box box = bounds.get(panel);
                if (box == null) {
                    continue;
                }
                boolean corner =
                        panel == Panel.TOOLBAR
                                ? onToolbarGrip(x, y)
                                : HudHandles.corner(box).padded(1).contains(x, y);
                boolean height =
                        panel == Panel.KEY && HudHandles.height(box).padded(1).contains(x, y);
                if (pass == 0 ? !(corner || height) : !box.contains(x, y)) {
                    continue;
                }
                active = panel;
                keyHeightDrag = height && !corner;
                // Use visible dimensions, not an oversized saved viewport hidden by cropping.
                keyStartWidth = box.width();
                keyStartHeight = box.height();
                drag = new HudLayout.Drag(box, scales.get(panel), x, y, corner || keyHeightDrag);
                return true;
            }
        }
        return false;
    }

    boolean drag(double x, double y, Screen screen) {
        if (drag == null) {
            return false;
        }
        if (drag.resize()) {
            position(active, drag.start().x(), drag.start().y());
            if (active == Panel.KEY && keyHeightDrag) {
                var safe = HudLayout.safeArea(screen.width, screen.height);
                Profile p = AtlasClient.profile;
                int keyWidth = 0;
                int keyHeight =
                        Math.max(
                                12,
                                Math.min(
                                        safe.height(),
                                        keyStartHeight
                                                + (keyHeightDrag
                                                        ? (int) Math.round(y - drag.mouseY())
                                                        : 0)));
                p.keySize(keyWidth, keyHeight);
            } else {
                double resized = drag.resizedScale(x, y);
                scale(active, resized);
                if (active == Panel.KEY) {
                    double ratio = resized / drag.scale();
                    AtlasClient.profile.keySize(
                            (int) Math.round(keyStartWidth * ratio),
                            (int) Math.round(keyStartHeight * ratio));
                }
            }
        } else {
            Box moved = drag.move(x, y, HudLayout.safeArea(screen.width, screen.height));
            position(active, moved.x(), moved.y());
        }
        return true;
    }

    boolean finish() {
        if (drag == null) {
            return false;
        }
        drag = null;
        active = null;
        AtlasClient.save();
        return true;
    }

    void render(GuiGraphics g, int width, int height) {
        if (!editing) {
            return;
        }
        for (var entry : bounds.entrySet()) {
            Box b = entry.getValue();
            int colour = entry.getKey() == active ? 0xFFFFDA7A : 0xFF9BCABC;
            g.renderOutline(b.x() - 1, b.y() - 1, b.width() + 2, b.height() + 2, colour);
            if (entry.getKey() != Panel.TOOLBAR) {
                drawGrip(g, HudHandles.corner(b), colour);
            }
            if (entry.getKey() == Panel.KEY) {
                drawGrip(g, HudHandles.height(b), colour);
            }
        }
        var font = Minecraft.getInstance().font;
        String text = "Drag panels · corner = scale · key bottom = rows · Done / Esc";
        float scale = Math.min(.75f, (width - 64f) / font.width(text));
        g.pose().pushPose();
        g.pose().translate(width / 2., 29, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(text);
        g.fill(-w / 2 - 3, -2, w - w / 2 + 3, 10, 0xE0161B1D);
        g.drawCenteredString(font, text, 0, 0, 0xFFFFDA7A);
        g.pose().popPose();
    }

    private static void drawGrip(GuiGraphics g, Box grip, int colour) {
        g.fill(grip.x(), grip.y(), grip.right(), grip.bottom(), 0xFF161B1D);
        g.renderOutline(grip.x(), grip.y(), grip.width(), grip.height(), colour);
        g.fill(grip.x() + 2, grip.y() + 2, grip.right() - 2, grip.bottom() - 2, colour);
    }
}
