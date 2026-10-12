package dev.ryan.tfcatlas.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Small, version-independent named selections, saved separately from world profiles. */
public final class CustomGroupStore {
    public record Entry(String field, String name, Set<String> members) {
        public Entry {
            members = java.util.Collections.unmodifiableSet(new TreeSet<>(members));
        }
    }

    private final Map<String, Entry> entries = new TreeMap<>();

    private static String key(String field, String name) {
        return field.toLowerCase(java.util.Locale.ROOT)
                + ":"
                + name.strip()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace('_', ' ')
                        .replaceAll("\\s+", " ");
    }

    public static boolean custom(BiomeGroups.Group group) {
        return group.id().startsWith("custom:");
    }

    public List<BiomeGroups.Group> groups(String field) {
        return entries.values().stream()
                .filter(entry -> entry.field().equals(field))
                .map(
                        entry ->
                                new BiomeGroups.Group(
                                        "custom:" + key(field, entry.name()).replace(' ', '_'),
                                        entry.name(),
                                        entry.members()))
                .sorted(
                        java.util.Comparator.comparing(
                                BiomeGroups.Group::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<BiomeGroups.Group> catalog(
            String field, List<BiomeGroups.Group> builtins, Set<String> available) {
        List<BiomeGroups.Group> result = new ArrayList<>(builtins);
        for (var group : groups(field)) {
            Set<String> members = new TreeSet<>(group.members());
            members.retainAll(available);
            // Keep the name visible if its members aren't installed in this world.
            result.add(new BiomeGroups.Group(group.id(), group.label(), members));
        }
        return List.copyOf(result);
    }

    public void put(
            String field,
            String name,
            Set<String> members,
            List<BiomeGroups.Group> builtins,
            Set<String> available) {
        name = name.strip().replaceAll("\\s+", " ");
        if (!Set.of("biomes", "rocks", "climateZones").contains(field)) {
            throw new IllegalArgumentException("Unknown group type");
        }
        if (name.isEmpty() || name.length() > 40 || name.matches(".*[,\\r\\n:/].*")) {
            throw new IllegalArgumentException(
                    "Use 1–40 characters, without commas, slashes or colons.");
        }
        final String chosenName = name;
        if (SearchGroups.group(name, builtins) != null
                || builtins.stream()
                        .anyMatch(
                                g ->
                                        SearchGroups.aliases(g).stream()
                                                .anyMatch(
                                                        alias ->
                                                                alias.equalsIgnoreCase(
                                                                        chosenName
                                                                                .strip()
                                                                                .replace(
                                                                                        ' ',
                                                                                        '_'))))) {
            throw new IllegalArgumentException("Choose a name different from a built-in group.");
        }
        String token = name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (available.stream()
                .anyMatch(
                        value ->
                                value.equalsIgnoreCase(token)
                                        || value.equalsIgnoreCase("tfc:" + token))) {
            throw new IllegalArgumentException("Choose a name different from an individual entry.");
        }
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Select at least one entry first.");
        }
        if (!available.containsAll(members)) {
            throw new IllegalArgumentException(
                    "This selection includes entries unavailable in this world.");
        }
        entries.put(key(field, name), new Entry(field, name, members));
    }

    public void remove(String field, String name) {
        entries.remove(key(field, name));
    }

    /** Presets retain concrete members even after a custom group is changed or deleted. */
    public String snapshot(String field, String names) {
        var groups = groups(field);
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String token : SearchQuery.names(names)) {
            var group = SearchGroups.group(token, groups);
            if (group == null) {
                result.add(token.replace('_', ' '));
            } else {
                group.members()
                        .forEach(
                                member ->
                                        result.add(
                                                (member.startsWith("tfc:")
                                                                ? member.substring(4)
                                                                : member)
                                                        .replace('_', ' ')));
            }
        }
        return String.join(", ", result);
    }

    public static CustomGroupStore read(Path file) throws IOException {
        var store = new CustomGroupStore();
        if (!Files.exists(file)) {
            return store;
        }
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for (var value : json) {
            var item = value.getAsJsonObject();
            String field = item.get("field").getAsString(), name = item.get("name").getAsString();
            Set<String> members = new TreeSet<>();
            item.getAsJsonArray("members").forEach(member -> members.add(member.getAsString()));
            // Names already validated by the save UI; don't expand nested groups on load.
            if (Set.of("biomes", "rocks", "climateZones").contains(field)
                    && !name.isBlank()
                    && !members.isEmpty()) {
                store.entries.put(key(field, name), new Entry(field, name, members));
            }
        }
        return store;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(file.toAbsolutePath().getParent(), "groups-", ".tmp");
        try {
            Files.writeString(
                    temp, new GsonBuilder().setPrettyPrinting().create().toJson(entries.values()));
            try {
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
