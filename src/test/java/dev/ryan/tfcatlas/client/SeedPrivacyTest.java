package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.BackgroundPrecache;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.PrecacheArea;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileRetries;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public final class SeedPrivacyTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        privacy();
        persistence();
        writeFailure();
        System.out.println(
                "PASS: "
                        + checks
                        + " seed-free persistence, real regional tile caching, restart reuse and failed-write retry checks");
    }

    private static void privacy() {
        Profile p = new Profile();
        p.seed = "-827362514928374650";
        p.rocks = "granite, marble";
        p.searchRockLayer = "Bottom";
        p.radius = 16384;
        p.minRain = 123;
        Profile older = new Profile();
        older.seed = "725193847652341824";
        p.savedSearches.put("Legacy", Profiles.JSON.toJson(older));
        // Older releases saved the whole profile recursively into the next preset.
        p.savedSearches.put("Nested", Profiles.JSON.toJson(p));
        p.savedSearches.put("Unreadable", "broken seed " + p.seed);
        String multiplayer = Profiles.stored(p, true),
                singleplayer = Profiles.stored(p, false),
                preset = Profiles.preset(p);
        check(
                !multiplayer.contains(p.seed)
                        && !multiplayer.contains(older.seed)
                        && !multiplayer.contains("\\\"seed\\\""),
                "No raw multiplayer seed survives in the profile or nested preset strings");
        check(
                !JsonParser.parseString(multiplayer).getAsJsonObject().has("seed"),
                "Multiplayer JSON omits the seed field entirely");
        check(
                singleplayer.contains(p.seed) && !singleplayer.contains(older.seed),
                "Singleplayer can remember its own seed but presets never contain any");
        check(
                !preset.contains("seed")
                        && !preset.contains("savedSearches")
                        && !preset.contains("temperatureScale"),
                "Presets contain only search settings and do not grow recursively");
        var restored = Profiles.decode(JsonParser.parseString(preset).getAsJsonObject());
        check(
                restored.query().equals(p.query())
                        && restored.radius == 16384
                        && restored.searchRockLayer.equals("Bottom"),
                "Sanitization preserves all intended search criteria");
        check(
                p.seed.equals("-827362514928374650") && p.savedSearches.size() == 3,
                "Writing a safe disk representation does not erase the active prediction session");
        var clean = Profiles.decode(JsonParser.parseString(multiplayer).getAsJsonObject());
        check(
                clean.seed.isBlank() && clean.savedSearches.size() == 2,
                "Reconnecting cannot restore a multiplayer seed from a cleaned profile");
        check(
                Profiles.stored(clean, true).equals(multiplayer),
                "Legacy profile sanitization is stable on repeated saves");
    }

    private static Tile sample(OverviewSampler sampler, Tile.Key key) {
        Cell[] cells = new Cell[1024];
        for (int i = 0; i < cells.length; i++) {
            int x = key.blockX() + (i % 32) * 256 + 128, z = key.blockZ() + (i / 32) * 256 + 128;
            Region.Point p = sampler.point(Math.floorDiv(x, 128), Math.floorDiv(z, 128));
            cells[i] =
                    new Cell(
                            "granite",
                            "biome_" + p.biome,
                            p.rock & 3,
                            p.rainfall,
                            p.temperature,
                            p.biomeAltitude,
                            p.baseLandHeight,
                            p.distanceToOcean,
                            (p.land() ? 1 : 0)
                                    | (p.river() ? 2 : 0)
                                    | (p.lake() ? 4 : 0)
                                    | (p.mountain() ? 8 : 0),
                            "marble",
                            "gneiss");
        }
        return new Tile(key, cells);
    }

    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("atlas-background-integration");
        var settings = new Settings(false, 4000, 0, 0, 20000, 0, 20000, 0, null, .5f, .5f);
        var sampler =
                new OverviewSampler(
                        new AtlasRegionGenerator(settings, new XoroshiroRandomSource(22)));
        List<Tile.Key> keys = new ArrayList<>();
        for (var key : PrecacheArea.at(-1000, 3000)) {
            keys.add(key);
            if (keys.size() == 32) {
                break;
            }
        }
        Set<Tile.Key> targets = Set.copyOf(keys);
        var expected = new ConcurrentHashMap<Tile.Key, Tile>();
        var errors = new ConcurrentLinkedQueue<Exception>();
        var workers = Executors.newFixedThreadPool(2);
        var saved = new CountDownLatch(keys.size());
        BackgroundPrecache cache =
                new BackgroundPrecache(
                        workers,
                        k ->
                                !targets.contains(k)
                                        || Files.isRegularFile(directory.resolve(k.fileName())),
                        (key, obsolete) -> {
                            Tile tile = sample(sampler, key);
                            tile.write(directory.resolve(key.fileName()));
                            expected.put(key, tile);
                            saved.countDown();
                            return true;
                        },
                        () -> false,
                        errors::add,
                        2);
        try {
            // Only the session update is invoked: no map window, render request or Minecraft
            // screen.
            cache.update(-1000, 3000, true);
            check(
                    saved.await(20, TimeUnit.SECONDS),
                    "Background work writes real TFC regional data after one session update without a map");
        } finally {
            cache.close();
            workers.shutdown();
            check(workers.awaitTermination(5, TimeUnit.SECONDS), "Background workers stop cleanly");
        }
        check(
                errors.isEmpty() && expected.size() == 32 && cache.progress().completed() == 32,
                "Progress counts successful persistent tiles only");
        long bytes = 0;
        for (var key : keys) {
            Path path = directory.resolve(key.fileName());
            bytes += Files.size(path);
            var read = Tile.read(path, key);
            check(
                    Arrays.equals(read.cells(), expected.get(key).cells()),
                    "All regional fields and three rock IDs survive a real compressed write/read");
        }
        var queued = new ArrayDeque<Runnable>();
        var warm =
                new BackgroundPrecache(
                        queued::add,
                        k ->
                                !targets.contains(k)
                                        || Files.isRegularFile(directory.resolve(k.fileName())),
                        (key, obsolete) -> {
                            throw new AssertionError(
                                    "Previously saved background tile regenerated");
                        },
                        () -> false,
                        ex -> {
                            throw new AssertionError(ex);
                        },
                        2);
        warm.update(-1000, 3000, true);
        int scans = 0;
        while (!queued.isEmpty()) {
            queued.remove().run();
            check(++scans < 70, "Warm disk cache finishes scanning without repeated generation");
        }
        check(
                warm.progress().finished() && warm.progress().completed() == 0,
                "A new session reuses disk tiles rather than relying on the previous in-memory cache");
        warm.close();
        System.out.println(
                "Background disk audit: 32 real regional tiles / 32,768 samples, "
                        + bytes
                        + " compressed bytes; exact restart readback passed");
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) {
                Files.delete(path);
            }
        }
        Files.delete(directory);
    }

    private static void writeFailure() throws Exception {
        Path dir = Files.createTempDirectory("atlas-cache-write-failure"),
                blocked = dir.resolve("cache");
        Files.writeString(blocked, "not a directory");
        var key = Tile.Key.at(0, 0, PrecacheArea.STEP);
        Cell[] cells = new Cell[1024];
        Arrays.fill(cells, new Cell("granite", "plains", 2, 200, 15, 2, 3, 4, 1));
        Tile tile = new Tile(key, cells);
        var jobs = new ArrayDeque<Runnable>();
        var clock = new AtomicLong();
        var errors = new AtomicInteger();
        var retries = new TileRetries(clock::get);
        var cache =
                new BackgroundPrecache(
                        jobs::add,
                        k -> !key.equals(k) || Files.isRegularFile(blocked.resolve(k.fileName())),
                        (k, obsolete) -> {
                            tile.write(blocked.resolve(k.fileName()));
                            return true;
                        },
                        () -> false,
                        (k, ex) -> errors.incrementAndGet(),
                        2,
                        retries);
        cache.update(0, 0, true);
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        check(
                errors.get() == 1
                        && cache.progress().completed() == 0
                        && !cache.progress().finished(),
                "An actual disk write error remains incomplete and waits for retry");
        Files.delete(blocked);
        Files.createDirectory(blocked);
        clock.set(1000);
        cache.update(0, 0, true);
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        check(
                Files.isRegularFile(blocked.resolve(key.fileName()))
                        && cache.progress().completed() == 1
                        && cache.progress().finished(),
                "The missing persistent tile is restored after the write failure is repaired");
        cache.close();
        Files.delete(blocked.resolve(key.fileName()));
        Files.delete(blocked);
        Files.delete(dir);
    }
}
