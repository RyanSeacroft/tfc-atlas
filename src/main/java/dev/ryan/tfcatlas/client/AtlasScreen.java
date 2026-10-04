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
import java.util.HashSet;
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

    private static final String[] TABS = {
        "World", "Search", "Results", "Display", "Worldgen", "Cache"
    };
    private static final Option[][] OPTIONS = {
        {
            o(
                    "seed",
                    "Numeric world seed",
                    "Leave blank for verified TFC multiplayer detection. Manual multiplayer seeds are used only for this session and are not saved."),
            o(
                    "mode",
                    "Coverage",
                    "Full Map can show TFC in unexplored terrain. Explored Only restricts TFC rendering to discovered terrain. Saved Xaero maps stay untouched.",
                    "Full map",
                    "Explored only"),
            o(
                    "useLocalSettings",
                    "Use singleplayer settings",
                    "Read the actual generator settings in singleplayer; multiplayer uses the Worldgen tab.",
                    "true",
                    "false"),
            o(
                    "layer",
                    "Data layer",
                    "All layers share the cached samples.",
                    Arrays.stream(Layer.values()).map(Enum::name).toArray(String[]::new)),
            o(
                    "spawn",
                    "Spawn search area",
                    "Gold square marks the configured search bounds, not a promised spawn point.",
                    "true",
                    "false")
        },
        {
            o(
                    "atlasEnabled",
                    "TFC Atlas",
                    "Turns Atlas rendering on or off immediately without changing Xaero's stored map.",
                    "true",
                    "false"),
            o(
                    "climateZones",
                    "Climate zones: names separated by commas",
                    "TFC sea-level climate names, such as Hot Desert or Oceanic. Tab completes each comma-separated name. Blank = any."),
            o(
                    "minGroundwater",
                    "Minimum groundwater potential (mm)",
                    "At least this much annual rainfall plus river groundwater before elevation reduction. High river banks may have less actual water. Blank = no minimum."),
            o(
                    "maxGroundwater",
                    "Maximum groundwater potential (mm)",
                    "Upper bound on potential, not guaranteed surface groundwater. Blank = no maximum."),
            o(
                    "rocks",
                    "Rocks: names separated by commas",
                    "Examples: granite, gneiss. Blank = any. Tab completes each comma-separated name."),
            o(
                    "biomes",
                    "Biomes: names separated by commas",
                    "Examples: plains, rolling hills. Blank = any. Tab completes each comma-separated name."),
            o(
                    "categories",
                    "Rock regions",
                    "Surface geology: Oceanic, Volcanic, Land or Uplift. All can have deeper strata, so Uplift + Bottom is valid. Commas = any listed region; blank = any."),
            o(
                    "feature",
                    "Terrain features",
                    "Comma-separated: Land, Ocean, River, Lake, Mountain, Coast. Blank = any; Tab completes a name."),
            o(
                    "minRain",
                    "Minimum rainfall (mm)",
                    "Use 20 here for at least 20 mm of rainfall. Blank = no minimum."),
            o(
                    "maxRain",
                    "Maximum rainfall (mm)",
                    "Use 200 here for at most 200 mm. Blank = no maximum."),
            o(
                    "minTemp",
                    "Minimum annual temperature (°C)",
                    "Use 20 here for at least 20 °C. Blank = no minimum."),
            o(
                    "maxTemp",
                    "Maximum annual temperature (°C)",
                    "Annual mean, not current weather or elevation-adjusted temperature. Blank = no maximum."),
            o(
                    "radius",
                    "Search radius (blocks)",
                    "Default: 16,384 blocks. Auto: 128 to 262,144. Expensive searches ask for confirmation; reducing radius lowers their cost. Manual sampling removes the radius cap. Searches stop at the world border."),
            o(
                    "searchX",
                    "Search centre X",
                    "X coordinate used only when Coordinates is selected. Range: -29,999,984 to 29,999,984."),
            o(
                    "searchZ",
                    "Search centre Z",
                    "Z coordinate used only when Coordinates is selected. Range: -29,999,984 to 29,999,984."),
            o(
                    "resultSpacing",
                    "Candidate separation (blocks)",
                    "Minimum straight-line X/Z distance between each of the five closest listed matches. Does not change highlighted areas or the distinct-area count."),
            o(
                    "highlights",
                    "Highlight matches",
                    "Show outlined regions with a highlighted interior as matches are found, including while the search runs.",
                    "true",
                    "false"),
            o(
                    "minY",
                    "Minimum surface Y",
                    "Predicted ground height in blocks. Use 100 for terrain at Y 100 or higher. TFC base-height prediction at the selected 16–512-block sample spacing; local terrain can differ."),
            o(
                    "maxY",
                    "Maximum surface Y",
                    "Inclusive maximum predicted ground Y. Normal world range: -64 to 319. This filters terrain height; choose a rock layer separately for host rocks."),
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
                    "Top / Middle / Bottom select geological strata, not fixed Y levels. Any layer checks all three. Dikes finds predicted granite, diorite or gabbro intrusions, including buried ones; use fine sampling. Not confirmed ore.",
                    "Top",
                    "Middle",
                    "Bottom",
                    "Any layer",
                    "Dikes")
        },
        {},
        {
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
                    "accessible",
                    "Alternate categorical palette",
                    "An alternate palette; names remain the reliable way to identify many categories.",
                    "true",
                    "false"),
            o(
                    "labels",
                    "Candidate labels",
                    "Names on the active Rocks/Biomes layer when their region has room.",
                    "true",
                    "false"),
            o(
                    "labelSpacing",
                    "Candidate label spacing",
                    "50 to 400 GUI pixels for search candidate labels. Region names are positioned by their visible shape."),
            o(
                    "highlightColor",
                    "Highlight colour (hex)",
                    "Six hexadecimal digits, for example FFFF55. Used for searches without a rock filter; rock searches use layer colours."),
            o(
                    "highlightOpacity",
                    "Highlight strength (0–1)",
                    "Strength of the search tint. Rock-filter searches use the Top / Middle / Bottom match colours."),
            o("outline", "Spawn outline width (pixels)", "0 to 5."),
            o(
                    "toolbarX",
                    "Toolbar preferred X",
                    "Placement avoids native controls and fixed screen margins."),
            o(
                    "toolbarY",
                    "Toolbar preferred Y",
                    "Preferred position; adjusted when a native control is in the way.")
        },
        {
            o(
                    "finiteContinents",
                    "Finite continents",
                    "Use TFC 1.21 finite continents. Must match the world generator; singleplayer settings are read automatically.",
                    "false",
                    "true"),
            o(
                    "temperatureScale",
                    "Temperature scale (blocks)",
                    "Default 20000. Use zero for a constant climate axis."),
            o("rainfallScale", "Rainfall scale (blocks)", "Default 20000."),
            o(
                    "temperatureConstant",
                    "Constant temperature input (-1–1)",
                    "Used only when temperature scale is zero."),
            o(
                    "rainfallConstant",
                    "Constant rainfall input (-1–1)",
                    "Used only when rainfall scale is zero."),
            o("continentalness", "Continentalness (0–1)", "Default 0.5. Must match the server."),
            o("spawnX", "Spawn search centre X", "Default 0."),
            o("spawnZ", "Spawn search centre Z", "Default 0."),
            o(
                    "spawnRadius",
                    "Spawn search half-width",
                    "Default 4000 blocks. The search uses a square spiral.")
        },
        {
            o(
                    "diskCache",
                    "Save generated tiles to disk",
                    "Versioned by world, seed, TFC version and settings.",
                    "true",
                    "false"),
            o("memoryTiles", "Memory tile limit", "64 to 4096. Each tile stores all layers."),
            o(
                    "diskMB",
                    "Disk limit per seed (MiB)",
                    "16 to 8192. Older tiles are removed automatically.")
        }
    };
    private final Screen parent;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<Runnable> readers = new ArrayList<>();
    private int tab;
    private boolean resetHud;
    private String message = "", presetName = "My search";
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
                            option.key.equals("highlightColor")
                                    ? String.format("%06X", v)
                                    : AtlasClient.profile.searchValue(option.key));
                } catch (Exception ignored) {
                }
            }
        }
        values.put("searchOrigin", AtlasClient.profile.searchOrigin);
        values.computeIfPresent("climateZones", (key, value) -> ClimateZones.displayList(value));
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
            button(TABS[t], left + t * tw, 25, tw - 2, () -> navigate(next)).active = t != tab;
        }
        if (tab == 2) {
            initResults(left, content);
            return;
        }
        Option[] options = OPTIONS[tab];
        if (tab == 1) {
            for (var group :
                    SearchLayout.groups(
                            width, height, values.get("searchOrigin").equals("Coordinates"))) {
                for (int i = 0; i < group.keys().size(); i++) {
                    String key = group.keys().get(i);
                    Option option =
                            Arrays.stream(options)
                                    .filter(o -> o.key.equals(key))
                                    .findFirst()
                                    .orElseThrow();
                    var box = group.field(i);
                    addOption(option, box.x(), box.y(), box.width(), box.height());
                }
            }
        } else {
            var fields = SettingsLayout.fields(width, height, options.length);
            for (int n = 0; n < options.length; n++) {
                var box = fields.get(n).input();
                addOption(options[n], box.x(), box.y(), box.width(), box.height());
            }
        }
        if (tab == 1) {
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
        } else if (tab == 5) {
            button(
                    "Clear cache",
                    left + 60,
                    height - 66,
                    Math.min(130, (content - 65) / 2),
                    this::clearCache);
            button(
                    "Export PNG",
                    left + 64 + Math.min(130, (content - 65) / 2),
                    height - 66,
                    Math.min(124, (content - 65) / 2),
                    this::export);
        } else if (tab == 3) {
            button(
                    "Edit colours",
                    left + 60,
                    height - 66,
                    100,
                    () -> {
                        if (apply()) {
                            minecraft.setScreen(new LegendScreen(this));
                        }
                    });
            button(
                    "Reset UI",
                    left + 164,
                    height - 66,
                    75,
                    () -> {
                        read();
                        values.put("toolbarX", "32");
                        values.put("toolbarY", "40");
                        resetHud = true;
                        readers.clear();
                        refresh();
                    });
        } else if (tab == 4) {
            button(
                    "Reset worldgen",
                    left + 60,
                    height - 66,
                    110,
                    () -> {
                        read();
                        Profile defaults = new Profile();
                        for (Option o : OPTIONS[4]) {
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
        button(
                "Apply",
                left,
                height - 27,
                70,
                () -> {
                    if (apply()) {
                        message = "Saved. Return to the map to view the result.";
                    }
                });
        button(
                "Back to map",
                left + 74,
                height - 27,
                100,
                () -> {
                    if (apply()) {
                        minecraft.setScreen(parent);
                    }
                });
        button("Cancel", left + 178, height - 27, 65, () -> minecraft.setScreen(parent));
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
                        case "rocks" ->
                                List.copyOf(
                                        Layer.categories(Layer.ROCKS, false, Map.of()).keySet());
                        case "biomes" ->
                                List.copyOf(
                                        Layer.categories(Layer.BIOMES, false, Map.of()).keySet());
                        case "categories" -> List.of(Cell.TYPE_NAMES);
                        case "feature" -> SearchQuery.FEATURE_NAMES;
                        default -> List.of();
                    };
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

    private void initResults(int left, int content) {
        RegionEngine e = AtlasClient.engine;
        shown = e == null ? List.of() : e.results;
        shownSearching = e != null && e.searching;
        if (selected != null && !shown.contains(selected)) {
            selected = null;
        }
        if (selected == null && !shown.isEmpty()) {
            selected = shown.get(0);
        }
        int pitch = Math.min(24, Math.max(20, (height - 126) / 5));
        for (int i = 0; i < Math.min(5, shown.size()); i++) {
            SearchQuery.Result r = shown.get(i);
            String y = r.surfaceY() == null ? "" : " · ~Y " + r.surfaceY();
            String text =
                    String.format(
                            Locale.ROOT,
                            "%d. %d, %d%s · %.0f m · %s · %s",
                            i + 1,
                            r.x(),
                            r.z(),
                            y,
                            r.distance(),
                            r.rocks(),
                            Cell.label(r.cell().biome()));
            Button b =
                    button(
                            font.plainSubstrByWidth(text, content - 12),
                            left,
                            71 + i * Math.min(pitch, 20),
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
            b.setTooltip(
                    Tooltip.create(
                            Component.literal(
                                    text
                                            + String.format(
                                                    Locale.ROOT,
                                                    " · %.1f mm · %.1f °C annual mean",
                                                    r.cell().rain(),
                                                    r.cell().temperature())
                                            + " (predicted host rocks, not confirmed ore)")));
        }
        int y = height - 54, unit = content / 4;
        button(
                "Centre",
                left,
                y,
                unit - 2,
                () -> {
                    if (selected != null) {
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
        button("Back to map", left, height - 27, 100, () -> minecraft.setScreen(parent));
        button("Refresh", left + 104, height - 27, 68, this::refresh);
    }

    private boolean shownSearching;

    @Override
    public void tick() {
        for (var child : children()) {
            if (child instanceof CompactEditBox box) {
                box.tick();
            }
        }
        if (tab == 2
                && AtlasClient.engine != null
                && (shown != AtlasClient.engine.results
                        || shownSearching != AtlasClient.engine.searching)) {
            refresh();
        }
    }

    private Profile candidate() throws Exception {
        read();
        Profile p =
                Profiles.JSON.fromJson(Profiles.JSON.toJson(AtlasClient.profile), Profile.class);
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
                            entry.getKey().equals("highlightColor")
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
        p.query();
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
            AtlasClient.prepareCoverage();
            resetHud = false;
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
        for (Option o : OPTIONS[1]) {
            if (!o.key.equals("highlights")) {
                values.put(o.key, AtlasClient.profile.searchValue(o.key));
            }
        }
        values.put("searchOrigin", AtlasClient.profile.searchOrigin);
        values.computeIfPresent("climateZones", (key, value) -> ClimateZones.displayList(value));
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
        Set<String> rockNames = new HashSet<>(), biomeNames = new HashSet<>();
        Layer.categories(Layer.ROCKS, false, Map.of())
                .keySet()
                .forEach(s -> rockNames.add(s.toLowerCase(Locale.ROOT).replace(' ', '_')));
        Layer.categories(Layer.BIOMES, false, Map.of())
                .keySet()
                .forEach(s -> biomeNames.add(s.toLowerCase(Locale.ROOT).replace(' ', '_')));
        for (String s : SearchQuery.names(p.rocks)) {
            if (!rockNames.contains(
                    s.substring(Math.max(s.lastIndexOf(':'), s.lastIndexOf('/')) + 1))) {
                message = "Unknown rock: " + s + " (see Legend)";
                return;
            }
        }
        for (String s : SearchQuery.names(p.biomes)) {
            if (!biomeNames.contains(s.substring(s.lastIndexOf(':') + 1))) {
                message = "Unknown biome: " + s + " (see Legend)";
                return;
            }
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
                            p.query(),
                            x,
                            z,
                            p.radius,
                            p.resultLimit,
                            p.resultSpacing,
                            p.searchResolution(),
                            p.precision.equals("Auto"));
                    tab = 2;
                    minecraft.setScreen(this);
                };
        SearchWorkload work =
                AtlasClient.engine.searchWorkload(
                        p.query(),
                        x,
                        z,
                        p.radius,
                        p.searchResolution(),
                        !p.precision.equals("Auto"));
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
        AtlasClient.engine.export(
                (int) AtlasClient.view.x(), (int) AtlasClient.view.z(), AtlasClient.profile);
        message = "Exporting 32 km map in background…";
    }

    @Override
    public void renderContent(GuiGraphics g, int mx, int my, float delta) {
        g.drawCenteredString(font, "TFC Atlas · " + TABS[tab], width / 2, 8, 0xE8D9B6);
        int content = Math.min(width - 24, 620), left = (width - content) / 2;
        if (tab == 1) {
            for (var group :
                    SearchLayout.groups(
                            width, height, values.get("searchOrigin").equals("Coordinates"))) {
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
        } else if (tab != 2) {
            var fields = SettingsLayout.fields(width, height, OPTIONS[tab].length);
            for (int n = 0; n < fields.size(); n++) {
                var label = fields.get(n).label();
                smallText(g, OPTIONS[tab][n].label, label.x(), label.y(), label.width(), 0xCEC9BD);
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
        if (tab == 2 && AtlasClient.engine != null) {
            fittedText(
                    g, AtlasClient.engine.searchAdvice, left, height - 65, content, .75f, 0xC9C49E);
        }
        if (tab == 5) {
            var fields = SettingsLayout.fields(width, height, OPTIONS[5].length);
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
        if (!message.isBlank()) {
            g.drawString(
                    font, font.plainSubstrByWidth(message, content), left, height - 39, 0xFFE098);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
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
        private int presetScroll;

        private record Entry(String label, Profile profile, String savedName) {}

        PresetScreen(AtlasScreen b) {
            super(Component.literal("Search presets"));
            back = b;
        }

        private void load(Profile p) {
            for (Option o : OPTIONS[1]) {
                try {
                    back.values.put(o.key, p.searchValue(o.key));
                } catch (Exception ignored) {
                }
            }
            back.values.put("searchOrigin", p.searchOrigin);
            minecraft.setScreen(back);
        }

        @Override
        protected void init() {
            EditBox name =
                    new HoverEditBox(
                            font, width / 2 - 120, 48, 240, 20, Component.literal("Preset name"));
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
                                            minecraft.setScreen(back);
                                        } catch (Exception e) {
                                            back.message = e.getMessage();
                                            minecraft.setScreen(back);
                                        }
                                    })
                            .bounds(width / 2 - 120, 74, 240, 20)
                            .build());
            List<Entry> entries = new ArrayList<>();
            AtlasClient.profile.savedSearches.forEach(
                    (label, json) -> {
                        try {
                            entries.add(
                                    new Entry(
                                            "Load: " + label,
                                            Profiles.decode(
                                                    com.google.gson.JsonParser.parseString(json)
                                                            .getAsJsonObject()),
                                            label));
                        } catch (RuntimeException ignored) {
                        }
                    });
            int capacity = Math.max(1, (height - 146) / 24);
            presetScroll = Math.max(0, Math.min(presetScroll, entries.size() - capacity));
            for (int i = presetScroll; i < Math.min(entries.size(), presetScroll + capacity); i++) {
                Entry entry = entries.get(i);
                int y = 100 + (i - presetScroll) * 24;
                var b =
                        addRenderableWidget(
                                Button.builder(
                                                Component.literal(
                                                        font.plainSubstrByWidth(
                                                                entry.label(), 198)),
                                                button -> load(entry.profile()))
                                        .bounds(width / 2 - 120, y, 210, 20)
                                        .build());
                b.setTooltip(Tooltip.create(Component.literal(entry.label())));
                if (entry.savedName() != null) {
                    addRenderableWidget(
                            Button.builder(
                                            Component.literal("X"),
                                            button -> {
                                                AtlasClient.profile.savedSearches.remove(
                                                        entry.savedName());
                                                AtlasClient.save();
                                                rebuildWidgets();
                                            })
                                    .bounds(width / 2 + 94, y, 26, 20)
                                    .build());
                }
            }
            addRenderableWidget(
                                    Button.builder(
                                                    Component.literal("↑"),
                                                    b -> {
                                                        presetScroll--;
                                                        rebuildWidgets();
                                                    })
                                            .bounds(width / 2 - 120, height - 28, 24, 20)
                                            .build())
                            .active =
                    entries.size() > capacity;
            addRenderableWidget(
                    Button.builder(Component.literal("Back"), b -> minecraft.setScreen(back))
                            .bounds(width / 2 - 50, height - 28, 100, 20)
                            .build());
            addRenderableWidget(
                                    Button.builder(
                                                    Component.literal("↓"),
                                                    b -> {
                                                        presetScroll++;
                                                        rebuildWidgets();
                                                    })
                                            .bounds(width / 2 + 96, height - 28, 24, 20)
                                            .build())
                            .active =
                    entries.size() > capacity;
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
        public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
            presetScroll = Math.max(0, presetScroll - (int) Math.signum(delta) * 3);
            rebuildWidgets();
            return true;
        }

        @Override
        public void onClose() {
            minecraft.setScreen(back);
        }
    }
}
