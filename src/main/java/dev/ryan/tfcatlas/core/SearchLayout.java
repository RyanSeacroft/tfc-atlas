package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;

/** All search filters fit together at Minecraft's 320 x 240 minimum GUI size. */
public final class SearchLayout {
    /** Origin toggle, primary Search, Presets, Clear; fit even the minimum GUI width. */
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
                    bounds.x() + index * (w + gap), bounds.y() + 9, w, bounds.height() - 9);
        }
    }

    public static List<Group> groups(int width, int height) {
        return groups(width, height, false);
    }

    public static List<Group> groups(int width, int height, boolean coordinates) {
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        int pitch = Math.min(30, Math.max(22, (height - 128) / 5)), h = Math.min(16, pitch - 10);
        List<Group> groups = new ArrayList<>();
        add(groups, left, content, pitch, h, 0, 0, 2, "Rocks (comma separated)", "rocks");
        add(groups, left, content, pitch, h, 0, 1, 2, "Biomes (comma separated)", "biomes");
        add(groups, left, content, pitch, h, 1, 0, 3, "Rock regions (list)", "categories");
        add(groups, left, content, pitch, h, 1, 1, 3, "Terrain features (list)", "feature");
        add(groups, left, content, pitch, h, 1, 2, 3, "Rock layer", "searchRockLayer");
        add(groups, left, content, pitch, h, 2, 0, 3, "Rain mm: min / max", "minRain", "maxRain");
        add(groups, left, content, pitch, h, 2, 1, 3, "Temp °C: min / max", "minTemp", "maxTemp");
        add(groups, left, content, pitch, h, 2, 2, 3, "Surface Y: min / max", "minY", "maxY");
        add(groups, left, content, pitch, h, 3, 0, 2, "Search radius (blocks)", "radius");
        add(groups, left, content, pitch, h, 3, 1, 2, "Sampling (blocks)", "precision");
        if (coordinates) {
            add(groups, left, content, pitch, h, 4, 0, 3, "Centre X / Z", "searchX", "searchZ");
        }
        add(groups, left, content, pitch, h, 4, 1, 3, "Separation (blocks)", "resultSpacing");
        add(groups, left, content, pitch, h, 4, 2, 3, "Highlight matches", "highlights");
        return List.copyOf(groups);
    }

    private static void add(
            List<Group> out,
            int left,
            int content,
            int pitch,
            int height,
            int row,
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
                                52 + row * pitch,
                                content / cols - 8,
                                height + 9)));
    }
}
