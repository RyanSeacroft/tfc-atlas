package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BackgroundPrecache;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.HoverCell;
import dev.ryan.tfcatlas.core.PrecacheArea;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileRetries;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public final class CursorAndRetryTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        cursor();
        retries();
        regions();
        System.out.println(
                "PASS: "
                        + checks
                        + " independent cursor, tile retry and invalid-region recovery checks");
    }

    private static Cell cell(int x, int z) {
        return new Cell("granite", "plains", 2, x, z, 0, 0, 0, 1);
    }

    private static void cursor() {
        var jobs = new ArrayDeque<Runnable>();
        var now = new AtomicLong();
        var sampled = new ArrayList<String>();
        var error = new AtomicBoolean();
        var hover =
                new HoverCell(
                        jobs::add,
                        (x, z) -> {
                            sampled.add(x + "," + z);
                            if (error.get()) {
                                throw new NullPointerException();
                            }
                            return cell(x, z);
                        },
                        ex -> {},
                        now::get);
        check(
                hover.at(-182033, 303141) == null && jobs.isEmpty(),
                "A wide-map cursor starts an independent debounced request");
        now.set(80);
        hover.at(-182033, 303141);
        check(jobs.size() == 1, "The cursor can schedule without loaded map tiles or any LOD gate");
        for (int i = 0; i < 100; i++) {
            now.addAndGet(100);
            hover.at(i, -i);
            hover.at(i, -i);
        }
        check(jobs.size() == 1, "Fast pointer movement never queues hundreds of cursor samples");
        jobs.remove().run();
        var result = hover.at(-182033, 303141);
        check(
                result != null && !result.failed() && result.cell().rain() == -182033,
                "Exact signed coordinates are sampled and retained");
        check(
                hover.at(555, 666) == null,
                "A previous coordinate's value is never shown as the current cursor");
        now.addAndGet(80);
        hover.at(555, 666);
        jobs.remove().run();
        check(
                hover.at(555, 666).cell().temperature() == 666,
                "The latest stationary cursor is served next");
        error.set(true);
        hover.at(10, 20);
        now.addAndGet(80);
        hover.at(10, 20);
        jobs.remove().run();
        check(
                hover.at(10, 20).failed(),
                "A null-message exception becomes an explicit unavailable cursor state");
        now.addAndGet(1999);
        hover.at(10, 20);
        check(jobs.isEmpty(), "Cursor failures wait before retrying");
        now.incrementAndGet();
        hover.at(10, 20);
        error.set(false);
        jobs.remove().run();
        check(!hover.at(10, 20).failed(), "Transient cursor failures recover automatically");
        hover.at(123, 456);
        now.addAndGet(80);
        hover.at(123, 456);
        hover.close();
        jobs.remove().run();
        check(hover.at(123, 456) == null, "Closing a world discards an in-flight cursor result");
    }

    private static void retries() {
        var now = new AtomicLong();
        var retry = new TileRetries(now::get);
        var key = new Tile.Key(-3, 4, 32);
        retry.failed(key, new NullPointerException());
        check(
                retry.deferred(key)
                        && retry.status().contains("NullPointerException")
                        && !retry.status().contains(": null"),
                "Message-less exceptions still identify their type");
        now.set(999);
        check(retry.deferred(key), "First retry is delayed");
        now.set(1000);
        check(
                !retry.deferred(key),
                "A failed tile becomes eligible again instead of permanently blacklisted");
        retry.failed(key, new java.io.IOException());
        now.set(2999);
        check(retry.deferred(key), "Repeated failures back off");
        now.set(3000);
        check(!retry.deferred(key), "Second attempt becomes eligible after two seconds");
        retry.succeeded(key);
        check(
                retry.count() == 0 && retry.status().isEmpty(),
                "Recovery clears the stale error message");
        var jobs = new ArrayDeque<Runnable>();
        var attempts = new AtomicInteger();
        var errors = new AtomicInteger();
        var target = Tile.Key.at(0, 0, PrecacheArea.STEP);
        var loaded = new HashSet<Tile.Key>();
        var background =
                new BackgroundPrecache(
                        jobs::add,
                        k -> !k.equals(target) || loaded.contains(k),
                        (k, obsolete) -> {
                            if (attempts.incrementAndGet() == 1) {
                                throw new NullPointerException();
                            }
                            loaded.add(k);
                            return true;
                        },
                        () -> false,
                        (k, ex) -> {
                            check(
                                    k.equals(target),
                                    "Background diagnostics identify the failed tile");
                            errors.incrementAndGet();
                        },
                        2,
                        retry);
        background.update(0, 0, true);
        int scans = 0;
        while (!jobs.isEmpty()) {
            jobs.remove().run();
            check(++scans < 100, "A delayed failure cannot spin in a hot retry loop");
        }
        check(
                attempts.get() == 1 && errors.get() == 1 && retry.count() == 1,
                "The remaining background plan continues while a failed tile waits");
        now.addAndGet(1000);
        background.update(0, 0, true);
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        check(
                attempts.get() == 2 && loaded.contains(target) && retry.count() == 0,
                "Failed background tiles retry even after the rest of the plan has finished");
        background.close();
    }

    private static void equal(Region.Point a, Region.Point b) throws Exception {
        for (var field : Region.Point.class.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                check(
                        Objects.equals(field.get(a), field.get(b)),
                        "Recovered terrain retains stock TFC field " + field.getName());
            }
        }
        check(
                a.land() == b.land()
                        && a.river() == b.river()
                        && a.lake() == b.lake()
                        && a.mountain() == b.mountain(),
                "Recovered region flags match stock TFC");
    }

    private static void regions() throws Exception {
        var settings = new Settings(false, 4000, 0, 0, 20000, 0, 20000, 0, null, .5f, .5f);
        var reference = new RegionGenerator(settings, new XoroshiroRandomSource(19));
        Region wrong = reference.getOrCreateRegion(1000, 1000);
        var rebuilds = new AtomicInteger();
        var repaired =
                new AtlasRegionGenerator(settings, new XoroshiroRandomSource(19)) {
                    @Override
                    protected Region cachedRegion(int x, int z) {
                        return wrong;
                    }

                    @Override
                    protected Region freshRegion(int x, int z) {
                        rebuilds.incrementAndGet();
                        return super.freshRegion(x, z);
                    }
                };
        equal(repaired.getOrCreateRegionPoint(5, -3), reference.getOrCreateRegionPoint(5, -3));
        check(
                rebuilds.get() == 1,
                "A cached region lacking the requested point is regenerated exactly once");
        var jobs = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Region.Point>>();
            for (int i = 0; i < 64; i++) {
                futures.add(jobs.submit(() -> repaired.getOrCreateRegionPoint(5, -3)));
            }
            for (var f : futures) {
                equal(f.get(), reference.getOrCreateRegionPoint(5, -3));
            }
        } finally {
            jobs.shutdownNow();
        }
        check(
                rebuilds.get() == 1,
                "Parallel readers share the verified recovery rather than regenerating repeatedly");
        var sampler = new OverviewSampler(repaired);
        equal(sampler.point(5, -3), reference.getOrCreateRegionPoint(5, -3));
        var absent =
                new AtlasRegionGenerator(settings, new XoroshiroRandomSource(19)) {
                    @Override
                    protected Region cachedRegion(int x, int z) {
                        return null;
                    }

                    @Override
                    protected Region freshRegion(int x, int z) {
                        return null;
                    }
                };
        try {
            absent.getOrCreateRegionPoint(-67, 89);
            throw new AssertionError("Missing point silently accepted");
        } catch (IllegalStateException expected) {
            check(
                    expected.getMessage().contains("X -67, Z 89"),
                    "Persistent missing data produces a useful coordinate-specific error, never fabricated terrain");
        }
        var thrown =
                new AtlasRegionGenerator(settings, new XoroshiroRandomSource(19)) {
                    @Override
                    protected Region cachedRegion(int x, int z) {
                        throw new NullPointerException();
                    }
                };
        equal(thrown.getOrCreateRegionPoint(5, -3), reference.getOrCreateRegionPoint(5, -3));
        var cancelled =
                new AtlasRegionGenerator(settings, new XoroshiroRandomSource(19)) {
                    @Override
                    protected Region cachedRegion(int x, int z) {
                        throw new CancellationException();
                    }

                    @Override
                    protected Region freshRegion(int x, int z) {
                        throw new AssertionError("Cancellation must not regenerate");
                    }
                };
        try {
            cancelled.getOrCreateRegionPoint(5, -3);
            throw new AssertionError("Cancellation ignored");
        } catch (CancellationException expected) {
            checks++;
        }
    }
}
