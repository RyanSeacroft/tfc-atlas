package dev.ryan.tfcatlas.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** A concrete selection lets individual choices override any overlapping group safely. */
public final class GroupSelection {
    private List<BiomeGroups.Group> groups;
    private final Set<String> selected = new TreeSet<>();
    private final Set<String> preferredGroups = new TreeSet<>();

    public GroupSelection(List<BiomeGroups.Group> groups, String groupNames, String names) {
        this.groups = List.copyOf(groups);
        preferredGroups.addAll(SearchQuery.names(groupNames));
        for (String token : SearchQuery.names(names)) {
            var group = SearchGroups.group(token, groups);
            if (group != null) {
                preferredGroups.add(group.id());
            }
        }
        Set<String> available = new TreeSet<>();
        groups.forEach(g -> available.addAll(g.members()));
        for (String name : SearchGroups.resolve(names, groupNames, groups)) {
            String canonical =
                    available.contains(name)
                            ? name
                            : available.contains("tfc:" + name) ? "tfc:" + name : name;
            String shortName =
                    name.substring(Math.max(name.lastIndexOf(':'), name.lastIndexOf('/')) + 1);
            if (!available.contains(canonical) && available.contains(shortName)) {
                canonical = shortName;
            }
            selected.add(canonical);
        }
    }

    public void catalog(List<BiomeGroups.Group> groups) {
        this.groups = List.copyOf(groups);
        pruneGroups();
    }

    public void prefer(BiomeGroups.Group group) {
        if (contains(group)) {
            preferredGroups.add(group.id());
        }
    }

    public boolean contains(String name) {
        return selected.contains(name);
    }

    public boolean contains(BiomeGroups.Group group) {
        return selected.containsAll(group.members());
    }

    public int count(BiomeGroups.Group group) {
        return (int) group.members().stream().filter(selected::contains).count();
    }

    public void toggle(String name) {
        if (!selected.remove(name)) {
            selected.add(name);
        }
        pruneGroups();
    }

    public void toggle(BiomeGroups.Group group) {
        if (contains(group)) {
            selected.removeAll(group.members());
            preferredGroups.remove(group.id());
        } else {
            selected.addAll(group.members());
            preferredGroups.add(group.id());
        }
        pruneGroups();
    }

    private void pruneGroups() {
        preferredGroups.removeIf(
                id -> groups.stream().noneMatch(g -> g.id().equals(id) && contains(g)));
    }

    public void clear() {
        selected.clear();
        preferredGroups.clear();
    }

    public Set<String> names() {
        return Set.copyOf(selected);
    }

    public record Saved(String groups, String names) {}

    public String text() {
        var saved = save();
        return SearchGroups.typedList(saved.names(), saved.groups(), groups);
    }

    public Saved save() {
        Set<String> remaining = new TreeSet<>(selected), ids = new TreeSet<>();
        // Keep explicitly chosen whole-group names, including overlapping groups.
        for (var group : groups) {
            if (preferredGroups.contains(group.id())
                    && contains(group)
                    && !group.members().isEmpty()) {
                ids.add(group.id());
                remaining.removeAll(group.members());
            }
        }
        // Compress any remaining whole families; partial groups stay as individual names.
        for (var group :
                groups.stream()
                        .sorted(
                                Comparator.comparingInt((BiomeGroups.Group g) -> g.members().size())
                                        .reversed()
                                        .thenComparing(BiomeGroups.Group::id))
                        .toList()) {
            if (!CustomGroupStore.custom(group)
                    && !group.members().isEmpty()
                    && remaining.containsAll(group.members())) {
                ids.add(group.id());
                remaining.removeAll(group.members());
            }
        }
        return new Saved(
                String.join(", ", ids),
                SearchGroups.editableList(String.join(", ", remaining), "", groups));
    }
}
