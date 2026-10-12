package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.Layer;
import java.util.Set;

/** Biome shortcuts remain optional, version-aware, editable and safe to save. */
public final class BiomeGroupsTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void run() {
        Set<String> registered =
                Set.of(
                        "tfc:plains",
                        "tfc:old_mountains",
                        "tfc:old_mountains_lake",
                        "tfc:plateau",
                        "tfc:plateau_lake",
                        "tfc:tower_karst_hills",
                        "tfc:extinct_shield_volcano",
                        "tfc:deep_ocean",
                        "tfc:oceanic_mountains",
                        "addon:kaolin_hills",
                        "minecraft:mountains");
        var catalog =
                BiomeGroups.from(
                        registered, Set.of("tfc:plateau", "addon:kaolin_hills", "removed:biome"));
        check(catalog.resolve("", "").isEmpty(), "Fresh search stays unrestricted");
        check(
                catalog.resolve("", "kaolin").equals(Set.of("tfc:plateau", "addon:kaolin_hills")),
                "Kaolin uses the tag, including pack additions, filtered to installed biomes");
        check(
                catalog.resolve("", "mountains")
                        .equals(
                                Set.of(
                                        "tfc:old_mountains",
                                        "tfc:old_mountains_lake",
                                        "tfc:oceanic_mountains")),
                "Families include actual TFC variants, without unrelated vanilla names");
        check(
                catalog.resolve("plains, plateau", "plateaus, kaolin")
                        .equals(
                                Set.of(
                                        "tfc:plains",
                                        "tfc:plateau",
                                        "tfc:plateau_lake",
                                        "addon:kaolin_hills")),
                "Explicit biomes and groups form a deduplicated union");
        for (String group : Set.of("karst", "volcanic", "oceans")) {
            check(catalog.resolve("", group).size() == 1, "Recognised family: " + group);
        }
        String expanded = catalog.editableList("plains", "plateaus, kaolin");
        check(
                catalog.resolve(expanded, "").equals(catalog.resolve("plains", "plateaus, kaolin")),
                "Edit as list preserves namespace and selection");
        rejects(() -> catalog.resolve("", "typo"), "Unknown group cannot become unrestricted");
        rejects(
                () -> BiomeGroups.from(registered, Set.of()).resolve("", "kaolin"),
                "Unavailable tag must not silently become an unrestricted search");
        rejects(() -> catalog.resolve("missing biome", ""), "Invalid manual name is rejected");

        Profile profile = new Profile();
        profile.biomeGroups = "kaolin, plateaus";
        profile.biomes = "plains";
        profile.minRiver = 20;
        profile.maxRiver = 200;
        profile.minOcean = 50;
        profile.maxOcean = 500;
        profile.minSlope = 2;
        profile.maxSlope = 15;
        profile.minInland = 3;
        profile.maxInland = 10;
        profile.minArea = 65536;
        profile.minY = 90;
        profile.maxY = 200;
        profile.seed = "123456789";
        String saved = Profiles.preset(profile);
        Profile restored = Profiles.JSON.fromJson(saved, Profile.class);
        check(
                profile.query(catalog).equals(restored.query(catalog)),
                "Preset round-trip preserves all new filters and resolved group membership");
        check(
                !saved.contains("seed") && !saved.contains("savedSearches"),
                "Saved searches must not contain a seed or nested presets");
        rejects(profile::query, "Unresolved groups cannot be accidentally ignored");
        profile.showSearchLayer();
        check(profile.selected() == Layer.BIOMES, "Biome groups choose the biome map");
        profile.clearSearchSettings();
        check(
                profile.biomeGroups.isEmpty() && profile.biomes.isEmpty(),
                "Clear removes both groups and explicit biomes");
        check(profile.query().equals(new Profile().query()), "Clear resets the complete search");
        System.out.println("PASS biome groups and preset persistence: " + checks + " checks");
    }
}
