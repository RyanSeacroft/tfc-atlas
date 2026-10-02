package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.DoubleFunction;

public enum Layer {
    ROCKS("Rocks"),
    ROCK_TYPES("Rock regions"),
    BIOMES("Biomes"),
    RAINFALL("Rainfall"),
    TEMPERATURE("Temperature"),
    RIVERS("Rivers & mountains"),
    ALTITUDE("Biome altitude"),
    INLAND("Inlandness"),
    CLIMATE_ZONES("Climate zones (sea level)"),
    RAIN_VARIANCE("Rainfall seasonality"),
    JANUARY_RAIN("January rainfall"),
    JULY_RAIN("July rainfall"),
    GROUNDWATER("Groundwater potential");
    public final String label;

    Layer(String s) {
        label = s;
    }

    private static final Properties COLORS = new Properties();

    static {
        try (var in = Layer.class.getResourceAsStream("/assets/tfcatlas/colors.properties")) {
            if (in != null) {
                COLORS.load(in);
            }
        } catch (Exception ignored) {
        }
    }

    public String value(Cell c) {
        return switch (this) {
            case CLIMATE_ZONES -> ClimateZones.label(c.climateZone());
            case RAIN_VARIANCE ->
                    String.format(
                            Locale.ROOT, "%+.0f%% (positive: wet July)", 100 * c.rainVariance());
            case JANUARY_RAIN -> String.format(Locale.ROOT, "%.1f mm", c.januaryRain());
            case JULY_RAIN -> String.format(Locale.ROOT, "%.1f mm", c.julyRain());
            case GROUNDWATER ->
                    String.format(Locale.ROOT, "%.1f mm potential", c.groundwaterPotential());
            case ROCKS -> Cell.label(c.rock());
            case ROCK_TYPES -> c.typeName();
            case BIOMES -> Cell.label(c.biome());
            case RAINFALL -> String.format(Locale.ROOT, "%.1f mm", c.rain());
            case TEMPERATURE -> String.format(Locale.ROOT, "%.1f °C", c.temperature());
            case ALTITUDE -> altitudeBand(c);
            case INLAND -> inlandBand(c);
            case RIVERS ->
                    c.river()
                            ? "River"
                            : c.lake()
                                    ? "Lake"
                                    : c.mountain() ? "Mountain" : c.land() ? "Land" : "Ocean";
        };
    }

    public static String altitudeBand(Cell c) {
        return !c.land()
                ? "Ocean"
                : c.altitude() < 0
                        ? "Near island"
                        : c.altitude() < 4
                                ? "Lowland"
                                : c.altitude() < 8
                                        ? "Midland"
                                        : c.altitude() < 12 ? "Highland" : "Mountains";
    }

    public static String inlandBand(Cell c) {
        return !c.land()
                ? "Ocean"
                : c.inland() < 4
                        ? "Coastal"
                        : c.inland() < 8
                                ? "Near inland"
                                : c.inland() < 12
                                        ? "Interior"
                                        : c.inland() < 20 ? "Far inland" : "Deep inland";
    }

    /** A shared key makes range edits, swatches and map colours describe the same data. */
    public String colourKey(Cell c) {
        return switch (this) {
            case CLIMATE_ZONES -> c.climateZone();
            case RAIN_VARIANCE ->
                    String.format(Locale.ROOT, "%+.0f%%", Math.floor(c.rainVariance() * 4) * 25);
            case JANUARY_RAIN, JULY_RAIN, GROUNDWATER -> {
                float n =
                        this == JANUARY_RAIN
                                ? c.januaryRain()
                                : this == JULY_RAIN ? c.julyRain() : c.groundwaterPotential();
                yield n >= (this == GROUNDWATER ? 500 : 1000)
                        ? (this == GROUNDWATER ? "500" : "1000") + " mm (maximum)"
                        : ((int) (n / 100) * 100) + "–" + ((int) (n / 100) * 100 + 100) + " mm";
            }
            case RAINFALL ->
                    c.rain() < 0
                            ? "Below 0 mm"
                            : c.rain() >= 500
                                    ? "500+ mm"
                                    : ((int) (c.rain() / 100) * 100)
                                            + "–"
                                            + ((int) (c.rain() / 100) * 100 + 100)
                                            + " mm";
            case TEMPERATURE ->
                    c.temperature() < -20
                            ? "Below -20 °C"
                            : c.temperature() >= 30
                                    ? "30+ °C"
                                    : ((int) Math.floor(c.temperature() / 10) * 10)
                                            + "–"
                                            + ((int) Math.floor(c.temperature() / 10) * 10 + 10)
                                            + " °C";
            case ALTITUDE ->
                    !c.land()
                            ? "Ocean"
                            : c.altitude() < 0
                                    ? "Near island (<0)"
                                    : c.altitude() < 4
                                            ? "Lowland (0–3)"
                                            : c.altitude() < 8
                                                    ? "Midland (4–7)"
                                                    : c.altitude() < 12
                                                            ? "Highland (8–11)"
                                                            : "Mountains (12+)";
            case INLAND ->
                    !c.land()
                            ? "Ocean"
                            : c.inland() < 4
                                    ? "Coastal (0–3)"
                                    : c.inland() < 8
                                            ? "Near inland (4–7)"
                                            : c.inland() < 12
                                                    ? "Interior (8–11)"
                                                    : c.inland() < 20
                                                            ? "Far inland (12–19)"
                                                            : "Deep inland (20+)";
            default -> value(c);
        };
    }

    public int color(Cell c, boolean accessible, Map<String, Integer> overrides) {
        Integer override = null;
        if (!overrides.isEmpty()) {
            override = overrides.get(name() + ":" + colourKey(c));
            if (override == null) {
                override = overrides.get(name() + ":" + value(c));
            }
        }
        if (override != null) {
            return override & 0xffffff;
        }
        return switch (this) {
            case CLIMATE_ZONES -> category(c.climateZone(), accessible);
            case RAIN_VARIANCE -> ramp((c.rainVariance() + 1) / 2, 0xCF8844, 0xE6DEBD, 0x3763AC);
            case JANUARY_RAIN -> ramp(c.januaryRain() / 1000, 0xC59C59, 0x2D8A78, 0x3548A0);
            case JULY_RAIN -> ramp(c.julyRain() / 1000, 0xC59C59, 0x2D8A78, 0x3548A0);
            case GROUNDWATER -> ramp(c.groundwaterPotential() / 500, 0xC59C59, 0x2D8A78, 0x3548A0);
            case ROCKS -> category(c.rock(), accessible);
            case BIOMES -> category(c.biome(), accessible);
            case ROCK_TYPES ->
                    (accessible
                                    ? new int[] {0x0072B2, 0xD55E00, 0x009E73, 0xCC79A7}
                                    : new int[] {0x3A79A5, 0xD25B46, 0x789C52, 0xB386C5})
                            [c.rockType()];
            case RAINFALL -> ramp(c.rain() / 500f, 0xC59C59, 0x2D8A78, 0x3548A0);
            case TEMPERATURE -> ramp((c.temperature() + 20) / 50f, 0x407CB7, 0xE8DAAD, 0xC74B40);
            // TFC's four land bands are 0..3, 4..7, 8..11 and 12+, not 0..4.
            case ALTITUDE ->
                    !c.land()
                            ? 0x244F78
                            : c.altitude() < 0
                                    ? 0x477B87
                                    : c.altitude() < 4
                                            ? 0x5B8B6A
                                            : c.altitude() < 8
                                                    ? 0xA9AD7A
                                                    : c.altitude() < 12 ? 0xC4B995 : 0xE1DCBD;
            case INLAND ->
                    c.land() ? ramp(c.inland() / 20f, 0x457F68, 0xA9AD7A, 0xD1A27B) : 0x244F78;
            case RIVERS ->
                    c.river()
                            ? 0x46B7EB
                            : c.lake()
                                    ? 0x2786BF
                                    : c.mountain() ? 0xB9A5CA : c.land() ? 0x536C49 : 0x244F78;
        };
    }

    public boolean climate() {
        return this == RAINFALL
                || this == TEMPERATURE
                || this == CLIMATE_ZONES
                || this == RAIN_VARIANCE
                || this == JANUARY_RAIN
                || this == JULY_RAIN
                || this == GROUNDWATER;
    }

    public boolean continentFill() {
        return climate() || this == ROCKS;
    }

    /** Display-only ocean fill: climate values, land colours and cached samples stay intact. */
    public int mapColor(
            Cell c, boolean accessible, Map<String, Integer> overrides, boolean continents) {
        return mapColor(c, accessible, overrides, continents, RockLayer.TOP);
    }

    public int mapColor(
            Cell c,
            boolean accessible,
            Map<String, Integer> overrides,
            boolean continents,
            RockLayer rockLayer) {
        if (continents && continentFill() && !c.land()) {
            return oceanColor(overrides);
        }
        if (this != ROCKS) {
            return color(c, accessible, overrides);
        }
        String rock = c.rock(rockLayer);
        Integer override = overrides.isEmpty() ? null : overrides.get("ROCKS:" + Cell.label(rock));
        return override == null ? category(rock, accessible) : override & 0xffffff;
    }

    private int oceanColor(Map<String, Integer> overrides) {
        return overrides.getOrDefault(name() + ":Ocean", 0x142536) & 0xffffff;
    }

    /** Upgrade old single-value edits to the displayed ranges they belong to. */
    public static void migrateColourRanges(Map<String, Integer> colours) {
        for (var entry : new TreeMap<>(colours).entrySet()) {
            try {
                String[] parts = entry.getKey().split(":", 2);
                Layer layer = Layer.valueOf(parts[0]);
                String value = parts[1];
                Cell cell = null;
                if (layer == RAINFALL && value.matches("-?[0-9]+(?:\\.[0-9]+)? mm")) {
                    cell = sample(0, Double.parseDouble(value.replace(" mm", "")), 0, 0, 0, 1);
                }
                if (layer == TEMPERATURE && value.matches("-?[0-9]+(?:\\.[0-9]+)? °C")) {
                    cell = sample(0, 0, Double.parseDouble(value.replace(" °C", "")), 0, 0, 1);
                }
                if ((layer == ALTITUDE || layer == INLAND) && value.matches("Index -?[0-9]+")) {
                    int n = Integer.parseInt(value.substring(6));
                    cell = sample(0, 0, 0, layer == ALTITUDE ? n : 0, layer == INLAND ? n : 0, 1);
                }
                if (cell != null) {
                    colours.putIfAbsent(
                            layer.name() + ":" + layer.colourKey(cell), entry.getValue());
                    colours.remove(entry.getKey());
                }
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ignored) {
            }
        }
    }

    public record LegendEntry(String label, String colourKey, List<Integer> swatch) {
        public int colourAt(double t) {
            return swatch.get(Math.min(swatch.size() - 1, Math.max(0, (int) (t * swatch.size()))));
        }
    }

    /** Fixed semantic/numeric order shared by the map key and full key. */
    public List<LegendEntry> legend(boolean accessible, Map<String, Integer> overrides) {
        List<LegendEntry> entries = new ArrayList<>();
        if (this == CLIMATE_ZONES) {
            for (String zone : ClimateZones.CODES) {
                entries.add(
                        new LegendEntry(
                                ClimateZones.label(zone),
                                zone,
                                List.of(
                                        overrides.getOrDefault(
                                                name() + ":" + zone, category(zone, accessible)))));
            }
            return List.copyOf(entries);
        }
        if (this == RAIN_VARIANCE) {
            for (int i = -4; i <= 4; i++) {
                float variance = i / 4f;
                add(
                        entries,
                        t -> sample(0, 250, 0, 0, 0, 1).withClimate(variance, 0, "Unknown"),
                        accessible,
                        overrides);
            }
        }
        if (this == JANUARY_RAIN || this == JULY_RAIN || this == GROUNDWATER) {
            int limit = this == GROUNDWATER ? 5 : 10;
            for (int i = 0; i < limit; i++) {
                int low = i * 100;
                add(entries, t -> sample(0, low + 99.999 * t, 0, 0, 0, 1), accessible, overrides);
            }
            add(entries, t -> sample(0, limit * 100, 0, 0, 0, 1), accessible, overrides);
        }

        if (this == ROCKS || this == BIOMES) {
            categories(this, accessible, overrides)
                    .forEach(
                            (name, colour) ->
                                    entries.add(new LegendEntry(name, name, List.of(colour))));
            return List.copyOf(entries);
        }
        if (this == ROCK_TYPES) {
            for (int i = 0; i < 4; i++) {
                int type = i;
                add(entries, t -> sample(type, 0, 0, 0, 0, 1), accessible, overrides);
            }
        }
        if (this == RIVERS) {
            for (int flags : new int[] {0, 1, 3, 5, 9}) {
                add(entries, t -> sample(0, 0, 0, 0, 0, flags), accessible, overrides);
            }
        }
        if (this == RAINFALL) {
            for (int i = 0; i < 5; i++) {
                int low = i * 100;
                add(entries, t -> sample(0, low + 99.999 * t, 0, 0, 0, 1), accessible, overrides);
            }
            add(entries, t -> sample(0, 500, 0, 0, 0, 1), accessible, overrides);
        }
        if (this == TEMPERATURE) {
            add(entries, t -> sample(0, 0, -21, 0, 0, 1), accessible, overrides);
            for (int low = -20; low < 30; low += 10) {
                int from = low;
                add(entries, t -> sample(0, 0, from + 9.999 * t, 0, 0, 1), accessible, overrides);
            }
            add(entries, t -> sample(0, 0, 30, 0, 0, 1), accessible, overrides);
        }
        if (this == ALTITUDE) {
            add(entries, t -> sample(0, 0, 0, 0, 0, 0), accessible, overrides);
            for (int altitude : new int[] {-1, 0, 4, 8, 12}) {
                add(entries, t -> sample(0, 0, 0, altitude, 0, 1), accessible, overrides);
            }
        }
        if (this == INLAND) {
            add(entries, t -> sample(0, 0, 0, 0, 0, 0), accessible, overrides);
            for (int[] bounds : new int[][] {{0, 3}, {4, 7}, {8, 11}, {12, 19}, {20, 20}}) {
                add(
                        entries,
                        t ->
                                sample(
                                        0,
                                        0,
                                        0,
                                        0,
                                        bounds[0] + (int) Math.round(t * (bounds[1] - bounds[0])),
                                        1),
                        accessible,
                        overrides);
            }
        }
        return List.copyOf(entries);
    }

    public List<LegendEntry> legend(
            boolean accessible, Map<String, Integer> overrides, boolean continents) {
        List<LegendEntry> entries = legend(accessible, overrides);
        if (!continents || !continentFill()) {
            return entries;
        }
        List<LegendEntry> mapped = new ArrayList<>();
        mapped.add(new LegendEntry("Ocean", "Ocean", List.of(oceanColor(overrides))));
        mapped.addAll(entries);
        return List.copyOf(mapped);
    }

    private void add(
            List<LegendEntry> out,
            DoubleFunction<Cell> samples,
            boolean accessible,
            Map<String, Integer> overrides) {
        String key = colourKey(samples.apply(0));
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            colours.add(color(samples.apply(i / 31.), accessible, overrides));
        }
        String label =
                this == ALTITUDE
                        ? altitudeBand(samples.apply(0))
                        : this == INLAND ? inlandBand(samples.apply(0)) : key;
        out.add(new LegendEntry(label, key, List.copyOf(colours)));
    }

    private static Cell sample(
            int type, double rain, double temp, int altitude, int inland, int flags) {
        return new Cell("", "", type, (float) rain, (float) temp, altitude, inland, 0, flags);
    }

    public String legendNote() {
        return switch (this) {
            case CLIMATE_ZONES ->
                    "TFC climate classification at sea level; mountainous surface climates can differ. Hemisphere is evaluated separately at every location.";
            case RAIN_VARIANCE ->
                    "Signed seasonal variation: positive means wetter July, negative means wetter January. Zero is uniform rainfall.";
            case JANUARY_RAIN, JULY_RAIN ->
                    "Rainfall at the start of the named month, derived from TFC's annual rainfall and signed seasonal variation; not current weather.";
            case GROUNDWATER ->
                    "Annual rainfall plus river groundwater before TFC's elevation reduction, capped at 500 mm. Potential only: high river banks can have less actual groundwater.";
            case RAINFALL ->
                    "Annual mean rainfall in mm; ranges run from the lower bound up to the next.";
            case TEMPERATURE ->
                    "Annual mean sea-level °C; ranges run from the lower bound up to the next.";
            case ALTITUDE ->
                    "Regional terrain bands, not surface Y contours. Close mountain boundaries follow TFC's blended terrain biomes.";
            case RIVERS ->
                    "Rivers and lakes follow TFC biomes. Close mountain boundaries show dominant blended mountain terrain, not an elevation contour.";
            case ROCK_TYPES ->
                    "Surface geological regions: Oceanic, Volcanic, Land and Uplift. Every region can have deeper strata; Uplift + Bottom is valid. Zoom in for TFC's detailed boundaries.";
            case INLAND ->
                    "Relative inlandness from coast to interior; influenced by region edges, not metres.";
            default -> "Adaptive 16–128-block predictions; zoom in for finer boundaries.";
        };
    }

    public String legendNote(boolean continents) {
        return legendNote()
                + (continents && continentFill()
                        ? " Ocean uses a separate colour; hover and searches keep the underlying data."
                        : "");
    }

    public static int category(String s, boolean accessible) {
        if (!accessible) {
            String rgb = COLORS.getProperty(s);
            if (rgb != null) {
                try {
                    return Integer.parseInt(rgb, 16);
                } catch (Exception ignored) {
                }
            }
        }
        int[] palette = {
            0x0072B2, 0xE69F00, 0x009E73, 0xCC79A7, 0x56B4E9, 0xD55E00, 0xF0E442, 0xAAB8BA,
            0x804D97, 0x6EA548, 0xD8888A, 0xC5B37E
        };
        return palette[Math.floorMod(s.hashCode(), palette.length)];
    }

    public static int ramp(float t, int a, int b, int c) {
        t = Math.max(0, Math.min(1, t));
        return t < .5 ? mix(a, b, t * 2) : mix(b, c, t * 2 - 1);
    }

    private static int mix(int a, int b, float t) {
        int r = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            r |= (int) (((a >> shift) & 255) * (1 - t) + ((b >> shift) & 255) * t) << shift;
        }
        return r;
    }

    public static Map<String, Integer> categories(
            Layer layer, boolean accessible, Map<String, Integer> overrides) {
        Map<String, Integer> out = new TreeMap<>();
        if (layer == ROCKS || layer == BIOMES) {
            for (String key : COLORS.stringPropertyNames()) {
                if ((layer == ROCKS) == key.contains("rock/raw/")) {
                    out.put(
                            Cell.label(key),
                            overrides.getOrDefault(
                                    layer.name() + ":" + Cell.label(key),
                                    category(key, accessible)));
                }
            }
        }
        return out;
    }
}
