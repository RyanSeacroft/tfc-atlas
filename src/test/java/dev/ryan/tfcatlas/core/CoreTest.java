package dev.ryan.tfcatlas.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoreTest {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) {
            throw new AssertionError(message);
        }
    }

    private static Cell cell(String rock, String biome, int type, float rain, float temp) {
        return new Cell(rock, biome, type, rain, temp, 2, 4, 2, 1);
    }

    public static void main(String[] args) throws Exception {
        dev.ryan.tfcatlas.client.SearchPerformanceTest.run();
        SeedRecoveryTest.run();
        ExploredViewTest.run();
        SoilTest.run();
        dev.ryan.tfcatlas.client.SoilSamplerTest.run();
        Climate121Test.run();
        dev.ryan.tfcatlas.client.ClimateUiFixTest.run();
        dev.ryan.tfcatlas.client.QuickFixTest.run();
        dev.ryan.tfcatlas.client.SearchAndTerrainTest.run();
        dev.ryan.tfcatlas.client.TerrainAndTooltipTest.run();
        dev.ryan.tfcatlas.client.CursorAndRetryTest.run();
        dev.ryan.tfcatlas.client.SeedPrivacyTest.run();
        dev.ryan.tfcatlas.client.StrataTest.run();
        SearchUiTest.run();
        dev.ryan.tfcatlas.client.MapRefinementsTest.run();
        dev.ryan.tfcatlas.client.CoverageTest.run();
        dev.ryan.tfcatlas.client.DisplayTest.run();
        dev.ryan.tfcatlas.client.SamplingTest.run();
        OverviewTest.run();
        dev.ryan.tfcatlas.client.PolishTest.run();
        Cell granite = cell("tfc:rock/raw/granite", "tfc:plains", 3, 300, 20);
        SearchQuery q =
                new SearchQuery(
                        SearchQuery.names("granite, gneiss"),
                        SearchQuery.names("plains"),
                        3,
                        275,
                        325,
                        20,
                        40,
                        "Land",
                        -128,
                        127);
        check(q.matchesRegion(granite), "Combined query should match");
        check(
                !q.matchesRegion(cell("tfc:rock/raw/granite", "tfc:hills", 3, 300, 20)),
                "Biome must match");
        check(
                !q.matchesRegion(cell("tfc:rock/raw/granite", "tfc:plains", 2, 300, 20)),
                "Category must match");
        check(
                !q.matchesRegion(cell("tfc:rock/raw/granite", "tfc:plains", 3, 274, 20)),
                "Rain minimum");
        check(
                !q.matchesRegion(cell("tfc:rock/raw/granite", "tfc:plains", 3, 300, 19.99f)),
                "At least 20 means >=20");
        check(
                q.matchesRegion(cell("tfc:rock/raw/gneiss", "tfc:plains", 3, 325, 40)),
                "OR names and inclusive maxima");
        try {
            new SearchQuery(Set.of(), Set.of(), -1, 300, 100, 0, 10, "Any", 0, 4);
            throw new AssertionError("Reversed range accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        try {
            new SearchQuery(Set.of(), Set.of(), -1, 0, 500, Float.NaN, 10, "Any", 0, 4);
            throw new AssertionError("NaN accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        for (int x :
                new int[] {
                    -29900000, -4097, -4096, -129, -128, -1, 0, 127, 128, 4095, 4096, 29900000
                }) {
            for (int step : new int[] {1, 2, 8, 64}) {
                Tile.Key k = Tile.Key.at(x, x, step);
                check(
                        k.blockX() <= x && x < k.blockX() + k.span(),
                        "Negative tile alignment " + x + "/" + step);
            }
        }
        Cell[] cells = new Cell[1024];
        Arrays.fill(cells, granite);
        cells[1023] = cell("tfc:rock/raw/basalt", "tfc:ocean", 0, 17.5f, -7.2f);
        Tile t = new Tile(new Tile.Key(-1, 2, 1), cells);
        check(t.atBlock(-1, 767).equals(cells[1023]), "Last negative cell");
        check(t.atBlock(0, 512) == null, "Outside tile");
        Path temp = Files.createTempDirectory("atlas-test").resolve(t.key().fileName());
        t.write(temp);
        Tile restored = Tile.read(temp, t.key());
        check(Arrays.equals(cells, restored.cells()), "Cache roundtrip all data");
        try {
            Tile.read(temp, new Tile.Key(1, 2, 1));
            throw new AssertionError("Wrong cache accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
        Files.write(temp, new byte[] {1, 2, 3});
        try {
            Tile.read(temp, t.key());
            throw new AssertionError("Corruption accepted");
        } catch (java.io.IOException expected) {
            checks++;
        }
        List<SearchQuery.Result> list =
                new ArrayList<>(
                        List.of(
                                new SearchQuery.Result(1024, 0, 1024, granite),
                                new SearchQuery.Result(-128, 0, 128, granite),
                                new SearchQuery.Result(0, 0, 0, granite)));
        var spaced = SearchQuery.spaced(list, 2, 512);
        check(
                spaced.size() == 2 && spaced.get(0).x() == 0 && spaced.get(1).x() == 1024,
                "Nearest candidates, separation");
        check(
                Cell.TYPE_NAMES[3].equals("Uplift") && Cell.TYPE_NAMES[1].equals("Volcanic"),
                "Rock category IDs");
        check(Cell.label("tfc:rolling_hills").equals("Rolling hills"), "Readable labels");
        for (Layer l : Layer.values()) {
            check((l.color(granite, false, Map.of()) & 0xff000000) == 0, "24-bit colour " + l);
        }
        check(
                !Layer.categories(Layer.ROCKS, false, Map.of()).isEmpty(),
                "Rock legend resources loaded");
        long start = System.nanoTime();
        int count = 0;
        for (int i = 0; i < 1000000; i++) {
            if (q.matchesRegion(cells[i % 1024])) {
                count++;
            }
        }
        check(count > 990000, "Search benchmark work");
        System.out.println(
                "PASS: "
                        + checks
                        + " checks; 1,000,000 query checks in "
                        + ((System.nanoTime() - start) / 1000000)
                        + " ms");
    }
}
