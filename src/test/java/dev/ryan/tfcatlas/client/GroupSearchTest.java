package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.SearchGroups;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dries007.tfc.common.blocks.rock.Rock;

/** Regression checks use each supported TFC version's own rocks and climate families. */
public final class GroupSearchTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var biomes =
                BiomeGroups.from(
                        Set.of("tfc:plains", "tfc:old_mountains", "tfc:old_mountains_lake"),
                        Set.of());
        check(
                biomes.groups().stream().noneMatch(g -> g.id().equals("karst")),
                "Hide unavailable biome families");
        check(
                biomes.resolve("", "mountains").size() == 2,
                "Include lake variants in the member list");
        var rocks = WorldSearchGroups.rocks();
        var choices =
                new HashSet<>(
                        WorldSearchGroups.rockNames().stream()
                                .map(n -> n.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'))
                                .toList());
        for (var group : rocks) {
            check(
                    choices.containsAll(group.members()),
                    "Expanded rock groups must remain valid autocomplete and search choices");
        }
        Set<String> all = new HashSet<>();
        rocks.forEach(g -> all.addAll(g.members()));
        check(
                all.size() == Rock.values().length,
                "Every rock in this TFC version belongs to a family");
        check(
                SearchGroups.resolve("granite", "igneous", rocks).contains("basalt"),
                "Typed names and group members form a union");
        check(
                SearchGroups.resolve("", "igneous", rocks).contains("granite"),
                "Igneous includes intrusive rocks");
        check(
                !SearchGroups.resolve("", "igneous", rocks).contains("limestone"),
                "Sedimentary rocks cannot enter igneous family");
        check(
                SearchGroups.summary("igneous", rocks).equals("Igneous rocks"),
                "Selected group uses its readable name");
        String editable = SearchGroups.editableList("limestone", "igneous", rocks);
        check(
                SearchGroups.resolve(editable, "", rocks)
                        .equals(SearchGroups.resolve("limestone", "igneous", rocks)),
                "Customising groups preserves the exact union");
        Profile typed = new Profile();
        for (String selection :
                List.of(
                        "Igneous intrusive rocks",
                        "Igneous rocks",
                        "Igneous rocks, basalt",
                        "Sedimentary rocks",
                        "Metamorphic rocks")) {
            typed.rocks = selection;
            WorldSearchGroups.requireKnownRocks(typed.query());
            check(
                    !typed.query().rocks().isEmpty(),
                    "Search-submit validation accepts full rock group labels");
        }
        for (String shortened : List.of("igneous", "sedimentary", "metamorphic")) {
            typed.rocks = shortened;
            boolean invalid = false;
            try {
                WorldSearchGroups.requireKnownRocks(typed.query());
            } catch (IllegalArgumentException expected) {
                invalid = true;
            }
            check(invalid, "New searches require the complete rock group label");
        }
        typed.rocks = "not a real rock";
        boolean rejected = false;
        try {
            WorldSearchGroups.requireKnownRocks(typed.query());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Unknown individual rock names still receive a useful validation error");
        Profile profile = new Profile();
        profile.rockGroups = "igneous";
        profile.biomeGroups = "mountains";
        profile.rocks = "limestone";
        profile.minRiver = 40;
        profile.maxOcean = 250;

        var query = profile.query(biomes);
        var saved = Profiles.preset(profile);
        var restored = Profiles.JSON.fromJson(saved, Profile.class);
        check(
                restored.query(biomes).equals(query),
                "Preset round-trip includes every group field and distance filter");
        profile.clearSearchSettings();
        check(
                profile.rockGroups.isBlank() && profile.biomeGroups.isBlank(),
                "Clear resets groups and typed names together");
        check(profile.query().equals(new Profile().query()), "Clear leaves an unrestricted query");
        Cell ocean = new Cell("tfc:rock/raw/granite", "tfc:ocean", 0, 200, 15, 0, 0, 0, 0);
        Cell coast = new Cell("tfc:rock/raw/granite", "tfc:plains", 0, 200, 15, 1, 1, 0, 1);
        check(
                new Profile().query().matchesRegion(ocean),
                "Ocean remains available without an ocean-distance filter");
        profile.maxOcean = 500;
        check(
                !profile.query().matchesRegion(ocean),
                "Maximum ocean distance must exclude ocean terrain");
        check(profile.query().matchesRegion(coast), "Coastal land is still eligible");
        profile.maxOcean = Float.MAX_VALUE;
        profile.minOcean = 50;
        check(
                !profile.query().matchesRegion(ocean),
                "Minimum ocean distance also excludes ocean terrain");
        profile.clearSearchSettings();
        profile.maxRiver = 500;
        check(
                profile.query().matchesRegion(ocean),
                "River distance alone does not imply the ocean-distance land filter");
        System.out.println(
                "PASS version-aware group membership, preset unions and land-only ocean proximity");
    }
}
