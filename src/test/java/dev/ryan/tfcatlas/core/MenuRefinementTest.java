package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Regressions for circular search completion, submitted group names and compact settings. */
public final class MenuRefinementTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        int skipped = 0;
        for (int center : new int[] {-29_999_980, -513, 0, 201, 29_999_980}) {
            for (int resolution : new int[] {16, 128, 512}) {
                int radius = 1024;
                var window = SearchWindow.of(center, -center, radius, resolution);
                long completed = 0;
                var progress = new SearchProgress(false, false);
                for (var key : window) {
                    boolean loaded = SearchWindow.intersectsRadius(key, center, -center, radius);
                    if (!loaded) {
                        skipped++;
                    }
                    for (int i = 0; i < 1024; i++) {
                        int x = key.blockX() + (i % 32) * resolution + resolution / 2;
                        int z = key.blockZ() + (i / 32) * resolution + resolution / 2;
                        if (Math.hypot((double) x - center, (double) z + center) <= radius) {
                            check(
                                    loaded,
                                    "Every sample inside the search circle survives tile pruning");
                        }
                    }
                    completed++;
                    progress.update(completed * 1024, window.count() * 1024, 0, completed * 200);
                }
                check(
                        completed == window.count(),
                        "Skipped corner tiles still count towards completion");
                check(
                        progress.update(completed * 1024, completed * 1024, 0, completed * 200)
                                .contains("100%"),
                        "Search sampling reaches completion even with no matches");
            }
        }
        check(
                skipped > 0,
                "Outside-circle corners are pruned instead of generating no-match tiles");

        var igneous =
                new BiomeGroups.Group("igneous", "Igneous rocks", Set.of("granite", "basalt"));
        var custom =
                new BiomeGroups.Group(
                        "custom:rocks:my_favourite_rocks", "My favourite rocks", Set.of("granite"));
        var groups = List.of(igneous, custom);
        check(
                SearchGroups.resolve("igneous rocks", "", groups).equals(igneous.members()),
                "Built-in rocks accept the full label");
        check(
                SearchGroups.group("My favourite", groups) == null,
                "Custom groups do not inherit shortened built-in aliases");
        check(
                SearchGroups.group("My favourite rocks", groups) == custom,
                "Custom groups still accept their full name");
        check(
                !SearchGroups.suggestions(List.of("Granite"), groups).contains("My favourite"),
                "Autocomplete offers the full custom name only");
        var labels = new HashMap<String, String>();
        labels.put("rocks", SearchGroups.typedList("igneous rocks, basalt", "", groups));
        Profile profile = new Profile();
        profile.rocks = "igneous rocks, basalt";
        var snapshot = new SearchDetails(profile.query(), 0, 0, 1024, 5, 100, 16, labels);
        labels.put("rocks", "Something edited later");
        check(
                snapshot.lines().contains("Rocks: basalt, Igneous rocks"),
                "Submitted group and explicit member names survive resolution and later edits");
        check(
                new SearchDetails(profile.query(), 0, 0, 1024, 5, 100)
                        .lines().stream().anyMatch(line -> line.startsWith("Rocks: ")),
                "Older callers retain resolved-name summaries");

        var seen = new HashSet<String>();
        for (int section = 0; section < DisplayLayout.SECTIONS.size(); section++) {
            for (var key : DisplayLayout.keys(section)) {
                check(seen.add(key), "Display controls are not duplicated between sections");
            }
            for (int width : new int[] {320, 427, 854}) {
                for (int height : new int[] {240, 300, 480}) {
                    var fields =
                            SettingsLayout.fields(
                                    width, height, DisplayLayout.keys(section).size(), 74);
                    for (var field : fields) {
                        check(
                                field.label().y() >= 74
                                        && field.input().y() + field.input().height() < height - 72,
                                "Display fields leave room for subtabs and action rows");
                        check(
                                field.input().x() >= 0
                                        && field.input().x() + field.input().width() <= width,
                                "Display fields fit the screen");
                    }
                }
            }
        }
        var defaults = new Profile();
        check(defaults.showCriteria, "Search criteria remain enabled");
        System.out.println(
                "PASS circular search completion, submitted group names, strict custom names and display layout");
    }

    private MenuRefinementTest() {}
}
