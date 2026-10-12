package dev.ryan.tfcatlas.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Captures the submitted search, independently of later settings edits. */
public record SearchDetails(
        SearchQuery query,
        int x,
        int z,
        int radius,
        int limit,
        int spacing,
        int resolution,
        java.util.Map<String, String> selections) {
    public SearchDetails {
        selections = java.util.Map.copyOf(selections);
    }

    public SearchDetails(
            SearchQuery q, int x, int z, int radius, int limit, int spacing, int resolution) {
        this(q, x, z, radius, limit, spacing, resolution, java.util.Map.of());
    }

    public SearchDetails(SearchQuery q, int x, int z, int radius, int limit, int spacing) {
        this(q, x, z, radius, limit, spacing, 128);
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (!query.rocks().isEmpty()) {
            lines.add("Rocks: " + selected("rocks", names(query.rocks())));
        }
        if (!query.biomes().isEmpty()) {
            lines.add("Biomes: " + selected("biomes", biomeSummary(query.biomes())));
        }
        if (query.layered()) {
            lines.add("Rock layer: " + query.rockLayer());
        }
        lines.add(
                "Sampling: "
                        + resolution
                        + " blocks"
                        + (query.dikes() ? " · 8-block dike outlines" : ""));
        if (query.dikes()) {
            lines.add("Dikes: includes buried intrusions; not just surface exposures");
        }
        if (!query.rockTypes().isEmpty()) {
            lines.add(
                    "Surface rock region: "
                            + query.rockTypes().stream()
                                    .sorted()
                                    .map(t -> Cell.TYPE_NAMES[t])
                                    .collect(Collectors.joining(", ")));
        }
        if (!query.features().isEmpty()) {
            lines.add("Terrain: " + names(query.features()));
        }
        List<String> climate = new ArrayList<>();
        if (query.minRain() != -Float.MAX_VALUE || query.maxRain() != Float.MAX_VALUE) {
            climate.add("Rain: " + range(query.minRain(), query.maxRain()) + " mm");
        }
        if (query.minTemp() != -Float.MAX_VALUE || query.maxTemp() != Float.MAX_VALUE) {
            climate.add("Temp: " + range(query.minTemp(), query.maxTemp()) + " °C");
        }
        if (!climate.isEmpty()) {
            lines.add(String.join(" · ", climate));
        }
        if (query.heightRestricted()) {
            lines.add("Surface Y: " + query.minY() + "–" + query.maxY() + " (predicted)");
        }
        lines.add("Centre: X " + x + ", Z " + z);
        query.limits().describe(lines);
        lines.add("Search radius: " + radius + " blocks");
        lines.add("Closest matches: up to " + limit + " · Min distance: " + spacing + " blocks");
        return List.copyOf(lines);
    }

    private String selected(String key, String fallback) {
        String label = selections.get(key);
        return label == null || label.isBlank() ? fallback : label;
    }

    private static String biomeSummary(Set<String> values) {
        if (values.size() <= 3) {
            return names(values);
        }
        return values.stream().map(Cell::label).sorted().limit(3).collect(Collectors.joining(", "))
                + " + "
                + (values.size() - 3)
                + " more";
    }

    private static String names(Set<String> values) {
        return values.isEmpty()
                ? "Any"
                : values.stream().map(Cell::label).sorted().collect(Collectors.joining(", "));
    }

    private static String range(float min, float max) {
        if (min == -Float.MAX_VALUE && max == Float.MAX_VALUE) {
            return "Any";
        }
        if (min == -Float.MAX_VALUE) {
            return "≤ " + number(max);
        }
        if (max == Float.MAX_VALUE) {
            return "≥ " + number(min);
        }
        return number(min) + "–" + number(max);
    }

    private static String number(float value) {
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
}
