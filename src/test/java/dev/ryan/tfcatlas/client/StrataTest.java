package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.SearchCompletion;
import dev.ryan.tfcatlas.core.SearchDetails;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchQuery;
import dev.ryan.tfcatlas.core.SeedPrivacy;
import dev.ryan.tfcatlas.core.SettingsLayout;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileWindow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.noise.Noise2D;

public final class StrataTest {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        Cell c =
                new Cell(
                        "tfc:rock/raw/limestone",
                        "tfc:plateau",
                        2,
                        200,
                        20,
                        10,
                        8,
                        3,
                        1,
                        "tfc:rock/raw/marble",
                        "tfc:rock/raw/granite");
        Profile p = new Profile();
        p.rocks = "marble";
        check(
                p.mode.equals("Unexplored only"),
                "Fresh profiles start with explored terrain preserved");
        check(!p.query().matchesRegion(c), "Top search cannot match the middle rock");
        p.searchRockLayer = "Middle";
        check(
                p.query().matchesRegion(c) && p.query().matchingLayers(c) == 2,
                "Middle search selects the middle geological stratum");
        p.searchRockLayer = "Bottom";
        check(!p.query().matchesRegion(c), "Bottom is independent of the middle layer");
        p.rocks = "granite";
        check(
                p.query().matchesRegion(c) && p.query().matchingLayers(c) == 4,
                "Bottom search matches bottom host rock");
        p.rocks = "limestone, granite";
        p.searchRockLayer = "Any layer";
        check(p.query().matchingLayers(c) == 5, "All matching layers survive an OR rock search");
        var result = new SearchQuery.Result(-1024, 1024, 100, c, 200, 5);
        check(
                result.rocks().equals("Top: Limestone · Bottom: Granite"),
                "Results name each matched rock and layer");
        check(
                new SearchDetails(p.query(), 0, 0, 1000, 50, 100)
                        .lines()
                        .contains("Rock layer: Any layer"),
                "Search summary preserves submitted layer selection");
        for (RockLayer layer : RockLayer.values()) {
            p.rockLayer = layer.label;
            check(
                    p.mapColor(c) == Layer.ROCKS.color(c.withRock(c.rock(layer)), false, Map.of()),
                    "Map selects the same rock as the search: " + layer);
            var saved = Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject());
            check(
                    saved.rockLayer.equals(layer.label) && saved.query().equals(p.query()),
                    "Map and search layers persist independently");
        }
        var old =
                Profiles.decode(
                        com.google.gson.JsonParser.parseString(
                                        "{\"rockMode\":\"Y range\",\"rockMinY\":-30,\"rockMaxY\":19}")
                                .getAsJsonObject());
        check(
                old.searchRockLayer.equals("Any layer")
                        && !Profiles.JSON.toJson(old).contains("rockMinY"),
                "Legacy depth preset becomes an explicit layer search, without stale Y fields");
        old =
                Profiles.decode(
                        com.google.gson.JsonParser.parseString("{\"mode\":\"Full map\"}")
                                .getAsJsonObject());
        check(
                old.mode.equals("Unexplored only"),
                "Legacy full-map default migrates once to unexplored only");
        Cell[] cells = new Cell[1024];
        Arrays.fill(cells, c);
        var key = new Tile.Key(-1, 0, 2);
        var tile = new Tile(key, cells);
        Path file = Files.createTempDirectory("atlas-strata").resolve(key.fileName());
        tile.write(file);
        check(
                Arrays.equals(Tile.read(file, key).cells(), cells),
                "All three different rock IDs round-trip through the disk cache");
        Files.delete(file);
        Files.delete(file.getParent());
        byte[] masks = new byte[1024];
        masks[0] = 1;
        masks[1] = 2;
        masks[2] = 4;
        masks[3] = 5;
        var overlay = new SearchOverlay(Map.of(key, masks), List.of(result), true);
        masks[0] = 7;
        check(
                overlay.layerMask(key, 0) == 1 && overlay.count() == 4,
                "Completed layer coverage is immutable");
        for (int i = 0; i < 4; i++) {
            check(
                    overlay.colour(key, i, 0) == RockLayer.colour(new int[] {1, 2, 4, 5}[i]),
                    "Map tint and layer legend agree");
        }
        check(
                overlay.edges().stream()
                        .anyMatch(e -> e.x0() == key.blockX() + 16 && e.x1() == e.x0()),
                "Different adjacent match layers keep a visible internal border");
        check(
                overlay.edges().stream().anyMatch(e -> e.layerMask() == 5),
                "Multiple-layer match boundaries retain their layer mask");
        var plain = new SearchOverlay(Map.of(key, masks), List.of(), false);
        check(
                plain.colour(key, 0, 0x123456) == 0x123456,
                "Non-rock searches retain the chosen highlight colour");
        for (int width : new int[] {320, 426, 854}) {
            for (int height : new int[] {240, 260, 480}) {
                for (int count : new int[] {3, 5, 8, 13}) {
                    List<HudLayout.Box> used = new ArrayList<>();
                    for (var f : SettingsLayout.fields(width, height, count)) {
                        check(
                                f.input().bottom() <= height - 72
                                        && f.label().y() >= 50
                                        && f.input().height() >= 12,
                                "Every settings field fits above actions");
                        check(
                                used.stream().noneMatch(b -> b.overlaps(f.input()))
                                        && f.label().bottom() < f.input().y(),
                                "Labels and fields do not overlap");
                        used.add(f.input());
                    }
                    check(used.size() == count, "All settings are present on one page");
                }
            }
        }
        var box = new HudLayout.Box(40, 60, 100, 40);
        var near =
                HudLayout.place(new HudLayout.Box(0, 0, 320, 240), 100, 40, 40, 101, List.of(box));
        check(near != null && near.y() == 101, "HUD boxes can sit just one pixel apart");
        for (long seed : new long[] {Long.MIN_VALUE, -42, 0, Long.MAX_VALUE}) {
            check(
                    SeedPrivacy.input("" + seed, true).isEmpty()
                            && SeedPrivacy.exportValue(seed, true).equals("Hidden")
                            && SeedPrivacy.fileLabel(seed, true).equals("map"),
                    "Multiplayer seed cannot appear in settings or export names/metadata");
            check(
                    SeedPrivacy.input("" + seed, false).equals("" + seed)
                            && SeedPrivacy.exportValue(seed, false).equals("" + seed),
                    "Singleplayer seed display is retained");
        }
        var options = List.of("plateau", "plateau lake");
        check(
                SearchCompletion.suggest("plateau", 7, options, true) != null
                        && SearchCompletion.suggest("plateau", 7, options, false) == null,
                "Plateau lake ghost suggestion disappears when the field loses focus");
        check(
                SearchCompletion.suggest("granite, mar", 12, List.of("granite", "marble"), true)
                        .apply("granite, mar")
                        .equals("granite, marble"),
                "Tab still completes later comma-separated entries");
        checkStrataBoundaries();
        checkRadial();
        System.out.println(
                "PASS: "
                        + checks
                        + " three-strata, layer-search/cache, seed-privacy, single-page settings and radial-order checks");
    }

    private static void checkStrataBoundaries() throws Exception {
        var xOffset = RegionChunkDataGenerator.class.getDeclaredMethod("getOffsetX", int.class);
        xOffset.setAccessible(true);
        var zOffset = RegionChunkDataGenerator.class.getDeclaredMethod("getOffsetZ", int.class);
        zOffset.setAccessible(true);
        Noise2D noise = (x, z) -> 53 + 10 * Math.sin(x * .0017) * Math.cos(z * .0021);
        Random random = new Random(116);
        for (int i = 0; i < 1000; i++) {
            int x = random.nextInt(60_000_000) - 30_000_000,
                    z = random.nextInt(60_000_000) - 30_000_000;
            for (int expected = 0; expected < 3; expected++) {
                int y = RockStrata.referenceY(noise, x, z, expected);
                float delta = -y;
                int layer = 0;
                for (; ; ) {
                    float height =
                            (float)
                                    noise.noise(
                                            x + (int) xOffset.invoke(null, layer),
                                            z + (int) zOffset.invoke(null, layer));
                    if (delta <= height) {
                        break;
                    }
                    delta -= height;
                    layer++;
                }
                check(
                        layer == expected,
                        "Reference sample reaches TFC's actual requested layer at varied coordinates");
                check(
                        expected == 0 ? y == 0 : delta > 0 && delta <= 1.001,
                        "Middle/bottom sample is immediately below the variable upper boundary");
            }
        }
    }

    private static void checkRadial() {
        for (double x : new double[] {-2512.9, -.1, 0, 31, 910.8}) {
            for (double z : new double[] {-871.5, 0, 500.1}) {
                var window = TileWindow.visible(x, z, .15, 320, 240, 2);
                double last = -1;
                Set<Tile.Key> seen = new HashSet<>();
                for (var key : window) {
                    double d = window.distanceSquared(key);
                    check(
                            d >= last && seen.add(key),
                            "Loaded tiles move strictly outwards from the fractional map focus");
                    last = d;
                }
                check(
                        seen.size() == window.count(),
                        "Radial traversal has no holes or duplicate tiles");
            }
        }
    }
}
