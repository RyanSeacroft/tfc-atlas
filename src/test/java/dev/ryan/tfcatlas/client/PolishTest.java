package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.HudHandles;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.PrecacheArea;
import dev.ryan.tfcatlas.core.RegionLabels;
import dev.ryan.tfcatlas.core.TerrainCoverage;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileLoadFrontier;
import dev.ryan.tfcatlas.core.TileWindow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class PolishTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        labels();
        loading();
        handles();
        menus();
        System.out.println(
                "PASS: "
                        + checks
                        + " explored-label, centre-first upload, menu-focus/tooltip and handle checks");
    }

    private static void labels() {
        String[] original = new String[240];
        Arrays.fill(original, "granite");
        var view = new RegionLabels.View(-100, -60, 100, 60);
        var before = RegionLabels.layout(original, 20, 12, -100, -60, 10, view, s -> 30, 10, 0);
        var explored = List.of(new TerrainCoverage.Rect(-20, -60, 20, 60));
        check(
                before.size() == 1 && !RegionLabels.clearOfExplored(before.get(0), explored, 1),
                "A previously centred label would be clipped by explored terrain");
        String[] names = original.clone();
        RegionLabels.excludeExplored(names, 20, 12, -100, -60, 10, explored, 1);
        var after = RegionLabels.layout(names, 20, 12, -100, -60, 10, view, s -> 30, 10, 0);
        check(after.size() == 2, "Visible unexplored portions get their own interior anchors");
        for (var label : after) {
            check(
                    RegionLabels.clearOfExplored(label, explored, 1),
                    "The whole label and padding avoid explored terrain");
            check(Math.abs(label.x()) > 30, "Label moves away from the explored centre");
            var newlyExplored =
                    List.of(
                            new TerrainCoverage.Rect(
                                    (int) label.x() - 2,
                                    (int) label.z() - 2,
                                    (int) label.x() + 2,
                                    (int) label.z() + 2));
            check(
                    !RegionLabels.clearOfExplored(label, newlyExplored, 1),
                    "New coverage suppresses stale worker results immediately");
        }
        names = original.clone();
        RegionLabels.excludeExplored(
                names,
                20,
                12,
                -100,
                -60,
                10,
                List.of(new TerrainCoverage.Rect(-100, -60, 100, 60)),
                0);
        check(
                RegionLabels.layout(names, 20, 12, -100, -60, 10, view, s -> 30, 10, 0).isEmpty(),
                "Completely explored region has no clipped name");
        names = original.clone();
        RegionLabels.excludeExplored(
                names,
                20,
                12,
                -100,
                -60,
                10,
                List.of(new TerrainCoverage.Rect(500, 500, 510, 510)),
                1);
        check(
                Arrays.equals(names, original),
                "Offscreen explored terrain cannot erase visible names");
        check(
                original[110].equals("granite"),
                "Label exclusion never changes actual map sample data");
    }

    private static void loading() {
        var window = new TileWindow(-64, -64, 64, 64, 0, 0, PrecacheArea.STEP);
        var frontier = new TileLoadFrontier(window);
        Set<Tile.Key> displayed = new HashSet<>();
        var first = frontier.next(displayed::contains);
        check(
                first.size() == 64 && first.get(0).equals(new Tile.Key(0, 0, PrecacheArea.STEP)),
                "First batch starts at the visible centre");
        for (int i = 0; i < 20; i++) {
            check(
                    frontier.next(displayed::contains).equals(first),
                    "Cached/requested centre tiles cannot be skipped until uploaded");
        }
        displayed.addAll(first.subList(1, first.size()));
        check(
                frontier.next(displayed::contains).get(0).equals(first.get(0))
                        && frontier.readyPrefix(k -> !k.equals(first.get(0))).isEmpty(),
                "Prefetch refills without painting ahead of a pending centre upload");
        displayed.add(first.get(0));
        var next = frontier.next(displayed::contains);
        check(
                next.size() == 64 && Collections.disjoint(first, next),
                "Completing the centre advances the next bounded batch");
        check(
                new TileLoadFrontier(window).next(k -> false).equals(first),
                "Changing layer restarts loading from the centre");
        check(
                new TileLoadFrontier(window).next(k -> k.equals(first.get(0))).size() == 64,
                "Failed/complete tiles can be skipped without blocking later work");
        List<Tile.Key> uploads =
                new ArrayList<>(
                        List.of(
                                new Tile.Key(40, 40, PrecacheArea.STEP),
                                new Tile.Key(0, 0, PrecacheArea.STEP),
                                new Tile.Key(-10, 0, PrecacheArea.STEP)));
        uploads.sort(Comparator.comparingDouble(window::distanceSquared));
        check(
                uploads.get(0).x() == 0 && uploads.get(2).x() == 40,
                "Upload priority is distance, not memory-cache recency");
        var tiny = new TileLoadFrontier(new TileWindow(-1, -1, -1, -1, -1, -1, 4));
        check(
                tiny.next(k -> true).isEmpty() && tiny.next(k -> false).size() == 1,
                "Small completed/evicted views restart without duplicate jobs");
    }

    private static void handles() {
        for (int width : new int[] {24, 60, 174, 350}) {
            for (int height : new int[] {12, 40, 100, 200}) {
                var box = new HudLayout.Box(30, 40, width, height);
                var handles =
                        List.of(
                                HudHandles.corner(box),
                                HudHandles.width(box),
                                HudHandles.height(box));
                for (int i = 0; i < handles.size(); i++) {
                    var handle = handles.get(i);
                    check(
                            !box.overlaps(handle.padded(1))
                                    && HudHandles.footprint(box).contains(handle.padded(1)),
                            "Grip plus hit margin stays outside text but inside reserved space");
                    for (int j = i + 1; j < handles.size(); j++) {
                        check(
                                !handle.padded(1).overlaps(handles.get(j).padded(1)),
                                "Small-key edge and corner handles have separate hit targets");
                    }
                }
            }
        }
        for (int width : new int[] {320, 854}) {
            for (int height : new int[] {240, 480}) {
                var safe = HudLayout.safeArea(width, height);
                var outer = HudLayout.place(safe, 110, 70, safe.right(), safe.bottom(), List.of());
                var box = new HudLayout.Box(outer.x(), outer.y(), 100, 60);
                check(
                        safe.contains(HudHandles.corner(box)),
                        "Bottom/right resize handles remain on screen");
                var second =
                        HudLayout.place(
                                safe, 80, 60, box.x(), box.y(), List.of(HudHandles.footprint(box)));
                check(
                        second != null && !second.overlaps(HudHandles.footprint(box)),
                        "Panel placement reserves space for another panel's grips");
            }
        }
    }

    private static final class Widget extends AbstractWidget {
        Widget() {
            super(10, 10, 50, 20, Component.literal("Field"));
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            return button == 0 && x >= 10 && x < 60 && y >= 10 && y < 30;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int x, int y, float delta) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    private static final class Menu extends AtlasMenuScreen {
        final Widget field = new Widget();

        Menu() {
            super(Component.literal("Menu"));
            addRenderableWidget(field);
        }
    }

    private static void menus() {
        Menu menu = new Menu();
        check(
                menu.mouseClicked(20, 20, 0) && menu.field.isFocused(),
                "Clicking a field still focuses it");
        menu.mouseClicked(100, 100, 0);
        check(
                menu.getFocused() == null && !menu.field.isFocused() && !menu.isDragging(),
                "Background click clears focused field and mouse capture");
        menu.mouseClicked(20, 20, 0);
        var tooltip = Tooltip.create(Component.literal("Help"));
        menu.field.setTooltip(tooltip);
        HoverTooltips.render(
                menu.field,
                100,
                100,
                () ->
                        check(
                                menu.field.getTooltip() == null && menu.field.isFocused(),
                                "Keyboard-focused fields cannot pin a tooltip when the pointer is elsewhere"));
        check(
                menu.field.getTooltip() == tooltip && menu.field.isFocused(),
                "Hover policy preserves tooltip content and keyboard completion focus");
        HoverTooltips.render(
                menu.field,
                20,
                20,
                () ->
                        check(
                                menu.field.getTooltip() == tooltip,
                                "Tooltip remains available while the pointer hovers"));
    }
}
