package dev.ryan.tfcatlas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.DikeCoverage;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.RockPossibilities;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchQuery;
import dev.ryan.tfcatlas.core.TerrainCoverage;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TooltipLayout;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dries007.tfc.world.biome.BiomeBuilder;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.RegionBiomeSource;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.feature.vein.PipeVeinConfig;
import net.dries007.tfc.world.feature.vein.PipeVeinFeature;
import net.dries007.tfc.world.feature.vein.VeinConfig;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.settings.RockLayerSettings;
import net.dries007.tfc.world.settings.RockSettings;
import net.dries007.tfc.world.settings.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/** Stock TFC data/API comparisons and regression checks for 0.1.19. No game window required. */
public final class TerrainAndTooltipTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        tooltips();
        caves();
        dikes();
        geology();
        System.out.println(
                "PASS: "
                        + checks
                        + " tooltip bounds, cave masking, dike frequency/footprints and detailed-geology checks");
    }

    private static JsonObject resource(String path) throws Exception {
        try (var in = Settings.class.getResourceAsStream("/data/tfc/" + path)) {
            if (in == null) {
                throw new AssertionError(path);
            }
            return JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
        }
    }

    private static void tooltips() {
        for (int width : new int[] {320, 426, 854}) {
            for (int height : new int[] {240, 360, 480}) {
                for (int mx : new int[] {0, width / 2, width - 1}) {
                    for (int my : new int[] {0, 4, height / 2, height - 1}) {
                        for (int textH : new int[] {8, 80, 220, 600}) {
                            var fit = TooltipLayout.fit(width, height, mx, my, 170, textH);
                            check(
                                    fit.scale() <= .75f
                                            && (fit.x() - 4) * fit.scale() >= 3
                                            && (fit.y() - 4) * fit.scale() >= 3,
                                    "Small tooltips stay inside top and left edges");
                            check(
                                    (fit.x() + 174) * fit.scale() <= width - 2
                                            && (fit.y() + textH + 4) * fit.scale() <= height - 2,
                                    "Long tooltips fit at every screen edge and GUI scale");
                            check(
                                    fit.equals(
                                            TooltipLayout.fit(width, height, mx, my, 170, textH)),
                                    "Recreated toggle widgets cannot change tooltip placement");
                        }
                    }
                }
            }
        }
    }

    public static final class CaveProcessor {
        final CoverageTest.Region surface = new CoverageTest.Region(),
                cave = new CoverageTest.Region();

        public int currentLayer = Integer.MAX_VALUE;

        public static final class SaveLoad {
            public int mainTextureLevel = 0;
        }

        public SaveLoad getMapSaveLoad() {
            return new SaveLoad();
        }

        public int getCurrentCaveLayer() {
            return currentLayer;
        }

        public CoverageTest.Region getLeveledRegion(int layer, int x, int z, int level) {
            return x == 0 && z == 0
                    ? (layer == Integer.MAX_VALUE ? surface : layer == -16 ? cave : null)
                    : null;
        }
    }

    private static void caves() throws Exception {
        var p = new CaveProcessor();
        var top = new CoverageTest.Texture();
        var cave = new CoverageTest.Texture();
        top.mark(0, 0, 16, 16, 70);
        cave.mark(32, 16, 48, 32, -16);
        p.surface.textures[0][0] = top;
        p.cave.textures[0][0] = cave;
        var mask = new ExploredMask();
        var surface = mask.collect(p, 0, 0, 0, 63, 63);
        var underground = mask.collect(p, -16, 0, 0, 0, 63, 63);
        check(
                surface.equals(List.of(new TerrainCoverage.Rect(0, 0, 16, 16)))
                        && underground.equals(List.of(new TerrainCoverage.Rect(32, 16, 48, 32))),
                "Cave coverage comes from the selected cave layer, independently of saved surface exploration");
        var view = new XaeroBridge.View(32, 32, 1, "test", true, true, p, 32, 32);
        check(
                mask.coverage(view, 64, 64).equals(surface),
                "Surface imagery uses surface discoveries");
        long revision = mask.coverageRevision();
        p.currentLayer = -16;
        check(
                mask.coverage(view, 64, 64).equals(underground),
                "Changing native cave layer changes the rendering mask, without surface leakage");
        check(mask.coverageRevision() > revision, "Native view changes invalidate label coverage");
    }

    private static void dikes() throws Exception {
        var feature = new PipeVeinFeature(PipeVeinConfig.CODEC);
        var world = DikeSampler.seedView(123456789L);
        int count = 0;
        for (String rock : List.of("granite", "diorite", "gabbro")) {
            var json =
                    resource("worldgen/configured_feature/vein/" + rock + "_dike.json")
                            .getAsJsonObject("config");
            var hash =
                    net.minecraft.world.level.levelgen.RandomSupport.seedFromHashOf(
                            json.get("random_name").getAsString());
            var config =
                    new PipeVeinConfig(
                            new VeinConfig(
                                    Map.of(),
                                    Optional.empty(),
                                    json.get("rarity").getAsInt(),
                                    json.get("density").getAsFloat(),
                                    json.get("min_y").getAsInt(),
                                    json.get("max_y").getAsInt(),
                                    false,
                                    false,
                                    hash.seedLo() ^ hash.seedHi(),
                                    Optional.empty(),
                                    false),
                            json.get("height").getAsInt(),
                            json.get("radius").getAsInt(),
                            json.get("min_skew").getAsInt(),
                            json.get("max_skew").getAsInt(),
                            json.get("min_slant").getAsInt(),
                            json.get("max_slant").getAsInt(),
                            json.get("sign").getAsFloat());
            check(
                    config.config().rarity() == 300
                            && config.radius() == 18
                            && config.maxSkew() == 20,
                    "Installed TFC default dikes have rarity 300, radius 18 and up to 20 blocks of skew");
            check(
                    resource("worldgen/placed_feature/vein/" + rock + "_dike.json")
                            .getAsJsonArray("placement")
                            .isEmpty(),
                    "No extra placement filter changes default dike frequency");
            List veins = new ArrayList();
            for (int x = -160; x < 160; x++) {
                for (int z = -160; z < 160; z++) {
                    feature.getVeinsAtChunk(
                            world,
                            null,
                            x,
                            z,
                            veins,
                            config,
                            pos -> {
                                throw new AssertionError("Unexpected biome tag restriction");
                            });
                }
            }
            count += veins.size();
        }
        check(
                count > 850 && count < 1200,
                "Seeded TFC placement is close to one dike per 100 chunks across all three types");
        System.out.println(
                "Dike audit: "
                        + count
                        + " seeded starts across 102,400 chunks for the three installed default types");
        Map<Tile.Key, byte[]> masks = new HashMap<>();
        long n =
                DikeCoverage.add(
                        -12, 20, 38, 0, 0, 100, masks, (x, z) -> Math.hypot(x + 12, z - 20) < 18);
        check(
                n > 0 && n * 64 < 2000,
                "A detected narrow dike occupies its fine footprint, not a 512-block discovery square");
        check(
                DikeCoverage.add(
                                -12,
                                20,
                                38,
                                0,
                                0,
                                100,
                                masks,
                                (x, z) -> Math.hypot(x + 12, z - 20) < 18)
                        == 0,
                "Repeated dike discovery cannot inflate area counts");
        var overlay = new SearchOverlay(masks, List.of(), true);
        check(
                overlay.resolution() == 8 && overlay.areaCount() == 1,
                "Dike outlines stay at eight blocks with one connected pipe area");
        for (var e : masks.entrySet()) {
            for (int i = 0; i < 1024; i++) {
                if (e.getValue()[i] != 0) {
                    int x = e.getKey().blockX() + i % 32 * 8 + 4,
                            z = e.getKey().blockZ() + i / 32 * 8 + 4;
                    check(
                            Math.hypot(x + 12, z - 20) < 18,
                            "Dike coverage respects actual geometry at negative coordinates");
                }
            }
        }
    }

    private static void geology() throws Exception {
        var graph =
                resource("worldgen/world_preset/overworld.json")
                        .getAsJsonObject("dimensions")
                        .getAsJsonObject("minecraft:overworld")
                        .getAsJsonObject("generator")
                        .getAsJsonObject("tfc_settings")
                        .getAsJsonObject("rock_layer_settings");
        var allowed =
                RockPossibilities.read(
                        graph,
                        e ->
                                e.isJsonPrimitive()
                                        ? e.getAsString()
                                        : e.getAsJsonObject().get("raw").getAsString());
        check(
                allowed.get(RockLayer.BOTTOM).stream()
                        .noneMatch(id -> id.endsWith("shale") || id.endsWith("basalt")),
                "Stock bottom graph excludes shale and basalt");
        // Replace only block definitions with bootstrapped vanilla blocks. TFC's geological graph
        // and sampler stay unchanged.
        var definitions = graph.getAsJsonObject("rocks");
        var blocks =
                BuiltInRegistries.BLOCK.keySet().stream()
                        .filter(id -> id.getNamespace().equals("minecraft"))
                        .sorted(Comparator.comparing(Object::toString))
                        .limit(definitions.size())
                        .toList();
        Map<String, String> rockNames = new HashMap<>();
        int index = 0;
        for (var entry : new ArrayList<>(definitions.entrySet())) {
            String id = blocks.get(index++).toString();
            var data = new JsonObject();
            for (String field :
                    List.of("raw", "hardened", "gravel", "cobble", "sand", "sandstone")) {
                data.addProperty(field, id);
            }
            definitions.add(entry.getKey(), data);
            rockNames.put(id, entry.getKey());
        }
        var settings =
                RockLayerSettings.CODEC
                        .parse(JsonOps.INSTANCE, graph)
                        .getOrThrow(
                                false,
                                s -> {
                                    throw new AssertionError(s);
                                });
        int upliftBottom = 0;
        for (int seed = 0; seed < 512; seed++) {
            int point = seed << 2 | 3;
            String top = name(settings.sampleAtLayer(point, 0), rockNames),
                    mid = name(settings.sampleAtLayer(point, 1), rockNames),
                    bottom = name(settings.sampleAtLayer(point, 2), rockNames);
            var cell = new Cell(top, "tfc:mountains", 3, 200, 15, 12, 8, 8, 9, mid, bottom);
            check(
                    query(bottom, "Bottom", Set.of(3)).matchesRegion(cell),
                    "Uplift and bottom rock can legitimately match the same TFC geological column");
            upliftBottom++;
            check(
                    !query("shale", "Bottom", Set.of(3)).matchesRegion(cell),
                    "A shale top/middle cannot satisfy a bottom-rock filter");
            check(
                    !query(bottom, "Bottom", Set.of(0)).matchesRegion(cell),
                    "Rock region and layer filters are combined with AND");
            check(
                    query(top + ", " + bottom, "Any layer", Set.of(2, 3)).matchesRegion(cell),
                    "Comma-separated choices use OR within a filter");
        }
        check(upliftBottom == 512, "Bottom strata exist beneath uplift regions");
        var gen =
                new RegionGenerator(
                        new Settings(false, 4000, 0, 0, 20000, 0, 20000, 0, settings, .5f, .5f),
                        new XoroshiroRandomSource(19));
        var chunks = RegionChunkDataGenerator.create(991L, settings, gen);
        int varied = 0;
        for (int z = -800; z < 800; z += 41) {
            for (int x = -800; x < 800; x += 37) {
                List<String> tooltip = new ArrayList<>();
                chunks.displayDebugInfo(tooltip, new BlockPos(x, 0, z), 0);
                int actual =
                        Integer.parseInt(
                                tooltip.get(1).substring(tooltip.get(1).lastIndexOf("Type: ") + 6));
                check(
                        RockStrata.surfaceRegion(chunks, x, z) == actual,
                        "Fine region type matches TFC's own debug output including lateral skew");
                if (actual
                        != (gen.getOrCreateRegionPoint(Math.floorDiv(x, 128), Math.floorDiv(z, 128))
                                        .rock
                                & 3)) {
                    varied++;
                }
            }
        }
        check(varied > 0, "Detailed geology fixes real disagreements with the old 128-block grid");
        var mountainBiome =
                BiomeBuilder.builder()
                        .surface(seed -> (context, startY, endY) -> {})
                        .build(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.BIOME,
                                        new net.minecraft.resources.ResourceLocation(
                                                "tfc", "mountains")));
        var plainsBiome =
                BiomeBuilder.builder()
                        .surface(seed -> (context, startY, endY) -> {})
                        .build(
                                net.minecraft.resources.ResourceKey.create(
                                        net.minecraft.core.registries.Registries.BIOME,
                                        new net.minecraft.resources.ResourceLocation(
                                                "tfc", "plains")));
        var source =
                new RegionBiomeSource(null) {
                    @Override
                    public BiomeExtension getBiomeExtensionNoRiver(int x, int z) {
                        return x >= 0 && z >= 0 ? mountainBiome : plainsBiome;
                    }
                };
        net.dries007.tfc.world.Sampler<BiomeExtension> biomeSource =
                (x, z) -> source.getBiomeExtensionNoRiver(Math.floorDiv(x, 4), Math.floorDiv(z, 4));
        var shape = new TerrainShapeSampler(source);
        long start = System.nanoTime();
        int changed = 0;
        for (int z = -64; z < 64; z += 8) {
            for (int x = -64; x < 64; x += 8) {
                var stock =
                        net.dries007.tfc.world.ChunkBiomeSampler.sampleBiomes(
                                new net.minecraft.world.level.ChunkPos(
                                        Math.floorDiv(x, 16), Math.floorDiv(z, 16)),
                                biomeSource,
                                BiomeExtension::biomeBlendType);
                var expected =
                        new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<
                                BiomeExtension>();
                net.dries007.tfc.world.ChunkBiomeSampler.sampleBiomesColumn(
                        expected, stock, x & 15, z & 15);
                double total = expected.values().doubleStream().sum(),
                        mountain = expected.getDouble(mountainBiome);
                boolean actual = shape.mountain(x, z);
                check(
                        actual == (mountain >= total * .5),
                        "Close mountain shape agrees with stock TFC's complete terrain-blending API");
                if (actual != (x >= 0 && z >= 0)) {
                    changed++;
                }
            }
        }
        check(
                changed > 0,
                "TFC blending rounds a sharp biome corner instead of reproducing the blocky biome outline");
        System.out.println(
                "Close terrain blend audit: 256 samples compared with stock full-chunk weights in "
                        + (System.nanoTime() - start) / 1_000_000
                        + " ms");
    }

    private static String name(RockSettings rock, Map<String, String> names) {
        return "tfc:rock/raw/" + names.get(BuiltInRegistries.BLOCK.getKey(rock.raw()).toString());
    }

    private static SearchQuery query(String rocks, String layer, Set<Integer> types) {
        return new SearchQuery(
                SearchQuery.names(rocks),
                Set.of(),
                types,
                0,
                500,
                -20,
                30,
                Set.of(),
                -64,
                319,
                layer);
    }
}
