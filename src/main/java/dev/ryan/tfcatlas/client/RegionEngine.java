package dev.ryan.tfcatlas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ryan.tfcatlas.core.BackgroundPrecache;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.DikeCoverage;
import dev.ryan.tfcatlas.core.HoverCell;
import dev.ryan.tfcatlas.core.HoverHeight;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.NearestMatches;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.RockPossibilities;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchDetails;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchProgress;
import dev.ryan.tfcatlas.core.SearchQuery;
import dev.ryan.tfcatlas.core.SearchWindow;
import dev.ryan.tfcatlas.core.SearchWorkload;
import dev.ryan.tfcatlas.core.SeedPrivacy;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileRetries;
import dev.ryan.tfcatlas.core.TileWindow;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.layer.TFCLayers;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraftforge.fml.ModList;

/** Each session owns its generator. No Minecraft chunks or server are created. */
public final class RegionEngine implements AutoCloseable {
    public final Settings settings;
    private final Map<RockLayer, Set<String>> possibleRocks;

    public Set<String> possibleRocks(RockLayer layer) {
        return possibleRocks.get(layer);
    }

    public final long seed;
    public final Path directory;
    private final RegionGenerator generator;
    private final OverviewSampler overview;
    private final TerrainHeightSampler heights;
    private final FineSampler fine;
    private DikeSampler dikes;
    private Future<?> searchTask;
    private final HoverHeight hoverHeight;
    private final HoverCell hoverCell;
    private TerrainHeightSampler hoverSampler;
    private NearbyWater searchWater;
    private NearbyWater hoverWater;
    private final Map<Long, LocalInfo> localInfo = new LinkedHashMap<>(128, .75f, true);

    public record LocalInfo(double river, double ocean, double slope) {}

    public LocalInfo localInfo(int x, int z) {
        synchronized (localInfo) {
            return localInfo.get(((long) x << 32) | (z & 0xffffffffL));
        }
    }

    private void collectLocalInfo(int x, int z) {
        if (closed) {
            return;
        }
        LocalInfo info;
        try {
            if (hoverWater == null) {
                hoverWater = new NearbyWater(generator);
            }
            Cell cell = fine.sample(x, z);
            var water = hoverWater.sample(x, z, !cell.land(), cell.river());
            info =
                    new LocalInfo(
                            water.river(),
                            water.ocean(),
                            dev.ryan.tfcatlas.core.TerrainSlope.sample(x, z, hoverSampler::sample));
        } catch (RuntimeException failure) {
            info = new LocalInfo(Double.NaN, Double.NaN, Double.NaN);
        }
        synchronized (localInfo) {
            if (closed) {
                return;
            }
            localInfo.put(((long) x << 32) | (z & 0xffffffffL), info);
            while (localInfo.size() > 1024) {
                localInfo.remove(localInfo.keySet().iterator().next());
            }
        }
    }

    private boolean matchesLocal(SearchQuery query, int x, int z, Cell cell) {
        var limits = query.limits();
        if (limits.slope()) {
            double slope = dev.ryan.tfcatlas.core.TerrainSlope.sample(x, z, heights::sample);
            if (slope < limits.minSlope() || slope > limits.maxSlope()) {
                return false;
            }
        }
        if (limits.water()) {
            if (searchWater == null) {
                searchWater = new NearbyWater(generator);
            }
            var water = searchWater.sample(x, z, !cell.land(), cell.river());
            if (!limits.matchesWater(water.river(), water.ocean())) {
                return false;
            }
        }
        return true;
    }

    private final ExecutorService hoverWorker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "TFC Atlas cursor data");
                        t.setDaemon(true);
                        return t;
                    });
    private final Set<String> diskFiles = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong revision =
            new java.util.concurrent.atomic.AtomicLong();

    public long revision() {
        return revision.get();
    }

    public boolean cached(Tile.Key key) {
        return peek(key) != null || (disk && diskFiles.contains(key.fileName()));
    }

    public HoverHeight.Result hoverHeight(int x, int z) {
        return hoverHeight.at(x, z, System.nanoTime() / 1_000_000);
    }

    private volatile int displayStep = 32 / Tile.GRID;

    private record VisibleTiles(TileWindow main, TileWindow detail, TileWindow preview) {
        boolean contains(Tile.Key key) {
            return main.contains(key) || detail.contains(key) || preview.contains(key);
        }
    }

    private volatile VisibleTiles visibleTiles;
    private volatile boolean mapActive;

    private static final class ObsoleteTile extends IOException {}

    public void displayStep(int step) {
        displayStep = step;
    }

    private static final int TILE_WORKERS =
            dev.ryan.tfcatlas.core.GenerationBudget.workers(
                    Runtime.getRuntime().availableProcessors());
    private final dev.ryan.tfcatlas.core.TileQueue worker =
            new dev.ryan.tfcatlas.core.TileQueue(TILE_WORKERS, "TFC Atlas tiles");
    private final dev.ryan.tfcatlas.core.TileQueue cacheReader =
            new dev.ryan.tfcatlas.core.TileQueue(2, "TFC Atlas cached tiles");
    private final ExecutorService searchWorker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "TFC Atlas search");
                        t.setDaemon(true);
                        return t;
                    });
    private final Map<Tile.Key, Tile> tiles = new LinkedHashMap<>(128, .75f, true);
    private final Map<Tile.Key, Boolean> pending = new ConcurrentHashMap<>();
    private final TileRetries retries = new TileRetries();

    public String statusText() {
        String pending = retries.status();
        return pending.isEmpty() ? status : pending;
    }

    private void tileError(Tile.Key key, Exception error) {
        if (key == null) {
            com.mojang.logging.LogUtils.getLogger().warn("Atlas background cache failed", error);
            return;
        }
        com.mojang.logging.LogUtils.getLogger()
                .warn(
                        "Atlas tile generation failed at X {}, Z {}, spacing {} blocks",
                        key.blockX(),
                        key.blockZ(),
                        Tile.GRID * key.step(),
                        error);
    }

    private final AtomicInteger searchSerial = new AtomicInteger();
    private final int memoryTiles, diskMB;
    private final boolean disk;
    private volatile boolean closed;
    public volatile String status = "Ready", searchStatus = "No search yet", searchAdvice = "";
    public volatile List<SearchQuery.Result> results = List.of();
    public volatile SearchQuery activeQuery;
    public volatile SearchDetails searchDetails;
    public volatile SearchOverlay searchOverlay = SearchOverlay.EMPTY;
    public volatile int searchX, searchZ, searchRadius;
    public volatile boolean searching;

    private Future<?> closestTask;
    private SearchOverlay closestOverlay;
    private int closestX, closestZ;

    /** Re-rank all completed coverage off the UI thread when the player changes position. */
    public synchronized void updateClosestResults(int playerX, int playerZ) {
        SearchOverlay overlay = searchOverlay;
        SearchDetails details = searchDetails;
        if (closed
                || searching
                || !overlay.complete()
                || details == null
                || overlay.count() == 0
                || closestTask != null && !closestTask.isDone()) {
            return;
        }
        if (closestOverlay == overlay && closestX == playerX && closestZ == playerZ) {
            return;
        }
        int serial = searchSerial.get();
        closestTask =
                searchWorker.submit(
                        () -> {
                            try {
                                List<SearchQuery.Result> current = new ArrayList<>();
                                for (var point :
                                        overlay.closestTo(
                                                playerX,
                                                playerZ,
                                                Math.min(5, details.limit()),
                                                details.spacing())) {
                                    if (closed || serial != searchSerial.get()) {
                                        return;
                                    }
                                    // Reuse metadata when only the distance changed. Other
                                    // locations are sampled
                                    // on the same worker that owns the height/dike search samplers.
                                    SearchQuery.Result previous =
                                            results.stream()
                                                    .filter(
                                                            r ->
                                                                    r.x() == point.x()
                                                                            && r.z() == point.z())
                                                    .findFirst()
                                                    .orElse(null);
                                    if (previous != null) {
                                        current.add(
                                                new SearchQuery.Result(
                                                        point.x(),
                                                        point.z(),
                                                        point.distance(),
                                                        previous.cell(),
                                                        previous.surfaceY(),
                                                        point.layerMask(),
                                                        previous.dikeY()));
                                    } else {
                                        current.add(resultAt(point, details.query()));
                                    }
                                }
                                synchronized (this) {
                                    if (!closed
                                            && serial == searchSerial.get()
                                            && searchOverlay == overlay) {
                                        results = List.copyOf(current);
                                        closestOverlay = overlay;
                                        closestX = playerX;
                                        closestZ = playerZ;
                                    }
                                }
                            } catch (java.util.concurrent.CancellationException ignored) {
                            } catch (Exception error) {
                                com.mojang.logging.LogUtils.getLogger()
                                        .warn(
                                                "Atlas could not update closest search results",
                                                error);
                            }
                        });
    }

    private SearchQuery.Result resultAt(NearestMatches.Point point, SearchQuery query)
            throws Exception {
        Cell cell = load(point.key()).atBlock(point.x(), point.z());
        DikeSampler.Hit hit = query.dikes() ? dikes.at(point.x(), point.z(), query) : null;
        if (hit != null) {
            cell = cell.withRock(hit.rock());
        }
        return new SearchQuery.Result(
                point.x(),
                point.z(),
                point.distance(),
                cell,
                query.heightRestricted() ? heights.sample(point.x(), point.z()) : null,
                point.layerMask(),
                hit == null ? null : hit.y());
    }

    private record DiskEntry(long size, long modified) {}

    private final Map<Path, DiskEntry> diskEntries = new HashMap<>();
    private long diskBytes;
    private int diskWrites;
    private volatile boolean diskIndexed;
    private volatile boolean backgroundOverworld;
    private volatile String diskError = "";
    private final java.util.concurrent.atomic.AtomicLong
            backgroundWrites = new java.util.concurrent.atomic.AtomicLong(),
            closedMapWrites = new java.util.concurrent.atomic.AtomicLong(),
            diskReads = new java.util.concurrent.atomic.AtomicLong();
    private final Object diskLock = new Object();
    private final BackgroundPrecache precache;

    public RegionEngine(Profile p, String world) throws Exception {
        try {
            seed = Long.parseLong(p.seed.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit whole number");
        }
        Settings original = null;
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (p.useLocalSettings
                && server != null
                && server.getLevel(Level.OVERWORLD) != null
                && server.getLevel(Level.OVERWORLD).getChunkSource().getGenerator()
                        instanceof ChunkGeneratorExtension cg) {
            original = cg.settings();
        }
        if (original == null) {
            try (var in =
                    Settings.class.getResourceAsStream(
                            "/data/tfc/worldgen/world_preset/overworld.json")) {
                if (in == null) {
                    throw new IOException("Installed TFC has no default world preset");
                }
                JsonObject data =
                        JsonParser.parseReader(new InputStreamReader(in))
                                .getAsJsonObject()
                                .getAsJsonObject("dimensions")
                                .getAsJsonObject("minecraft:overworld")
                                .getAsJsonObject("generator")
                                .getAsJsonObject("tfc_settings");
                original =
                        Settings.CODEC
                                .codec()
                                .parse(JsonOps.INSTANCE, data)
                                .getOrThrow(
                                        false,
                                        s -> {
                                            throw new IllegalArgumentException(s);
                                        });
            }
            original =
                    new Settings(
                            original.flatBedrock(),
                            p.spawnRadius,
                            p.spawnX,
                            p.spawnZ,
                            p.temperatureScale,
                            p.temperatureConstant,
                            p.rainfallScale,
                            p.rainfallConstant,
                            original.rockLayerSettings(),
                            p.continentalness,
                            original.grassDensity());
        }
        settings = original;
        JsonObject rockGraph =
                net.dries007.tfc.world.settings.RockLayerSettings.CODEC
                        .encodeStart(JsonOps.INSTANCE, settings.rockLayerSettings())
                        .getOrThrow(
                                false,
                                s -> {
                                    throw new IllegalArgumentException(s);
                                })
                        .getAsJsonObject();
        possibleRocks =
                RockPossibilities.read(
                        rockGraph,
                        data -> {
                            var rock =
                                    net.dries007.tfc.world.settings.RockSettings.CODEC
                                            .parse(JsonOps.INSTANCE, data)
                                            .getOrThrow(
                                                    false,
                                                    s -> {
                                                        throw new IllegalArgumentException(s);
                                                    });
                            return BuiltInRegistries.BLOCK.getKey(rock.raw()).toString();
                        });
        var random = new XoroshiroRandomSource(seed);
        generator = new AtlasRegionGenerator(settings, random);
        overview = new OverviewSampler(generator);
        var chunks =
                net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator.create(
                        random.nextLong(), settings.rockLayerSettings(), generator);
        long biomeSeed = random.nextLong();
        var registry =
                Minecraft.getInstance()
                        .level
                        .registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        heights = new TerrainHeightSampler(seed, biomeSeed, generator, registry);
        hoverHeight =
                new HoverHeight(
                        hoverWorker,
                        (x, z) -> {
                            // Mutable TFC noise samplers never cross between the search and cursor
                            // workers.
                            if (hoverSampler == null) {
                                hoverSampler =
                                        new TerrainHeightSampler(
                                                seed, biomeSeed, generator, registry);
                            }
                            int y = hoverSampler.sample(x, z);
                            // Publish height first; the extra cursor data stays off the render
                            // thread.
                            hoverWorker.execute(() -> collectLocalInfo(x, z));
                            return y;
                        });
        fine = new FineSampler(generator, heights.source, chunks);
        hoverCell =
                new HoverCell(
                        hoverWorker,
                        (x, z) -> fine.sample(x, z, 32),
                        ex ->
                                com.mojang.logging.LogUtils.getLogger()
                                        .warn("Atlas cursor sampling failed", ex),
                        () -> System.nanoTime() / 1_000_000);
        String serialized =
                Profiles.canonical(
                                Settings.CODEC
                                        .codec()
                                        .encodeStart(JsonOps.INSTANCE, settings)
                                        .getOrThrow(
                                                false,
                                                s -> {
                                                    throw new IllegalArgumentException(s);
                                                }))
                        .toString();
        String version =
                ModList.get()
                        .getModContainerById("tfc")
                        .orElseThrow()
                        .getModInfo()
                        .getVersion()
                        .toString();
        directory =
                Profiles.root()
                        .resolve(
                                "cache/"
                                        + Profiles.hash(world)
                                        + "/"
                                        + Profiles.hash(
                                                seed
                                                        + ":"
                                                        + version
                                                        + ":"
                                                        + Tile.FORMAT
                                                        + ":"
                                                        + serialized));
        memoryTiles = p.memoryTiles;
        diskMB = p.diskMB;
        disk = p.diskCache;
        diskIndexed = !disk;
        // A memory-only tile is not a completed persistent preload.
        precache =
                new BackgroundPrecache(
                        worker,
                        key -> diskFiles.contains(key.fileName()),
                        (key, obsolete) -> {
                            try {
                                load(key, obsolete, true);
                                return true;
                            } catch (ObsoleteTile ignored) {
                                return false;
                            }
                        },
                        () -> !diskIndexed || !pending.isEmpty() || searching,
                        this::tileError,
                        TILE_WORKERS,
                        retries);
        if (disk) {
            cacheReader.execute(
                    () -> {
                        try {
                            synchronized (diskLock) {
                                if (Files.isDirectory(directory)) {
                                    try (var files = Files.list(directory)) {
                                        for (Path path :
                                                files.filter(f -> f.toString().endsWith(".gz"))
                                                        .toList()) {
                                            var attrs =
                                                    Files.readAttributes(
                                                            path,
                                                            java.nio.file.attribute
                                                                    .BasicFileAttributes.class);
                                            diskFiles.add(path.getFileName().toString());
                                            DiskEntry old =
                                                    diskEntries.put(
                                                            path,
                                                            new DiskEntry(
                                                                    attrs.size(),
                                                                    attrs.lastModifiedTime()
                                                                            .toMillis()));
                                            diskBytes +=
                                                    attrs.size() - (old == null ? 0 : old.size());
                                        }
                                    }
                                }
                            }
                        } catch (IOException ignored) {
                        }
                        trimDisk();
                        diskIndexed = true;
                    });
        }
    }

    public void background(int playerX, int playerZ, boolean overworld) {
        backgroundOverworld = overworld;
        precache.update(playerX, playerZ, disk && overworld && !closed);
    }

    public long backgroundWrites() {
        return backgroundWrites.get();
    }

    public BackgroundPrecache.Progress backgroundProgress() {
        return precache.progress();
    }

    public long closedMapWrites() {
        return closedMapWrites.get();
    }

    public List<String> cacheStatus() {
        var progress = precache.progress();
        String state =
                closed
                        ? "Stopped"
                        : !disk
                                ? "Off — enable disk caching"
                                : !backgroundOverworld
                                        ? "Paused — return to the Overworld"
                                        : !diskIndexed
                                                ? "Reading saved-tile index"
                                                : searching
                                                        ? "Paused for search"
                                                        : !pending.isEmpty()
                                                                ? "Paused for visible map tiles"
                                                                : !diskError.isEmpty()
                                                                        ? "Disk write failed — retrying"
                                                                        : progress.finished()
                                                                                ? "Overview scan complete"
                                                                                : progress
                                                                                                        .retrying()
                                                                                                > 0
                                                                                        ? "Caching; retrying failed tiles"
                                                                                        : "Caching in background";
        long bytes;
        int files;
        synchronized (diskLock) {
            bytes = diskBytes;
            files = diskEntries.size();
        }
        return List.of(
                "Background cache: " + state,
                String.format(
                        Locale.ROOT,
                        "Saved on disk: %,d tiles · %.1f / %d MiB · disk reads this session: %,d",
                        files,
                        bytes / (1024. * 1024),
                        diskMB,
                        diskReads.get()),
                String.format(
                        Locale.ROOT,
                        "Background saves this session: %,d · with map closed: %,d",
                        backgroundWrites.get(),
                        closedMapWrites.get()),
                "All "
                        + Layer.values().length
                        + " layers · 256-block overview · 512,000-block radius; zoomed detail loads on demand");
    }

    public Tile peek(Tile.Key key) {
        synchronized (tiles) {
            return tiles.get(key);
        }
    }

    public int count() {
        synchronized (tiles) {
            return tiles.size();
        }
    }

    public int pendingCount() {
        return pending.size();
    }

    public int pendingCachedCount() {
        return (int) pending.values().stream().filter(Boolean::booleanValue).count();
    }

    public int pendingCount(TileWindow window, boolean cachedReads) {
        return (int)
                pending.entrySet().stream()
                        .filter(
                                entry ->
                                        window.contains(entry.getKey())
                                                && entry.getValue() == cachedReads)
                        .count();
    }

    public boolean saved(Tile.Key key) {
        return disk
                && (diskFiles.contains(key.fileName())
                        || !diskIndexed && Files.isRegularFile(directory.resolve(key.fileName())));
    }

    public boolean failed(Tile.Key key) {
        return retries.deferred(key);
    }

    private final class TileJob implements Runnable {
        final Tile.Key key;
        final boolean cachedOnly, gapPreview;

        TileJob(Tile.Key key, boolean cachedOnly, boolean gapPreview) {
            this.key = key;
            this.cachedOnly = cachedOnly;
            this.gapPreview = gapPreview;
        }

        public void run() {
            boolean handedOff = false;
            java.util.function.BooleanSupplier obsolete =
                    () -> {
                        VisibleTiles v = visibleTiles;
                        return closed || !mapActive || v != null && !v.contains(key);
                    };
            try {
                if (obsolete.getAsBoolean()) {
                    return;
                }
                if (cachedOnly) {
                    Tile tile = readCached(key);
                    if (tile != null) {
                        remember(tile);
                        retries.succeeded(key);
                    } else if (!obsolete.getAsBoolean()) {
                        // A removed or corrupt cache entry needs regeneration. It must not
                        // turn a fast disk-reader thread into another terrain worker.
                        handedOff = queueGeneration(key, gapPreview);
                    }
                } else {
                    load(key, obsolete);
                }
            } catch (ObsoleteTile ignored) {
            } catch (Exception e) {
                if (!closed && !Thread.currentThread().isInterrupted()) {
                    retries.failed(key, e);
                    tileError(key, e);
                }
            } finally {
                if (!handedOff) {
                    pending.remove(key);
                }
            }
        }
    }

    public List<Tile> loaded() {
        synchronized (tiles) {
            return List.copyOf(tiles.values());
        }
    }

    public void prioritize(TileWindow window, TileWindow detail, TileWindow preview) {
        mapActive = true;
        VisibleTiles visible = new VisibleTiles(window, detail, preview);
        visibleTiles = visible;
        java.util.function.Predicate<Runnable> obsolete =
                job -> {
                    if (job instanceof TileJob t && !visible.contains(t.key)) {
                        pending.remove(t.key);
                        return true;
                    }
                    return false;
                };
        Comparator<Runnable> order =
                Comparator.<Runnable>comparingInt(
                                job ->
                                        job instanceof TileJob t
                                                ? (t.gapPreview
                                                        ? -1
                                                        : window.contains(t.key) ? 0 : 1)
                                                : 2)
                        .thenComparingDouble(
                                job ->
                                        job instanceof TileJob t
                                                ? window.distanceSquared(t.key)
                                                : Double.MAX_VALUE);
        worker.prioritize(obsolete, order);
        cacheReader.prioritize(obsolete, order);
    }

    public void mapClosed() {
        if (!mapActive) {
            return;
        }
        mapActive = false;
        for (var queue : List.of(worker, cacheReader)) {
            queue.getQueue()
                    .removeIf(
                            job -> {
                                if (job instanceof TileJob tile) {
                                    pending.remove(tile.key);
                                    return true;
                                }
                                return false;
                            });
        }
    }

    public void request(Tile.Key key) {
        request(key, false);
    }

    public void requestPreview(Tile.Key key) {
        request(key, true);
    }

    private void request(Tile.Key key, boolean gapPreview) {
        if (closed || peek(key) != null || retries.deferred(key)) {
            return;
        }
        boolean saved = saved(key);
        if (pending.putIfAbsent(key, saved) != null) {
            return;
        }
        try {
            if (saved) {
                cacheReader.execute(new TileJob(key, true, gapPreview));
            } else if (!queueGeneration(key, gapPreview)) {
                pending.remove(key);
            }
        } catch (RejectedExecutionException e) {
            pending.remove(key);
        }
    }

    private boolean queueGeneration(Tile.Key key, boolean gapPreview) {
        // Corrupt cached previews obey the same one-job bound as an initially missing preview.
        synchronized (pending) {
            VisibleTiles visible = visibleTiles;
            if (gapPreview
                    && visible != null
                    && visible.main.step() != key.step()
                    && pending.entrySet().stream()
                            .anyMatch(
                                    e ->
                                            !e.getKey().equals(key)
                                                    && e.getKey().step() == key.step()
                                                    && !e.getValue())) {
                return false;
            }
            pending.put(key, false);
        }
        worker.execute(new TileJob(key, false, gapPreview));
        return true;
    }

    private record OverviewRocks(String top, String middle, String bottom) {}

    private final ThreadLocal<dev.ryan.tfcatlas.core.SampleCache<OverviewRocks>> overviewRocks =
            ThreadLocal.withInitial(() -> new dev.ryan.tfcatlas.core.SampleCache<>(512));

    private OverviewRocks overviewRocks(int code) {
        var cache = overviewRocks.get();
        OverviewRocks rocks = cache.get(code);
        if (rocks == null) {
            var strata = settings.rockLayerSettings().sampler(code);
            rocks =
                    new OverviewRocks(
                            BuiltInRegistries.BLOCK.getKey(strata.next().raw()).toString(),
                            BuiltInRegistries.BLOCK.getKey(strata.next().raw()).toString(),
                            BuiltInRegistries.BLOCK.getKey(strata.next().raw()).toString());
            cache.put(code, rocks);
        }
        return rocks;
    }

    private Tile load(Tile.Key key) throws IOException {
        return load(key, () -> false);
    }

    private Tile load(Tile.Key key, java.util.function.BooleanSupplier obsolete)
            throws IOException {
        return load(key, obsolete, false);
    }

    private Tile load(Tile.Key key, java.util.function.BooleanSupplier obsolete, boolean background)
            throws IOException {
        if (obsolete.getAsBoolean()) {
            throw new ObsoleteTile();
        }
        Tile cached = peek(key);
        if (cached != null) {
            if (background && disk && !diskFiles.contains(key.fileName())) {
                try {
                    writeDisk(cached, true);
                } catch (IOException e) {
                    diskError = e.getClass().getSimpleName();
                    throw e;
                }
            }
            retries.succeeded(key);
            return cached;
        }
        if (closed || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException();
        }
        Tile tile = readCached(key);
        if (tile == null) {
            Cell[] cells = new Cell[Tile.SIDE * Tile.SIDE];
            for (int z = 0; z < Tile.SIDE; z++) {
                if (closed || Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException();
                }
                if (obsolete.getAsBoolean()) {
                    throw new ObsoleteTile();
                }
                for (int x = 0; x < Tile.SIDE; x++) {
                    int resolution = Tile.GRID * key.step(),
                            bx = key.blockX() + x * resolution + resolution / 2,
                            bz = key.blockZ() + z * resolution + resolution / 2;
                    if (resolution <= 128) {
                        cells[x + z * Tile.SIDE] = fine.sample(bx, bz, resolution);
                        continue;
                    }
                    if (obsolete.getAsBoolean()) {
                        throw new ObsoleteTile();
                    }
                    Region.Point point =
                            overview.point(Math.floorDiv(bx, 128), Math.floorDiv(bz, 128));
                    var column = overviewRocks(point.rock);
                    String rock = column.top(), middle = column.middle(), bottom = column.bottom();
                    String biome =
                            TFCLayers.getFromLayerId(point.biome).key().location().toString();
                    cells[x + z * Tile.SIDE] =
                            new Cell(
                                    rock,
                                    biome,
                                    point.rock & 3,
                                    point.rainfall,
                                    point.temperature,
                                    point.biomeAltitude,
                                    point.baseLandHeight,
                                    point.distanceToOcean,
                                    (point.land() ? 1 : 0)
                                            | (point.river() ? 2 : 0)
                                            | (point.lake() ? 4 : 0)
                                            | (point.mountain() ? 8 : 0),
                                    middle,
                                    bottom);
                }
            }
            tile = new Tile(key, cells);
            if (obsolete.getAsBoolean()) {
                throw new ObsoleteTile();
            }
            // Publish before compression/trimming so the first central tile can be drawn
            // immediately.
            if (!background) {
                remember(tile);
            }
            if (disk && !closed) {
                try {
                    writeDisk(tile, background);
                } catch (IOException e) {
                    diskError = e.getClass().getSimpleName();
                    status = "Disk cache unavailable; using memory";
                    if (background) {
                        throw e;
                    } // Keep failed background saves in the retry plan.
                }
            }
            retries.succeeded(key);
            return tile;
        }
        // An unseen preload must not evict tiles needed by the current map or search.
        if (!background) {
            remember(tile);
        }
        retries.succeeded(key);
        return tile;
    }

    private Tile readCached(Tile.Key key) {
        Path path = directory.resolve(key.fileName());
        if (disk && Files.isRegularFile(path)) {
            try {
                Tile tile = Tile.read(path, key);
                diskReads.incrementAndGet();
                return tile;
            } catch (IOException ignored) {
                // The caller schedules regeneration on the terrain queue.
            }
        }
        return null;
    }

    private void writeDisk(Tile tile, boolean background) throws IOException {
        Path path = directory.resolve(tile.key().fileName());
        tile.write(path);
        synchronized (diskLock) {
            diskFiles.add(tile.key().fileName());
            long size = Files.size(path);
            DiskEntry old = diskEntries.put(path, new DiskEntry(size, System.currentTimeMillis()));
            diskBytes += size - (old == null ? 0 : old.size());
            if (++diskWrites % 64 == 0) {
                trimDisk();
            }
        }
        diskError = "";
        if (status.equals("Disk cache unavailable; using memory")) {
            status = "Ready";
        }
        if (background) {
            long saved = backgroundWrites.incrementAndGet();
            if (!mapActive) {
                closedMapWrites.incrementAndGet();
            }
            if (saved == 1) {
                com.mojang.logging.LogUtils.getLogger()
                        .info(
                                "Atlas background cache persisted its first all-layer overview tile with the map {}",
                                mapActive ? "open" : "closed");
            }
        }
    }

    private void remember(Tile tile) {
        synchronized (tiles) {
            if (!closed) {
                tiles.put(tile.key(), tile);
                revision.incrementAndGet();
                while (tiles.size() > memoryTiles) {
                    tiles.remove(tiles.keySet().iterator().next());
                }
            }
        }
    }

    public Cell cell(int x, int z) {
        int preferred = Math.min(displayStep, 32 / Tile.GRID);
        Tile t = peek(Tile.Key.at(x, z, preferred));
        if (t != null) {
            return t.atBlock(x, z);
        }
        // This one-column job also runs when wide map textures outlive their CPU tile data.
        HoverCell.Result point = hoverCell.at(x, z);
        if (point != null && !point.failed()) {
            return point.cell();
        }
        for (int step = preferred * 2; step <= Sampling.MAX_MAP_STEP; step *= 2) {
            t = peek(Tile.Key.at(x, z, step));
            if (t != null) {
                return t.atBlock(x, z);
            }
        }
        return null;
    }

    public String cursorStatus(int x, int z) {
        var result = hoverCell.at(x, z);
        return result != null && result.failed()
                ? "Cursor sample unavailable · retrying…"
                : "Loading cursor data…";
    }

    public SearchWorkload searchWorkload(
            SearchQuery query, int x, int z, int radius, int resolution, boolean manual) {
        // Bounded, evenly distributed probes. Never walk a huge search window on the UI thread.
        int checked = 0, ready = 0;
        Set<Tile.Key> visited = new HashSet<>();
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                if (dx * dx + dz * dz > 16) {
                    continue;
                }
                long bx = x + (long) radius * dx / 4, bz = z + (long) radius * dz / 4;
                if (Math.abs(bx) > 29_999_984 || Math.abs(bz) > 29_999_984) {
                    continue;
                }
                Tile.Key key = Tile.Key.at((int) bx, (int) bz, resolution / Tile.GRID);
                if (visited.add(key)) {
                    checked++;
                    if (cached(key)) {
                        ready++;
                    }
                }
            }
        }
        return SearchWorkload.estimate(
                query, radius, resolution, checked == 0 ? 0 : ready / (double) checked, manual);
    }

    public void search(
            SearchQuery query, int centerX, int centerZ, int radius, int limit, int spacing) {
        search(query, centerX, centerZ, radius, limit, spacing, 128);
    }

    public synchronized void search(
            SearchQuery query,
            int centerX,
            int centerZ,
            int radius,
            int limit,
            int spacing,
            int resolution) {
        search(query, centerX, centerZ, radius, limit, spacing, resolution, false);
    }

    public synchronized void search(
            SearchQuery query,
            int centerX,
            int centerZ,
            int radius,
            int limit,
            int spacing,
            int resolution,
            boolean automatic) {
        search(query, centerX, centerZ, radius, limit, spacing, resolution, automatic, Map.of());
    }

    public synchronized void search(
            SearchQuery query,
            int centerX,
            int centerZ,
            int radius,
            int limit,
            int spacing,
            int resolution,
            boolean automatic,
            Map<String, String> selections) {
        Sampling.searchResolution(Integer.toString(resolution), radius);
        SearchProgress progress = new SearchProgress(false, query.heightRestricted());
        cancelSearch();
        int serial = searchSerial.get();
        results = List.of();
        searchOverlay = SearchOverlay.EMPTY;
        activeQuery = query;
        searchDetails =
                new SearchDetails(
                        query, centerX, centerZ, radius, limit, spacing, resolution, selections);
        searchX = centerX;
        searchZ = centerZ;
        searchRadius = radius;
        searching = true;
        searchStatus = progress.initial();
        searchAdvice = "Live highlights appear on the map as matches are found.";
        searchTask =
                searchWorker.submit(
                        () -> {
                            try {
                                if (query.dikes() && dikes == null) {
                                    dikes = new DikeSampler(seed, heights, fine);
                                }
                                TileWindow keys =
                                        SearchWindow.of(centerX, centerZ, radius, resolution);
                                long matches = 0, done = 0;
                                Map<Tile.Key, byte[]> matched = new HashMap<>();
                                Set<DikeSampler.Footprint> refinedDikes = new HashSet<>();
                                SearchOverlay.Builder live =
                                        new SearchOverlay.Builder(
                                                matched,
                                                query.layered(),
                                                radius,
                                                query.dikes() ? 8 : resolution);
                                long nextPublish = System.nanoTime() + 250_000_000L;
                                for (Tile.Key key : keys) {
                                    if (closed || serial != searchSerial.get()) {
                                        return;
                                    }
                                    if (!SearchWindow.intersectsRadius(
                                            key, centerX, centerZ, radius)) {
                                        searchProgress(
                                                serial,
                                                progress.update(
                                                        ++done * 1024,
                                                        keys.count() * 1024,
                                                        matches,
                                                        System.nanoTime() / 1_000_000));
                                        continue;
                                    }
                                    Tile tile = load(key);
                                    for (int i = 0; i < tile.cells().length; i++) {
                                        int
                                                x =
                                                        key.blockX()
                                                                + (i % 32) * resolution
                                                                + resolution / 2,
                                                z =
                                                        key.blockZ()
                                                                + (i / 32) * resolution
                                                                + resolution / 2;
                                        double dist =
                                                Math.hypot(
                                                        (double) x - centerX, (double) z - centerZ);
                                        if ((i & 31) == 0) {
                                            if (closed || serial != searchSerial.get()) {
                                                return;
                                            }
                                            searchProgress(
                                                    serial,
                                                    progress.update(
                                                            done * 1024 + i,
                                                            keys.count() * 1024,
                                                            matches,
                                                            System.nanoTime() / 1_000_000));
                                            if (live.changed()
                                                    && System.nanoTime() >= nextPublish) {
                                                nextPublish = publishLive(serial, live);
                                            }
                                        }
                                        Cell c = tile.cells()[i];
                                        if (Math.abs((long) x) <= 29_999_984
                                                && Math.abs((long) z) <= 29_999_984
                                                && dist <= radius
                                                && query.matchesRegion(c)) {
                                            Integer y =
                                                    query.heightRestricted()
                                                            ? heights.sample(x, z)
                                                            : null;
                                            if (y != null && !query.matchesHeight(y)) {
                                                continue;
                                            }
                                            if (!matchesLocal(query, x, z, c)) {
                                                continue;
                                            }
                                            if (query.dikes()) {
                                                var hit = dikes.at(x, z, query);
                                                if (hit == null) {
                                                    continue;
                                                }
                                                if (refinedDikes.add(hit.footprint())) {
                                                    matches +=
                                                            refineDike(
                                                                    hit.footprint(),
                                                                    query,
                                                                    centerX,
                                                                    centerZ,
                                                                    radius,
                                                                    matched,
                                                                    live);
                                                }
                                                continue;
                                            }
                                            int layerMask =
                                                    query.dikes() ? 8 : query.matchingLayers(c);
                                            matches++;
                                            matched.computeIfAbsent(key, k -> new byte[1024])[i] =
                                                    (byte) layerMask;
                                            live.changed(key);
                                        }
                                    }
                                    if (serial != searchSerial.get()) {
                                        return;
                                    }
                                    searchProgress(
                                            serial,
                                            progress.update(
                                                    (long) ++done * 1024,
                                                    keys.count() * 1024,
                                                    matches,
                                                    System.nanoTime() / 1_000_000));
                                    if (done < keys.count()
                                            && live.changed()
                                            && System.nanoTime() >= nextPublish) {
                                        nextPublish = publishLive(serial, live);
                                    }
                                }
                                searchProgress(
                                        serial, "Sampling complete · finalising highlights…");
                                SearchOverlay partial = live.publish(List.of(), false);
                                synchronized (this) {
                                    if (serial != searchSerial.get()) {
                                        return;
                                    }
                                    searchOverlay = partial;
                                    searchStatus = "Finalising closest matches and distinct areas…";
                                }
                                dev.ryan.tfcatlas.core.SearchAreas.retainRange(
                                        matched,
                                        query.limits().minArea(),
                                        query.limits().maxArea(),
                                        live::changed);
                                searchProgress(serial, "Selecting the closest matching locations…");
                                List<SearchQuery.Result> closest = new ArrayList<>();
                                for (var point :
                                        NearestMatches.select(
                                                matched, centerX, centerZ, limit, spacing)) {
                                    if (closed || serial != searchSerial.get()) {
                                        return;
                                    }
                                    closest.add(resultAt(point, query));
                                }
                                List<SearchQuery.Result> finalResults = List.copyOf(closest);
                                searchProgress(serial, "Counting distinct matching areas…");
                                SearchOverlay overlay = live.publish(finalResults, true);
                                String advice =
                                        SearchWorkload.advice(
                                                finalResults.size(),
                                                overlay.count(),
                                                overlay.groups().stream()
                                                        .mapToLong(g -> g.edges().size())
                                                        .sum(),
                                                radius,
                                                spacing,
                                                automatic);
                                synchronized (this) {
                                    if (serial == searchSerial.get()) {
                                        searchOverlay = overlay;
                                        searchAdvice = advice;
                                        results = finalResults;
                                        closestOverlay = overlay;
                                        closestX = centerX;
                                        closestZ = centerZ;
                                        searchStatus =
                                                overlay.areaCount()
                                                        + " total distinct areas · "
                                                        + overlay.count()
                                                        + " matching cells";
                                    }
                                }
                            } catch (Exception e) {
                                synchronized (this) {
                                    if (serial == searchSerial.get()) {
                                        searchStatus = "Search failed: " + TileRetries.describe(e);
                                        searchAdvice =
                                                "Any visible highlights are partial. Try a smaller search radius.";
                                    }
                                }
                            } finally {
                                synchronized (this) {
                                    if (serial == searchSerial.get()) {
                                        searching = false;
                                        if (!searchOverlay.complete()
                                                && !searchStatus.startsWith("Search failed:")) {
                                            searchStatus =
                                                    "Search stopped before finalisation · partial highlights kept";
                                            searchAdvice =
                                                    "Run the search again to finish, or Clear to remove partial results.";
                                        }
                                    }
                                }
                            }
                        });
    }

    private long publishLive(int serial, SearchOverlay.Builder live) {
        long start = System.nanoTime();
        SearchOverlay partial = live.publish(List.of(), false);
        synchronized (this) {
            if (serial == searchSerial.get()) {
                searchOverlay = partial;
            }
        }
        // Snapshot work is bounded to a minority of search time, even for huge result sets.
        return System.nanoTime() + Math.max(750_000_000L, (System.nanoTime() - start) * 5);
    }

    private long refineDike(
            DikeSampler.Footprint footprint,
            SearchQuery query,
            int centerX,
            int centerZ,
            int radius,
            Map<Tile.Key, byte[]> matched,
            SearchOverlay.Builder live)
            throws Exception {
        // Search spacing controls discovery, but a hit must never paint a 128/512-block square as a
        // dike.
        return DikeCoverage.add(
                footprint.x(),
                footprint.z(),
                footprint.reach(),
                centerX,
                centerZ,
                radius,
                matched,
                (x, z) -> {
                    if (closed || Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException();
                    }
                    if (dikes.at(x, z, query) == null) {
                        return false;
                    }
                    Cell cell = fine.sample(x, z);
                    return query.matchesRegion(cell)
                            && matchesLocal(query, x, z, cell)
                            && (!query.heightRestricted()
                                    || query.matchesHeight(heights.sample(x, z)));
                },
                live::changed);
    }

    private synchronized void searchProgress(int serial, String text) {
        if (text != null && serial == searchSerial.get()) {
            searchStatus = text;
        }
    }

    public synchronized void cancelSearch() {
        searchSerial.incrementAndGet();
        if (closestTask != null) {
            closestTask.cancel(true);
            closestTask = null;
        }
        closestOverlay = null;
        if (searchTask != null) {
            searchTask.cancel(true);
        }
        searching = false;
        searchStatus =
                searchOverlay.count() > 0
                        ? "Search cancelled · partial highlights kept"
                        : "Search cancelled";
        searchAdvice = "Search again to finish, or Clear to remove partial results.";
    }

    public synchronized void clearSearch() {
        cancelSearch();
        results = List.of();
        searchOverlay = SearchOverlay.EMPTY;
        activeQuery = null;
        searchDetails = null;
        searchStatus = "Search cleared";
        searchAdvice = "";
    }

    private void trimDisk() {
        if (!disk || closed) {
            return;
        }
        try {
            synchronized (diskLock) {
                long max = (long) diskMB * 1024 * 1024;
                if (diskBytes <= max) {
                    return;
                }
                var oldest = new ArrayList<>(diskEntries.entrySet());
                oldest.sort(Comparator.comparingLong(e -> e.getValue().modified()));
                for (var file : oldest) {
                    if (diskBytes <= max) {
                        break;
                    }
                    Files.deleteIfExists(file.getKey());
                    diskFiles.remove(file.getKey().getFileName().toString());
                    diskBytes -= file.getValue().size();
                    diskEntries.remove(file.getKey());
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void export(int centerX, int centerZ, Profile source) {
        Profile p = Profiles.JSON.fromJson(Profiles.JSON.toJson(source), Profile.class);
        final boolean hideSeed = ClientSeed.hidden();
        searchWorker.execute(
                () -> {
                    try {
                        java.awt.image.BufferedImage image =
                                new java.awt.image.BufferedImage(
                                        p.exportPixels,
                                        p.exportPixels,
                                        p.exportTransparentOcean
                                                ? java.awt.image.BufferedImage.TYPE_INT_ARGB
                                                : java.awt.image.BufferedImage.TYPE_INT_RGB);
                        int span = p.exportPixels * p.exportStep;
                        int startX = centerX - span / 2, startZ = centerZ - span / 2;
                        for (int z = 0; z < p.exportPixels; z++) {
                            for (int x = 0; x < p.exportPixels; x++) {
                                if (closed) {
                                    return;
                                }
                                int
                                        bx =
                                                Math.max(
                                                        -29_999_984,
                                                        Math.min(
                                                                29_999_984,
                                                                startX + x * p.exportStep)),
                                        bz =
                                                Math.max(
                                                        -29_999_984,
                                                        Math.min(
                                                                29_999_984,
                                                                startZ + z * p.exportStep));
                                Tile tile = load(Tile.Key.at(bx, bz, p.exportStep / Tile.GRID));
                                Cell cell = tile.atBlock(bx, bz);
                                image.setRGB(
                                        x,
                                        z,
                                        p.exportTransparentOcean && !cell.land()
                                                ? 0
                                                : 0xFF000000 | p.mapColor(cell));
                            }
                        }
                        Path dir =
                                Minecraft.getInstance()
                                        .gameDirectory
                                        .toPath()
                                        .resolve("screenshots/tfcatlas");
                        Files.createDirectories(dir);
                        String name =
                                "tfc-"
                                        + SeedPrivacy.fileLabel(seed, hideSeed)
                                        + "-"
                                        + centerX
                                        + "-"
                                        + centerZ
                                        + "-"
                                        + p.layer
                                        + "-"
                                        + System.currentTimeMillis();
                        Path out = dir.resolve(name + ".png");
                        javax.imageio.ImageIO.write(image, "png", out.toFile());
                        if (p.exportMetadata) {
                            Files.writeString(
                                    dir.resolve(name + ".txt"),
                                    "TFC Atlas region preview\nSeed: "
                                            + SeedPrivacy.exportValue(seed, hideSeed)
                                            + "\nLayer: "
                                            + p.layerTitle()
                                            + "\nTop-left X/Z: "
                                            + startX
                                            + ", "
                                            + startZ
                                            + "\n"
                                            + p.exportStep
                                            + " blocks per pixel; width "
                                            + span
                                            + " blocks\nRainfall in mm; temperature is annual mean in degrees C. Biome altitude shows terrain classes, not Y.\nContinents on climate / rocks: "
                                            + (p.climateContinents
                                                    ? "On (separate ocean colour)"
                                                    : "Off")
                                            + "\n");
                        }
                        Minecraft.getInstance()
                                .execute(
                                        () ->
                                                AtlasClient.message(
                                                        "PNG saved in screenshots/tfcatlas/"
                                                                + out.getFileName()));
                    } catch (Exception ex) {
                        Minecraft.getInstance()
                                .execute(
                                        () ->
                                                AtlasClient.message(
                                                        "Export failed: " + ex.getMessage()));
                    }
                });
    }

    public void awaitStopped() throws InterruptedException {
        if (!worker.awaitTermination(30, TimeUnit.SECONDS)
                || !cacheReader.awaitTermination(30, TimeUnit.SECONDS)
                || !searchWorker.awaitTermination(30, TimeUnit.SECONDS)
                || !hoverWorker.awaitTermination(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Generator is still stopping; try clearing again shortly");
        }
    }

    public void close() {
        closed = true;
        precache.close();
        cancelSearch();
        hoverHeight.close();
        hoverCell.close();
        worker.shutdownNow();
        cacheReader.shutdownNow();
        searchWorker.shutdownNow();
        hoverWorker.shutdownNow();
        synchronized (tiles) {
            tiles.clear();
        }
        pending.clear();
    }
}
