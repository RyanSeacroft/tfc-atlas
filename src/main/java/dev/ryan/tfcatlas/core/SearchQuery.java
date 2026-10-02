package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Commas mean OR within a field; different fields combine with AND. */
public record SearchQuery(
        Set<String> rocks,
        Set<String> biomes,
        Set<Integer> rockTypes,
        float minRain,
        float maxRain,
        float minTemp,
        float maxTemp,
        Set<String> features,
        int minY,
        int maxY,
        String rockLayer,
        Set<String> climateZones,
        float minGroundwater,
        float maxGroundwater) {
    public SearchQuery(
            Set<String> rocks,
            Set<String> biomes,
            Set<Integer> types,
            float minRain,
            float maxRain,
            float minTemp,
            float maxTemp,
            Set<String> features,
            int minY,
            int maxY,
            String rockLayer) {
        this(
                rocks,
                biomes,
                types,
                minRain,
                maxRain,
                minTemp,
                maxTemp,
                features,
                minY,
                maxY,
                rockLayer,
                Set.of(),
                -Float.MAX_VALUE,
                Float.MAX_VALUE);
    }

    public SearchQuery {
        climateZones =
                climateZones.stream()
                        .map(ClimateZones::code)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!Float.isFinite(minGroundwater)
                || !Float.isFinite(maxGroundwater)
                || minGroundwater > maxGroundwater) {
            throw new IllegalArgumentException("Invalid groundwater bounds");
        }
        if (!Set.of("Top", "Middle", "Bottom", "Any layer", "Dikes").contains(rockLayer)) {
            throw new IllegalArgumentException("Choose Top, Middle, Bottom, Any layer or Dikes");
        }
        rocks = Set.copyOf(rocks);
        biomes = Set.copyOf(biomes);
        rockTypes = Set.copyOf(rockTypes);
        features = Set.copyOf(features);
        if (rockTypes.stream().anyMatch(t -> t < 0 || t >= Cell.TYPE_NAMES.length)
                || !FEATURES.containsAll(features)) {
            throw new IllegalArgumentException("Unknown rock category or terrain feature");
        }
        if (!Float.isFinite(minRain)
                || !Float.isFinite(maxRain)
                || !Float.isFinite(minTemp)
                || !Float.isFinite(maxTemp)
                || minRain > maxRain
                || minTemp > maxTemp
                || minY > maxY) {
            throw new IllegalArgumentException("Minimum must be no greater than maximum");
        }
    }

    public SearchQuery(
            Set<String> rocks,
            Set<String> biomes,
            Set<Integer> types,
            float minRain,
            float maxRain,
            float minTemp,
            float maxTemp,
            Set<String> features,
            int minY,
            int maxY) {
        this(rocks, biomes, types, minRain, maxRain, minTemp, maxTemp, features, minY, maxY, "Top");
    }

    public boolean dikes() {
        return rockLayer.equals("Dikes");
    }

    public boolean layered() {
        return dikes() || !rocks.isEmpty();
    }

    public int matchingLayers(Cell cell) {
        int selected = RockLayer.mask(rockLayer), matched = 0;
        if ((selected & 1) != 0 && matchesRock(cell.rock())) {
            matched |= 1;
        }
        if ((selected & 2) != 0 && matchesRock(cell.middleRock())) {
            matched |= 2;
        }
        if ((selected & 4) != 0 && matchesRock(cell.bottomRock())) {
            matched |= 4;
        }
        return matched;
    }

    public boolean matchesRock(String rock) {
        return named(rocks, rock);
    }

    public static final Set<String> FEATURES =
            Set.of("land", "ocean", "river", "lake", "mountain", "coast");
    public static final List<String> FEATURE_NAMES =
            List.of("Land", "Ocean", "River", "Lake", "Mountain", "Coast");

    public SearchQuery(
            Set<String> rocks,
            Set<String> biomes,
            int type,
            float minRain,
            float maxRain,
            float minTemp,
            float maxTemp,
            String feature,
            int minY,
            int maxY) {
        this(
                rocks,
                biomes,
                type < 0 ? Set.of() : Set.of(type),
                minRain,
                maxRain,
                minTemp,
                maxTemp,
                names(feature),
                minY,
                maxY);
    }

    public static Set<Integer> types(String text) {
        Set<Integer> types = new HashSet<>();
        for (String name : names(text)) {
            int match = -1;
            for (int i = 0; i < Cell.TYPE_NAMES.length; i++) {
                if (name.equals(Cell.TYPE_NAMES[i].toLowerCase(Locale.ROOT))) {
                    match = i;
                }
            }
            if (match < 0) {
                if (name.equals("dike") || name.equals("dikes") || name.equals("dyke")) {
                    throw new IllegalArgumentException(
                            "Choose Dikes in the Rock layer control to search local intrusions.");
                }
                throw new IllegalArgumentException("Unknown rock category: " + name);
            }
            types.add(match);
        }
        return Set.copyOf(types);
    }

    public static Set<String> terrain(String text) {
        Set<String> result = names(text);
        for (String name : result) {
            if (!FEATURES.contains(name)) {
                throw new IllegalArgumentException("Unknown terrain feature: " + name);
            }
        }
        return result;
    }

    public static Set<String> names(String s) {
        Set<String> out = new HashSet<>();
        for (String v : s.split(",")) {
            v = v.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!v.isEmpty() && !v.equals("any")) {
                out.add(v);
            }
        }
        return out;
    }

    private static boolean named(Set<String> ids, String id) {
        return ids.isEmpty()
                || ids.contains(id)
                || ids.contains(
                        id.substring(Math.max(id.lastIndexOf('/'), id.lastIndexOf(':')) + 1));
    }

    public boolean heightRestricted() {
        return minY > -64 || maxY < 319;
    }

    public boolean matchesHeight(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean matches(Cell c, int surfaceY) {
        return matchesRegion(c) && matchesHeight(surfaceY);
    }

    public boolean matchesRegion(Cell c) {
        return (dikes() || matchingLayers(c) != 0)
                && named(biomes, c.biome())
                && (rockTypes.isEmpty() || rockTypes.contains(c.rockType()))
                && (climateZones.isEmpty()
                        || climateZones.contains(c.climateZone().toLowerCase(Locale.ROOT)))
                && c.groundwaterPotential() >= minGroundwater
                && c.groundwaterPotential() <= maxGroundwater
                && c.rain() >= minRain
                && c.rain() <= maxRain
                && c.temperature() >= minTemp
                && c.temperature() <= maxTemp
                && (features.isEmpty() || features.stream().anyMatch(f -> featureMatches(f, c)));
    }

    private static boolean featureMatches(String feature, Cell c) {
        return switch (feature) {
            case "land" -> c.land();
            case "ocean" -> !c.land();
            case "river" -> c.river();
            case "lake" -> c.lake();
            case "mountain" -> c.mountain();
            case "coast" -> c.land() && c.oceanDistance() <= 2;
            default -> false;
        };
    }

    public record Result(
            int x,
            int z,
            double distance,
            Cell cell,
            Integer surfaceY,
            int layerMask,
            Integer dikeY) {
        public String rocks() {
            return RockLayer.matches(cell, layerMask)
                    + (dikeY == null ? "" : " · predicted dike Y " + dikeY);
        }

        public Result(int x, int z, double distance, Cell cell, Integer surfaceY, int layerMask) {
            this(x, z, distance, cell, surfaceY, layerMask, null);
        }

        public Result(int x, int z, double distance, Cell cell, Integer surfaceY) {
            this(x, z, distance, cell, surfaceY, 1);
        }

        public Result(int x, int z, double distance, Cell cell) {
            this(x, z, distance, cell, null, 1);
        }
    }

    public static List<Result> spaced(List<Result> list, int limit, int spacing) {
        list.sort(
                Comparator.comparingDouble(Result::distance)
                        .thenComparingInt(Result::x)
                        .thenComparingInt(Result::z));
        List<Result> out = new ArrayList<>();
        for (Result r : list) {
            boolean close = false;
            for (Result p : out) {
                if (Math.hypot((double) r.x - p.x, (double) r.z - p.z) < spacing) {
                    close = true;
                    break;
                }
            }
            if (!close) {
                out.add(r);
            }
            if (out.size() >= limit) {
                break;
            }
        }
        return List.copyOf(out);
    }
}
