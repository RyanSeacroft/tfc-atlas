package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.DikeCoverage;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchWorkload;
import dev.ryan.tfcatlas.core.Tile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Compare growing snapshots with independent cell boundaries, not just the final snapshot code. */
public final class SearchPerformanceTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        warnings();
        snapshots(false);
        snapshots(true);
        sharing();
        dikeUpdates();
        System.out.println(
                "PASS: "
                        + checks
                        + " live coverage, exact outlines, shared geometry, workload and radius-advice checks");
    }

    private static void warnings() {
        Profile p = new Profile();
        check(
                p.radius == 16384 && p.precision.equals("Auto"),
                "Fresh profiles default to radius 16384 with Auto sampling");
        p.radius = 12345;
        p.clearSearchSettings();
        check(p.radius == 16384, "Clearing keeps the requested 16384 radius");
        p.biomes = "plains";
        check(
                !SearchWorkload.estimate(p.query(), 1024, 16, 1, false).warn(),
                "Small ordinary searches need no confirmation");
        check(
                !SearchWorkload.estimate(p.query(), p.radius, p.searchResolution(), 1, false)
                        .warn(),
                "Warm ordinary default searches need no confirmation");
        check(
                SearchWorkload.estimate(p.query(), 8192, 16, 0, false).warn(),
                "Uncached fine terrain gets a cost warning");
        check(
                SearchWorkload.estimate(p.query(), 1000000, 16, 1, true).warn(),
                "Existing manual oversampling confirmation is retained");
        p.minY = 200;
        check(
                SearchWorkload.estimate(p.query(), 8192, 16, 1, false).warn(),
                "Height-only computation gets a warning even when terrain is cached");
        p.minY = -64;
        p.searchRockLayer = "Dikes";
        check(
                SearchWorkload.estimate(p.query(), 8192, 16, 1, false).warn(),
                "Dike refinement still costs time with a warm terrain cache");
        p.clearSearchSettings();
        check(
                SearchWorkload.estimate(p.query(), 8192, 16, 1, false).warn(),
                "Unfiltered dense coverage is considered too");
        var extreme = SearchWorkload.estimate(p.query(), Integer.MAX_VALUE, 16, 0, true);
        check(
                extreme.samples() > 1_000_000_000L
                        && extreme.confirmation(16).contains("decreasing the search radius"),
                "Huge manual searches do not overflow and suggest smaller radius");
        for (int n = 0; n < 5; n++) {
            check(
                    SearchWorkload.advice(n, n, 0, 8192, 512, true)
                            .contains("increasing the search radius"),
                    "Fewer than five matches suggest increasing radius");
        }
        check(
                SearchWorkload.advice(4, 100, 10, 8192, 512, true).contains("candidate separation"),
                "Spacing can explain too few listed matches");
        check(
                !SearchWorkload.advice(0, 0, 0, Sampling.MAX_SEARCH_RADIUS, 512, true)
                        .contains("increasing"),
                "Do not suggest exceeding the Auto radius limit");
        check(
                SearchWorkload.advice(5, 100, 10, 8192, 512, true).isEmpty(),
                "Exactly five normal matches need no warning");
        check(
                SearchWorkload.advice(5, 800000, 90000, 8192, 512, true).contains("Decrease"),
                "Dense results suggest reducing radius independent of query type");
    }

    private record Segment(int x, int z, boolean horizontal) {}

    private static Map<Segment, Integer> expanded(SearchOverlay overlay) {
        Map<Segment, Integer> segments = new HashMap<>();
        int r = overlay.resolution();
        for (var edge : overlay.edges()) {
            boolean horizontal = edge.z0() == edge.z1();
            int x = edge.x0() / r, z = edge.z0() / r;
            int length = (Math.abs(edge.x1() - edge.x0()) + Math.abs(edge.z1() - edge.z0())) / r;
            for (int i = 0; i < length; i++) {
                check(
                        segments.put(
                                        new Segment(
                                                x + (horizontal ? i : 0),
                                                z + (horizontal ? 0 : i),
                                                horizontal),
                                        edge.layerMask())
                                == null,
                        "No duplicated exact boundary segments");
            }
        }
        return segments;
    }

    private static int at(Map<Tile.Key, byte[]> masks, int x, int z, int step) {
        byte[] a = masks.get(new Tile.Key(Math.floorDiv(x, 32), Math.floorDiv(z, 32), step));
        return a == null ? 0 : a[Math.floorMod(x, 32) + 32 * Math.floorMod(z, 32)];
    }

    private static Map<Segment, Integer> independent(
            Map<Tile.Key, byte[]> masks, int step, boolean layered) {
        Map<Segment, Integer> out = new HashMap<>();
        for (var entry : masks.entrySet()) {
            for (int i = 0; i < 1024; i++) {
                int a = entry.getValue()[i];
                if (a == 0) {
                    continue;
                }
                int x = entry.getKey().x() * 32 + i % 32, z = entry.getKey().z() * 32 + i / 32;
                for (int side = 0; side < 4; side++) {
                    int b =
                            at(
                                    masks,
                                    x + (side == 2 ? -1 : side == 3 ? 1 : 0),
                                    z + (side == 0 ? -1 : side == 1 ? 1 : 0),
                                    step);
                    if (a == b || !layered && b != 0) {
                        continue;
                    }
                    out.put(
                            new Segment(x + (side == 3 ? 1 : 0), z + (side == 1 ? 1 : 0), side < 2),
                            layered ? a | b : 1);
                }
            }
        }
        return out;
    }

    private static void snapshots(boolean layered) {
        Map<Tile.Key, byte[]> masks = new HashMap<>();
        var builder = new SearchOverlay.Builder(masks, layered, 8192, 16);
        Random random = new Random(618L);
        SearchOverlay before = builder.publish(List.of(), false);
        long oldCount = 0;
        for (int round = 0; round < 24; round++) {
            for (int i = 0; i < 200; i++) {
                Tile.Key key = new Tile.Key(random.nextInt(5) - 2, random.nextInt(5) - 2, 2);
                int index = random.nextInt(1024);
                masks.computeIfAbsent(key, k -> new byte[1024])[index] |=
                        (byte) (1 << random.nextInt(4));
                builder.changed(key);
            }
            check(before.count() == oldCount, "Already-published snapshots never mutate");
            SearchOverlay live = builder.publish(List.of(), false),
                    full = new SearchOverlay(masks, List.of(), layered);
            check(
                    live.count() == full.count() && !live.complete() && live.areaCount() == -1,
                    "Live counts are exact without claiming completed area counts");
            check(
                    Arrays.equals(live.overview(), full.overview()),
                    "Live texture equals the complete coverage texture");
            check(
                    expanded(live).equals(independent(masks, 2, layered)),
                    "Exact outline boundaries and layer colours agree with independent cell neighbours");
            check(
                    live.series() == before.series(),
                    "One search retains its series across every snapshot");
            check(
                    builder.publish(List.of(), false) == live,
                    "No changed cells means no renderer invalidation");
            before = live;
            oldCount = live.count();
        }
        var finished = builder.publish(List.of(), true);
        var expected = new SearchOverlay(masks, List.of(), layered);
        check(
                finished.complete() && finished.areaCount() == expected.areaCount(),
                "Final distinct-area count uses all published coverage");
        check(
                expanded(finished).equals(expanded(before)),
                "Finishing never changes the live search boundary");
    }

    private static void sharing() {
        Map<Tile.Key, byte[]> masks = new HashMap<>();
        Tile.Key a = new Tile.Key(-1, 0, 1),
                b = new Tile.Key(1, 0, 1),
                far = new Tile.Key(40, 40, 1);
        for (Tile.Key k : List.of(a, b, far)) {
            byte[] values = new byte[1024];
            values[0] = 1;
            masks.put(k, values);
        }
        var builder = new SearchOverlay.Builder(masks, true, 8192, 8);
        masks.keySet().forEach(builder::changed);
        var first = builder.publish(List.of(), false);
        Object identity = first.pageIdentity(far);
        var farGroup =
                first.groups().stream()
                        .filter(g -> g.blockX() > 0 && g.blockZ() > 0)
                        .findFirst()
                        .orElseThrow();
        masks.get(a)[31] = 2;
        builder.changed(a);
        var next = builder.publish(List.of(), false);
        check(next.pageIdentity(far) == identity, "Unchanged texture pages survive live updates");
        check(
                next.groups().stream().anyMatch(g -> g == farGroup),
                "Unchanged GPU geometry survives live updates");
        check(
                first.layerMask(a, 31) == 0 && next.layerMask(a, 31) == 2,
                "Changed pages are copied before publication");
        check(
                next.pageIdentity(a) != first.pageIdentity(a),
                "Changed pages invalidate their texture");
        check(
                new SearchOverlay.Builder(new HashMap<>(), false, 8192, 8)
                                .publish(List.of(), false)
                                .series()
                        != first.series(),
                "Replacement searches cannot reuse stale geometry");
        var fine = new SearchOverlay.Builder(new HashMap<>(), true, Integer.MAX_VALUE, 8);
        check(
                !fine.publish(List.of(), false).complete(),
                "Batch-size selection handles the largest manual radius");
    }

    private static void dikeUpdates() throws Exception {
        Map<Tile.Key, byte[]> masks = new HashMap<>();
        var builder = new SearchOverlay.Builder(masks, true, 8192, 8);
        long n = DikeCoverage.add(255, 0, 35, 0, 0, 8192, masks, (x, z) -> true, builder::changed);
        var first = builder.publish(List.of(), false);
        check(
                n == first.count() && first.keys().size() >= 4,
                "Dike refinement marks every changed tile across both axes");
        DikeCoverage.add(280, 0, 35, 0, 0, 8192, masks, (x, z) -> true, builder::changed);
        var second = builder.publish(List.of(), false);
        check(
                second.count() > n && first.count() == n,
                "Overlapping refined pipes grow live without modifying earlier coverage");
        check(
                expanded(second).equals(independent(masks, 1, true)),
                "Eight-block live dike outlines retain the exact footprint");
    }
}
