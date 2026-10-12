package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import dev.ryan.tfcatlas.client.Profiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Connected-region bounds and compact border behaviour must agree across both branches. */
public final class AreaAndOutlineTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        // The four-cell region crosses X=0 and a tile boundary; rock-layer colour changes
        // within the region must not split its size or draw an internal border.
        Tile.Key west = new Tile.Key(-1, 0, 1), east = new Tile.Key(0, 0, 1);
        Map<Tile.Key, byte[]> regions = new HashMap<>();
        byte[] left = new byte[1024], right = new byte[1024];
        left[30] = left[31] = 1;
        right[0] = right[1] = 2;
        right[10] = 4;
        regions.put(west, left);
        regions.put(east, right);
        SearchAreas.retainRange(regions, 128, 256, ignored -> {});
        check(
                right[10] == 0 && left[30] == 1 && right[1] == 2,
                "Inclusive range keeps the whole cross-tile region, with its original colours");
        SearchOverlay outline = new SearchOverlay(regions, List.of(), true);
        check(outline.areaCount() == 1, "Area count uses only retained connected regions");
        check(
                outline.edges().stream().noneMatch(e -> e.x0() == 0 && e.x1() == 0),
                "Adjacent matched rock layers have no duplicate internal border");
        SearchAreas.retainRange(regions, 0, 255, ignored -> {});
        check(
                new SearchOverlay(regions, List.of(), true).count() == 0,
                "Maximum rejects the entire region, not individual cells");
        SearchLimits limit =
                new SearchLimits(
                        Set.of(),
                        0,
                        Float.MAX_VALUE,
                        0,
                        Float.MAX_VALUE,
                        0,
                        90,
                        0,
                        Float.MAX_VALUE,
                        0,
                        256);
        check(limit.areaRestricted(), "Maximum alone enables area filtering");
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            boolean rejected = false;
            try {
                new SearchLimits(
                        Set.of(),
                        0,
                        Float.MAX_VALUE,
                        0,
                        Float.MAX_VALUE,
                        0,
                        90,
                        0,
                        Float.MAX_VALUE,
                        0,
                        bad);
            } catch (IllegalArgumentException ex) {
                rejected = true;
            }
            check(rejected, "Invalid maximum is rejected");
        }
        boolean reversed = false;
        try {
            new SearchLimits(
                    Set.of(),
                    0,
                    Float.MAX_VALUE,
                    0,
                    Float.MAX_VALUE,
                    0,
                    90,
                    0,
                    Float.MAX_VALUE,
                    300,
                    256);
        } catch (IllegalArgumentException ex) {
            reversed = true;
        }
        check(reversed, "Maximum below minimum is rejected");
        Profile original = new Profile();
        original.maxArea = 256;
        Profile restored = Profiles.decode(Profiles.JSON.toJsonTree(original).getAsJsonObject());
        check(
                restored.maxArea == 256 && restored.query().limits().maxArea() == 256,
                "Area maximum survives profile persistence");
        var legacy = Profiles.JSON.toJsonTree(original).getAsJsonObject();
        legacy.remove("maxArea");
        check(
                Profiles.decode(legacy).maxArea == Double.MAX_VALUE,
                "Older profiles inherit an unrestricted area maximum");
        check(original.searchValue("maxArea").equals("256.0"), "Filled limit is visible");
        original.clearSearchSettings();
        check(original.searchValue("maxArea").isEmpty(), "Clear restores the blank maximum");
        double previous = 0;
        for (double zoom : new double[] {.001, .01, .03, .1, .5, 1}) {
            double stroke = SearchStroke.width(8, zoom, false);
            check(
                    stroke >= .35 && stroke <= .75 && stroke >= previous,
                    "Coloured outlines stay thin and visible at both wide and close zooms");
            previous = stroke;
        }
        for (double zoom : new double[] {.0001, .01, .05, .1, 1, 4}) {
            check(SearchStroke.width(8, zoom, true) == 0, "No black result border at any zoom");
        }
        check(
                DisplayLayout.SECTIONS.equals(List.of("Map", "Panels", "Colours")),
                "Obsolete Labels subtab is removed");
        check(
                DisplayLayout.keys(0)
                        .containsAll(List.of("highlights", "mapLabels", "minimapHighlights")),
                "Map controls share highlight and label settings");
        System.out.println("PASS connected area bounds, saved maximums and zoom-aware outlines");
    }

    private AreaAndOutlineTest() {}
}
