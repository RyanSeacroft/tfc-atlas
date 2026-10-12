package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.CustomGroupStore;
import dev.ryan.tfcatlas.core.SearchGroups;
import java.util.Set;

/** Exercise actual query/preset code with saved groups, including deletion after saving. */
public final class SavedGroupsTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        var field = SavedGroups.class.getDeclaredField("store");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            var store = new CustomGroupStore();
            var base =
                    BiomeGroups.from(
                            Set.of("tfc:shore", "tfc:plains", "tfc:old_mountains"), Set.of());
            store.put(
                    "biomes",
                    "Favourite spots",
                    Set.of("tfc:shore", "tfc:plains"),
                    base.groups(),
                    base.registered());
            var rocks = WorldSearchGroups.rocks();
            var available = SearchGroups.resolve("", "igneous, sedimentary, metamorphic", rocks);
            store.put("rocks", "Favourite stone", Set.of("granite", "basalt"), rocks, available);
            field.set(null, store);
            Profile profile = new Profile();
            profile.biomes = "Favourite spots";
            profile.rocks = "Favourite stone";
            profile.minRiver = 128;
            profile.maxOcean = 1024;
            profile.searchRockLayer = "Bottom";
            profile.radius = 16384;
            var catalog = base.withGroups(SavedGroups.catalog("biomes", base.groups()));
            var expected = profile.query(catalog);
            String preset = Profiles.preset(profile);
            check(
                    !preset.toLowerCase(java.util.Locale.ROOT).contains("favourite"),
                    "Saved presets store members, not custom references");
            check(
                    Profiles.JSON.fromJson(preset, Profile.class).query(catalog).equals(expected),
                    "Custom query and all unrelated filters survive preset save/load");
            store.remove("biomes", "Favourite spots");
            store.remove("rocks", "Favourite stone");
            check(
                    Profiles.JSON.fromJson(preset, Profile.class).query(base).equals(expected),
                    "Deleting custom groups cannot break the real preset loader");
            climate(store, field, profile, base);
            System.out.println(
                    "PASS actual custom-group queries and preset round trips after group deletion");
        } finally {
            field.set(null, previous);
        }
    }

    private static void climate(
            CustomGroupStore store,
            java.lang.reflect.Field field,
            Profile profile,
            BiomeGroups base)
            throws Exception {
        var climates = dev.ryan.tfcatlas.core.ClimateGroups.groups();
        var available =
                SearchGroups.resolve("", "tropical, arid, temperate, continental, polar", climates);
        store.put(
                "climateZones",
                "Favourite weather",
                Set.of("humid_tropical", "oceanic"),
                climates,
                available);
        field.set(null, store);
        profile.biomes = "plains";
        profile.rocks = "granite";
        profile.climateZones = "Favourite weather";
        var expected = profile.query(base);
        String preset = Profiles.preset(profile);
        store.remove("climateZones", "Favourite weather");
        check(
                Profiles.decode(com.google.gson.JsonParser.parseString(preset).getAsJsonObject())
                        .query(base)
                        .equals(expected),
                "Actual 1.21 climate preset survives custom-group deletion");
    }
}
