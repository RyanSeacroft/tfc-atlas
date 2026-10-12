package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.DefaultHud;
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
    public String seed = "", layer = "ROCKS", mode = "Full map";
    public boolean legend = true,
            hover = true,
            spawn = false,
            highlights = true,
            diskCache = true,
            atlasEnabled = true;
    public boolean showCriteria = true;
    public int exportPixels = 256, exportStep = 128;
    public String exportCenter = "Map focus";
    public boolean exportTransparentOcean = false, exportMetadata = true;
    public String display = "Xaero Map";
    public double opacity = .5, highlightOpacity = .55, labelScale = .5;
    // Negative positions and zero key dimensions use automatic placement and full-list sizing.
    public double toolbarScale = .5,
            keyScale = .5,
            infoScale = DefaultHud.INFO_SCALE,
            criteriaScale = DefaultHud.SEARCH_SCALE;
    public int cacheRevision = 1;
    public int criteriaX = -1, criteriaY = -1;
    public int keyX = -1, keyY = -1, infoX = -1, infoY = -1, keyWidth = 0, keyHeight = 0;
    public String mapLabels = "Active layer";
    public boolean searchCircle = true;
    public boolean minimapHighlights = false;
    public boolean climateContinents = true;
    public Map<String, int[]> keySizes = new HashMap<>();
    public Map<String, Double> keyScales = new HashMap<>();
    public int uiRevision = 9, searchRevision = 2, coverageRevision = 2;
    public int highlightColor = 0x1FFF55,
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
    public boolean useLocalSettings = true;
    public String rocks = "", biomes = "", categories = "", feature = "";
    public String biomeGroups = "", rockGroups = "";
    public String soils = "";
    public float minRiver = 0,
            maxRiver = Float.MAX_VALUE,
            minOcean = 0,
            maxOcean = Float.MAX_VALUE,
            minSlope = 0,
            maxSlope = 90,
            minInland = 0,
            maxInland = Float.MAX_VALUE;
    public double minArea = 0, maxArea = Double.MAX_VALUE;
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
                    "minRiver",
                    "maxRiver",
                    "minOcean",
                    "maxOcean",
                    "minSlope",
                    "maxSlope",
                    "minInland",
                    "maxInland",
                    "minArea",
                    "maxArea",
                    "minRain",
                    "maxRain",
                    "minTemp",
                    "maxTemp",
                    "minY",
                    "maxY");

    public String searchValue(String key) {
        try {
            Object value = Profile.class.getField(key).get(this);
            if (Set.of("minRiver", "minOcean", "minSlope", "minInland", "minArea").contains(key)
                    && value instanceof Number number
                    && number.doubleValue() == 0) {
                return "";
            }
            if (key.equals("maxArea") && maxArea == Double.MAX_VALUE) {
                return "";
            }
            if (key.equals("maxSlope") && maxSlope == 90) {
                return "";
            }
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
        soils = "";
        minRiver = minOcean = minSlope = minInland = 0;
        maxRiver = maxOcean = maxInland = Float.MAX_VALUE;
        maxSlope = 90;
        minArea = 0;
        maxArea = Double.MAX_VALUE;
        rocks = biomes = biomeGroups = rockGroups = categories = feature = "";
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

    public static java.util.List<String> displays(String coverage) {
        return switch (coverage) {
            case "Full map" -> java.util.List.of("Xaero Map", "TFC Layers Only", "Overlay");
            case "Explored only" -> java.util.List.of("TFC Layers Only", "Overlay");
            default -> java.util.List.of();
        };
    }

    public String coverageLabel() {
        return mode.replace(" map", " Map").replace(" only", " Only");
    }

    public boolean displayEnabled() {
        return !displays(mode).isEmpty();
    }

    public String effectiveDisplay() {
        return displays(mode).contains(display)
                ? display
                : mode.equals("Full map") ? "Xaero Map" : "TFC Layers Only";
    }

    public void cycleDisplay() {
        var choices = displays(mode);
        if (!choices.isEmpty()) {
            display = choices.get((choices.indexOf(effectiveDisplay()) + 1) % choices.size());
        }
    }

    public float exploredOpacity() {
        if (!atlasEnabled) {
            return 0;
        }
        return switch (effectiveDisplay()) {
            case "Xaero Map" -> 0;
            case "Overlay" -> (float) opacity;
            default -> 1;
        };
    }

    public float unexploredOpacity() {
        return atlasEnabled && mode.equals("Full map") ? 1 : 0;
    }

    public boolean maskedCoverage() {
        return exploredOpacity() != unexploredOpacity();
    }

    public boolean overlayVisible() {
        return atlasEnabled;
    }

    public void cycleCoverage() {
        mode =
                switch (mode) {
                    case "Full map" -> "Explored only";
                    default -> "Full map";
                };
        if (!displays(mode).contains(display)) {
            display = displays(mode).get(0);
        }
    }

    public Layer selected() {
        try {
            return Layer.valueOf(layer);
        } catch (Exception e) {
            return Layer.ROCKS;
        }
    }

    public int mapColor(Cell cell) {
        return selected().mapColor(cell, false, colors, climateContinents, selectedRockLayer());
    }

    public int mapStyleHash() {
        return Objects.hash(colors, climateContinents, rockLayer);
    }

    public SearchQuery query() {
        if (!biomeGroups.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolve biome groups against the current world first");
        }
        return queryWithBiomes(SearchQuery.names(biomes));
    }

    public SearchQuery query(dev.ryan.tfcatlas.core.BiomeGroups catalog) {
        return queryWithBiomes(catalog.resolve(biomes, biomeGroups));
    }

    private Set<String> resolvedRocks() {
        return dev.ryan.tfcatlas.core.SearchGroups.resolve(
                rocks, rockGroups, WorldSearchGroups.rocks());
    }

    private SearchQuery queryWithBiomes(Set<String> selectedBiomes) {
        return new SearchQuery(
                resolvedRocks(),
                selectedBiomes,
                SearchQuery.types(categories),
                minRain,
                maxRain,
                minTemp,
                maxTemp,
                SearchQuery.terrain(feature),
                minY,
                maxY,
                searchRockLayer,
                searchLimits());
    }

    private dev.ryan.tfcatlas.core.SearchLimits searchLimits() {
        return new dev.ryan.tfcatlas.core.SearchLimits(
                SearchQuery.names(soils),
                minRiver,
                maxRiver,
                minOcean,
                maxOcean,
                minSlope,
                maxSlope,
                minInland,
                maxInland,
                minArea,
                maxArea);
    }

    public RockLayer selectedRockLayer() {
        return RockLayer.of(rockLayer);
    }

    /** Applied only when Search is pressed; later manual layer choices remain untouched. */
    public void showSearchLayer() {
        SearchQuery q = queryWithBiomes(SearchQuery.names(biomes));
        Layer chosen = null;
        if (q.dikes() || !q.rocks().isEmpty()) {
            chosen = Layer.ROCKS;
            if (!searchRockLayer.equals("Any layer") && !q.dikes()) {
                rockLayer = searchRockLayer;
            }
        } else if (!q.biomes().isEmpty() || !biomeGroups.isBlank()) {
            chosen = Layer.BIOMES;
        } else if (!q.rockTypes().isEmpty()) {
            chosen = Layer.ROCK_TYPES;
        } else if (!q.features().isEmpty()) {
            chosen = Layer.RIVERS;
        } else if (q.limits().water()) {
            chosen = Layer.RIVERS;
        } else if (q.heightRestricted() || q.limits().slope()) {
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
        return keyScales.getOrDefault(
                selected().name(),
                keyScale == .5 ? DefaultHud.keyScale(selected().name()) : keyScale);
    }

    public void keyScale(double scale) {
        keyScales.put(selected().name(), HudLayout.scale(scale));
    }

    public int keyWidth() {
        return 0;
    }

    public int keyHeight() {
        return keySizes.getOrDefault(
                        selected().name(), new int[] {0, DefaultHud.keyHeight(selected().name())})[
                1];
    }

    public void keySize(int width, int height) {
        keySizes.put(selected().name(), new int[] {0, height});
    }

    public void resetHud() {
        toolbarX = 32;
        toolbarY = 40;
        keyX = keyY = infoX = infoY = criteriaX = criteriaY = -1;
        toolbarScale = keyScale = .5;
        infoScale = DefaultHud.INFO_SCALE;
        criteriaScale = DefaultHud.SEARCH_SCALE;
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
        if (!Set.of("Full map", "Explored only").contains(mode == null ? "" : mode)) {
            mode = "Full map";
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
        if ("Xaero Map Only".equals(display)) {
            display = "Xaero Map";
        }
        if (!java.util.List.of("TFC Layers Only", "Xaero Map", "Overlay")
                .contains(display == null ? "" : display)) {
            display = mode.equals("Full map") ? "Xaero Map" : "TFC Layers Only";
        }
        if (!displays(mode).contains(display)) {
            display = displays(mode).get(0);
        }
        if (opacity != .25 && opacity != .5 && opacity != .75) {
            opacity = .5;
        }
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
        if (!Set.of(256, 512, 1024).contains(exportPixels)) {
            exportPixels = 256;
        }
        if (!Set.of(16, 32, 64, 128, 256).contains(exportStep)) {
            exportStep = 128;
        }
        if (!Set.of("Map focus", "Player").contains(exportCenter == null ? "" : exportCenter)) {
            exportCenter = "Map focus";
        }
    }

    private static double panelScale(double scale) {
        return scale == 0 || !Double.isFinite(scale) ? .5 : HudLayout.scale(scale);
    }
}
