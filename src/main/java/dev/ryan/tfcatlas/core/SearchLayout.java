package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;

/** Each row starts after the preceding input, including its label and a visible gap. */
public final class SearchLayout {
    public static final List<String> SECTIONS =
            List.of("Terrain", "Climate", "Geology", "Location");
    private static final int TOP = 70, LABEL = 9, INPUT = 16, GAP = 4;

    public static List<Group> groups(int width, int height, boolean coordinates, int section) {
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        int listHeight = Math.min(72, 29 + Math.max(0, height - 240) / 4);
        List<Group> groups = new ArrayList<>();
        switch (section) {
            case 0 -> {
                wideList(groups, left, content, listHeight, "Biomes", "biomes");
                int top = TOP + LABEL + listHeight + GAP;
                row(
                        groups,
                        left,
                        content,
                        top,
                        0,
                        3,
                        "Area blocks²: min / max",
                        "minArea",
                        "maxArea");
                row(groups, left, content, top, 1, 3, "Surface Y: min / max", "minY", "maxY");
                row(
                        groups,
                        left,
                        content,
                        top,
                        2,
                        3,
                        "Steepness °: min / max",
                        "minSlope",
                        "maxSlope");
                row(
                        groups,
                        left,
                        content,
                        top + LABEL + INPUT + GAP,
                        0,
                        2,
                        "Terrain features",
                        "feature");
            }
            case 1 -> {
                row(groups, left, content, TOP, 0, 2, "Rain mm: min / max", "minRain", "maxRain");
                row(groups, left, content, TOP, 1, 2, "Temp °C: min / max", "minTemp", "maxTemp");
            }
            case 2 -> {
                wideList(groups, left, content, listHeight, "Rocks", "rocks");
                int top = TOP + LABEL + listHeight + GAP;
                row(groups, left, content, top, 0, 2, "Rock regions / uplift", "categories");
                row(groups, left, content, top, 1, 2, "Rock layer / dikes", "searchRockLayer");
            }
            case 3 -> {
                int pitch = LABEL + INPUT + GAP;
                row(groups, left, content, TOP, 0, 3, "Search radius", "radius");
                row(groups, left, content, TOP, 1, 3, "Sampling (blocks)", "precision");
                row(groups, left, content, TOP, 2, 3, "Separation (blocks)", "resultSpacing");
                row(
                        groups,
                        left,
                        content,
                        TOP + pitch,
                        0,
                        3,
                        "River: min / max",
                        "minRiver",
                        "maxRiver");
                row(
                        groups,
                        left,
                        content,
                        TOP + pitch,
                        1,
                        3,
                        "Ocean: min / max",
                        "minOcean",
                        "maxOcean");
                row(
                        groups,
                        left,
                        content,
                        TOP + pitch,
                        2,
                        3,
                        "Inlandness: min / max",
                        "minInland",
                        "maxInland");
                if (coordinates) {
                    row(
                            groups,
                            left,
                            content,
                            TOP + 2 * pitch,
                            0,
                            2,
                            "Centre X / Z",
                            "searchX",
                            "searchZ");
                }
                row(
                        groups,
                        left,
                        content,
                        TOP + 2 * pitch,
                        1,
                        2,
                        "Highlight matches",
                        "highlights");
            }
            default -> throw new IllegalArgumentException("Unknown search section");
        }
        return List.copyOf(groups);
    }

    /** Shared footer stays in the same place on every section. */
    public static List<HudLayout.Box> actions(int width, int height) {
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        int origin = (content - 12) * 34 / 100,
                search = (content - 12) * 29 / 100,
                presets = (content - 12) * 21 / 100;
        return List.of(
                new HudLayout.Box(left, height - 62, origin, 20),
                new HudLayout.Box(left + origin + 4, height - 62, search, 20),
                new HudLayout.Box(left + origin + search + 8, height - 62, presets, 20),
                new HudLayout.Box(
                        left + origin + search + presets + 12,
                        height - 62,
                        content - origin - search - presets - 12,
                        20));
    }

    public record Group(String label, List<String> keys, HudLayout.Box bounds) {
        public HudLayout.Box field(int index) {
            int gap = 6, w = (bounds.width() - gap * (keys.size() - 1)) / keys.size();
            return new HudLayout.Box(
                    bounds.x() + index * (w + gap), bounds.y() + LABEL, w, bounds.height() - LABEL);
        }
    }

    public static List<Group> groups(int width, int height) {
        return groups(width, height, false, 0);
    }

    public static List<Group> groups(int width, int height, boolean coordinates) {
        return groups(width, height, coordinates, 3);
    }

    private static void wideList(
            List<Group> out, int left, int content, int height, String label, String key) {
        out.add(
                new Group(
                        label,
                        List.of(key),
                        new HudLayout.Box(left, TOP, content - 8, height + LABEL)));
    }

    private static void row(
            List<Group> out,
            int left,
            int content,
            int top,
            int col,
            int cols,
            String label,
            String... keys) {
        out.add(
                new Group(
                        label,
                        List.of(keys),
                        new HudLayout.Box(
                                left + col * content / cols,
                                top,
                                content / cols - 8,
                                LABEL + INPUT)));
    }
}
