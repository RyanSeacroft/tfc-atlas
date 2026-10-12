package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.HudLayout;
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
                || cachedAccessible != false
                || cachedContinents != p.climateContinents
                || !cachedColours.equals(p.colors)
                || !cachedRocks.equals(rocks)) {
            cachedLayer = p.selected();
            cachedAccessible = false;
            cachedContinents = p.climateContinents;
            cachedColours = Map.copyOf(p.colors);
            cachedRocks = rocks;
            cachedEntries =
                    p.selected() == Layer.ROCKS && !rocks.isEmpty()
                            ? RockPossibilities.legend(rocks, false, p.colors, p.climateContinents)
                            : p.selected().legend(false, p.colors, p.climateContinents);
        }
        return cachedEntries;
    }

    private Layer listedLayer;
    private dev.ryan.tfcatlas.core.SmoothScroll scroll = new dev.ryan.tfcatlas.core.SmoothScroll();
    private int content, left, noteY;

    @Override
    protected void init() {
        if (listedLayer != AtlasClient.profile.selected()) {
            scroll = new dev.ryan.tfcatlas.core.SmoothScroll();
        }
        listedLayer = AtlasClient.profile.selected();
        entries = entries(AtlasClient.profile);
        entries =
                entries.stream()
                        .sorted(
                                java.util.Comparator.comparing(
                                        Layer.LegendEntry::label, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        content = Math.min(720, width - 24);
        left = (width - content) / 2;
        var note =
                font.split(
                        Component.literal(
                                listedLayer.legendNote(AtlasClient.profile.climateContinents)),
                        content);
        noteY = height - 38 - note.size() * 10;
        var list =
                addRenderableWidget(
                        new MinecraftScrollPanel(
                                left, 44, content, Math.max(28, noteY - 48), scroll));
        int cell = (list.contentWidth() - 8) / 3;
        int pitch =
                Math.max(
                        28,
                        entries.stream()
                                .mapToInt(
                                        e ->
                                                font.split(
                                                                                Component.literal(
                                                                                        e.label()),
                                                                                Math.max(
                                                                                        24,
                                                                                        cell - 40))
                                                                        .size()
                                                                * 10
                                                        + 12)
                                .max()
                                .orElse(28));
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            list.cell(
                    (i % 3) * (cell + 4),
                    (i / 3) * pitch,
                    cell,
                    pitch - 4,
                    entry::label,
                    () -> minecraft.setScreen(new ColorScreen(this, listedLayer, entry)),
                    entry.label() + " · edit colour",
                    entry::colourAt);
        }
        int half = (content - 4) / 2;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Layer: " + listedLayer.label),
                                ignored -> {
                                    Layer[] layers = Layer.values();
                                    AtlasClient.profile.layer =
                                            layers[(listedLayer.ordinal() + 1) % layers.length]
                                                    .name();
                                    AtlasClient.save();
                                    rebuildWidgets();
                                })
                        .bounds(left, height - 30, half, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Back"), ignored -> onClose())
                        .bounds(left + half + 4, height - 30, content - half - 4, 20)
                        .build());
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float delta) {
        g.drawCenteredString(
                font,
                "Colour key · " + AtlasClient.profile.layerTitle(),
                width / 2,
                12,
                HudStyle.TITLE);
        g.drawCenteredString(
                font,
                scroll.maximum() > 0
                        ? "Select a colour to edit · scroll or drag the scrollbar"
                        : "Select a colour to edit",
                width / 2,
                28,
                0xB9B5A7);
        int y = noteY;
        for (var line :
                font.split(
                        Component.literal(
                                listedLayer.legendNote(AtlasClient.profile.climateContinents)),
                        content)) {
            g.drawString(font, line, left, y, 0xB9B5A7);
            y += 10;
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
            List<Integer> heights,
            int viewport,
            int barWidth) {
        int contentHeight() {
            return heights.stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static int naturalKeyWidth(Profile p, float scale) {
        var font = Minecraft.getInstance().font;
        int padding = HudStyle.padding(scale);
        int width = font.width(p.layerTitle() + " key") + padding * 2;
        for (var entry : entries(p)) {
            width = Math.max(width, font.width(entry.label()) + padding * 2 + 14);
        }
        return p.selected() == Layer.BIOMES ? Math.max(200, width) : width;
    }

    static int defaultKeyWidth(Profile p, float scale) {
        return (int) Math.ceil(Math.min(200, naturalKeyWidth(p, scale)) * scale);
    }

    static KeyPlan plan(Profile p, int physicalWidth, int maximumHeight, float scale) {
        var font = Minecraft.getInstance().font;
        int padding = HudStyle.padding(scale);
        int width =
                Math.max(30, Math.min(naturalKeyWidth(p, scale), (int) (physicalWidth / scale)));
        int header = padding + 12, available = (int) (maximumHeight / scale);
        if (available < header + padding + 11) {
            return null;
        }
        int requested =
                p.keyHeight() == 0 ? available : Math.min((int) (p.keyHeight() / scale), available);
        int budget = Math.max(header + padding + 11, requested);
        List<List<net.minecraft.util.FormattedCharSequence>> rows = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        int barWidth = 0;
        // Only reserve scrollbar space when there is overflow, then wrap once more.
        for (int pass = 0; pass < 2; pass++) {
            rows.clear();
            heights.clear();
            int reserve = barWidth == 0 ? 0 : (int) Math.ceil(barWidth / scale) + 4;
            for (var entry : entries(p)) {
                var row =
                        font.split(
                                Component.literal(entry.label()),
                                Math.max(8, width - padding * 2 - 14 - reserve));
                rows.add(row);
                heights.add(Math.max(11, row.size() * 10 + 1));
            }
            int content = heights.stream().mapToInt(Integer::intValue).sum();
            if (pass == 0 && content > budget - header - padding) {
                barWidth = Math.max(3, (int) Math.ceil(12 * scale));
                width = Math.max(width, padding * 2 + 14 + (int) Math.ceil(barWidth / scale) + 12);
                continue;
            }
            int viewport = Math.min(content, budget - header - padding);
            if (content <= viewport) {
                barWidth = 0;
            }
            return new KeyPlan(
                    width,
                    header + viewport + padding,
                    header,
                    List.copyOf(rows),
                    List.copyOf(heights),
                    viewport,
                    barWidth);
        }
        throw new IllegalStateException("Key layout did not settle");
    }

    static void compact(
            GuiGraphics g,
            HudLayout.Box bounds,
            Profile p,
            float scale,
            KeyPlan plan,
            MapKeyView view) {
        var font = Minecraft.getInstance().font;
        var entries = entries(p);
        int padding = HudStyle.padding(scale);
        int bodyTop = bounds.y() + (int) Math.ceil(plan.header() * scale);
        int bodyBottom =
                Math.max(
                        bodyTop,
                        bounds.y() + (int) Math.floor((plan.header() + plan.viewport()) * scale));
        int right = bounds.x() + (int) Math.floor((plan.width() - padding) * scale);
        int barX = right - plan.barWidth();
        view.layout(
                p.layerTitle(),
                barX,
                bodyTop,
                Math.max(3, plan.barWidth()),
                Math.max(0, bodyBottom - bodyTop),
                plan.barWidth() == 0 ? 0 : plan.contentHeight(),
                scale);
        g.pose().pushPose();
        g.pose().translate(bounds.x(), bounds.y(), 0);
        g.pose().scale(scale, scale, 1);
        HudStyle.panel(g, plan.width(), plan.height());
        g.drawString(
                font,
                font.plainSubstrByWidth(p.layerTitle() + " key", plan.width() - padding * 2),
                padding,
                padding,
                HudStyle.TITLE);
        g.pose().popPose();
        // Scissor in screen coordinates so wrapped rows cannot cover the title, frame or scrollbar.
        int bodyLeft = bounds.x() + (int) Math.ceil(padding * scale);
        int bodyRight =
                plan.barWidth() > 0 ? barX - Math.max(1, (int) Math.ceil(4 * scale)) : right;
        if (bodyRight > bodyLeft && bodyBottom > bodyTop) {
            g.enableScissor(bodyLeft, bodyTop, bodyRight, bodyBottom);
            g.pose().pushPose();
            g.pose().translate(bounds.x(), bodyTop, 0);
            g.pose().scale(scale, scale, 1);
            float line = -(float) view.offset();
            for (int i = 0; i < plan.rows().size(); i++) {
                int rowHeight = plan.heights().get(i);
                if (line + rowHeight > 0 && line < plan.viewport()) {
                    swatch(g, entries.get(i), padding, (int) Math.ceil(line), 10, 7);
                    float textY = line;
                    for (var text : plan.rows().get(i)) {
                        g.drawString(
                                font, text, padding + 14, (int) Math.ceil(textY), HudStyle.TEXT);
                        textY += 10;
                    }
                }
                line += rowHeight;
            }
            g.pose().popPose();
            g.disableScissor();
        }
        if (plan.barWidth() > 0) {
            view.render(g);
        }
        HudStyle.frame(g, bounds, false);
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
