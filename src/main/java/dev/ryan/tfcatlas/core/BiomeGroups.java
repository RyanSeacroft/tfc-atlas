package dev.ryan.tfcatlas.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Version-specific biome families, restricted to the current world's registry. */
public final class BiomeGroups {
    public record Group(String id, String label, Set<String> members) {
        public Group {
            members = Set.copyOf(members);
        }
    }

    // Audited against this branch's TFC registry, biome tags and world-generation documentation.
    private static final Map<String, Set<String>> BUNDLED = readFamilies();
    private final Set<String> registered;
    private final List<Group> groups;

    private BiomeGroups(Set<String> registered, List<Group> groups) {
        this.registered = Set.copyOf(registered);
        this.groups =
                groups.stream()
                        .filter(g -> !g.members().isEmpty())
                        .sorted(
                                java.util.Comparator.comparing(
                                        Group::label, String.CASE_INSENSITIVE_ORDER))
                        .toList();
    }

    private static Map<String, Set<String>> readFamilies() {
        try (var stream =
                BiomeGroups.class.getResourceAsStream("/assets/tfcatlas/biome-groups.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing biome family data");
            }
            Map<String, Set<String>> families = new TreeMap<>();
            var json =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            json.entrySet()
                    .forEach(
                            entry -> {
                                Set<String> members = new TreeSet<>();
                                entry.getValue()
                                        .getAsJsonArray()
                                        .forEach(value -> members.add(value.getAsString()));
                                families.put(entry.getKey(), Set.copyOf(members));
                            });
            return Map.copyOf(families);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not read biome families", error);
        }
    }

    public static BiomeGroups from(Set<String> registered, Set<String> kaolinTag) {
        List<Group> groups = new ArrayList<>();
        Set<String> kaolin = new TreeSet<>(kaolinTag);
        kaolin.retainAll(registered);
        groups.add(new Group("kaolin", "Kaolinite candidate biomes", kaolin));
        family(
                groups,
                registered,
                "hills",
                "Hill biomes",
                id -> token(id, "hill") || token(id, "hills"));
        family(
                groups,
                registered,
                "plains",
                "Plains & lowlands",
                id -> token(id, "plains") || token(id, "lowlands") || token(id, "flatlands"));
        family(groups, registered, "lakes", "Lake biomes", id -> token(id, "lake"));
        family(
                groups,
                registered,
                "coasts",
                "Coastal biomes",
                id ->
                        token(id, "shore")
                                || token(id, "shores")
                                || token(id, "coast")
                                || token(id, "coastal"));
        family(
                groups,
                registered,
                "mountains",
                "Mountain biomes",
                id -> token(id, "mountain") || token(id, "mountains"));
        family(
                groups,
                registered,
                "plateaus",
                "Plateau biomes",
                id -> token(id, "plateau") || token(id, "plateaus"));
        family(
                groups,
                registered,
                "karst",
                "Karst biomes",
                id ->
                        token(id, "karst")
                                || token(id, "doline")
                                || token(id, "cenote")
                                || token(id, "shilin")
                                || token(id, "burren"));
        family(
                groups,
                registered,
                "volcanic",
                "Volcanic biomes",
                id -> token(id, "volcanic") || token(id, "volcano") || token(id, "tuyas"));
        family(groups, registered, "oceans", "Ocean biomes", id -> token(id, "ocean"));
        family(groups, registered, "rivers", "River biomes", id -> token(id, "river"));
        family(groups, registered, "badlands", "Badlands biomes", id -> token(id, "badlands"));
        family(
                groups,
                registered,
                "canyons",
                "Canyon biomes",
                id -> token(id, "canyon") || token(id, "canyons"));
        family(groups, registered, "highlands", "Highland biomes", id -> token(id, "highlands"));
        family(groups, registered, "dry", "Dry biomes", id -> false);
        family(
                groups,
                registered,
                "glacial",
                "Glacial biomes",
                id ->
                        token(id, "ice")
                                || token(id, "glaciated")
                                || token(id, "glacially")
                                || token(id, "subglacial"));
        family(
                groups,
                registered,
                "islands",
                "Island biomes",
                id -> token(id, "island") || token(id, "islands") || token(id, "atolls"));
        family(groups, registered, "wetlands", "Wetland biomes", id -> token(id, "marsh"));
        family(groups, registered, "rifts", "Rift biomes", id -> token(id, "rift"));
        Set<String> other = new TreeSet<>(registered);
        other.removeIf(id -> id.startsWith("minecraft:"));
        groups.forEach(g -> other.removeAll(g.members()));
        groups.add(new Group("other", "Other biomes", other));
        return new BiomeGroups(registered, groups);
    }

    /** Add user-defined groups without changing the registered biome set. */
    public BiomeGroups withGroups(List<Group> catalog) {
        return new BiomeGroups(registered, catalog);
    }

    private static void family(
            List<Group> groups,
            Set<String> registered,
            String id,
            String label,
            Predicate<String> matches) {
        Set<String> members = new TreeSet<>();
        for (String biome : registered) {
            if (biome.startsWith("tfc:")
                    && (BUNDLED.getOrDefault(id, Set.of()).contains(biome)
                            || matches.test(biome.substring(4)))) {
                members.add(biome);
            }
        }
        groups.add(new Group(id, label, members));
    }

    private static boolean token(String path, String token) {
        return List.of(path.split("_")).contains(token);
    }

    public List<Group> groups() {
        return groups;
    }

    public Set<String> registered() {
        return registered;
    }

    /** Groups and explicit names are ORed; other search fields still use AND. */
    public Set<String> resolve(String explicitNames, String selectedGroups) {
        Set<String> result = new TreeSet<>();
        Set<String> legacyGroups = new TreeSet<>(SearchQuery.names(selectedGroups));
        if (legacyGroups.remove("all")) {
            registered.stream().filter(id -> !id.startsWith("minecraft:")).forEach(result::add);
        }
        for (String name :
                SearchGroups.resolve(explicitNames, String.join(",", legacyGroups), groups)) {
            String id = name.contains(":") ? name : "tfc:" + name;
            if (!registered.contains(id)) {
                throw new IllegalArgumentException("Unknown biome in this world: " + name);
            }
            result.add(id);
        }
        return Set.copyOf(result);
    }

    public String editableList(String explicitNames, String selectedGroups) {
        return resolve(explicitNames, selectedGroups).stream()
                .sorted()
                .map(id -> id.startsWith("tfc:") ? id.substring(4).replace('_', ' ') : id)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
