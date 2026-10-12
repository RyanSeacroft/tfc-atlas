package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.ClimateZones;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.SearchLayout;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchQuery;
import dev.ryan.tfcatlas.core.SearchWorkload;
import dev.ryan.tfcatlas.core.SeedPrivacy;
import dev.ryan.tfcatlas.core.SettingsLayout;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AtlasScreen extends AtlasMenuScreen {
    private record Option(String key, String label, String help, String... choices) {}

    private static Option o(String key, String label, String help, String... choices) {
        return new Option(key, label, help, choices);
    }

    public static final int SEARCH = 0, RESULTS = 1, DISPLAY = 2, WORLDGEN = 3, CACHE = 4;
    private static final String[] TABS = {"Search", "Results", "Display", "Worldgen", "Cache"};
    private static final Option[][] OPTIONS = {
        {
            o(
                    "climateZones",
                    "Climate zones",
                    "Enter climate-zone names or groups separated by commas or new lines, such as Hot Desert or Tropical zones. Press Tab to complete a name or group. Leave blank to allow any zone."),
            o(
                    "minGroundwater",
                    "Minimum groundwater potential (mm)",
                    "Enter the minimum groundwater potential in mm. For example, 300 requires at least 300 mm. Leave blank for no minimum. This includes rainfall and river contribution before elevation reduction."),
            o(
                    "maxGroundwater",
                    "Maximum groundwater potential (mm)",
                    "Enter the maximum groundwater potential in mm. Leave blank for no maximum. Potential includes rainfall and river contribution before elevation reduction; actual surface water can be lower."),
            o(
                    "minRiver",
                    "Minimum river distance (blocks)",
                    "Enter the minimum distance to a predicted river bank in blocks (0–8192). For example, 100 excludes locations nearer than 100 blocks. Leave blank for no minimum."),
            o(
                    "maxRiver",
                    "Maximum river distance (blocks)",
                    "Enter the maximum distance to a predicted river bank in blocks (0–8192). For example, 100 requires a river within 100 blocks. Leave blank for no maximum."),
            o(
                    "minOcean",
                    "Minimum ocean distance (blocks)",
                    "Enter the minimum distance to the predicted ocean coastline in blocks (0–8192). Leave blank for no minimum. Coastlines are estimated on a 128-block regional grid."),
            o(
                    "maxOcean",
                    "Maximum ocean distance (blocks)",
                    "Enter the maximum distance to the predicted ocean coastline in blocks (0–8192). Leave blank for no maximum. Coastlines are estimated on a 128-block regional grid."),
            o(
                    "minSlope",
                    "Minimum terrain steepness (degrees)",
                    "Enter the minimum terrain steepness in degrees (0–90). Leave blank for no minimum. 0 is flat; 45 is one block of rise per horizontal block. Prediction samples eight directions over 32 blocks."),
            o(
                    "maxSlope",
                    "Maximum terrain steepness (degrees)",
                    "Enter the maximum terrain steepness in degrees (0–90). Use a small value for flatter terrain. Leave blank for no maximum. Prediction samples eight directions over 32 blocks."),
            o(
                    "minInland",
                    "Minimum inlandness index",
                    "Enter the minimum TFC inlandness index. Leave blank for no minimum. This is a regional index, not a distance in blocks; use ocean distance for that."),
            o(
                    "maxInland",
                    "Maximum inlandness index",
                    "Enter the maximum TFC inlandness index. Leave blank for no maximum. This is a regional index, not a distance in blocks; use ocean distance for that."),
            o(
                    "minArea",
                    "Minimum matching area (square blocks)",
                    "Enter the minimum matching region area in square blocks. For example, 4096 is the area of a 64×64 square. Leave blank for no minimum. Connected samples define each region inside the radius; live highlights are provisional."),
            o(
                    "maxArea",
                    "Maximum matching area (square blocks)",
                    "Enter the largest matching region in square blocks. Leave blank for no maximum. The whole connected region inside the search radius is measured; live highlights are provisional."),
            o(
                    "soils",
                    "Dirt types",
                    "Enter soil names separated by commas. Press Tab to complete a name. Leave blank to allow any soil. These are regional predictions, not farm nutrients or guaranteed surface blocks."),
            o(
                    "rocks",
                    "Rocks",
                    "Enter rock names separated by commas or new lines, such as granite, gneiss. Enter a group name such as Igneous rocks to select that family. Press Tab to complete a name or group. Leave blank to allow any rock."),
            o(
                    "biomes",
                    "Biomes",
                    "Enter biome names separated by commas or new lines, such as plains, rolling hills. Enter a group name such as Mountain biomes to select that family. Press Tab to complete a name or group. Leave blank to allow any biome."),
            o("biomeGroups", "Biome groups", "Optional shortcuts resolved for the current world."),
            o("rockGroups", "Rock groups", "Optional rock-family shortcuts."),
            o("climateGroups", "Climate groups", "Optional climate-family shortcuts."),
            o(
                    "categories",
                    "Rock regions",
                    "Enter rock-region names separated by commas: Oceanic, Volcanic, Land, Uplift. Press Tab to complete a name. Leave blank to allow any region. Uplift can have bottom-layer rocks."),
            o(
                    "feature",
                    "Terrain features",
                    "Enter terrain-feature names separated by commas: Land, Ocean, River, Lake, Mountain, Coast. Press Tab to complete a name. Leave blank to allow any feature."),
            o(
                    "minRain",
                    "Minimum rainfall (mm)",
                    "Enter the minimum annual rainfall in mm. For example, 20 requires at least 20 mm. Leave blank for no minimum."),
            o(
                    "maxRain",
                    "Maximum rainfall (mm)",
                    "Enter the maximum annual rainfall in mm. For example, 200 allows at most 200 mm. Leave blank for no maximum."),
            o(
                    "minTemp",
                    "Minimum annual temperature (°C)",
                    "Enter the minimum annual temperature in °C. For example, 20 requires at least 20 °C. Leave blank for no minimum. This is a sea-level annual mean."),
            o(
                    "maxTemp",
                    "Maximum annual temperature (°C)",
                    "Enter the maximum annual temperature in °C. For example, 20 allows at most 20 °C. Leave blank for no maximum. This is a sea-level annual mean."),
            o(
                    "radius",
                    "Search radius (blocks)",
                    "Enter the search radius in blocks. Default: 16384. Auto sampling supports 128–262144; manual sampling allows larger radii after confirmation. Smaller radii cost less; the world border still applies."),
            o(
                    "searchX",
                    "Search centre X",
                    "Enter the search centre X coordinate (-29999984 to 29999984). This is used only when the centre button is set to Coordinates."),
            o(
                    "searchZ",
                    "Search centre Z",
                    "Enter the search centre Z coordinate (-29999984 to 29999984). This is used only when the centre button is set to Coordinates."),
            o(
                    "resultSpacing",
                    "Candidate separation (blocks)",
                    "Enter the minimum distance between the five closest listed matches in blocks. This does not change highlights or the total distinct-area count."),
            o(
                    "highlights",
                    "Highlight matches",
                    "Show outlined regions with a highlighted interior as matches are found, including while the search runs.",
                    "true",
                    "false"),
            o(
                    "minY",
                    "Minimum surface Y",
                    "Enter the minimum predicted surface Y (-64 to 319). For example, 100 requires ground at Y 100 or higher. Leave blank for no minimum. Rock layers are selected separately."),
            o(
                    "maxY",
                    "Maximum surface Y",
                    "Enter the maximum predicted surface Y (-64 to 319). For example, 200 allows ground at Y 200 or lower. Leave blank for no maximum. Rock layers are selected separately."),
            o(
                    "precision",
                    "Sampling (blocks)",
                    "Auto: 16 blocks to radius 8192; 32 to 16384; 64 to 32768; 128 to 65536; 256 to 131072; 512 to 262144. Manual sampling accepts larger radii after a performance confirmation. Auto remains bounded.",
                    "Auto",
                    "16",
                    "32",
                    "64",
                    "128",
                    "256",
                    "512"),
            o(
                    "searchRockLayer",
                    "Rock layer",
                    "Top / Middle / Bottom select geological strata, not fixed Y levels. Any layer checks all three. Dikes finds predicted granite, diorite or gabbro intrusions, including buried ones; use fine sampling.",
                    "Top",
                    "Middle",
                    "Bottom",
                    "Any layer",
                    "Dikes")
        },
        {},
        {
            o(
                    "atlasEnabled",
                    "TFC Atlas",
                    "Turns Atlas rendering on or off immediately without changing Xaero's stored map.",
                    "true",
                    "false"),
            o(
                    "mode",
                    "Coverage",
                    "Full Map can show TFC in unexplored terrain. Explored Only restricts TFC rendering to discovered terrain. Saved Xaero maps stay untouched.",
                    "Full map",
                    "Explored only"),
            o(
                    "layer",
                    "Data layer",
                    "All layers share the cached samples.",
                    Arrays.stream(Layer.values()).map(Enum::name).toArray(String[]::new)),
            o(
                    "spawn",
                    "Show spawn region",
                    "Show the actual world spawn as a small white marker and label at zoom 0.02 or higher.",
                    "true",
                    "false"),
            o(
                    "climateContinents",
                    "Continent outlines",
                    "Climate, rocks and soils keep their data colours on land and use a dark ocean fill to show continent shapes. Off restores data colours over oceans too. Hover values and searches are unchanged.",
                    "true",
                    "false"),
            o(
                    "mapLabels",
                    "Map labels",
                    "Rock names on Rocks; biome names on Biomes; climate names on Climate zones; soil names on Soil regions. One name is centred inside each connected visible region; small patches wait until there is room.",
                    "Off",
                    "Active layer"),
            o(
                    "display",
                    "Display",
                    "In Full Map, Xaero Map keeps discovered imagery and fills unexplored terrain with TFC. TFC Layers Only replaces the selected coverage; Overlay blends over discovered imagery.",
                    "Xaero Map",
                    "TFC Layers Only",
                    "Overlay"),
            o(
                    "opacity",
                    "Overlay opacity",
                    "Only blends TFC over discovered Xaero imagery in Overlay mode.",
                    "0.25",
                    "0.5",
                    "0.75"),
            o(
                    "legend",
                    "Map colour key",
                    "In Resize / Move UI, drag the corner to scale the whole key. Use the bottom edge for visible rows; width fits the text automatically. Each layer remembers its size.",
                    "true",
                    "false"),
            o(
                    "hover",
                    "Hover readout",
                    "Coordinates, rock, biome, climate and predicted surface Y. Height is calculated in the background for the cursor column.",
                    "true",
                    "false"),
            o(
                    "minimapHighlights",
                    "Search results on minimap",
                    "Show the same live search regions on Xaero's Minimap, beneath its symbols. Requires Xaero's Minimap in normal framebuffer mode; Safe Mode is not supported. No map data is changed.",
                    "true",
                    "false"),
            o(
                    "highlightColor",
                    "Highlight colour (hex)",
                    "Enter a six-digit hexadecimal colour, such as FFFF55. Searches with rock filters use rock-layer colours instead."),
            o(
                    "highlightOpacity",
                    "Highlight strength (0–1)",
                    "Enter the search highlight strength from 0 to 1. For example, 0.5 gives half strength. Rock searches retain their layer colours."),
            o(
                    "highlights",
                    "Highlight matches",
                    "Show outlined regions with a highlighted interior as matches are found, including while the search runs.",
                    "false",
                    "true"),
            o(
                    "showCriteria",
                    "Current search panel",
                    "Show the submitted search criteria on the map. Hiding this panel keeps the search and its highlights.",
                    "true",
                    "false"),
        },
        {
            o(
                    "seed",
                    "Numeric world seed",
                    "Enter the numeric world seed, or leave blank for automatic detection. Multiplayer seeds are used only for this session and are not saved."),
            o(
                    "useLocalSettings",
                    "Use singleplayer settings",
                    "Read the actual generator settings in singleplayer; multiplayer uses the Worldgen tab.",
                    "true",
                    "false"),
            o(
                    "finiteContinents",
                    "Finite continents",
                    "Use TFC 1.21 finite continents. Must match the world generator; singleplayer settings are read automatically.",
                    "false",
                    "true"),
            o(
                    "temperatureScale",
                    "Temperature scale (blocks)",
                    "Enter the temperature scale in blocks. Default: 20000. Use 0 for a constant climate axis. Match the world generator settings."),
            o(
                    "rainfallScale",
                    "Rainfall scale (blocks)",
                    "Enter the rainfall scale in blocks. Default: 20000. Use 0 for a constant climate axis. Match the world generator settings."),
            o(
                    "temperatureConstant",
                    "Constant temperature input (-1–1)",
                    "Enter the constant temperature input (-1 to 1). This is used only when temperature scale is 0. Match the world generator settings."),
            o(
                    "rainfallConstant",
                    "Constant rainfall input (-1–1)",
                    "Enter the constant rainfall input (-1 to 1). This is used only when rainfall scale is 0. Match the world generator settings."),
            o(
                    "continentalness",
                    "Continentalness (0–1)",
                    "Enter continentalness from 0 to 1. Default: 0.5. Match the world generator settings."),
            o(
                    "spawnX",
                    "Spawn search centre X",
                    "Enter the spawn search centre X coordinate in blocks. Default: 0. Match the world generator settings."),
            o(
                    "spawnZ",
                    "Spawn search centre Z",
                    "Enter the spawn search centre Z coordinate in blocks. Default: 0. Match the world generator settings."),
            o(
                    "spawnRadius",
                    "Spawn search half-width",
                    "Enter the spawn search half-width in blocks. Default: 4000. This defines a square search area; match the world generator settings.")
        },
        {
            o(
                    "diskCache",
                    "Save generated tiles to disk",
                    "Versioned by world, seed, TFC version and settings.",
                    "true",
                    "false"),
            o(
                    "memoryTiles",
                    "Memory tile limit",
                    "Enter the maximum cached tile count (64–4096). Each tile stores every available layer. Larger limits use more memory."),
            o(
                    "diskMB",
                    "Disk limit per seed (MiB)",
                    "Enter the disk cache limit per seed in MiB (16–8192). Older tiles are removed when the limit is reached."),
            o(
                    "exportPixels",
                    "PNG width / height (pixels)",
                    "Choose the square image size. Larger images take longer to export.",
                    "256",
                    "512",
                    "1024"),
            o(
                    "exportStep",
                    "PNG sampling (blocks per pixel)",
                    "Choose the distance between PNG samples. Image width in blocks equals pixels times sampling.",
                    "16",
                    "32",
                    "64",
                    "128",
                    "256"),
            o(
                    "exportCenter",
                    "PNG centre",
                    "Choose the current map focus or the player position as the exported image centre.",
                    "Map focus",
                    "Player"),
            o(
                    "exportTransparentOcean",
                    "Transparent ocean in PNG",
                    "Export ocean pixels with transparency. Land colours and saved map data are unchanged.",
                    "false",
                    "true"),
            o(
                    "exportMetadata",
                    "PNG information file",
                    "Save a small text file with the image coordinates, layer and block scale. Multiplayer seeds remain hidden.",
                    "true",
                    "false")
        }
    };
    private final Screen parent;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<Runnable> readers = new ArrayList<>();
    private int tab;
    private boolean resetHud, resetAll;
    private String message = "", presetName = "My search";
    private String loadedPreset = "";
    private long presetLoadedAt;
    private int searchSection, displaySection, cacheSection;
    private final Map<Button, SearchQuery.Result> resultButtons = new LinkedHashMap<>();
    private SearchQuery.Result selected;
    private List<SearchQuery.Result> shown = List.of();

    public AtlasScreen(Screen parent, int tab) {
        super(Component.literal("TFC Atlas"));
        this.parent = parent;
        this.tab = tab;
        for (Option[] options : OPTIONS) {
            for (Option option : options) {
                try {
                    Object v = Profile.class.getField(option.key).get(AtlasClient.profile);
                    values.put(
                            option.key,
                            Set.of("highlightColor").contains(option.key)
                                    ? String.format("%06X", v)
                                    : AtlasClient.profile.searchValue(option.key));
                } catch (Exception ignored) {
                }
            }
        }
        values.put("searchOrigin", AtlasClient.profile.searchOrigin);
        values.computeIfPresent("climateZones", (key, value) -> ClimateZones.displayList(value));
        mirrorGroupFields();
    }

    private String groupTitle(String key) {
        return switch (key) {
            case "biomes" -> "Biomes";
            case "rocks" -> "Rocks";
            case "climateZones" -> "Climate zones";
            default -> key;
        };
    }

    private void mirrorGroupFields() {
        for (String key : java.util.List.of("biomes", "rocks", "climateZones")) {
            String legacyKey = groupKey(key), legacy = values.getOrDefault(legacyKey, "");
            String names = values.getOrDefault(key, "");
            var catalog = groupCatalog(key);
            try {
                if (key.equals("biomes") && SearchQuery.names(legacy).contains("all")) {
                    names =
                            names
                                    + ","
                                    + String.join(
                                            ",", WorldBiomeGroups.current().resolve("", legacy));
                    legacy = "";
                }
                if (!legacy.isBlank()) {
                    values.put(
                            key,
                            new dev.ryan.tfcatlas.core.GroupSelection(catalog, legacy, names)
                                    .text());
                }
            } catch (IllegalArgumentException unavailable) {
                // Leave unsupported saved names visible so the player can correct them.
                values.put(key, names + (names.isBlank() || legacy.isBlank() ? "" : ", ") + legacy);
            }
            values.put(legacyKey, "");
        }
    }

    public void syncLayer() {
        values.put("layer", AtlasClient.profile.layer);
    }

    private void navigate(int next) {
        read();
        tab = next;
        refresh();
    }

    private void read() {
        readers.forEach(Runnable::run);
    }

    private void refresh() {
        read();
        clearWidgets();
        init();
    }

    private Button button(String s, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(s), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    @Override
    protected void init() {
        readers.clear();
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        int tw = content / TABS.length;
        for (int t = 0; t < TABS.length; t++) {
            final int next = t;
            var navigation = button(TABS[t], left + t * tw, 25, tw - 2, () -> navigate(next));
            navigation.active = t != tab;
            navigation.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Open " + TABS[t].toLowerCase(Locale.ROOT) + " settings.")));
        }
        if (tab == RESULTS) {
            initResults(left, content);
            return;
        }
        Option[] options = displayedOptions();
        if (tab == DISPLAY) {
            int sectionWidth = content / dev.ryan.tfcatlas.core.DisplayLayout.SECTIONS.size();
            for (int i = 0; i < dev.ryan.tfcatlas.core.DisplayLayout.SECTIONS.size(); i++) {
                final int section = i;
                smallButton(
                                        dev.ryan.tfcatlas.core.DisplayLayout.SECTIONS.get(i),
                                        left + i * sectionWidth,
                                        49,
                                        sectionWidth - 2,
                                        18,
                                        () -> {
                                            read();
                                            displaySection = section;
                                            refresh();
                                        })
                                .active =
                        displaySection != i;
            }
        }
        if (tab == CACHE) {
            int half = content / 2;
            for (int i = 0; i < 2; i++) {
                final int section = i;
                smallButton(
                                        i == 0 ? "Storage" : "PNG export",
                                        left + i * half,
                                        49,
                                        half - 2,
                                        18,
                                        () -> {
                                            read();
                                            cacheSection = section;
                                            refresh();
                                        })
                                .active =
                        cacheSection != i;
            }
        }
        if (tab == SEARCH) {
            int sectionWidth = content / SearchLayout.SECTIONS.size();
            for (int i = 0; i < SearchLayout.SECTIONS.size(); i++) {
                final int section = i;
                smallButton(
                                        SearchLayout.SECTIONS.get(i),
                                        left + i * sectionWidth,
                                        49,
                                        sectionWidth - 2,
                                        18,
                                        () -> {
                                            read();
                                            searchSection = section;
                                            refresh();
                                        })
                                .active =
                        searchSection != i;
            }
            for (var group :
                    SearchLayout.groups(
                            width,
                            height,
                            values.get("searchOrigin").equals("Coordinates"),
                            searchSection)) {
                for (int i = 0; i < group.keys().size(); i++) {
                    String key = group.keys().get(i);
                    Option option =
                            Arrays.stream(options)
                                    .filter(o -> o.key.equals(key))
                                    .findFirst()
                                    .orElseThrow();
                    var box = group.field(i);
                    if (groupKey(key) != null) {
                        int groupWidth = 40;
                        addOption(
                                option,
                                box.x(),
                                box.y(),
                                box.width() - groupWidth - 3,
                                box.height());
                        var groupButton =
                                smallButton(
                                        "Groups",
                                        box.x() + box.width() - groupWidth,
                                        box.y(),
                                        groupWidth,
                                        Math.min(16, box.height()),
                                        () -> {
                                            read();
                                            minecraft.setScreen(
                                                    new SearchGroupScreen(
                                                            this,
                                                            key,
                                                            groupTitle(key),
                                                            values.getOrDefault(groupKey(key), ""),
                                                            values.get(key),
                                                            groupCatalog(key),
                                                            key.equals("biomes")
                                                                    ? WorldBiomeGroups.kaolinHelp()
                                                                    : "Selected groups and individual names are combined. Other search fields still apply.",
                                                            (groups, names) -> {
                                                                values.put(groupKey(key), groups);
                                                                values.put(key, names);
                                                            }));
                                        });
                        groupButton.setTooltip(
                                Tooltip.create(
                                        Component.literal(
                                                "Select groups or individual entries. The selections mirror this field.")));
                    } else {
                        addOption(option, box.x(), box.y(), box.width(), box.height());
                    }
                }
            }
        } else {
            var fields =
                    SettingsLayout.fields(
                            width,
                            height,
                            options.length,
                            (tab == DISPLAY || tab == CACHE) ? 74 : 52);
            for (int n = 0; n < options.length; n++) {
                var box = fields.get(n).input();
                addOption(options[n], box.x(), box.y(), box.width(), box.height());
            }
        }
        if (tab == SEARCH) {
            var actions = SearchLayout.actions(width, height);
            var origin = actions.get(0);
            button(
                            values.get("searchOrigin"),
                            origin.x(),
                            origin.y(),
                            origin.width(),
                            () -> {
                                values.put(
                                        "searchOrigin",
                                        switch (values.get("searchOrigin")) {
                                            case "Player" -> "World spawn";
                                            case "World spawn" -> "Coordinates";
                                            default -> "Player";
                                        });
                                if (values.get("searchOrigin").equals("Coordinates")) {
                                    searchSection = 3;
                                }
                                refresh();
                            })
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            "Cycle Player, World spawn or Coordinates. Coordinates reveals X and Z fields. Then press Search.")));
            var search = actions.get(1);
            addRenderableWidget(
                    new SearchButton(search.x(), search.y(), search.width(), b -> search()));
            var presets = actions.get(2);
            button(
                    "Presets",
                    presets.x(),
                    presets.y(),
                    presets.width(),
                    () -> {
                        read();
                        minecraft.setScreen(new PresetScreen(this));
                    });
            var clear = actions.get(3);
            button("Clear", clear.x(), clear.y(), clear.width(), this::clearSearchSettings)
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            "Clear all search filters and current results; reset radius, sampling, separation and centre. Your saved presets are kept.")));
        } else if (tab == CACHE) {
            outlinedButton(
                    cacheSection == 0 ? "Clear cache" : "Export PNG",
                    left,
                    height - 66,
                    Math.min(160, content),
                    cacheSection == 0 ? this::clearCache : this::export);
        } else if (tab == DISPLAY) {
            if (displaySection == 2) {
                outlinedButton(
                        "Edit layer colours",
                        left,
                        height - 66,
                        Math.min(170, content),
                        () -> {
                            if (apply()) {
                                minecraft.setScreen(new LegendScreen(this));
                            }
                        });
            } else if (displaySection == 1) {
                int buttonWidth = Math.min(190, content);
                outlinedButton(
                        "Reset map panel positions",
                        left,
                        height - 66,
                        buttonWidth,
                        () -> {
                            read();
                            resetHud = true;
                            readers.clear();
                            refresh();
                        });
            } else if (displaySection == 0) {
                outlinedButton(
                        "Reset all Atlas settings",
                        left,
                        height - 66,
                        Math.min(190, content),
                        this::confirmResetSettings);
            }
        } else if (tab == WORLDGEN) {
            outlinedButton(
                    "Reset worldgen",
                    left,
                    height - 66,
                    110,
                    () -> {
                        read();
                        Profile defaults = new Profile();
                        for (Option o : OPTIONS[WORLDGEN]) {
                            try {
                                values.put(
                                        o.key,
                                        String.valueOf(
                                                Profile.class.getField(o.key).get(defaults)));
                            } catch (Exception ignored) {
                            }
                        }
                        readers.clear();
                        refresh();
                    });
        }
        button("Back to map", left, height - 27, Math.min(140, content), this::onClose);
    }

    private Option[] displayedOptions() {
        if (tab == CACHE) {
            return Arrays.stream(OPTIONS[CACHE])
                    .filter(option -> option.key.startsWith("export") == (cacheSection == 1))
                    .toArray(Option[]::new);
        }
        if (tab != DISPLAY) {
            return OPTIONS[tab];
        }
        var keys = dev.ryan.tfcatlas.core.DisplayLayout.keys(displaySection);
        return keys.stream()
                .map(
                        key ->
                                Arrays.stream(OPTIONS[DISPLAY])
                                        .filter(option -> option.key.equals(key))
                                        .findFirst()
                                        .orElseThrow())
                .toArray(Option[]::new);
    }

    private void confirmResetSettings() {
        read();
        minecraft.setScreen(
                new net.minecraft.client.gui.screens.ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                Profile defaults = new Profile();
                                for (Option[] options : OPTIONS) {
                                    for (Option option : options) {
                                        try {
                                            Object value =
                                                    Profile.class
                                                            .getField(option.key)
                                                            .get(defaults);
                                            values.put(
                                                    option.key,
                                                    Set.of("highlightColor").contains(option.key)
                                                            ? String.format("%06X", value)
                                                            : defaults.searchValue(option.key));
                                        } catch (ReflectiveOperationException e) {
                                            throw new IllegalStateException(e);
                                        }
                                    }
                                }
                                values.put("searchOrigin", "Player");
                                resetAll = true;
                                resetHud = true;
                                readers.clear();
                                message =
                                        "Defaults restored; saved presets and custom groups are kept.";
                            }
                            minecraft.setScreen(this);
                        },
                        Component.literal("Reset all Atlas settings?"),
                        Component.literal(
                                "Restore display, search, worldgen and export defaults. Saved presets, custom groups and map files are kept.")));
    }

    private Button smallButton(String text, int x, int y, int w, int h, Runnable action) {
        CompactButton button =
                new CompactButton(text, x, y, w, CompactEditBox.SCALE, b -> action.run())
                        .minecraftStyle();
        button.setHeight(h);
        return addRenderableWidget(button);
    }

    private void addOption(Option option, int x, int y, int w, int h) {
        if (option.choices.length > 0) {
            String[] choices =
                    option.key.equals("display")
                            ? Profile.displays(values.get("mode")).toArray(String[]::new)
                            : option.choices;
            String shown = values.get(option.key);
            if (option.key.equals("display") && !Arrays.asList(choices).contains(shown)) {
                shown = choices.length == 0 ? "—" : choices[0];
            }
            Runnable action =
                    () -> {
                        String old = values.get(option.key);
                        int at = Arrays.asList(choices).indexOf(old);
                        if (option.key.equals("display") && at < 0) {
                            at = 0;
                        }
                        values.put(option.key, choices[(at + 1) % choices.length]);
                        refresh();
                    };
            Button b = smallButton(display(option.key, shown), x, y, w, h, action);
            b.active =
                    choices.length > 0
                            && (!option.key.equals("opacity")
                                    || (Profile.displays(values.get("mode")).contains("Overlay")
                                            && values.get("display").equals("Overlay")));
            b.setHeight(h);
            b.setTooltip(Tooltip.create(Component.literal(option.label + ": " + option.help)));
        } else {
            List<String> suggestions =
                    switch (option.key) {
                        case "climateZones" -> ClimateZones.NAMES;
                        case "soils" ->
                                Arrays.stream(dev.ryan.tfcatlas.core.Soil.values())
                                        .filter(s -> s != dev.ryan.tfcatlas.core.Soil.UNKNOWN)
                                        .map(s -> Cell.label(s.name()))
                                        .toList();
                        case "rocks" -> WorldSearchGroups.rockNames();
                        case "biomes" -> biomeSuggestions();
                        case "categories" -> List.of(Cell.TYPE_NAMES);
                        case "feature" -> SearchQuery.FEATURE_NAMES;
                        default -> List.of();
                    };
            if (groupKey(option.key) != null) {
                suggestions =
                        dev.ryan.tfcatlas.core.SearchGroups.suggestions(
                                suggestions, groupCatalog(option.key));
            }
            if (groupKey(option.key) != null) {
                ListEditBox box =
                        new ListEditBox(
                                font, x, y, w, h, Component.literal(option.label), suggestions);
                box.value(values.getOrDefault(option.key, ""));
                box.responder(v -> values.put(option.key, v));
                box.setTooltip(
                        Tooltip.create(Component.literal(option.label + ": " + option.help)));
                addRenderableWidget(box);
                readers.add(() -> values.put(option.key, box.value()));
                return;
            }
            CompactEditBox box =
                    new CompactEditBox(
                            font, x, y, w, h, Component.literal(option.label), suggestions);
            if (Profile.OPTIONAL_LIMITS.contains(option.key) || !suggestions.isEmpty()) {
                box.hint("Any");
            }
            boolean hiddenSeed = option.key.equals("seed") && ClientSeed.hidden();
            box.value(SeedPrivacy.input(values.getOrDefault(option.key, ""), hiddenSeed));
            if (hiddenSeed) {
                box.hint(
                        values.getOrDefault("seed", "").isBlank()
                                ? "Auto detect / enter seed"
                                : "Seed hidden · replace here");
            }
            box.responder(v -> values.put(option.key, v));
            box.setTooltip(Tooltip.create(Component.literal(option.label + ": " + option.help)));
            addRenderableWidget(box);
            // The hidden seed is never inserted into the input or copied by its reader.
            if (!hiddenSeed) {
                readers.add(() -> values.put(option.key, box.value()));
            }
        }
    }

    private String display(String key, String value) {
        if (key.equals("mode")) {
            return value.replace(" map", " Map").replace(" only", " Only");
        }
        if (key.equals("opacity")) {
            return Math.round(Double.parseDouble(value) * 100) + "%";
        }
        if (value.equals("true")) {
            return "On";
        }
        if (value.equals("false")) {
            return "Off";
        }
        if (key.equals("layer")) {
            return Layer.valueOf(value).label;
        }
        return value;
    }

    private List<String> biomeSuggestions() {
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(Layer.categories(Layer.BIOMES, false, Map.of()).keySet());
        for (String id : WorldBiomeGroups.current().registered()) {
            if (!id.startsWith("minecraft:")) {
                names.add(id.startsWith("tfc:") ? Cell.label(id) : id.replace('_', ' '));
            }
        }
        return List.copyOf(names);
    }

    private String groupKey(String field) {
        return switch (field) {
            case "biomes" -> "biomeGroups";
            case "rocks" -> "rockGroups";
            case "climateZones" -> "climateGroups";
            default -> null;
        };
    }

    private java.util.List<dev.ryan.tfcatlas.core.BiomeGroups.Group> groupCatalog(String field) {
        return switch (field) {
            case "biomes" -> WorldBiomeGroups.current().groups();
            case "rocks" -> WorldSearchGroups.rocks();
            case "climateZones" ->
                    SavedGroups.catalog(
                            "climateZones", dev.ryan.tfcatlas.core.ClimateGroups.groups());
            default -> java.util.List.of();
        };
    }

    private Button outlinedButton(String text, int x, int y, int width, Runnable action) {
        int border =
                text.startsWith("Reset") || text.equals("Clear cache") ? 0xFFCC5555 : 0xFFAA8C48;
        return addRenderableWidget(new SearchButton(text, x, y, width, b -> action.run(), border));
    }

    private void refreshClosestResults() {
        var player = minecraft.player;
        if (AtlasClient.engine != null && player != null) {
            AtlasClient.engine.updateClosestResults(player.getBlockX(), player.getBlockZ());
        }
    }

    private void initResults(int left, int content) {
        refreshClosestResults();
        resultButtons.clear();
        RegionEngine e = AtlasClient.engine;
        shown = e == null ? List.of() : e.results;
        shownSearching = e != null && e.searching;
        if (selected != null) {
            var previous = selected;
            selected =
                    shown.stream()
                            .filter(
                                    result ->
                                            result.x() == previous.x()
                                                    && result.z() == previous.z())
                            .findFirst()
                            .orElse(null);
        }
        if (selected == null && !shown.isEmpty()) {
            selected = shown.get(0);
        }
        int pitch = dev.ryan.tfcatlas.core.ResultsLayout.rowPitch(height);
        for (int i = 0; i < Math.min(5, shown.size()); i++) {
            SearchQuery.Result r = shown.get(i);
            String y = r.surfaceY() == null ? "" : " · ~Y " + r.surfaceY();
            String text =
                    String.format(
                            Locale.ROOT,
                            "%d. %d, %d%s · %.0f blocks from player · %s · %s",
                            i + 1,
                            r.x(),
                            r.z(),
                            y,
                            ResultDistances.toPlayer(r),
                            r.rocks(),
                            Cell.label(r.cell().biome()));
            Button b =
                    button(
                            font.plainSubstrByWidth(text, content - 12),
                            left,
                            71 + i * pitch,
                            content,
                            () -> {
                                selected = r;
                                message =
                                        String.format(
                                                        Locale.ROOT,
                                                        "%s · %.1f mm · %.1f °C · %s",
                                                        r.cell().typeName(),
                                                        r.cell().rain(),
                                                        r.cell().temperature(),
                                                        Cell.label(r.cell().biome()))
                                                + y;
                            });
            resultButtons.put(b, r);
            b.setHeight(pitch - 2);
            b.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    text
                                            + String.format(
                                                    Locale.ROOT,
                                                    " · %.1f mm · %.1f °C annual mean",
                                                    r.cell().rain(),
                                                    r.cell().temperature()))));
        }
        int y = height - 54, unit = content / 4;
        button(
                "Centre",
                left,
                y,
                unit - 2,
                () -> {
                    if (selected != null && apply()) {
                        XaeroBridge.center(parent, selected.x(), selected.z());
                        minecraft.setScreen(parent);
                    }
                });
        button(
                "Waypoint",
                left + unit,
                y,
                unit - 2,
                () -> {
                    if (selected != null) {
                        XaeroBridge.waypoint(
                                parent,
                                selected.x(),
                                selected.z(),
                                selected.rocks() + " · " + Cell.label(selected.cell().biome()));
                    }
                });
        button(
                "Copy X/Z",
                left + unit * 2,
                y,
                unit - 2,
                () -> {
                    if (selected != null) {
                        minecraft.keyboardHandler.setClipboard(selected.x() + ", " + selected.z());
                        message = "Coordinates copied";
                    }
                });
        button(
                e != null && e.searching ? "Cancel" : "Clear",
                left + unit * 3,
                y,
                unit - 2,
                () -> {
                    if (e != null) {
                        if (e.searching) {
                            e.cancelSearch();
                        } else {
                            e.clearSearch();
                        }
                    }
                    refresh();
                });
        button("Back to map", left, height - 27, 100, this::onClose);
    }

    /** Keep result hover details inside the results area, clear of both footer button rows. */
    int tooltipBottom() {
        return tab == RESULTS ? Math.max(120, height - 78) : height;
    }

    private boolean shownSearching;

    @Override
    public void tick() {
        for (var child : children()) {
            if (child instanceof CompactEditBox box) {
                box.tick();
            }
        }
        if (tab == RESULTS) {
            refreshClosestResults();
        }
        updateResultDistances();
        if (tab == RESULTS
                && AtlasClient.engine != null
                && (shown != AtlasClient.engine.results
                        || shownSearching != AtlasClient.engine.searching)) {
            refresh();
        }
    }

    private void updateResultDistances() {
        if (tab != RESULTS) {
            return;
        }
        int number = 0, content = Math.min(width - 24, 620);
        for (var entry : resultButtons.entrySet()) {
            var r = entry.getValue();
            String y = r.surfaceY() == null ? "" : " · Y " + r.surfaceY();
            String text =
                    String.format(
                            Locale.ROOT,
                            "%d. %d, %d%s · %.0f blocks from player · %s · %s",
                            ++number,
                            r.x(),
                            r.z(),
                            y,
                            ResultDistances.toPlayer(r),
                            r.rocks(),
                            Cell.label(r.cell().biome()));
            entry.getKey()
                    .setMessage(Component.literal(font.plainSubstrByWidth(text, content - 12)));
            entry.getKey()
                    .setTooltip(
                            Tooltip.create(
                                    Component.literal(
                                            text
                                                    + String.format(
                                                            Locale.ROOT,
                                                            " · %.1f mm · %.1f °C annual mean",
                                                            r.cell().rain(),
                                                            r.cell().temperature()))));
        }
    }

    private Map<String, String> submittedNames(Profile p) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(
                "rocks",
                dev.ryan.tfcatlas.core.SearchGroups.typedList(p.rocks, "", groupCatalog("rocks")));
        labels.put(
                "biomes",
                dev.ryan.tfcatlas.core.SearchGroups.typedList(
                        p.biomes, "", groupCatalog("biomes")));
        labels.put(
                "climateZones",
                dev.ryan.tfcatlas.core.SearchGroups.typedList(
                        p.climateZones, "", groupCatalog("climateZones")));
        return Map.copyOf(labels);
    }

    private Profile candidate() throws Exception {
        read();
        Profile p =
                resetAll
                        ? new Profile()
                        : Profiles.JSON.fromJson(
                                Profiles.JSON.toJson(AtlasClient.profile), Profile.class);
        if (resetAll) {
            p.savedSearches.putAll(AtlasClient.profile.savedSearches);
        }
        for (var entry : values.entrySet()) {
            Field f = Profile.class.getField(entry.getKey());
            String s = entry.getValue().trim();
            if (s.isEmpty() && Profile.OPTIONAL_LIMITS.contains(entry.getKey())) {
                s = String.valueOf(f.get(new Profile()));
            }
            Object value;
            try {
                if (f.getType() == int.class) {
                    value =
                            Set.of("highlightColor").contains(entry.getKey())
                                    ? Integer.parseInt(s.replace("#", ""), 16)
                                    : Integer.valueOf(s);
                } else if (f.getType() == float.class) {
                    value = Float.valueOf(s);
                } else if (f.getType() == double.class) {
                    value = Double.valueOf(s);
                } else if (f.getType() == boolean.class) {
                    value = Boolean.valueOf(s);
                } else {
                    value = s;
                }
                f.set(p, value);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid value for " + entry.getKey());
            }
        }
        if (!p.seed.isBlank()) {
            try {
                Long.parseLong(p.seed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Seed must be a signed 64-bit whole number");
            }
        }
        if (p.temperatureScale < 0
                || p.rainfallScale < 0
                || p.spawnRadius < 0
                || p.spawnRadius > 1000000) {
            throw new IllegalArgumentException(
                    "Worldgen distances must be nonnegative (spawn ≤ 1,000,000)");
        }
        if (!Float.isFinite(p.continentalness)
                || p.continentalness < 0
                || p.continentalness > 1
                || !Float.isFinite(p.temperatureConstant)
                || Math.abs(p.temperatureConstant) > 1
                || !Float.isFinite(p.rainfallConstant)
                || Math.abs(p.rainfallConstant) > 1) {
            throw new IllegalArgumentException("Check continentalness and constant climate inputs");
        }
        if (!Double.isFinite(p.opacity)
                || p.opacity < 0
                || p.opacity > 1
                || !Double.isFinite(p.highlightOpacity)
                || p.highlightOpacity < 0
                || p.highlightOpacity > 1) {
            throw new IllegalArgumentException("Opacity must be between 0 and 1");
        }
        if (p.minY < -64 || p.maxY > 319) {
            throw new IllegalArgumentException("Surface Y must be between -64 and 319");
        }
        if (p.radius < 128 || p.resultSpacing < 0) {
            throw new IllegalArgumentException("Search radius ≥ 128; separation ≥ 0");
        }
        if (Math.abs((long) p.searchX) > 29_999_984 || Math.abs((long) p.searchZ) > 29_999_984) {
            throw new IllegalArgumentException("Search X/Z must be within ±29,999,984");
        }
        if (resetHud) {
            p.resetHud();
        }
        p.query(WorldBiomeGroups.current());
        p.searchResolution();
        p.validate();
        return p;
    }

    private boolean apply() {
        try {
            Profile next = candidate(), old = AtlasClient.profile;
            AtlasClient.error = "";
            String before = generationKey(old), after = generationKey(next);
            AtlasClient.profile = next;
            resetHud = resetAll = false;
            AtlasClient.save();
            if (!before.equals(after) || AtlasClient.engine == null) {
                AtlasClient.rebuild();
            }
            message = AtlasClient.error.isBlank() ? "Saved" : AtlasClient.error;
            return AtlasClient.error.isBlank();
        } catch (Exception ex) {
            message = ex.getMessage();
            return false;
        }
    }

    private static String generationKey(Profile p) {
        return p.seed
                + ":"
                + p.useLocalSettings
                + ":"
                + p.spawnX
                + ":"
                + p.spawnZ
                + ":"
                + p.spawnRadius
                + ":"
                + p.temperatureScale
                + ":"
                + p.rainfallScale
                + ":"
                + p.temperatureConstant
                + ":"
                + p.rainfallConstant
                + ":"
                + p.continentalness
                + ":"
                + p.finiteContinents
                + ":"
                + p.memoryTiles
                + ":"
                + p.diskCache
                + ":"
                + p.diskMB;
    }

    private void clearSearchSettings() {
        read();
        AtlasClient.profile.clearSearchSettings();
        for (Option o : OPTIONS[SEARCH]) {
            if (!o.key.equals("highlights")) {
                values.put(o.key, AtlasClient.profile.searchValue(o.key));
            }
        }
        values.put("searchOrigin", AtlasClient.profile.searchOrigin);
        values.computeIfPresent("climateZones", (key, value) -> ClimateZones.displayList(value));
        mirrorGroupFields();
        if (AtlasClient.engine != null) {
            AtlasClient.engine.clearSearch();
        }
        AtlasClient.save();
        selected = null;
        readers.clear();
        refresh();
        message = "Search cleared. Blank filters match any value.";
    }

    private void search() {
        if (!apply()) {
            return;
        }
        if (AtlasClient.engine == null) {
            message = "Enter a seed first";
            return;
        }
        Profile p = AtlasClient.profile;
        // Resolve once: validation, warnings and the worker use the same immutable selection.
        SearchQuery query = p.query(WorldBiomeGroups.current());
        try {
            WorldSearchGroups.requireKnownRocks(query);
        } catch (IllegalArgumentException invalid) {
            message = invalid.getMessage();
            return;
        }
        if (minecraft.level == null
                || minecraft.player == null
                || !minecraft.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            message = "Search from the Overworld";
            return;
        }
        var origin =
                p.searchOrigin.equals("World spawn")
                        ? minecraft.level.getSharedSpawnPos()
                        : minecraft.player.blockPosition();
        int x = p.searchOrigin.equals("Coordinates") ? p.searchX : origin.getX(),
                z = p.searchOrigin.equals("Coordinates") ? p.searchZ : origin.getZ();
        p.showSearchLayer();
        values.put("layer", p.layer);
        AtlasClient.save();
        selected = null;
        Runnable start =
                () -> {
                    AtlasClient.engine.search(
                            query,
                            x,
                            z,
                            p.radius,
                            p.resultLimit,
                            p.resultSpacing,
                            p.searchResolution(),
                            p.precision.equals("Auto"),
                            submittedNames(p));
                    tab = RESULTS;
                    minecraft.setScreen(this);
                };
        SearchWorkload work =
                AtlasClient.engine.searchWorkload(
                        query, x, z, p.radius, p.searchResolution(), !p.precision.equals("Auto"));
        if (work.warn()) {
            minecraft.setScreen(
                    new net.minecraft.client.gui.screens.ConfirmScreen(
                            confirmed -> {
                                if (confirmed) {
                                    start.run();
                                } else {
                                    minecraft.setScreen(this);
                                }
                            },
                            Component.literal("This search may take longer"),
                            Component.literal(work.confirmation(p.searchResolution())),
                            Component.literal("Search anyway"),
                            Component.literal("Back")));
        } else {
            start.run();
        }
    }

    private void clearCache() {
        RegionEngine e = AtlasClient.engine;
        if (e == null) {
            return;
        }
        Path dir = e.directory;
        e.close();
        AtlasClient.engine = null;
        AtlasClient.renderer.clear();
        java.util.concurrent.CompletableFuture.runAsync(
                () -> {
                    try {
                        e.awaitStopped();
                        if (Files.exists(dir)) {
                            try (var files = Files.list(dir)) {
                                for (Path p : files.toList()) {
                                    if (p.toString().endsWith(".gz")) {
                                        Files.deleteIfExists(p);
                                    }
                                }
                            }
                        }
                        minecraft.execute(
                                () -> {
                                    AtlasClient.rebuild();
                                    message = "This seed's cache cleared";
                                });
                    } catch (Exception ex) {
                        minecraft.execute(
                                () -> {
                                    AtlasClient.rebuild();
                                    message = "Cache: " + ex.getMessage();
                                });
                    }
                });
    }

    private void export() {
        if (!apply() || AtlasClient.engine == null) {
            return;
        }
        var p = AtlasClient.profile;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var view = AtlasClient.view;
        boolean player = p.exportCenter.equals("Player") && mc.player != null;
        int x = player ? mc.player.getBlockX() : view == null ? 0 : (int) view.x();
        int z = player ? mc.player.getBlockZ() : view == null ? 0 : (int) view.z();
        AtlasClient.engine.export(x, z, p);
        message = "Exporting PNG in the background…";
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float delta) {
        g.drawCenteredString(font, "TFC Atlas · " + TABS[tab], width / 2, 8, 0xE8D9B6);
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        if (tab == SEARCH) {
            for (var group :
                    SearchLayout.groups(
                            width,
                            height,
                            values.get("searchOrigin").equals("Coordinates"),
                            searchSection)) {
                smallText(
                        g,
                        group.label(),
                        group.bounds().x(),
                        group.bounds().y(),
                        group.bounds().width(),
                        0xCEC9BD);
            }
            smallText(
                    g,
                    "Search centred on:",
                    left,
                    height - 72,
                    SearchLayout.actions(width, height).get(0).width(),
                    0xE8D9B6);
        } else if (tab != RESULTS) {
            var options = displayedOptions();
            var fields =
                    SettingsLayout.fields(
                            width,
                            height,
                            options.length,
                            (tab == DISPLAY || tab == CACHE) ? 74 : 52);
            for (int n = 0; n < fields.size(); n++) {
                var label = fields.get(n).label();
                smallText(g, options[n].label, label.x(), label.y(), label.width(), 0xCEC9BD);
            }
        } else {
            RegionEngine e = AtlasClient.engine;
            String heading =
                    e == null
                            ? "Set a seed first"
                            : e.searching
                                    ? e.searchStatus
                                    : e.searchOverlay == SearchOverlay.EMPTY
                                                    || !e.searchOverlay.complete()
                                                    || e.activeQuery == null
                                            ? e.searchStatus
                                            : "Showing "
                                                    + e.results.size()
                                                    + " closest matches · "
                                                    + e.searchOverlay.areaCount()
                                                    + " total distinct areas";
            fittedText(g, heading, left, 49, content, .8f, 0xD3CBAA);
            if (e != null && e.searchDetails != null) {
                fittedText(
                        g,
                        "Search radius: "
                                + e.searchRadius
                                + " blocks · Sampling: "
                                + e.searchDetails.resolution()
                                + " blocks"
                                + (e.activeQuery.dikes() ? " · Dike outlines: 8 blocks" : ""),
                        left,
                        60,
                        content,
                        .8f,
                        0xB9B5A7);
            }
        }
        if (tab == RESULTS && AtlasClient.engine != null) {
            fittedText(
                    g, AtlasClient.engine.searchAdvice, left, height - 73, content, .75f, 0xC9C49E);
        }
        if (tab == CACHE && cacheSection == 0) {
            var fields = SettingsLayout.fields(width, height, displayedOptions().length, 74);
            int top =
                    fields.stream()
                                    .mapToInt(f -> f.input().y() + f.input().height())
                                    .max()
                                    .orElse(100)
                            + 5;
            var lines = AtlasClient.cacheStatus();
            float pitch = Math.min(12, Math.max(6, (height - 72 - top) / (float) lines.size()));
            for (int i = 0; i < lines.size(); i++) {
                fittedText(
                        g,
                        lines.get(i),
                        left,
                        top + Math.round(i * pitch),
                        content,
                        Math.min(.75f, pitch / 10),
                        i == 0 ? 0xE8D9B6 : 0xB9B5A7);
            }
        }
        if (!loadedPreset.isBlank()) {
            int alpha =
                    dev.ryan.tfcatlas.core.PresetNotice.alpha(
                            net.minecraft.Util.getMillis() - presetLoadedAt);
            if (alpha > 0) {
                int x = left + (tab == RESULTS ? 100 : Math.min(140, content)) + 8;
                fittedText(
                        g,
                        loadedPreset,
                        x,
                        height - 21,
                        Math.max(1, left + content - x),
                        .75f,
                        (alpha << 24) | 0xFFD36A);
            }
        }
        if (!message.isBlank()) {
            fittedText(
                    g,
                    message,
                    left,
                    tab == RESULTS
                            ? dev.ryan.tfcatlas.core.ResultsLayout.statusY(
                                    height,
                                    AtlasClient.engine != null
                                            && !AtlasClient.engine.searchAdvice.isBlank())
                            : height - 39,
                    content,
                    .7f,
                    0xFFE098);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (apply()) {
            minecraft.setScreen(parent);
        }
    }

    private void fittedText(
            GuiGraphics g, String text, int x, int y, int w, float scale, int colour) {
        float fit = Math.min(scale, (float) w / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(fit, fit, 1);
        g.drawString(font, text, 0, 0, colour);
        g.pose().popPose();
    }

    private void smallText(GuiGraphics g, String text, int x, int y, int w, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(CompactEditBox.SCALE, CompactEditBox.SCALE, 1);
        g.drawString(
                font,
                font.plainSubstrByWidth(text, (int) (w / CompactEditBox.SCALE)),
                0,
                0,
                colour);
        g.pose().popPose();
    }

    private final class PresetScreen extends AtlasMenuScreen {
        private final AtlasScreen back;
        private final dev.ryan.tfcatlas.core.SmoothScroll presetScroll =
                new dev.ryan.tfcatlas.core.SmoothScroll();

        private record Entry(String label, Profile profile, String savedName) {}

        PresetScreen(AtlasScreen b) {
            super(Component.literal("Search presets"));
            back = b;
        }

        private void load(Profile p, String name) {
            for (Option o : OPTIONS[SEARCH]) {
                try {
                    back.values.put(o.key, p.searchValue(o.key));
                } catch (Exception ignored) {
                }
            }
            back.values.put("searchOrigin", p.searchOrigin);
            back.mirrorGroupFields();
            back.loadedPreset = "Preset: " + name + " loaded";
            back.presetLoadedAt = net.minecraft.Util.getMillis();
            minecraft.setScreen(back);
        }

        @Override
        protected void init() {
            EditBox name =
                    new HoverEditBox(
                            font,
                            (width - Math.min(width - 24, 420)) / 2,
                            48,
                            Math.min(width - 24, 420),
                            20,
                            Component.literal("Preset name"));
            name.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    "Enter a name for this saved search. Save stores the current search filters, not the world seed.")));
            name.setValue(presetName);
            name.setResponder(v -> presetName = v);
            addRenderableWidget(name);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Save current search"),
                                    b -> {
                                        presetName = name.getValue().trim();
                                        if (presetName.isEmpty()) {
                                            return;
                                        }
                                        try {
                                            Profile p = back.candidate();
                                            AtlasClient.profile.savedSearches.put(
                                                    presetName, Profiles.preset(p));
                                            AtlasClient.save();
                                            rebuildWidgets();
                                        } catch (Exception e) {
                                            back.message = e.getMessage();
                                        }
                                    })
                            .bounds(
                                    (width - Math.min(width - 24, 420)) / 2,
                                    74,
                                    Math.min(width - 24, 420),
                                    20)
                            .build());
            List<Entry> entries = new ArrayList<>();
            AtlasClient.profile.savedSearches.forEach(
                    (label, json) -> {
                        try {
                            entries.add(
                                    new Entry(
                                            "Load preset: " + label,
                                            Profiles.decode(
                                                    com.google.gson.JsonParser.parseString(json)
                                                            .getAsJsonObject()),
                                            label));
                        } catch (RuntimeException ignored) {
                        }
                    });
            entries.sort(
                    java.util.Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
            int content = Math.min(width - 24, 420), left = (width - content) / 2;
            var list =
                    addRenderableWidget(
                            new MinecraftScrollPanel(
                                    left, 100, content, Math.max(28, height - 140), presetScroll));
            int row = 0;
            for (var entry : entries) {
                int y = row++ * 28, remove = entry.savedName() == null ? 0 : 28;
                list.cell(
                        0,
                        y,
                        list.contentWidth() - remove,
                        24,
                        entry::label,
                        () -> load(entry.profile(), entry.savedName()),
                        entry.label(),
                        null);
                if (entry.savedName() != null) {
                    list.cell(
                            list.contentWidth() - 24,
                            y,
                            24,
                            24,
                            () -> "X",
                            () -> {
                                AtlasClient.profile.savedSearches.remove(entry.savedName());
                                AtlasClient.save();
                                rebuildWidgets();
                            },
                            "Delete this preset",
                            null);
                }
            }
            addRenderableWidget(
                    Button.builder(Component.literal("Back"), ignored -> minecraft.setScreen(back))
                            .bounds(left, height - 28, content, 20)
                            .build());
        }

        @Override
        public void renderContent(GuiGraphics g, int x, int y, float d) {
            g.drawCenteredString(font, "Saved searches", width / 2, 22, 0xffffff);
            if (AtlasClient.profile.savedSearches.isEmpty()) {
                g.drawCenteredString(
                        font,
                        "No presets yet. Save your own search above.",
                        width / 2,
                        108,
                        0xCEC9BD);
            }
        }

        @Override
        public void onClose() {
            minecraft.setScreen(back);
        }
    }
}
