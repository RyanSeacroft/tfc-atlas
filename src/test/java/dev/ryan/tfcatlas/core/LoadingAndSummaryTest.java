package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import dev.ryan.tfcatlas.client.Profiles;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real cached-file reads must finish even while both generation workers are occupied. */
public final class LoadingAndSummaryTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        Profile profile = new Profile();
        check(profile.highlightColor == 0x1FFF55, "New profiles use the requested green");
        var saved = Profiles.JSON.toJsonTree(profile).getAsJsonObject();
        saved.remove("highlightColor");
        check(
                Profiles.decode(saved).highlightColor == 0x1FFF55,
                "Missing saved colour inherits green");
        profile.highlightColor = 0x123456;
        check(
                Profiles.decode(Profiles.JSON.toJsonTree(profile).getAsJsonObject()).highlightColor
                        == 0x123456,
                "An explicitly saved colour is preserved");
        profile.minRain = 200;
        var rain = new SearchDetails(profile.query(), 10, -20, 16384, 5, 512).lines();
        check(rain.contains("Rain: ≥ 200 mm"), "Only the active climate range is displayed");
        check(
                rain.stream()
                        .noneMatch(
                                s ->
                                        s.contains("Any")
                                                || s.startsWith("Rock layer:")
                                                || s.startsWith("Temp:")
                                                || s.startsWith("Rocks:")
                                                || s.startsWith("Biomes:")),
                "Unused filters and irrelevant strata are omitted");
        profile.rocks = "granite";
        profile.searchRockLayer = "Bottom";
        var rocks = new SearchDetails(profile.query(), 0, 0, 1024, 5, 512).lines();
        check(
                rocks.contains("Rocks: Granite") && rocks.contains("Rock layer: Bottom"),
                "Actual rock criteria keep their selected stratum");
        for (int step : new int[] {1, 2, 4, 8, 16, 32}) {
            check(
                    Sampling.prefetchStep(step, .01) == Math.min(step, 4),
                    "Distant views retain 32-block detail without prefetching finer terrain");
        }
        check(
                Sampling.prefetchStep(4, .2) == 2 && Sampling.prefetchStep(2, .4) == 1,
                "16/8-block detail is prepared before its normal zoom thresholds");
        for (int height : new int[] {240, 480, 854}) {
            var safe = HudLayout.safeArea(854, height);
            var toolbar = new HudLayout.Box(safe.x(), safe.y(), 600, 10);
            var info = new HudLayout.Box(safe.x(), safe.bottom() - 50, 175, 50);
            var criteria = HudLayout.above(safe, info, 160, 30, 6);
            var key = HudLayout.keyArea(safe, toolbar, criteria, info);
            check(
                    key.x() == toolbar.x() && key.y() == toolbar.bottom() + DefaultHud.GAP,
                    "The automatic key shares the left edge and follows the toolbar");
            check(
                    key.bottom() == criteria.y() - DefaultHud.GAP && !key.overlaps(info),
                    "The key stops before the stacked search/info panels");
        }
        for (int height : new int[] {240, 300, 480, 854}) {
            int gap = height - 54 - (71 + 5 * ResultsLayout.rowPitch(height) - 2);
            check(
                    gap >= 21 && gap <= 25,
                    "The result-to-action gap is half the previous 41–45 pixels");
            int status = ResultsLayout.statusY(height, false);
            check(status + 7 < height - 54, "Status text leaves a small gap before action buttons");
            int adviceStatus = ResultsLayout.statusY(height, true);
            check(
                    height - 73 + 7 < adviceStatus && adviceStatus + 7 < height - 54,
                    "Advice and status text both fit without overlapping each other or buttons");
        }
        cachedReads();
        System.out.println(
                "PASS independent cached reads, close-up prefetch, thin outlines and active summaries");
    }

    private static void cachedReads() throws Exception {
        var directory = Files.createTempDirectory("atlas-cache-queue-test");
        var key = new Tile.Key(-1, 2, 1);
        var cells = new Cell[1024];
        Arrays.fill(cells, new Cell("tfc:rock/raw/granite", "tfc:plains", 3, 200, 15, 2, 4, 2, 1));
        var tile = new Tile(key, cells);
        var file = directory.resolve(key.fileName());
        tile.write(file);
        var generation = new TileQueue(2, "Atlas test generation");
        var readers = new TileQueue(1, "Atlas test cache");
        CountDownLatch started = new CountDownLatch(2),
                release = new CountDownLatch(1),
                read = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int i = 0; i < 2; i++) {
                generation.execute(
                        () -> {
                            started.countDown();
                            try {
                                release.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
            }
            check(started.await(5, TimeUnit.SECONDS), "Both generation workers are occupied");
            readers.execute(
                    () -> {
                        try {
                            check(
                                    Arrays.equals(Tile.read(file, key).cells(), cells),
                                    "Cached close-up data retains exact samples");
                        } catch (Throwable e) {
                            failure.set(e);
                        } finally {
                            read.countDown();
                        }
                    });
            check(
                    read.await(5, TimeUnit.SECONDS) && failure.get() == null,
                    "Disk reads finish before either slow generation job is released");
            check(release.getCount() == 1, "Cache reads cannot be held behind terrain generation");
            List<Integer> order =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            CountDownLatch blocked = new CountDownLatch(1), unblock = new CountDownLatch(1);
            readers.execute(
                    () -> {
                        blocked.countDown();
                        try {
                            unblock.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
            check(
                    blocked.await(5, TimeUnit.SECONDS),
                    "Reader is paused for deterministic ordering");
            record Job(int distance, List<Integer> output) implements Runnable {
                public void run() {
                    output.add(distance);
                }
            }
            readers.execute(new Job(99, order));
            readers.execute(new Job(10, order));
            readers.execute(new Job(1, order));
            readers.prioritize(
                    job -> job instanceof Job j && j.distance() == 99,
                    Comparator.comparingInt(job -> ((Job) job).distance()));
            unblock.countDown();
            readers.shutdown();
            check(
                    readers.awaitTermination(5, TimeUnit.SECONDS) && order.equals(List.of(1, 10)),
                    "Obsolete cache work is removed and current tiles read from the focus outward");
        } finally {
            release.countDown();
            generation.shutdownNow();
            readers.shutdownNow();
            generation.awaitTermination(5, TimeUnit.SECONDS);
            readers.awaitTermination(5, TimeUnit.SECONDS);
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private LoadingAndSummaryTest() {}
}
