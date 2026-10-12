package dev.ryan.tfcatlas.core;

import java.util.List;
import java.util.Set;

/** Optional local filters. Distances are horizontal blocks; area is sampled square blocks. */
public record SearchLimits(
        Set<String> soils,
        float minRiver,
        float maxRiver,
        float minOcean,
        float maxOcean,
        float minSlope,
        float maxSlope,
        float minInland,
        float maxInland,
        double minArea,
        double maxArea) {
    public static final SearchLimits ANY =
            new SearchLimits(
                    Set.of(), 0, Float.MAX_VALUE, 0, Float.MAX_VALUE, 0, 90, 0, Float.MAX_VALUE, 0);
    public static final int WATER_REACH = 8192;

    public SearchLimits(
            Set<String> soils,
            float minRiver,
            float maxRiver,
            float minOcean,
            float maxOcean,
            float minSlope,
            float maxSlope,
            float minInland,
            float maxInland,
            double minArea) {
        this(
                soils,
                minRiver,
                maxRiver,
                minOcean,
                maxOcean,
                minSlope,
                maxSlope,
                minInland,
                maxInland,
                minArea,
                Double.MAX_VALUE);
    }

    public boolean areaRestricted() {
        return minArea > 0 || maxArea != Double.MAX_VALUE;
    }

    public SearchLimits {
        soils = Set.copyOf(soils);
        range(minRiver, maxRiver, "River distance");
        range(minOcean, maxOcean, "Ocean distance");
        range(minSlope, maxSlope, "Steepness");
        range(minInland, maxInland, "Inlandness");
        if (maxSlope > 90
                || minRiver > WATER_REACH
                || minOcean > WATER_REACH
                || maxRiver != Float.MAX_VALUE && maxRiver > WATER_REACH
                || maxOcean != Float.MAX_VALUE && maxOcean > WATER_REACH) {
            throw new IllegalArgumentException("Steepness: 0–90°; water distance: 0–8192 blocks");
        }
        if (!Double.isFinite(minArea)
                || !Double.isFinite(maxArea)
                || minArea < 0
                || minArea > maxArea) {
            throw new IllegalArgumentException(
                    "Matching area: use nonnegative minimum ≤ maximum in square blocks");
        }
    }

    private static void range(float min, float max, String label) {
        if (!Float.isFinite(min) || !Float.isFinite(max) || min < 0 || min > max) {
            throw new IllegalArgumentException(label + ": use nonnegative minimum ≤ maximum");
        }
    }

    public boolean water() {
        return minRiver > 0
                || maxRiver != Float.MAX_VALUE
                || minOcean > 0
                || maxOcean != Float.MAX_VALUE;
    }

    /** Ocean proximity describes land near a coastline, never the ocean itself. */
    public boolean ocean() {
        return minOcean > 0 || maxOcean != Float.MAX_VALUE;
    }

    public boolean slope() {
        return minSlope > 0 || maxSlope < 90;
    }

    public boolean matchesWater(double river, double ocean) {
        return accepts(river, minRiver, maxRiver) && accepts(ocean, minOcean, maxOcean);
    }

    private static boolean accepts(double value, float min, float max) {
        // Infinity means no water found within the documented lookup reach.
        return !Double.isNaN(value) && value >= min && (max == Float.MAX_VALUE || value <= max);
    }

    public void describe(List<String> lines) {
        if (!soils.isEmpty()) {
            lines.add("Likely soil: " + String.join(", ", soils.stream().sorted().toList()));
        }
        if (minRiver > 0 || maxRiver != Float.MAX_VALUE) {
            lines.add("River bank ≈ " + bounds(minRiver, maxRiver) + " blocks");
        }
        if (minOcean > 0 || maxOcean != Float.MAX_VALUE) {
            lines.add(
                    "Ocean edge ≈ "
                            + bounds(minOcean, maxOcean)
                            + " blocks (128-block coast grid)");
        }
        if (slope()) {
            lines.add("Steepness ≈ " + bounds(minSlope, maxSlope) + "° over 32 blocks");
        }
        if (minInland > 0 || maxInland != Float.MAX_VALUE) {
            lines.add("Inlandness index: " + bounds(minInland, maxInland));
        }
        if (areaRestricted()) {
            String minimum = String.format(java.util.Locale.ROOT, "%.0f", minArea);
            String range =
                    maxArea == Double.MAX_VALUE
                            ? minimum + "+"
                            : minimum + "–" + String.format(java.util.Locale.ROOT, "%.0f", maxArea);
            lines.add("Matching area: " + range + " blocks²");
        }
    }

    private static String bounds(float min, float max) {
        return String.format(java.util.Locale.ROOT, "%.0f", min)
                + (max == Float.MAX_VALUE
                        ? "+"
                        : "–" + String.format(java.util.Locale.ROOT, "%.0f", max));
    }
}
