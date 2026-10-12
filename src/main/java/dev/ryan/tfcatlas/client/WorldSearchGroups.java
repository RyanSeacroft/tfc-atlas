package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BiomeGroups;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.dries007.tfc.common.blocks.rock.Rock;

/** Rock families use this installed TFC version's own display categories. */
final class WorldSearchGroups {
    private WorldSearchGroups() {}

    /** Include version-specific rocks even when the bundled colour palette has no entry yet. */
    static List<String> rockNames() {
        return java.util.stream.Stream.concat(
                        dev.ryan.tfcatlas.core.Layer.categories(
                                dev.ryan.tfcatlas.core.Layer.ROCKS, false, java.util.Map.of())
                                .keySet()
                                .stream(),
                        java.util.Arrays.stream(Rock.values())
                                .map(rock -> dev.ryan.tfcatlas.core.Cell.label(rock.name())))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Validate the expanded query, so a typed family or custom group is not mistaken for a rock.
     */
    static void requireKnownRocks(dev.ryan.tfcatlas.core.SearchQuery query) {
        Set<String> known = new java.util.HashSet<>();
        rockNames().forEach(name -> known.add(name.toLowerCase(Locale.ROOT).replace(' ', '_')));
        for (String name : query.rocks()) {
            String shortName =
                    name.substring(Math.max(name.lastIndexOf(':'), name.lastIndexOf('/')) + 1);
            if (!known.contains(shortName)) {
                throw new IllegalArgumentException(
                        "Unknown rock: " + name + " (see the colour key)");
            }
        }
    }

    static List<BiomeGroups.Group> rocks() {
        var families = new TreeMap<String, Set<String>>();
        Set<String> igneous = new TreeSet<>();
        for (Rock rock : Rock.values()) {
            String category = rock.displayCategory().category().name().toLowerCase(Locale.ROOT);
            String name = rock.name().toLowerCase(Locale.ROOT);
            families.computeIfAbsent(category, ignored -> new TreeSet<>()).add(name);
            if (category.startsWith("igneous")) {
                igneous.add(name);
            }
        }
        List<BiomeGroups.Group> groups = new ArrayList<>();
        if (!igneous.isEmpty()) {
            groups.add(new BiomeGroups.Group("igneous", "Igneous rocks", igneous));
        }
        families.forEach(
                (id, members) ->
                        groups.add(
                                new BiomeGroups.Group(
                                        id,
                                        dev.ryan.tfcatlas.core.Cell.label(id) + " rocks",
                                        members)));
        return SavedGroups.catalog("rocks", groups);
    }
}
