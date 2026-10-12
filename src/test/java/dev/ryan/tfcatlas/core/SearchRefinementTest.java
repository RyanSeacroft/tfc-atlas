package dev.ryan.tfcatlas.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Dependency-free regressions; also run by the normal Gradle coreTest task. */
public final class SearchRefinementTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        var options = List.of("Old mountains lake", "Old mountains", "Old mountain lake");
        check(
                SearchCompletion.suggest("old m", 5, options).text().equals("old mountains"),
                "Choose shortest completion regardless of input order");
        check(
                SearchCompletion.suggest("old mountains", 13, options) == null,
                "Exact name must not grow into a lake");
        check(
                SearchCompletion.suggest("old m, old mountains", 5, options)
                        .text()
                        .equals("old mountain lake"),
                "Do not duplicate a following list item");
        check(
                SearchCompletion.suggest("old mountains", 5, options) == null,
                "Editing the middle must not replace the rest of a token");
        check(
                SearchCompletion.suggest("old m", 5, options, false) == null,
                "No unfocused ghost text");
        check(SearchCompletion.suggest("old m", -1, options) == null, "Clamp caret safely");

        check(TerrainSlope.sample(0, 0, (x, z) -> 100) == 0, "Flat terrain is zero");
        check(
                Math.abs(TerrainSlope.sample(0, 0, (x, z) -> x) - 45) < .00001,
                "1:1 grade is 45 degrees");
        check(
                TerrainSlope.sample(-1024, -512, (x, z) -> x)
                        == TerrainSlope.sample(0, 0, (x, z) -> x),
                "Slope independent of origin and negative coordinates");
        check(
                SearchLimits.ANY.matchesWater(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                "No water restriction must not reject distant land");
        var nearRiver =
                new SearchLimits(
                        Set.of(), 0, 100, 0, Float.MAX_VALUE, 0, 90, 0, Float.MAX_VALUE, 0);
        check(nearRiver.matchesWater(100, Double.POSITIVE_INFINITY), "Water maximum is inclusive");
        check(!nearRiver.matchesWater(101, 0), "Reject outside river range");
        check(!nearRiver.matchesWater(Double.NaN, 0), "Unknown data cannot satisfy a restriction");

        Random random = new Random(26);
        for (int trial = 0; trial < 100; trial++) {
            Map<Tile.Key, byte[]> masks = new HashMap<>();
            boolean[] original = new boolean[64 * 64];
            for (int z = 0; z < 64; z++) {
                for (int x = 0; x < 64; x++) {
                    boolean hit = random.nextDouble() < .53;
                    original[x + z * 64] = hit;
                    Tile.Key key = new Tile.Key(x / 32 - 1, z / 32 - 1, 2);
                    masks.computeIfAbsent(key, ignored -> new byte[1024])[
                                    (x % 32) + 32 * (z % 32)] =
                            (byte) (hit ? 7 : 0);
                }
            }
            int minCells = random.nextInt(25), maxCells = minCells + random.nextInt(80);
            boolean[] expected = original.clone(), visited = new boolean[4096];
            for (int at = 0; at < original.length; at++) {
                if (!original[at] || visited[at]) {
                    continue;
                }
                var queue = new ArrayDeque<Integer>();
                var component = new HashSet<Integer>();
                queue.add(at);
                visited[at] = true;
                while (!queue.isEmpty()) {
                    int i = queue.remove();
                    component.add(i);
                    int x = i % 64, z = i / 64;
                    for (int next :
                            new int[] {
                                x > 0 ? i - 1 : -1,
                                x < 63 ? i + 1 : -1,
                                z > 0 ? i - 64 : -1,
                                z < 63 ? i + 64 : -1
                            }) {
                        if (next >= 0 && original[next] && !visited[next]) {
                            visited[next] = true;
                            queue.add(next);
                        }
                    }
                }
                if (component.size() < minCells || component.size() > maxCells) {
                    for (int i : component) {
                        expected[i] = false;
                    }
                }
            }
            SearchAreas.retainRange(masks, minCells * 256.0, maxCells * 256.0, ignored -> {});
            for (int z = 0; z < 64; z++) {
                for (int x = 0; x < 64; x++) {
                    byte actual =
                            masks.get(new Tile.Key(x / 32 - 1, z / 32 - 1, 2))[
                                    x % 32 + 32 * (z % 32)];
                    check(
                            (actual != 0) == expected[x + z * 64],
                            "Whole connected area agrees with independent flood fill");
                    if (actual != 0) {
                        check(actual == 7, "Retain rock layer colours");
                    }
                }
            }
        }
        for (int width : new int[] {320, 480, 854}) {
            for (int height : new int[] {240, 360, 480}) {
                for (int section = 0; section < SearchLayout.SECTIONS.size(); section++) {
                    Set<String> fields = new HashSet<>();
                    for (var group : SearchLayout.groups(width, height, true, section)) {
                        check(group.bounds().y() >= 70, "Fields below navigation");
                        check(
                                group.bounds().y() + group.bounds().height() <= height - 72,
                                "Fields clear persistent actions");
                        for (int i = 0; i < group.keys().size(); i++) {
                            var field = group.field(i);
                            check(
                                    field.width() > 20 && field.height() >= 12,
                                    "Usable compact input bounds");
                            check(
                                    fields.add(group.keys().get(i)),
                                    "No duplicate fields in a section");
                        }
                    }
                }
            }
        }
        System.out.println("PASS search refinements: " + checks + " checks");
    }
}
