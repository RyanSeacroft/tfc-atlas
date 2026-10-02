package dev.ryan.tfcatlas.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/** Traverse the installed world's rock graph exactly, including repeating bottom strata. */
public final class RockPossibilities {
    public static Map<RockLayer, Set<String>> read(
            JsonObject data, Function<JsonElement, String> rawRock) {
        Map<String, String> rocks = new HashMap<>();
        data.getAsJsonObject("rocks")
                .entrySet()
                .forEach(e -> rocks.put(e.getKey(), rawRock.apply(e.getValue())));
        Map<String, Map<String, String>> graph = new HashMap<>();
        Map<String, String> bottom = new LinkedHashMap<>();
        data.getAsJsonArray("bottom").forEach(e -> bottom.put(e.getAsString(), "bottom"));
        graph.put("bottom", bottom);
        for (JsonElement e : data.getAsJsonArray("layers")) {
            JsonObject layer = e.getAsJsonObject();
            Map<String, String> edges = new LinkedHashMap<>();
            layer.getAsJsonObject("layers")
                    .entrySet()
                    .forEach(edge -> edges.put(edge.getKey(), edge.getValue().getAsString()));
            graph.put(layer.get("id").getAsString(), edges);
        }
        Set<String> active = new HashSet<>();
        for (String root : List.of("ocean_floor", "land", "volcanic", "uplift")) {
            for (JsonElement e : data.getAsJsonArray(root)) {
                active.add(e.getAsString());
            }
        }
        Map<RockLayer, Set<String>> result = new EnumMap<>(RockLayer.class);
        for (RockLayer depth : RockLayer.values()) {
            Set<String> found = new TreeSet<>(), next = new HashSet<>();
            for (String id : active) {
                for (var edge : graph.get(id).entrySet()) {
                    found.add(Objects.requireNonNull(rocks.get(edge.getKey())));
                    next.add(edge.getValue());
                }
            }
            result.put(depth, Set.copyOf(found));
            active = next;
        }
        return Map.copyOf(result);
    }

    public static List<Layer.LegendEntry> legend(
            Set<String> ids, boolean accessible, Map<String, Integer> colours, boolean continents) {
        Map<String, Layer.LegendEntry> entries = new TreeMap<>();
        for (String id : ids) {
            String name = Cell.label(id);
            entries.put(
                    name,
                    new Layer.LegendEntry(
                            name,
                            name,
                            List.of(
                                    colours.getOrDefault(
                                            "ROCKS:" + name, Layer.category(id, accessible)))));
        }
        List<Layer.LegendEntry> result = new ArrayList<>();
        if (continents) {
            result.add(
                    new Layer.LegendEntry(
                            "Ocean",
                            "Ocean",
                            List.of(colours.getOrDefault("ROCKS:Ocean", 0x142536))));
        }
        result.addAll(entries.values());
        return List.copyOf(result);
    }
}
