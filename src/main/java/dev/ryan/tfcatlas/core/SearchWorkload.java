package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conservative work estimate, not a hardware-dependent promise of a completion time. */
public record SearchWorkload(long samples, List<String> reasons) {
    public SearchWorkload {
        reasons = List.copyOf(reasons);
    }

    public boolean warn() {
        return !reasons.isEmpty();
    }

    public static SearchWorkload estimate(
            SearchQuery query, int radius, int resolution, double cachedFraction, boolean manual) {
        long samples = Sampling.estimatedSamples(radius, resolution);
        List<String> reasons = new ArrayList<>();
        if (manual && radius > resolution * 512L || samples > 2_000_000) {
            reasons.add("Very many sample locations");
        }
        if (query.dikes() && (samples >= 16_384 || radius >= 4096)) {
            reasons.add("Underground dike checks and 8-block outline refinement");
        }
        if (query.heightRestricted() && samples >= 65_536) {
            reasons.add("Surface-height prediction at matching locations");
        }
        if (resolution <= 128
                && samples * (1 - Math.max(0, Math.min(1, cachedFraction))) >= 262_144) {
            reasons.add("Much of this detailed terrain is not cached yet");
        }
        if (!query.dikes()
                && query.rocks().isEmpty()
                && query.biomes().isEmpty()
                && query.features().isEmpty()
                && query.rockTypes().isEmpty()
                && !query.heightRestricted()
                && query.minRain() <= 0
                && query.maxRain() >= 500
                && query.minTemp() <= -20
                && query.maxTemp() >= 30
                && samples >= 524_288) {
            reasons.add("Broad filters may highlight a large amount of terrain");
        }
        return new SearchWorkload(samples, reasons);
    }

    public String confirmation(int resolution) {
        return String.join(". ", reasons)
                + String.format(
                        Locale.ROOT,
                        ". Up to about %,d sample locations at %d-block spacing, plus any refinement. This search may take longer than usual or produce a heavy overlay. Try decreasing the search radius. Results appear live; you can cancel from Results. Continue?",
                        samples,
                        resolution);
    }

    public static String advice(
            int closest, long cells, long edges, int radius, int spacing, boolean automatic) {
        if (closest < 5) {
            String more =
                    automatic && radius >= Sampling.MAX_SEARCH_RADIUS
                            ? "Try broader filters"
                            : "Try increasing the search radius";
            if (cells > closest && spacing > 0) {
                more += " or reducing candidate separation";
            }
            return closest + " / 5 matches. " + more + ".";
        }
        if (edges >= 50_000 || cells >= 500_000) {
            return "Viewing slow? Decrease the radius or narrow the filters.";
        }
        return "";
    }
}
