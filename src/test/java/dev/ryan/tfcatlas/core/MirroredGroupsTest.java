package dev.ryan.tfcatlas.core;

import java.util.List;
import java.util.Random;
import java.util.Set;

/** Visible group fields round-trip exactly, and labels are part of the layout checks. */
public final class MirroredGroupsTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        var rocks =
                List.of(
                        new BiomeGroups.Group(
                                "igneous", "Igneous rocks", Set.of("granite", "diorite", "basalt")),
                        new BiomeGroups.Group(
                                "igneous_intrusive",
                                "Igneous intrusive rocks",
                                Set.of("granite", "diorite")),
                        new BiomeGroups.Group(
                                "sedimentary", "Sedimentary rocks", Set.of("chalk", "shale")));
        var selection = new GroupSelection(rocks, "", "Igneous rocks");
        check(
                selection.text().equals("Igneous rocks"),
                "Typed full group name is a readable whole-group field");
        selection.toggle("granite");
        check(
                !SearchGroups.resolve(selection.text(), "", rocks).contains("granite"),
                "Unticking a member is reflected in the field without a hidden group re-adding it");
        check(
                !new GroupSelection(rocks, "", selection.text()).contains("granite"),
                "Reopening Groups uses exactly the field");
        selection.toggle("granite");
        check(
                selection.text().equals("Igneous rocks"),
                "Selecting the whole family collapses it to one group name");
        var sameMembers =
                List.of(
                        new BiomeGroups.Group("a", "Alpha family", Set.of("one", "two")),
                        new BiomeGroups.Group("b", "Beta family", Set.of("one", "two")));
        check(
                new GroupSelection(sameMembers, "", "Beta family").text().equals("Beta family"),
                "Preserve the chosen whole-group name when another group has identical members");
        var suggestions = SearchGroups.suggestions(List.of("Granite", "Diorite", "Basalt"), rocks);
        check(
                SearchCompletion.suggest("igne", 4, suggestions).text().equals("igneous rocks"),
                "Shortest completion includes group names");
        check(
                SearchGroups.resolve("igneous rocks, shale", "", rocks).size() == 4,
                "Groups and individuals can be mixed");
        check(
                SearchGroups.resolve("igneous intrusive rocks", "", rocks)
                        .equals(Set.of("granite", "diorite")),
                "Full group identifiers work without Tab");
        selection = new GroupSelection(rocks, "", "");
        Random random = new Random(2626);
        for (int i = 0; i < 2000; i++) {
            if (random.nextBoolean()) {
                selection.toggle(rocks.get(random.nextInt(rocks.size())));
            } else {
                selection.toggle(
                        List.of("granite", "diorite", "basalt", "chalk", "shale")
                                .get(random.nextInt(5)));
            }
            String field = selection.text();
            check(
                    SearchGroups.resolve(field, "", rocks).equals(selection.names()),
                    "Visible field preserves partial overlapping selections");
            check(
                    new GroupSelection(rocks, "", field).names().equals(selection.names()),
                    "Menu and visible field mirror in both directions");
        }
        var biomes =
                BiomeGroups.from(
                        Set.of(
                                "tfc:plains",
                                "tfc:lowlands",
                                "tfc:rolling_hills",
                                "tfc:mountains",
                                "tfc:old_mountains",
                                "tfc:canyon",
                                "addon:valley"),
                        Set.of());
        check(
                biomes.groups().stream().noneMatch(g -> g.id().equals("all")),
                "All biomes is not a selectable group");
        check(
                biomes.resolve("plains", "").equals(Set.of("tfc:plains")),
                "An individual biome stays individual when a short group ID overlaps");
        check(
                biomes.resolve("Plains & lowlands", "")
                        .equals(Set.of("tfc:plains", "tfc:lowlands")),
                "Distinct group label selects the whole family");
        check(
                biomes.resolve("Mountain biomes", "")
                        .equals(Set.of("tfc:mountains", "tfc:old_mountains")),
                "Readable biome groups are searchable");
        check(biomes.resolve("", "all").size() == 7, "Old All-biomes presets remain readable");
        var covered = new java.util.HashSet<String>();
        biomes.groups().forEach(g -> covered.addAll(g.members()));
        check(
                covered.equals(biomes.registered()),
                "Removing All does not remove individual access to uncommon and addon biomes");
        List<String> labels = biomes.groups().stream().map(BiomeGroups.Group::label).toList();
        check(
                labels.equals(labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()),
                "Biome group menu is alphabetical");
        for (String field :
                List.of("Hill biomes", "Mountain biomes, canyon", "plains", "Plains & lowlands")) {
            selection = new GroupSelection(biomes.groups(), "", field);
            check(
                    biomes.resolve(selection.text(), "").equals(selection.names()),
                    "Biome field uses exact registry IDs after compression");
        }
        var climates = ClimateGroups.groups();
        check(
                SearchGroups.resolve("Polar", "", climates).equals(Set.of("polar")),
                "Polar individual zone is distinct from its family");
        check(
                SearchGroups.resolve("Polar zones", "", climates).equals(Set.of("polar", "tundra")),
                "Readable family selects both polar zones");
        check(
                SearchCompletion.suggest(
                                "polar z",
                                7,
                                SearchGroups.suggestions(ClimateZones.NAMES, climates))
                        .text()
                        .equals("polar zones"),
                "Climate-family autocomplete is available");
        check(
                SearchGroups.resolve("sedimentary rocks", "", rocks)
                        .equals(Set.of("chalk", "shale")),
                "Rock families resolve with the rocks suffix");
        check(
                SearchCompletion.suggest(
                                "trop", 4, SearchGroups.suggestions(ClimateZones.NAMES, climates))
                        .text()
                        .equals("tropical zones"),
                "Climate family autocomplete includes the zones suffix");
        check(
                SearchGroups.resolve("tropical zones", "", climates).size() == 4,
                "Full climate group labels resolve to their members");
        var profile = new dev.ryan.tfcatlas.client.Profile();
        profile.climateZones = "Tropical zones";
        check(
                profile.query(biomes).climateZones().size() == 4,
                "Search resolves climate groups before mapping to real TFC codes");
        for (int width : new int[] {320, 426, 640, 854, 1280}) {
            for (int height : new int[] {240, 260, 360, 480}) {
                for (int section = 0; section < SearchLayout.SECTIONS.size(); section++) {
                    var groups = SearchLayout.groups(width, height, true, section);
                    for (var group : groups) {
                        var label =
                                new HudLayout.Box(
                                        group.bounds().x(),
                                        group.bounds().y(),
                                        group.bounds().width(),
                                        8);
                        for (var other : groups) {
                            for (int i = 0; i < other.keys().size(); i++) {
                                var field = other.field(i);
                                check(
                                        !label.overlaps(field.padded(1)),
                                        "Labels never clip into any input box, including the large lists");
                                check(
                                        field.bottom() + 1 < height - 72,
                                        "Inputs stay clear of the footer at minimum GUI size");
                            }
                        }
                    }
                    for (var group : groups) {
                        if (List.of("biomes", "rocks", "climateZones")
                                .contains(group.keys().get(0))) {
                            check(
                                    group.field(0).height() >= 29,
                                    "Each name/group list is multiline");
                        }
                    }
                }
            }
        }
        var safe = new HudLayout.Box(32, 40, 600, 400);
        var info = new HudLayout.Box(32, 380, 140, 60);
        var criteria = HudLayout.above(safe, info, 120, 30, 6);
        check(
                criteria.x() == info.x() && criteria.bottom() + 6 == info.y(),
                "Default panels share a left edge and have a consistent gap");
        check(
                HudLayout.place(
                                safe,
                                criteria.width(),
                                criteria.height(),
                                criteria.x(),
                                criteria.y(),
                                List.of(info))
                        .equals(criteria),
                "Automatic stacking is non-overlapping");
        System.out.println(
                "PASS 2000 visible-group round trips, group autocomplete, label clearances and default panel stacking");
    }
}
