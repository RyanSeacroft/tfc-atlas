package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchQuery;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class Profile {
    public String seed = "", layer = "ROCKS", mode = "Unexplored only";
    public boolean legend = true,
            hover = true,
            spawn = false,
            highlights = true,
            labels = false,
            accessible = false,
            diskCache = true;
    public double opacity = .65, highlightOpacity = .55, labelScale = .5;
    // Negative positions and zero key dimensions use automatic placement and full-list sizing.
    public double toolbarScale = .5, keyScale = .5, infoScale = .5, criteriaScale = .5;
    public int criteriaX = -1, criteriaY = -1;
    public int keyX = -1, keyY = -1, infoX = -1, infoY = -1, keyWidth = 0, keyHeight = 0;
    public String mapLabels = "Off";
    public boolean searchCircle = true;
    public boolean climateContinents = true;
    public Map<String, int[]> keySizes = new HashMap<>();
    public Map<String, Double> keyScales = new HashMap<>();
    public int uiRevision = 6, searchRevision = 2, coverageRevision = 1;
    public int cacheRevision = 1;
    public int highlightColor = 0xFFFF55,
            outline = 1,
            labelSpacing = 100,
            memoryTiles = 2048,
            diskMB = 1024,
            toolbarX = 32,
            toolbarY = 40;
    public int spawnX = 0,
            spawnZ = 0,
            spawnRadius = 4000,
            temperatureScale = 20000,
            rainfallScale = 20000;
    public float continentalness = .5f, temperatureConstant = 0, rainfallConstant = 0;
    public boolean useLocalSettings = true, finiteContinents = false;
    public String climateZones = "";
    public float minGroundwater = -Float.MAX_VALUE, maxGroundwater = Float.MAX_VALUE;
    public String rocks = "", biomes = "", categories = "", feature = "";
    public String precision = "Auto",
            rockLayer = "Top",
            searchRockLayer = "Top",
            searchOrigin = "Player";

    public int searchResolution() {
        return Sampling.searchResolution(precision, radius);
    }

    public int searchX = 0, searchZ = 0;
    public int radius = 16384, resultLimit = 5, resultSpacing = 512, minY = -64, maxY = 319;
    public float minRain = -Float.MAX_VALUE,
            maxRain = Float.MAX_VALUE,
            minTemp = -Float.MAX_VALUE,
            maxTemp = Float.MAX_VALUE;
    public static final Set<String> OPTIONAL_LIMITS =
            Set.of(
                    "minGroundwater",
                    "maxGroundwater",
                    "minRain",
                    "maxRain",
                    "minTemp",
                    "maxTemp",
                    "minY",
                    "maxY");

    public String searchValue(String key) {
        try {
            Object value = Profile.class.getField(key).get(this);
            if (value instanceof Float f && Math.abs(f) == Float.MAX_VALUE) {
                return "";
            }
            if (key.equals("minY") && minY == -64 || key.equals("maxY") && maxY == 319) {
                return "";
            }
            return String.valueOf(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(key, e);
        }
    }

    public void startSession() {
        rocks = categories = "";
        searchRockLayer = "Top";
    }

    public void clearSearchSettings() {
        climateZones = "";
        minGroundwater = -Float.MAX_VALUE;
        maxGroundwater = Float.MAX_VALUE;
        rocks = biomes = categories = feature = "";
        precision = "Auto";
        searchRockLayer = "Top";
        searchOrigin = "Player";
        searchX = searchZ = 0;
        radius = 16384;
        resultLimit = 5;
        resultSpacing = 512;
        minY = -64;
        maxY = 319;
        minRain = minTemp = -Float.MAX_VALUE;
        maxRain = maxTemp = Float.MAX_VALUE;
    }

    public Map<String, Integer> colors = new HashMap<>();
    public Map<String, String> savedSearches = new TreeMap<>();

    public boolean maskedCoverage() {
        return mode.equals("Unexplored only") || mode.equals("Explored only");
    }

    public boolean overlayVisible() {
        return !mode.equals("Off");
    }

    public void cycleCoverage() {
        mode =
                switch (mode) {
                    case "Full map" -> "Unexplored only";
                    case "Unexplored only" -> "Explored only";
                    case "Explored only" -> "Off";
                    default -> "Full map";
                };
    }

    public Layer selected() {
        try {
            return Layer.valueOf(layer);
        } catch (Exception e) {
            return Layer.ROCKS;
        }
    }

    public int mapColor(Cell cell) {
        return selected()
                .mapColor(cell, accessible, colors, climateContinents, selectedRockLayer());
    }

    public int mapStyleHash() {
        return Objects.hash(accessible, colors, climateContinents, rockLayer);
    }

    public SearchQuery query() {
        return new SearchQuery(
                SearchQuery.names(rocks),
                SearchQuery.names(biomes),
                SearchQuery.types(categories),
                minRain,
                maxRain,
                minTemp,
                maxTemp,
                SearchQuery.terrain(feature),
                minY,
                maxY,
                searchRockLayer,
                SearchQuery.names(climateZones),
                minGroundwater,
                maxGroundwater);
    }

    public RockLayer selectedRockLayer() {
        return RockLayer.of(rockLayer);
    }

    /** Applied only when Search is pressed; later manual layer choices remain untouched. */
    public void showSearchLayer() {
        SearchQuery q = query();
        Layer chosen = null;
        if (q.dikes() || !q.rocks().isEmpty()) {
            chosen = Layer.ROCKS;
            if (!searchRockLayer.equals("Any layer") && !q.dikes()) {
                rockLayer = searchRockLayer;
            }
        } else if (!q.biomes().isEmpty()) {
            chosen = Layer.BIOMES;
        } else if (!q.rockTypes().isEmpty()) {
            chosen = Layer.ROCK_TYPES;
        } else if (!q.features().isEmpty()) {
            chosen = Layer.RIVERS;
        } else if (!q.climateZones().isEmpty()) {
            chosen = Layer.CLIMATE_ZONES;
        } else if (minGroundwater != -Float.MAX_VALUE || maxGroundwater != Float.MAX_VALUE) {
            chosen = Layer.GROUNDWATER;
        } else if (q.heightRestricted()) {
            chosen = Layer.ALTITUDE;
        } else {
            boolean rain = minRain != -Float.MAX_VALUE || maxRain != Float.MAX_VALUE,
                    temp = minTemp != -Float.MAX_VALUE || maxTemp != Float.MAX_VALUE;
            if (rain && temp) {
                chosen =
                        (Math.min(500, maxRain) - Math.max(0, minRain)) / 500f
                                        <= (Math.min(30, maxTemp) - Math.max(-20, minTemp)) / 50f
                                ? Layer.RAINFALL
                                : Layer.TEMPERATURE;
            } else if (rain) {
                chosen = Layer.RAINFALL;
            } else if (temp) {
                chosen = Layer.TEMPERATURE;
            }
        }
        if (chosen != null) {
            layer = chosen.name();
        }
    }

    public String layerTitle() {
        return selected().label
                + (selected() == Layer.ROCKS ? " · " + selectedRockLayer().label : "");
    }

    public double keyScale() {
        return keyScales.getOrDefault(selected().name(), keyScale);
    }

    public void keyScale(double scale) {
        keyScales.put(selected().name(), HudLayout.scale(scale));
    }

    public int keyWidth() {
        return 0;
    }

    public int keyHeight() {
        return keySizes.getOrDefault(selected().name(), new int[] {0, 0})[1];
    }

    public void keySize(int width, int height) {
        keySizes.put(selected().name(), new int[] {0, height});
    }

    public void resetHud() {
        toolbarX = 32;
        toolbarY = 40;
        keyX = keyY = infoX = infoY = criteriaX = criteriaY = -1;
        toolbarScale = keyScale = infoScale = criteriaScale = .5;
        keyWidth = keyHeight = 0;
        keySizes.clear();
        keyScales.clear();
    }

    public void validate() {
        if (searchOrigin == null
                || !Set.of("Player", "World spawn", "Coordinates").contains(searchOrigin)) {
            searchOrigin = "Player";
        }
        if (precision == null
                || !Set.of("Auto", "16", "32", "64", "128", "256", "512").contains(precision)) {
            precision = "Auto";
        }
        rockLayer = RockLayer.of(rockLayer).label;
        if (searchRockLayer == null
                || !Set.of("Top", "Middle", "Bottom", "Any layer", "Dikes")
                        .contains(searchRockLayer)) {
            searchRockLayer = "Top";
        }
        if (colors == null) {
            colors = new HashMap<>();
        }
        if (savedSearches == null) {
            savedSearches = new TreeMap<>();
        }
        Layer.migrateColourRanges(colors);
        if (!Set.of("Full map", "Unexplored only", "Explored only", "Off")
                .contains(mode == null ? "" : mode)) {
            mode = "Unexplored only";
        }
        if (Set.of("Biomes", "Rocks", "Both").contains(mapLabels == null ? "" : mapLabels)) {
            mapLabels = "Active layer";
        }
        if (mapLabels == null || !Set.of("Off", "Active layer").contains(mapLabels)) {
            mapLabels = "Off";
        }
        labelScale = HudLayout.scale(labelScale);
        if (keySizes == null) {
            keySizes = new HashMap<>();
        }
        if (keyScales == null) {
            keyScales = new HashMap<>();
        }
        keyScales.entrySet().removeIf(e -> e.getValue() == null);
        keyScales.replaceAll((k, v) -> HudLayout.scale(v));
        if ((keyWidth > 0 || keyHeight > 0) && !keySizes.containsKey(selected().name())) {
            keySize(keyWidth, keyHeight);
        }
        keyWidth = keyHeight = 0;
        keySizes.entrySet().removeIf(e -> e.getValue() == null || e.getValue().length != 2);
        keySizes.replaceAll((k, v) -> new int[] {0, Math.max(0, Math.min(2000, v[1]))});
        if (categories == null) {
            categories = "";
        }
        if (feature == null) {
            feature = "";
        }
        toolbarScale = panelScale(toolbarScale);
        keyScale = panelScale(keyScale);
        infoScale = panelScale(infoScale);
        criteriaScale = panelScale(criteriaScale);
        opacity = Double.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : .65;
        highlightOpacity =
                Double.isFinite(highlightOpacity)
                        ? Math.max(0, Math.min(1, highlightOpacity))
                        : .65;
        memoryTiles = Math.max(64, Math.min(4096, memoryTiles));
        diskMB = Math.max(16, Math.min(8192, diskMB));
        radius =
                Math.max(
                        128,
                        precision.equals("Auto")
                                ? Math.min(Sampling.MAX_SEARCH_RADIUS, radius)
                                : radius);
        resultLimit = 5;
        searchX = Math.max(-29_999_984, Math.min(29_999_984, searchX));
        searchZ = Math.max(-29_999_984, Math.min(29_999_984, searchZ));
        outline = Math.max(0, Math.min(5, outline));
        labelSpacing = Math.max(50, Math.min(400, labelSpacing));
    }

    private static double panelScale(double scale) {
        return scale == 0 || !Double.isFinite(scale) ? .5 : HudLayout.scale(scale);
    }
}
