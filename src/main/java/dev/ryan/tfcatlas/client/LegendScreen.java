package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.KeyLayout;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockPossibilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LegendScreen extends AtlasMenuScreen {
    private final Screen parent;
    private List<Layer.LegendEntry> entries;
    private static Layer cachedLayer;
    private static boolean cachedAccessible, cachedContinents;
    private static Map<String, Integer> cachedColours = Map.of();
    private static List<Layer.LegendEntry> cachedEntries = List.of();
    private static Set<String> cachedRocks = Set.of();

    public LegendScreen(Screen parent) {
        super(Component.literal("TFC layer key"));
        this.parent = parent;
    }

    public static List<Layer.LegendEntry> entries(Profile p) {
        Set<String> rocks =
                p.selected() == Layer.ROCKS && AtlasClient.engine != null
                        ? AtlasClient.engine.possibleRocks(p.selectedRockLayer())
                        : Set.of();
        if (cachedLayer != p.selected()
                || cachedAccessible != p.accessible
                || cachedContinents != p.climateContinents
                || !cachedColours.equals(p.colors)
                || !cachedRocks.equals(rocks)) {
            cachedLayer = p.selected();
            cachedAccessible = p.accessible;
            cachedContinents = p.climateContinents;
            cachedColours = Map.copyOf(p.colors);
            cachedRocks = rocks;
            cachedEntries =
                    p.selected() == Layer.ROCKS && !rocks.isEmpty()
                            ? RockPossibilities.legend(
                                    rocks, p.accessible, p.colors, p.climateContinents)
                            : p.selected().legend(p.accessible, p.colors, p.climateContinents);
        }
        return cachedEntries;
    }

    private LegendList list;
    private Layer listedLayer;
    private double scroll;
    private int noteY;

    @Override
    protected void init() {
        if (list != null && listedLayer == AtlasClient.profile.selected()) {
            scroll = list.getScrollAmount();
        } else {
            scroll = 0;
        }
        listedLayer = AtlasClient.profile.selected();
        entries = entries(AtlasClient.profile);
        int content = Math.min(430, width - 24), left = (width - content) / 2;
        var note =
                font.split(
                        Component.literal(
                                listedLayer.legendNote(AtlasClient.profile.climateContinents)),
                        content);
        noteY = height - 38 - note.size() * 10;
        list = addRenderableWidget(new LegendList(content, Math.max(36, noteY - 58), 48));
        list.setX(left);
        list.setScrollAmount(scroll);
        addRenderableWidget(
                Button.builder(
                                Component.literal("Layer: " + listedLayer.label),
                                b -> {
                                    Layer[] a = Layer.values();
                                    AtlasClient.profile.layer =
                                            a[
                                                    (AtlasClient.profile.selected().ordinal() + 1)
                                                            % a.length]
                                                    .name();
                                    AtlasClient.save();
                                    rebuildWidgets();
                                })
                        .bounds(left, height - 30, Math.min(280, content - 80), 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> onClose())
                        .bounds(left + content - 74, height - 30, 74, 20)
                        .build());
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float d) {
        g.drawCenteredString(
                font, "Colour key · " + AtlasClient.profile.layerTitle(), width / 2, 12, 0xE8D9B6);
        g.drawCenteredString(
                font, "Select a colour to edit · scroll for more", width / 2, 28, 0xB9B5A7);
        int content = Math.min(430, width - 24), y = noteY;
        for (var line :
                font.split(
                        Component.literal(
                                AtlasClient.profile
                                        .selected()
                                        .legendNote(AtlasClient.profile.climateContinents)),
                        content)) {
            g.drawString(font, line, (width - content) / 2, y, 0xB9B5A7);
            y += 10;
        }
    }

    private final class LegendList extends ObjectSelectionList<LegendRow> {
        LegendList(int width, int height, int top) {
            super(LegendScreen.this.minecraft, width, height, top, rowHeight(width));
            entries.forEach(e -> addEntry(new LegendRow(e)));
        }

        @Override
        public int getRowWidth() {
            return getWidth() - 24;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }
    }

    private int rowHeight(int width) {
        return entries.stream()
                .mapToInt(
                        e ->
                                font.split(Component.literal(e.label()), Math.max(30, width - 54))
                                                        .size()
                                                * 10
                                        + 10)
                .max()
                .orElse(20);
    }

    private final class LegendRow extends ObjectSelectionList.Entry<LegendRow> {
        private final Layer.LegendEntry entry;
        private final Button skin =
                Button.builder(Component.empty(), b -> edit()).bounds(0, 0, 100, 20).build();

        LegendRow(Layer.LegendEntry entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.label() + ", edit colour");
        }

        @Override
        public void render(
                GuiGraphics g,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mx,
                int my,
                boolean hovered,
                float delta) {
            skin.setX(left);
            skin.setY(top);
            skin.setWidth(width);
            skin.setHeight(height);
            skin.setFocused(isFocused());
            skin.render(g, mx, my, delta);
            swatch(g, entry, left + 5, top + 4, 12, Math.min(12, height - 6));
            int y = top + 4;
            for (var line : font.split(Component.literal(entry.label()), width - 30)) {
                g.drawString(font, line, left + 24, y, 0xFFFFFF);
                y += 10;
            }
        }

        private void edit() {
            minecraft.setScreen(
                    new ColorScreen(LegendScreen.this, AtlasClient.profile.selected(), entry));
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) {
                return false;
            }
            skin.playDownSound(minecraft.getSoundManager());
            edit();
            return true;
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
                edit();
                return true;
            }
            return false;
        }
    }

    private static void swatch(
            GuiGraphics g, Layer.LegendEntry entry, int x, int y, int width, int height) {
        for (int i = 0; i < width; i++) {
            g.fill(x + i, y, x + i + 1, y + height, 0xff000000 | entry.colourAt((i + .5) / width));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (parent instanceof AtlasScreen settings) {
            settings.syncLayer();
        }
        minecraft.setScreen(parent);
    }

    record KeyPlan(
            int width,
            int height,
            int header,
            List<List<net.minecraft.util.FormattedCharSequence>> rows,
            List<net.minecraft.util.FormattedCharSequence> note,
            KeyLayout.Fit fit) {}

    private static int naturalKeyWidth(Profile p) {
        var font = Minecraft.getInstance().font;
        int width = font.width(p.layerTitle() + " key") + 8;
        for (var entry : entries(p)) {
            width = Math.max(width, font.width(entry.label()) + 22);
        }
        return width;
    }

    static int defaultKeyWidth(Profile p, float scale) {
        return (int) Math.ceil(Math.min(200, naturalKeyWidth(p)) * scale);
    }

    static KeyPlan plan(Profile p, int physicalWidth, int maximumHeight, float scale) {
        var font = Minecraft.getInstance().font;
        int width = Math.max(30, Math.min(naturalKeyWidth(p), (int) (physicalWidth / scale)));
        List<List<net.minecraft.util.FormattedCharSequence>> rows = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (var entry : entries(p)) {
            var row = font.split(Component.literal(entry.label()), Math.max(8, width - 22));
            rows.add(row);
            heights.add(Math.max(11, row.size() * 10 + 1));
        }
        // Detailed explanations remain in the full key; the map key fits tightly around its rows.
        int header = 15, available = (int) (maximumHeight / scale);
        if (available < header + 14) {
            return null;
        }
        int requested =
                p.keyHeight() == 0 ? available : Math.min((int) (p.keyHeight() / scale), available);
        int budget = Math.max(Math.min(available, header + 14), requested);
        var fit = KeyLayout.fit(heights, header, budget - 3, 11);
        return new KeyPlan(width, fit.height() + 3, header, List.copyOf(rows), List.of(), fit);
    }

    static void compact(GuiGraphics g, HudLayout.Box bounds, Profile p, float scale, KeyPlan plan) {
        var font = Minecraft.getInstance().font;
        var entries = entries(p);
        g.pose().pushPose();
        g.pose().translate(bounds.x(), bounds.y(), 0);
        g.pose().scale(scale, scale, 1);
        g.fill(0, 0, plan.width(), plan.height(), 0xBD161B1D);
        g.drawString(
                font,
                font.plainSubstrByWidth(p.layerTitle() + " key", plan.width() - 8),
                4,
                3,
                0xE8D9B6);
        int line = 15;
        for (var note : plan.note()) {
            g.drawString(font, note, 5, line, 0xB9B5A7);
            line += 10;
        }
        line = plan.header();
        for (int i = 0; i < plan.fit().count(); i++) {
            swatch(g, entries.get(i), 4, line, 10, 7);
            for (var text : plan.rows().get(i)) {
                g.drawString(font, text, 18, line, 0xDDD7C9);
                line += 10;
            }
            line++;
        }
        if (plan.fit().hidden() > 0) {
            g.drawString(
                    font,
                    font.plainSubstrByWidth("+" + plan.fit().hidden() + " more", plan.width() - 8),
                    4,
                    line,
                    0xAAA99D);
        }
        g.pose().popPose();
    }

    private static final class ColorScreen extends AtlasMenuScreen {
        private final Screen parent;
        private final Layer layer;
        private final Layer.LegendEntry entry;
        private EditBox box;
        private String error = "";

        ColorScreen(Screen parent, Layer layer, Layer.LegendEntry entry) {
            super(Component.literal(entry.label()));
            this.parent = parent;
            this.layer = layer;
            this.entry = entry;
        }

        @Override
        protected void init() {
            box =
                    new HoverEditBox(
                            font, width / 2 - 80, 65, 160, 20, Component.literal("Hex colour"));
            box.setValue(String.format("%06X", entry.colourAt(.5)));
            addRenderableWidget(box);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Save colour"),
                                    b -> {
                                        try {
                                            String hex = box.getValue().replace("#", "");
                                            if (hex.length() != 6) {
                                                throw new IllegalArgumentException();
                                            }
                                            int rgb = Integer.parseInt(hex, 16);
                                            AtlasClient.profile.colors.put(
                                                    layer.name() + ":" + entry.colourKey(), rgb);
                                            AtlasClient.save();
                                            AtlasClient.renderer.clear();
                                            minecraft.setScreen(parent);
                                        } catch (Exception e) {
                                            error = "Enter six hexadecimal digits";
                                        }
                                    })
                            .bounds(width / 2 - 80, 95, 160, 20)
                            .build());
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Restore default"),
                                    b -> {
                                        AtlasClient.profile.colors.remove(
                                                layer.name() + ":" + entry.colourKey());
                                        AtlasClient.save();
                                        AtlasClient.renderer.clear();
                                        minecraft.setScreen(parent);
                                    })
                            .bounds(width / 2 - 80, 119, 160, 20)
                            .build());
            addRenderableWidget(
                    Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent))
                            .bounds(width / 2 - 80, 143, 160, 20)
                            .build());
        }

        @Override
        public void renderContent(GuiGraphics g, int x, int y, float d) {
            g.drawCenteredString(font, entry.label(), width / 2, 24, 0xffffff);
            g.drawCenteredString(
                    font, "RGB hex for the whole category/range", width / 2, 45, 0xCCCCCC);
            g.drawCenteredString(font, error, width / 2, 173, 0xFF9977);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
