package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import dev.ryan.tfcatlas.core.CustomGroupStore;
import java.util.List;
import java.util.Set;

/** Global saved groups survive search resets and are separate for each search field. */
final class SavedGroups {
    private static CustomGroupStore store = new CustomGroupStore();
    private static boolean loaded;

    private SavedGroups() {}

    static void load() {
        if (loaded) {
            return;
        }
        try {
            store = CustomGroupStore.read(Profiles.root().resolve("custom-groups.json"));
            loaded = true;
        } catch (Exception error) {
            AtlasClient.message("Custom groups could not be read: " + error.getMessage());
        }
    }

    static List<BiomeGroups.Group> catalog(String field, List<BiomeGroups.Group> builtins) {
        var available = new java.util.HashSet<String>();
        builtins.forEach(group -> available.addAll(group.members()));
        return store.catalog(field, builtins, available);
    }

    static String snapshot(String field, String names) {
        return store.snapshot(field, names);
    }

    static void save(
            String field, String name, Set<String> members, List<BiomeGroups.Group> builtins)
            throws java.io.IOException {
        if (field.equals("climateZones")
                && dev.ryan.tfcatlas.core.ClimateZones.CODES.stream()
                        .anyMatch(code -> code.equalsIgnoreCase(name.strip()))) {
            throw new IllegalArgumentException("Choose a name different from a climate-zone code.");
        }
        var available = new java.util.HashSet<String>();
        builtins.forEach(group -> available.addAll(group.members()));
        // Write a candidate first; a failed write must not leave an unsaved group active.
        var next = CustomGroupStore.read(Profiles.root().resolve("custom-groups.json"));
        next.put(field, name, members, builtins, available);
        next.write(Profiles.root().resolve("custom-groups.json"));
        store = next;
    }

    static void delete(String field, String name) throws java.io.IOException {
        var next = CustomGroupStore.read(Profiles.root().resolve("custom-groups.json"));
        next.remove(field, name);
        next.write(Profiles.root().resolve("custom-groups.json"));
        store = next;
    }
}
