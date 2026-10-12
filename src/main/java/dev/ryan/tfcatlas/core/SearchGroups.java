package dev.ryan.tfcatlas.core;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Readable group names and individual names share one search field. */
public final class SearchGroups {
    private SearchGroups() {}

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** Group tokens use the full visible label; individual names remain unambiguous. */
    public static Set<String> aliases(BiomeGroups.Group group) {
        return Set.of(normalized(group.label()));
    }

    public static BiomeGroups.Group group(String token, List<BiomeGroups.Group> groups) {
        String key = normalized(token);
        return groups.stream()
                .filter(group -> normalized(group.label()).equals(key))
                .findFirst()
                .orElse(null);
    }

    /** Legacy separate group IDs remain readable; new UI writes only the combined visible field. */
    public static Set<String> resolve(
            String names, String selection, List<BiomeGroups.Group> groups) {
        Set<String> result = new TreeSet<>();
        for (String token : SearchQuery.names(names)) {
            var group = group(token, groups);
            if (group == null) {
                result.add(token);
            } else {
                if (group.members().isEmpty()) {
                    throw new IllegalArgumentException("Unavailable group: " + group.label());
                }
                result.addAll(group.members());
            }
        }
        for (String id : SearchQuery.names(selection)) {
            var group =
                    groups.stream()
                            .filter(g -> g.id().equals(id))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Unavailable group: " + id));
            if (group.members().isEmpty()) {
                throw new IllegalArgumentException("Unavailable group: " + group.label());
            }
            result.addAll(group.members());
        }
        return Set.copyOf(result);
    }

    public static List<String> suggestions(
            Collection<String> names, List<BiomeGroups.Group> groups) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        result.addAll(names);
        groups.forEach(group -> result.add(group.label()));
        return List.copyOf(result);
    }

    public static String summary(String selection, List<BiomeGroups.Group> groups) {
        return SearchQuery.names(selection).stream()
                .map(
                        id ->
                                groups.stream()
                                        .filter(g -> g.id().equals(id))
                                        .map(BiomeGroups.Group::label)
                                        .findFirst()
                                        .orElse(id))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }

    /** Display whole groups without expanding them into a long list of names. */
    public static String typedList(String names, String selection, List<BiomeGroups.Group> groups) {
        Set<String> labels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : SearchQuery.names(names)) {
            var group = group(name, groups);
            labels.add(group == null ? readable(name) : group.label());
        }
        for (String id : SearchQuery.names(selection)) {
            labels.add(
                    groups.stream()
                            .filter(g -> g.id().equals(id))
                            .map(BiomeGroups.Group::label)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Unavailable group: " + id)));
        }
        return String.join(", ", labels);
    }

    private static String readable(String id) {
        return (id.startsWith("tfc:") ? id.substring(4) : id).replace('_', ' ');
    }

    public static String editableList(
            String names, String selection, List<BiomeGroups.Group> groups) {
        return resolve(names, selection, groups).stream()
                .map(SearchGroups::readable)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
    }
}
