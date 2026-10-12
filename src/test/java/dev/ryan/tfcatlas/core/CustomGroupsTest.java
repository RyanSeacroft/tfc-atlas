package dev.ryan.tfcatlas.core;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/** Named selections survive reopen, deletion, preset snapshots and overlapping families. */
public final class CustomGroupsTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    public static void run() throws Exception {
        var builtins =
                List.of(
                        new BiomeGroups.Group(
                                "coasts",
                                "Coastal biomes",
                                Set.of("tfc:shore", "tfc:coastal_dunes", "tfc:sea_stacks")),
                        new BiomeGroups.Group(
                                "plains",
                                "Plains & lowlands",
                                Set.of("tfc:plains", "tfc:lowlands")));
        var available =
                Set.of(
                        "tfc:shore",
                        "tfc:coastal_dunes",
                        "tfc:sea_stacks",
                        "tfc:plains",
                        "tfc:lowlands");
        check(SearchGroups.group("coastal", builtins) == null, "Group suffix is required");
        check(
                SearchGroups.resolve("coastal biomes", "", builtins)
                        .equals(builtins.get(0).members()),
                "Full group label resolves the correct members");
        check(
                SearchGroups.resolve("plains", "", builtins).equals(Set.of("plains")),
                "Individual names remain individual");
        var store = new CustomGroupStore();
        var members = Set.of("tfc:shore", "tfc:plains");
        store.put("biomes", "Nice places", members, builtins, available);
        var catalog = store.catalog("biomes", builtins, available);
        var selection = new GroupSelection(catalog, "", "Nice places, Coastal biomes");
        check(
                selection
                        .names()
                        .equals(
                                Set.of(
                                        "tfc:shore",
                                        "tfc:plains",
                                        "tfc:coastal_dunes",
                                        "tfc:sea_stacks")),
                "Custom and standard groups form the exact union");
        check(
                selection.text().contains("Nice places"),
                "Preferred custom name survives serialization with spaces");
        check(
                new GroupSelection(catalog, "", selection.text()).names().equals(selection.names()),
                "Custom names mirror the field in both directions");
        selection.toggle("tfc:shore");
        check(
                !SearchGroups.resolve(selection.text(), "", catalog).contains("tfc:shore"),
                "Unticking an overlapping member does not re-add it");
        var chosen = new GroupSelection(catalog, "", "shore, plains");
        chosen.prefer(SearchGroups.group("Nice places", catalog));
        check(
                chosen.text().equals("Nice places"),
                "Creating a group gives it a readable single name");
        String preset = store.snapshot("biomes", "Nice places, Coastal biomes");
        check(!preset.contains("Nice"), "Presets snapshot custom members");
        var expected = SearchGroups.resolve(preset, "", builtins);
        var directory = Files.createTempDirectory("atlas-groups-test-");
        var file = directory.resolve("custom-groups.json");
        try {
            store.write(file);
            var reopened = CustomGroupStore.read(file);
            check(
                    reopened.groups("biomes").equals(store.groups("biomes")),
                    "Saved groups survive a restart");
            check(reopened.groups("rocks").isEmpty(), "Custom names are scoped by field");
            reopened.put("biomes", "Nice places", Set.of("tfc:sea_stacks"), builtins, available);
            check(
                    SearchGroups.resolve(preset, "", builtins).equals(expected),
                    "Updating a custom group preserves preset membership");
            reopened.remove("biomes", "Nice places");
            reopened.write(file);
            check(
                    CustomGroupStore.read(file).groups("biomes").isEmpty(),
                    "Deleting a group is persisted");
            check(
                    SearchGroups.resolve(preset, "", builtins).equals(expected),
                    "Deleting a custom group preserves presets");
            check(
                    new GroupSelection(
                                    reopened.catalog("biomes", builtins, available),
                                    "",
                                    chosen.names().stream()
                                            .map(n -> n.substring(4))
                                            .collect(java.util.stream.Collectors.joining(",")))
                            .names()
                            .equals(chosen.names()),
                    "Selection survives custom deletion as individuals");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        rejects(
                () -> store.put("biomes", "Coastal biomes", members, builtins, available),
                "Built-in aliases cannot be shadowed");
        rejects(
                () -> store.put("biomes", "plains", members, builtins, available),
                "Individual names cannot be shadowed");
        rejects(
                () -> store.put("biomes", "two, names", members, builtins, available),
                "Delimited names are rejected");
        rejects(
                () -> store.put("biomes", "Empty", Set.of(), builtins, available),
                "Empty groups cannot become unrestricted filters");
        var climateBase =
                List.of(
                        new BiomeGroups.Group(
                                "temperate", "Temperate zones", Set.of("oceanic", "coastal")));
        store.put(
                "climateZones",
                "Good weather",
                Set.of("oceanic", "coastal"),
                climateBase,
                Set.of("oceanic", "coastal"));
        var climateCatalog =
                store.catalog("climateZones", climateBase, Set.of("oceanic", "coastal"));
        check(
                new GroupSelection(climateCatalog, "", "Good weather")
                        .text()
                        .equals("Good weather"),
                "Mixed-case field identifiers preserve custom labels");
        var hidden = store.catalog("biomes", builtins, Set.of("tfc:sea_stacks"));
        rejects(
                () -> SearchGroups.resolve("Nice places", "", hidden),
                "Unavailable custom groups give a clear error");
        for (int width : new int[] {320, 426, 640, 854}) {
            for (int height : new int[] {240, 260, 360, 480}) {
                var layout = GroupMenuLayout.of(width, height);
                check(
                        layout.left() + layout.columnWidth() < layout.dividerX()
                                && layout.dividerX() < layout.right(),
                        "Divider stays between equal group columns");
                check(
                        layout.right() + layout.columnWidth() <= layout.left() + layout.width(),
                        "Both columns fit the content width");
                check(
                        layout.listY() + layout.listHeight() + 4 <= layout.actionsY(),
                        "Lists clear fixed actions and footer");
                check(
                        layout.width() >= 280 && layout.listHeight() >= 24,
                        "Compact layouts keep readable controls");
            }
        }
        System.out.println(
                "PASS saved custom groups, full labels, snapshots and equal-column layout");
    }
}
