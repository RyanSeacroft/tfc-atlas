package dev.ryan.tfcatlas.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Captures the submitted search, independently of later settings edits. */
public record SearchDetails(
        SearchQuery query, int x, int z, int radius, int limit, int spacing, int resolution) {
    public SearchDetails(SearchQuery q, int x, int z, int radius, int limit, int spacing) {
        this(q, x, z, radius, limit, spacing, 128);
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("Rocks: " + names(query.rocks()));
        lines.add("Biomes: " + names(query.biomes()));
        lines.add("Rock layer: " + query.rockLayer());
        lines.add(
                "Sampling: "
                        + resolution
                        + " blocks"
                        + (query.dikes() ? " · 8-block dike outlines" : ""));
        if (query.dikes()) {
            lines.add("Dikes: includes buried intrusions; not just surface exposures");
        }
        lines.add(
                "Surface rock region: "
                        + (query.rockTypes().isEmpty()
                                ? "Any"
                                : query.rockTypes().stream()
                                        .sorted()
                                        .map(t -> Cell.TYPE_NAMES[t])
                                        .collect(Collectors.joining(", "))));
        lines.add("Terrain: " + names(query.features()));
        lines.add(
                "Rain: "
                        + range(query.minRain(), query.maxRain())
                        + " mm · Temp: "
                        + range(query.minTemp(), query.maxTemp())
                        + " °C");
        if (query.heightRestricted()) {
            lines.add("Surface Y: " + query.minY() + "–" + query.maxY() + " (predicted)");
        }
        lines.add("Centre: X " + x + ", Z " + z);
        lines.add("Search radius: " + radius + " blocks");
        lines.add("Closest matches: up to " + limit + " · Min distance: " + spacing + " blocks");
        return List.copyOf(lines);
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
