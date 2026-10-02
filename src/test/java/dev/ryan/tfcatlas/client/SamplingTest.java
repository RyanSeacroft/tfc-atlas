package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchDetails;
import dev.ryan.tfcatlas.core.SearchLayout;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileWindow;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.dries007.tfc.world.ChunkBiomeSampler;
import net.dries007.tfc.world.Sampler;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.minecraft.world.level.ChunkPos;

public final class SamplingTest {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) {
            throw new AssertionError(message);
        }
    }

    private record TestBiome(int id, BiomeBlendType blend) {}

    public static void run() {
        for (int i = 0; i < 6; i++) {
            int resolution = 16 << i, radius = 8192 << i;
            check(
                    Sampling.searchResolution("Auto", radius) == resolution,
                    "Auto resolution at radius boundary");
            check(
                    Sampling.searchResolution("" + resolution, radius) == resolution,
                    "Explicit precision preserved");
            if (i < 5) {
                check(
                        Sampling.searchResolution("Auto", radius + 1) == resolution * 2,
                        "Auto coarsens only above the supported radius");
            }
            check(
                    Sampling.searchResolution("" + resolution, radius + 1) == resolution
                            && Sampling.expensiveSearch("" + resolution, radius + 1),
                    "Manual detail is allowed after a performance confirmation");
        }
        check(
                Sampling.mapStep(.5, 854, 480) == 1
                        && Sampling.mapStep(.25, 854, 480) == 2
                        && Sampling.mapStep(.249, 854, 480) == 4,
                "Map uses only 8, 16, 32 blocks at the zoom boundaries");
        for (double zoom : new double[] {.000001, .0001, .001, .01, .1, .25, .5, 1, 4}) {
            for (int width : new int[] {320, 854, 1920, 7680}) {
                int resolution = Tile.GRID * Sampling.mapStep(zoom, width, 480);
                check(
                        resolution >= 8
                                && resolution <= Tile.GRID * Sampling.MAX_MAP_STEP
                                && Integer.bitCount(resolution) == 1,
                        "Power-of-two detail levels remain bounded");
                if (zoom >= 1. / 64) {
                    check(resolution <= 32, "Ordinary settled map zoom stays at 32 or finer");
                }
            }
        }
        checkWindows();
        checkEmptySearch();
        for (int resolution : new int[] {16, 32, 64, 128, 256, 512}) {
            int step = resolution / Tile.GRID;
            BitSet bits = new BitSet();
            bits.set(0);
            bits.set(1);
            var overlay = new SearchOverlay(Map.of(new Tile.Key(-1, -1, step), bits), List.of());
            check(
                    overlay.resolution() == resolution
                            && overlay.bounds().blockX() == -32 * resolution,
                    "Negative search geometry uses submitted resolution");
            check(
                    overlay.edges().size() == 4
                            && overlay.edges().stream()
                                            .mapToInt(SearchOverlay.Edge::x1)
                                            .max()
                                            .orElseThrow()
                                    == -30 * resolution,
                    "Adjacent detailed cells form a stable outlined region");
            check(
                    overlay.edgesIn(-100000, -100000, 100000, 100000).size() == 4
                            && overlay.edgesIn(5000, 5000, 6000, 6000).isEmpty(),
                    "Outline buckets retain edges and skip distant regions");
            for (var e : overlay.edges()) {
                check(
                        overlay.edgesIn(e.x0() - 1, e.z0() - 1, e.x0() + 1, e.z0() + 1).contains(e),
                        "Viewport lookup preserves negative and shared-boundary edges");
            }
            var q = Tile.Key.at(-1, -1, step);
            check(
                    q.blockX() == -32 * resolution && q.span() == 32 * resolution,
                    "Negative detail tiles line up at every scale");
        }
        compareHeightWeights();
        System.out.println(
                "PASS: "
                        + checks
                        + " adaptive-detail, underground-rock and stock-TFC height-weight checks");
    }

    private static void checkWindows() {
        for (int[] bounds :
                new int[][] {
                    {-3, -4, 7, 8},
                    {-1, -1, 0, 0},
                    {0, 0, 0, 0},
                    {-100, -1, 100, 1},
                    {2, -50, 3, 50}
                }) {
            for (int offset : new int[] {-100, 0, 100}) {
                var w =
                        new TileWindow(
                                bounds[0], bounds[1], bounds[2], bounds[3], offset, offset, 4);
                Set<Tile.Key> seen = new HashSet<>();
                double last = -1;
                int cx = Math.max(w.minX(), Math.min(w.maxX(), offset)),
                        cz = Math.max(w.minZ(), Math.min(w.maxZ(), offset));
                for (var k : w) {
                    double ring = w.distanceSquared(k);
                    check(
                            w.contains(k) && seen.add(k) && ring >= last,
                            "Centre-first traversal covers each visible tile once, including clipped/negative bounds");
                    last = ring;
                }
                check(seen.size() == w.count(), "Traversal covers the whole rectangle");
            }
        }
        var large = new TileWindow(-30000, -30000, 30000, 30000, 0, 0, 4);
        var cursor = large.iterator();
        for (int i = 0; i < 256; i++) {
            var k = cursor.next();
            check(
                    Math.abs(k.x()) <= 10 && Math.abs(k.z()) <= 10,
                    "Huge zoom-out starts near the centre without creating/sorting billions of keys");
        }
        check(
                cursor.hasNext() && large.count() > Integer.MAX_VALUE,
                "Wide-view counts do not overflow");
    }

    private static void checkEmptySearch() {
        Profile p = new Profile();
        check(
                p.savedSearches.isEmpty() && p.rocks.isBlank() && p.biomes.isBlank(),
                "Release starts without examples or prefilled name filters");
        p.minY = 200;
        p.maxY = 319;
        for (String rock : List.of("granite", "basalt", "limestone")) {
            for (String biome : List.of("plains", "mountains")) {
                Cell cold = new Cell(rock, biome, 3, 700, -45, 2, 2, 2, 1);
                check(
                        p.query().matches(cold, 200)
                                && p.query().matches(cold, 319)
                                && !p.query().matches(cold, 199),
                        "Height-only search accepts any rock/biome/climate and uses inclusive Y bounds");
            }
        }
        p.rocks = "granite";
        p.biomes = "plains";
        p.categories = "Uplift";
        p.feature = "Mountain";
        p.searchRockLayer = "Bottom";
        p.precision = "16";
        p.radius = 1024;
        p.minTemp = 20;
        p.maxTemp = 30;
        p.minRain = 200;
        p.maxRain = 300;
        p.searchOrigin = "World spawn";
        p.resultSpacing = 100;
        p.resultLimit = 5;
        p.seed = "998877";
        p.keyX = 80;
        p.savedSearches.put("Keep me", "{}");
        p.clearSearchSettings();
        check(
                p.query().equals(new Profile().query())
                        && p.radius == 16384
                        && p.resultSpacing == 512
                        && p.resultLimit == 5
                        && p.precision.equals("Auto")
                        && p.searchOrigin.equals("Player"),
                "Clear resets every search constraint, sampling, origin and result control");
        check(
                p.seed.equals("998877") && p.keyX == 80 && p.savedSearches.containsKey("Keep me"),
                "Clear preserves world, HUD and user presets");
        for (String name : List.of("minRain", "maxRain", "minTemp", "maxTemp", "minY", "maxY")) {
            check(p.searchValue(name).isBlank(), "Unrestricted limits are presented as blank");
        }
        p.minRain = 20;
        check(
                !p.query().matchesRegion(new Cell("basalt", "plains", 1, 19, 0, 0, 0, 0, 1))
                        && p.query()
                                .matchesRegion(new Cell("basalt", "plains", 1, 20, 0, 0, 0, 0, 1)),
                "Rain-only search works with all named filters blank");
        p.searchOrigin = "World spawn";
        var loaded = Profiles.decode(Profiles.JSON.toJsonTree(p).getAsJsonObject());
        check(
                loaded.searchOrigin.equals("World spawn") && loaded.query().equals(p.query()),
                "Origin and optional limits survive JSON roundtrip");
        var old =
                Profiles.decode(
                        com.google.gson.JsonParser.parseString(
                                        "{\"minRain\":0,\"maxRain\":500,\"minTemp\":-30,\"maxTemp\":40}")
                                .getAsJsonObject());
        check(
                old.query().equals(new Profile().query()),
                "Legacy default climate pairs migrate to unrestricted filters");
        var explicit =
                Profiles.decode(
                        com.google.gson.JsonParser.parseString(
                                        "{\"minRain\":20,\"maxRain\":500,\"minTemp\":10,\"maxTemp\":40}")
                                .getAsJsonObject());
        check(
                explicit.minRain == 20
                        && explicit.maxRain == 500
                        && explicit.minTemp == 10
                        && explicit.maxTemp == 40,
                "Existing customized climate ranges are retained");
        check(
                new SearchDetails(new Profile().query(), 0, 0, 1024, 50, 512, 16)
                        .lines().stream().anyMatch(l -> l.equals("Rain: Any mm · Temp: Any °C")),
                "Unrestricted criteria read Any without sentinel numbers");
        for (int width : new int[] {320, 360, 427, 854, 1920}) {
            for (int height : new int[] {240, 300, 480}) {
                var buttons = SearchLayout.actions(width, height);
                for (int i = 0; i < buttons.size(); i++) {
                    var b = buttons.get(i);
                    check(
                            b.x() >= 0
                                    && b.x() + b.width() <= width
                                    && b.y() + b.height() < height - 39
                                    && b.width() >= 44,
                            "Search actions fit minimum GUI");
                    if (i > 0) {
                        check(
                                buttons.get(i - 1).x() + buttons.get(i - 1).width() < b.x(),
                                "Search actions have separate click targets");
                    }
                }
                check(
                        buttons.get(0).width() >= 90 && buttons.get(1).width() >= 70,
                        "World spawn toggle and primary Search have room for full native text");
            }
        }
    }

    private static void compareHeightWeights() {
        List<TestBiome> biomes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            biomes.add(new TestBiome(i, BiomeBlendType.values()[i % 3]));
        }
        AtomicInteger reads = new AtomicInteger();
        Sampler<TestBiome> source =
                (x, z) -> {
                    reads.incrementAndGet();
                    return biomes.get(
                            Math.floorMod(Math.floorDiv(x, 29) * 7 + Math.floorDiv(z, 43) * 13, 9));
                };
        var optimized = new HeightWeights<>(source, TestBiome::blend);
        Random random = new Random(19847);
        for (int i = 0; i < 250; i++) {
            int x = i < 32 ? i - 16 : random.nextInt(200000) - 100000,
                    z = i < 32 ? 15 - i : random.nextInt(200000) - 100000;
            var vanilla =
                    ChunkBiomeSampler.sampleBiomes(
                            new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)),
                            source,
                            TestBiome::blend);
            var expected = new Object2DoubleOpenHashMap<TestBiome>();
            var actual = new Object2DoubleOpenHashMap<TestBiome>();
            ChunkBiomeSampler.sampleBiomesColumn(expected, vanilla, x & 15, z & 15);
            ChunkBiomeSampler.sampleBiomesColumn(actual, optimized.at(x, z), x & 15, z & 15);
            for (var biome : biomes) {
                check(
                        Math.abs(expected.getDouble(biome) - actual.getDouble(biome)) < 1e-12,
                        "Optimized height weights agree with TFC at " + x + ", " + z);
            }
        }
        optimized = new HeightWeights<>(source, TestBiome::blend);
        reads.set(0);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                optimized.at(x * 16 + 8, z * 16 + 8);
            }
        }
        int fast = reads.get();
        int full = 256 * (16 + 49) * 81;
        check(fast < full / 5, "Shared kernels materially reduce fine height-sampling work");
        System.out.println(
                "Height-weight sampling: "
                        + fast
                        + " biome reads versus "
                        + full
                        + " for full chunk weights (256 columns)");
    }
}
