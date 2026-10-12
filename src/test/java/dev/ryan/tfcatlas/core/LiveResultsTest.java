package dev.ryan.tfcatlas.core;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Moving across a finished search must expose matches absent from the original five. */
public final class LiveResultsTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        var masks = new HashMap<Tile.Key, byte[]>();
        for (int x : new int[] {-20, -10, 0, 10, 20, 30, 40}) {
            byte[] cells = new byte[1024];
            cells[0] = (byte) (x < 10 ? 1 : 4);
            masks.put(new Tile.Key(x, 0, 1), cells);
        }
        var overlay = new SearchOverlay(masks, List.of(), true);
        var west = overlay.closestTo(-5000, 4, 5, 256);
        var east = overlay.closestTo(11000, 4, 5, 256);
        check(west.size() == 5 && east.size() == 5, "Both positions have five separated matches");
        check(
                west.get(0).x() == -5116 && east.get(0).x() == 10244,
                "Nearest matches change with the player, not the old search centre");
        check(
                east.stream().anyMatch(p -> west.stream().noneMatch(w -> w.key().equals(p.key()))),
                "Re-selection includes coverage outside the original five candidates");
        for (var selected : List.of(west, east)) {
            double previous = -1;
            for (var point : selected) {
                check(point.distance() >= previous, "Results are ordered nearest first");
                check(
                        point.layerMask() == (point.x() < 2560 ? 1 : 4),
                        "Updated candidates preserve their matching rock layer");
                previous = point.distance();
                for (var other : selected) {
                    check(
                            point == other
                                    || Math.hypot(point.x() - other.x(), point.z() - other.z())
                                            >= 256,
                            "Minimum separation survives movement");
                }
            }
        }
        check(
                overlay.count() == 7 && overlay.candidates().isEmpty(),
                "Re-ranking does not alter immutable search coverage");
        check(
                overlay.closestTo(0, 0, 5, 100000).size() == 1,
                "Movement does not bypass the submitted minimum separation");
        var groups =
                List.of(
                        new BiomeGroups.Group("wetlands", "Wetland biomes", Set.of("tfc:marsh")),
                        new BiomeGroups.Group("igneous", "Igneous rocks", Set.of("granite")),
                        new BiomeGroups.Group("temperate", "Temperate zones", Set.of("oceanic")));
        for (var group : groups) {
            String full = group.label();
            String shortName = full.substring(0, full.lastIndexOf(' '));
            check(SearchGroups.group(full, groups) == group, "Full labels resolve");
            check(SearchGroups.group(shortName, groups) == null, "Generic suffix is required");
            check(
                    SearchGroups.resolve("", group.id(), groups).equals(group.members()),
                    "Legacy separate group IDs remain compatible");
            check(
                    !SearchGroups.suggestions(List.of(), groups).contains(shortName),
                    "Autocomplete inserts the complete group name");
        }
        System.out.println(
                "PASS player-relative full-coverage results, spacing, masks and explicit group labels");
    }

    private LiveResultsTest() {}
}
