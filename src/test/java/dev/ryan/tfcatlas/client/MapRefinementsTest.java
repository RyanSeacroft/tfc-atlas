package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.DetailCoverage;
import dev.ryan.tfcatlas.core.HoverHeight;
import dev.ryan.tfcatlas.core.HudLayout;
import dev.ryan.tfcatlas.core.RegionLabels;
import dev.ryan.tfcatlas.core.Sampling;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class MapRefinementsTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        labels();
        detail();
        hover();
        keyAndStartup();
        System.out.println(
                "PASS: "
                        + checks
                        + " visible-region labels, detail priority, cursor-height and key/startup checks");
    }

    private static void labels() {
        String[] solid = new String[20];
        Arrays.fill(solid, "granite");
        var result =
                RegionLabels.layout(
                        solid,
                        5,
                        4,
                        -20,
                        -10,
                        10,
                        new RegionLabels.View(-17, -7, 24, 28),
                        id -> 8,
                        4,
                        0);
        check(result.size() == 1, "A solid visible region has one label, not repeated grid labels");
        var label = result.get(0);
        check(
                Math.abs(label.x() - 3.5) < 1e-9 && Math.abs(label.z() - 10.5) < 1e-9,
                "Label is centred on the clipped visible area with a continuous, non-grid anchor");
        var panned =
                RegionLabels.layout(
                        solid,
                        5,
                        4,
                        -20,
                        -10,
                        10,
                        new RegionLabels.View(-12, -7, 24, 28),
                        id -> 8,
                        4,
                        0);
        check(
                panned.get(0).x() == 6 && panned.get(0).z() == 10.5,
                "Panning changes the visible-region centre");
        String[] split = {
            "a", "a", null, "a", "a", "a", "a", null, "a", "a", "a", "a", null, "a", "a"
        };
        var patches =
                RegionLabels.layout(
                        split, 5, 3, 0, 0, 10, new RegionLabels.View(0, 0, 50, 30), id -> 8, 4, 0);
        check(
                patches.size() == 2 && patches.stream().allMatch(l -> l.id().equals("a")),
                "Separate subregions of the same name get separate labels");
        String[] ring = new String[225];
        Arrays.fill(ring, "forest");
        for (int z = 5; z < 10; z++) {
            for (int x = 5; x < 10; x++) {
                ring[x + z * 15] = null;
            }
        }
        var hole =
                RegionLabels.layout(
                        ring,
                        15,
                        15,
                        -75,
                        -75,
                        10,
                        new RegionLabels.View(-75, -75, 75, 75),
                        id -> 18,
                        8,
                        0);
        check(hole.size() == 1, "Concave region still gets one interior label");
        var a = hole.get(0);
        check(
                Math.abs(a.x()) >= 34 || Math.abs(a.z()) >= 29,
                "A centroid inside a hole moves to usable space in the actual region");
        String[] thin = {"river", "river", "river"};
        check(
                RegionLabels.layout(
                                thin,
                                3,
                                1,
                                0,
                                0,
                                10,
                                new RegionLabels.View(0, 0, 30, 10),
                                id -> 40,
                                8,
                                0)
                        .isEmpty(),
                "A label cannot spill outside a narrow region");
        String[] clipped = {"a", "a", "a", "a", null, "a", "a", null, "a"};
        var cropped =
                RegionLabels.layout(
                        clipped,
                        3,
                        3,
                        0,
                        0,
                        10,
                        new RegionLabels.View(0, 10, 30, 30),
                        id -> 4,
                        4,
                        0);
        check(cropped.size() == 2, "An offscreen connection does not join two visible subregions");
    }

    private static void detail() {
        check(Sampling.mapStep(.04, 854, 480, false) == 4, "Normal view uses 32 blocks");
        check(
                Sampling.mapStep(.01, 854, 480, false) == 8
                        && Sampling.mapStep(.003, 854, 480, false) == 16,
                "Wide views retain 64/128 detail");
        check(
                Sampling.mapStep(.04, 854, 480, true) == 4
                        && Sampling.mapStep(.01, 854, 480, true) == 8,
                "Rapid motion cannot trigger an extra coarse-quality jump");
        check(
                Sampling.mapStep(.04, 854, 480, false) == 4,
                "Settling returns to normal detail policy");
        for (int resolution : new int[] {64, 128, 256}) {
            DetailCoverage coverage = new DetailCoverage(resolution);
            int ratio = resolution / 32, pixels = 32;
            check(!coverage.complete(0, 0), "Coarse tile initially needs data");
            coverage.mark(0, 0, 9);
            check(
                    !coverage.needs(0, 0, 9) && coverage.needs(0, 0, 10),
                    "Only changed cached fine content needs copying");
            for (int z = 0; z < 8 * pixels; z++) {
                for (int x = 0; x < 8 * pixels; x++) {
                    check(
                            coverage.coarsePixelAllowed(x, z, pixels)
                                    == !(x < pixels / ratio && z < pixels / ratio),
                            "Late coarse pixels cannot overwrite cached 32-block patch");
                }
            }
            for (int z = 0; z < ratio; z++) {
                for (int x = 0; x < ratio; x++) {
                    coverage.mark(x, z, 10 + x + z * ratio);
                }
            }
            check(
                    coverage.complete(0, 0) && !coverage.complete(1, 0),
                    "Complete fine coverage skips only its own coarse tile");
            coverage.invalidateRevisions();
            check(
                    coverage.needs(0, 0, 10) && !coverage.coarsePixelAllowed(0, 0, pixels),
                    "Resizing requests a refreshed copy while preserving fine-data priority");
        }
    }

    private static void hover() {
        List<Runnable> jobs = new ArrayList<>();
        AtomicInteger reads = new AtomicInteger();
        HoverHeight height =
                new HoverHeight(
                        jobs::add,
                        (x, z) -> {
                            reads.incrementAndGet();
                            return x - z;
                        });
        check(
                height.at(-10, 4, 0) == null && height.at(-10, 4, 79) == null && jobs.isEmpty(),
                "Moving/settling cursor never does synchronous height work");
        height.at(-10, 4, 80);
        check(jobs.size() == 1 && reads.get() == 0, "Stable cursor queues one background sample");
        for (int i = 0; i < 500; i++) {
            height.at(i, 8, 100 + i);
        }
        check(jobs.size() == 1, "Fast mouse movement cannot build an unbounded height queue");
        jobs.remove(0).run();
        check(
                height.at(-10, 4, 700).y() == -14,
                "Cached height belongs to its exact negative world coordinate");
        check(
                height.at(7, 8, 710) == null,
                "Old cursor height is never returned at a new coordinate");
        height.at(7, 8, 800);
        jobs.remove(0).run();
        check(
                height.at(7, 8, 801).y() == -1 && reads.get() == 2,
                "New position gets its own sample");
        height.close();
        check(
                height.at(9, 8, 1000) == null && jobs.isEmpty(),
                "Closed world no longer schedules cursor work");
        HoverHeight fail =
                new HoverHeight(
                        Runnable::run,
                        (x, z) -> {
                            throw new IllegalArgumentException("Fixture");
                        });
        fail.at(0, 0, 0);
        fail.at(0, 0, 80);
        check(
                fail.at(0, 0, 90).failed(),
                "Sampling errors become an unavailable readout, not a render-thread exception");
    }

    private static void keyAndStartup() {
        Profile p = new Profile();
        p.keySize(80, 160);
        p.keyScale(.4);
        p.layer = "BIOMES";
        p.keySize(90, 200);
        p.keyScale(.8);
        var restored = Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject());
        check(
                restored.keyScale() == .8 && restored.keyHeight() == 200,
                "Biome key scale and viewport persist separately");
        restored.layer = "ROCKS";
        check(
                restored.keyScale() == .4
                        && restored.keyWidth() == 0
                        && restored.keyHeight() == 160,
                "Rock key retains its own scale and dimensions");
        var drag = new HudLayout.Drag(new HudLayout.Box(10, 10, 80, 160), .4, 90, 170, true);
        check(
                Math.abs(drag.resizedScale(170, 330) - .8) < 1e-9,
                "Corner doubling scales the entire key, including its text");
        p.rocks = "granite, diorite";
        p.categories = "Uplift";
        p.searchRockLayer = "Bottom";
        p.seed = "1234";
        p.minY = 200;
        p.savedSearches.put("My rocks", Profiles.JSON.toJson(p));
        var explicit = Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject());
        check(
                explicit.rocks.equals(p.rocks) && explicit.searchRockLayer.equals("Bottom"),
                "Explicitly chosen presets keep their rock settings");
        p.startSession();
        check(
                p.rocks.isEmpty() && p.categories.isEmpty() && p.searchRockLayer.equals("Top"),
                "New session starts without restored rock names, regions or filled depth bounds");
        check(
                p.seed.equals("1234") && p.minY == 200 && p.savedSearches.containsKey("My rocks"),
                "Blank startup rock query preserves world, unrelated filters and saved presets");
        p.resetHud();
        check(
                p.keyScales.isEmpty() && p.keySizes.isEmpty(),
                "Reset UI clears every layer's key scale and viewport");
    }
}
