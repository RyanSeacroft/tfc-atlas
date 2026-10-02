package dev.ryan.tfcatlas.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.NearestMatches;
import dev.ryan.tfcatlas.core.PrecacheArea;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.RockPossibilities;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchLayout;
import dev.ryan.tfcatlas.core.SearchQuery;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileLoadFrontier;
import dev.ryan.tfcatlas.core.TileWindow;
import dev.ryan.tfcatlas.core.ToolbarLayout;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class QuickFixTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        loading();
        legends();
        search();
        nearest();
        toolbar();
        System.out.println(
                "PASS: "
                        + checks
                        + " cache-upload, stratum-key, coordinate-search and small-toolbar regression checks");
    }

    private static void loading() {
        var w = TileWindow.visible(1337, -3199, .001, 854, 480, PrecacheArea.STEP);
        var frontier = new TileLoadFrontier(w);
        Set<Tile.Key> painted = new HashSet<>();
        List<Tile.Key> all = new ArrayList<>();
        w.forEach(all::add);
        var batch = frontier.next(painted::contains);
        var first = batch.get(0);
        check(
                frontier.readyPrefix(k -> !k.equals(first)).isEmpty(),
                "Even an inner ring waits if the very first centre tile is missing");
        check(
                frontier.readyPrefix(k -> k.equals(first)).equals(Set.of(first)),
                "First available centre paints without waiting for all 64");
        // Reproduce a warm LRU: only the final outer tiles survive the previous layer's scan.
        for (var key : all.subList(all.size() - 512, all.size())) {
            check(
                    !frontier.allowsUpload(key),
                    "Resident outer ring waits for missing centre disk uploads");
        }
        for (var key : batch) {
            check(frontier.allowsUpload(key), "Central ready tile can paint immediately");
        }
        check(frontier.allowsUpload(Tile.Key.at(1337, -3199, 4)), "Cached fine centre is allowed");
        check(
                !frontier.allowsUpload(Tile.Key.at(400000, 200000, 4)),
                "Cached fine outer ring cannot jump ahead");
        double last = -1;
        int frames = 0;
        while (painted.size() < all.size()) {
            batch = frontier.next(painted::contains);
            check(!batch.isEmpty(), "Frontier makes progress until all cached tiles are visible");
            for (var key : batch) {
                double distance = w.distanceSquared(key);
                check(distance >= last, "Every new batch follows the actual map focus radially");
                last = distance;
                painted.add(key);
            }
            check(++frames < all.size() / 32 + 2, "Bounded batches do not stall");
        }
        check(
                frontier.next(painted::contains).isEmpty(),
                "Completed native layer makes no duplicate uploads");
        check(
                frontier.allowsUpload(Tile.Key.at(1337, -3199, 4)),
                "Fine cached detail can still improve a completed layer");
    }

    private static void legends() throws Exception {
        JsonObject graph;
        try (var in =
                QuickFixTest.class.getResourceAsStream(
                        "/data/tfc/worldgen/world_preset/overworld.json")) {
            graph =
                    JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in)))
                            .getAsJsonObject()
                            .getAsJsonObject("dimensions")
                            .getAsJsonObject("minecraft:overworld")
                            .getAsJsonObject("generator")
                            .getAsJsonObject("tfc_settings")
                            .getAsJsonObject("rock_layer_settings");
        }
        var possible =
                RockPossibilities.read(graph, e -> "tfc:rock/raw/" + e.getAsString().split(":")[1]);
        check(
                possible.get(RockLayer.TOP).contains("tfc:rock/raw/limestone"),
                "Sedimentary surface rock exists");
        check(
                possible.get(RockLayer.MIDDLE).contains("tfc:rock/raw/limestone"),
                "Uplift transitions allow sedimentary middle rock");
        check(
                !possible.get(RockLayer.BOTTOM).contains("tfc:rock/raw/limestone"),
                "Sedimentary rock is excluded from bottom key");
        check(
                !possible.get(RockLayer.BOTTOM).contains("tfc:rock/raw/basalt"),
                "Extrusive rock is excluded from bottom key");
        check(
                possible.get(RockLayer.BOTTOM).contains("tfc:rock/raw/granite"),
                "Bottom igneous hosts are retained");
        for (RockLayer depth : RockLayer.values()) {
            var entries =
                    RockPossibilities.legend(
                            possible.get(depth), false, Map.of("ROCKS:Granite", 0x123456), true);
            check(
                    entries.size() == possible.get(depth).size() + 1
                            && entries.get(0).label().equals("Ocean"),
                    "Key contains all and only reachable rocks plus optional ocean");
            check(
                    entries.stream()
                            .filter(e -> e.label().equals("Granite"))
                            .allMatch(e -> e.colourAt(.5) == 0x123456),
                    "Custom swatches survive stratum filtering");
        }
        JsonObject custom =
                JsonParser.parseString(
                                "{\"rocks\":{\"a\":\"custom:surface\",\"b\":\"custom:deep\"},\"bottom\":[\"b\"],\"layers\":[{\"id\":\"surface\",\"layers\":{\"a\":\"bottom\"}}],\"ocean_floor\":[\"surface\"],\"land\":[\"surface\"],\"volcanic\":[\"surface\"],\"uplift\":[\"surface\"]}")
                        .getAsJsonObject();
        var modified = RockPossibilities.read(custom, JsonElement::getAsString);
        check(
                modified.get(RockLayer.TOP).equals(Set.of("custom:surface"))
                        && modified.get(RockLayer.BOTTOM).equals(Set.of("custom:deep")),
                "Custom rock graphs and repeated bottom layers are supported");
    }

    private static void search() {
        Profile p = new Profile();
        p.rocks = "granite";
        p.searchRockLayer = "Bottom";
        p.biomes = "plains";
        p.showSearchLayer();
        check(
                p.selected() == Layer.ROCKS && p.selectedRockLayer() == RockLayer.BOTTOM,
                "Combined query prioritizes the requested rock stratum");
        p.rocks = "";
        p.showSearchLayer();
        check(p.selected() == Layer.BIOMES, "Biome query selects biome map");
        p.biomes = "";
        p.categories = "Uplift";
        p.showSearchLayer();
        check(p.selected() == Layer.ROCK_TYPES, "Rock category query selects regions");
        p.clearSearchSettings();
        p.feature = "river";
        p.showSearchLayer();
        check(p.selected() == Layer.RIVERS, "Feature query selects terrain features");
        p.clearSearchSettings();
        p.minY = 200;
        p.showSearchLayer();
        check(p.selected() == Layer.ALTITUDE, "Height-only query selects altitude overview");
        p.clearSearchSettings();
        p.minTemp = 20;
        p.showSearchLayer();
        check(p.selected() == Layer.TEMPERATURE, "Temperature-only query selects temperature");
        p.clearSearchSettings();
        p.minRain = 200;
        p.showSearchLayer();
        check(p.selected() == Layer.RAINFALL, "Rain-only query selects rainfall");
        p.clearSearchSettings();
        p.layer = "BIOMES";
        p.showSearchLayer();
        check(p.selected() == Layer.BIOMES, "Unrestricted search retains chosen map");
        p.searchOrigin = "Coordinates";
        p.searchX = -123456;
        p.searchZ = 98765;
        p.radius = Sampling.MAX_SEARCH_RADIUS;
        p.resultLimit = 50;
        p.validate();
        var restored = Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject());
        check(
                restored.searchX == -123456
                        && restored.searchZ == 98765
                        && restored.searchOrigin.equals("Coordinates")
                        && restored.resultLimit == 5,
                "Coordinates roundtrip and old result count becomes five");
        check(
                restored.searchResolution() == 512,
                "Largest search remains bounded to roughly one million samples");
        p.mode = "Full map";
        check(
                Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject())
                        .mode
                        .equals("Full map"),
                "After migration, manual coverage changes persist");
        for (int width : new int[] {320, 854}) {
            for (int height : new int[] {240, 480}) {
                var groups = SearchLayout.groups(width, height, true);
                Set<String> keys = new HashSet<>();
                List<HudLayout.Box> boxes = new ArrayList<>();
                for (var group : groups) {
                    for (int i = 0; i < group.keys().size(); i++) {
                        keys.add(group.keys().get(i));
                        var b = group.field(i);
                        check(
                                b.width() >= 35 && b.bottom() < height - 72,
                                "Conditional coordinates fit smallest search menu");
                        check(
                                boxes.stream().noneMatch(b::overlaps),
                                "Coordinates do not overlap other search filters");
                        boxes.add(b);
                    }
                }
                check(
                        keys.containsAll(Set.of("searchX", "searchZ"))
                                && !keys.contains("resultLimit"),
                        "Coordinate origin replaces obsolete result-count control");
                check(
                        SearchLayout.groups(width, height).stream()
                                .noneMatch(g -> g.keys().contains("searchX")),
                        "Other origins hide coordinate inputs");
            }
        }
    }

    private static void toolbar() {
        int[] labels = {48, 36, 90, 30, 72, 18, 90, 96};
        for (int available : new int[] {280, 320, 854, 1920}) {
            for (double scale : new double[] {.2, .35, .5, 1, 1.5}) {
                var fit = ToolbarLayout.fit(labels, scale, available);
                check(
                        fit.width() <= available && fit.scale() > 0,
                        "Complete toolbar fits small viewports");
                for (int i = 0; i < labels.length; i++) {
                    check(
                            fit.widths()[i] >= Math.ceil(labels[i] * fit.scale()) + 7,
                            "Every scaled label retains physical padding without cropping");
                }
            }
        }
    }

    private static void nearest() {
        Cell cell = new Cell("granite", "plains", 1, 100, 20, 0, 0, 0, 1);
        Random random = new Random(17);
        Map<Tile.Key, byte[]> masks = new HashMap<>();
        List<SearchQuery.Result> reference = new ArrayList<>();
        for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                Tile.Key key = new Tile.Key(x, z, 2);
                byte[] mask = new byte[1024];
                masks.put(key, mask);
                for (int i = 0; i < 1024; i++) {
                    if (random.nextInt(5) == 0) {
                        mask[i] = (byte) (1 + random.nextInt(7));
                        int px = key.blockX() + i % 32 * 16 + 8,
                                pz = key.blockZ() + i / 32 * 16 + 8;
                        reference.add(
                                new SearchQuery.Result(
                                        px,
                                        pz,
                                        Math.hypot(px - 99, pz + 317),
                                        cell,
                                        null,
                                        mask[i]));
                    }
                }
            }
        }
        for (int spacing : new int[] {0, 64, 512, 2048, 10000}) {
            var actual = NearestMatches.select(masks, 99, -317, 5, spacing);
            var expected = SearchQuery.spaced(new ArrayList<>(reference), 5, spacing);
            check(
                    actual.size() == expected.size(),
                    "Complete coverage gives the same result count as sorting every candidate");
            for (int i = 0; i < actual.size(); i++) {
                check(
                        actual.get(i).x() == expected.get(i).x()
                                && actual.get(i).z() == expected.get(i).z()
                                && actual.get(i).layerMask() == expected.get(i).layerMask(),
                        "Nearest matches respect exact distance, tie order, spacing and rock layer");
            }
        }
        masks.clear();
        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 8; x++) {
                byte[] dense = new byte[1024];
                Arrays.fill(dense, (byte) 1);
                masks.put(new Tile.Key(x, z, 2), dense);
            }
        }
        for (int x = 40; x <= 160; x += 40) {
            byte[] far = new byte[1024];
            far[0] = 4;
            masks.put(new Tile.Key(x, 0, 2), far);
        }
        check(
                NearestMatches.select(masks, 0, 0, 5, 10000).size() == 5,
                "Dense nearby matches cannot crowd out the remaining distant candidates");
    }
}
