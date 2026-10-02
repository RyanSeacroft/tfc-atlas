package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.SearchQuery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development-only integration harness. Not included in the release JAR. */
@Mod.EventBusSubscriber(modid = "tfcatlas", value = Dist.CLIENT)
public final class IntegrationSmoke {
    private static int phase = 0, ticks = 0;
    private static long start;
    private static Screen map;

    private static void log(String s) {
        System.out.println("ATLAS_SMOKE " + s);
    }

    private static void screenshot(String name) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        Path p = mc.gameDirectory.toPath().resolve("smoke/" + name + ".png");
        Files.createDirectories(p.getParent());
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(p);
        }
        log("SCREENSHOT " + p);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("tfcatlas.smoke")
                || event.phase != TickEvent.Phase.END
                || phase < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0
                    && mc.screen != null
                    && mc.screen
                            .getClass()
                            .getSimpleName()
                            .equals("AccessibilityOnboardingScreen")) {
                mc.setScreen(new TitleScreen());
                return;
            }
            if (phase == 0 && mc.screen instanceof TitleScreen) {
                log("START");
                start = System.currentTimeMillis();
                phase = 1;
                Class.forName("xaero.map.gui.GuiMap");
                log("GUI_MAP_MIXIN_TRANSFORMED");
                mc.options.renderDistance().set(2);
                mc.options.simulationDistance().set(2);
                if (Files.isDirectory(mc.gameDirectory.toPath().resolve("saves/atlas-smoke"))) {
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), "atlas-smoke");
                } else {
                    mc.createWorldOpenFlows()
                            .createFreshLevel(
                                    "atlas-smoke",
                                    new LevelSettings(
                                            "Atlas test",
                                            GameType.CREATIVE,
                                            false,
                                            Difficulty.PEACEFUL,
                                            false,
                                            new GameRules(),
                                            WorldDataConfiguration.DEFAULT),
                                    new WorldOptions(123456789L, false, false),
                                    registries ->
                                            registries
                                                    .registryOrThrow(Registries.WORLD_PRESET)
                                                    .get(new ResourceLocation("tfc", "overworld"))
                                                    .createWorldDimensions());
                }
            } else if (phase == 1 && mc.player != null && ++ticks > 120) {
                // Do not open Xaero until real persistent tiles have been saved during play.
                var precache = AtlasClient.engine;
                if (precache == null
                        || precache.closedMapWrites() < 8
                                && !precache.backgroundProgress().finished()) {
                    if (ticks > 1200) {
                        throw new IllegalStateException(
                                "No map-closed preloading after 60 seconds: "
                                        + AtlasClient.cacheStatus());
                    }
                    return;
                }
                long stored;
                try (var files = Files.list(precache.directory)) {
                    stored = files.filter(p -> p.toString().endsWith(".gz")).count();
                }
                if (stored < 8 || AtlasClient.map != null) {
                    throw new IllegalStateException(
                            "Preload did not persist before the first map open");
                }
                log(
                        (precache.closedMapWrites() >= 8
                                        ? "MAP_CLOSED_PRECACHE_PASS"
                                        : "MAP_CLOSED_PRECACHE_REUSE_PASS")
                                + " saved="
                                + precache.closedMapWrites()
                                + " files="
                                + stored);
                phase = 2;
                ticks = 0;
                Object session =
                        XaeroBridge.field(
                                Class.forName("xaero.map.core.XaeroWorldMapCore"),
                                "currentSession");
                Object processor = XaeroBridge.call(session, "getMapProcessor");
                Class<?> cls = Class.forName("xaero.map.gui.GuiMap");
                map =
                        (Screen)
                                cls.getConstructors()[0].newInstance(
                                        null, null, processor, mc.player);
                mc.setScreen(map);
                log("MAP_OPENED");
            } else if (phase == 2 && ++ticks > 100) {
                if (AtlasClient.engine == null) {
                    throw new IllegalStateException("No engine: " + AtlasClient.error);
                }
                AtlasClient.profile.mode = "Full map";
                AtlasClient.profile.opacity = .8;
                AtlasClient.profile.spawn = true;
                AtlasClient.profile.rocks = "granite";
                AtlasClient.profile.biomes = "";
                AtlasClient.engine.search(
                        new SearchQuery(Set.of(), Set.of(), -1, 0, 500, -50, 50, "Land", -64, 318),
                        (int) mc.player.getX(),
                        (int) mc.player.getZ(),
                        512,
                        30,
                        32,
                        16);
                phase = 3;
                ticks = 0;
            } else if (phase == 3 && !AtlasClient.engine.searching && ++ticks > 120) {
                if (AtlasClient.engine.results.isEmpty()) {
                    throw new IllegalStateException(
                            "Empty search " + AtlasClient.engine.searchStatus);
                }
                if (System.currentTimeMillis()
                                - (long) XaeroBridge.field(AtlasClient.class, "lastHook")
                        > 5000) {
                    throw new IllegalStateException("Overlay hook did not execute");
                }
                if (!AtlasClient.error.isEmpty()) {
                    throw new IllegalStateException(AtlasClient.error);
                }
                var engine = AtlasClient.engine;
                var serverGenerator =
                        (net.dries007.tfc.world.TFCChunkGenerator)
                                mc.getSingleplayerServer()
                                        .getLevel(Level.OVERWORLD)
                                        .getChunkSource()
                                        .getGenerator();
                var stockSource =
                        (net.dries007.tfc.world.biome.BiomeSourceExtension)
                                serverGenerator.getBiomeSource();
                var random = new XoroshiroRandomSource(engine.seed);
                var heightRegions =
                        new net.dries007.tfc.world.region.RegionGenerator(engine.settings, random);
                var localChunks =
                        net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator.create(
                                random.nextLong(),
                                engine.settings.rockLayerSettings(),
                                heightRegions);
                var heightSampler =
                        new TerrainHeightSampler(
                                engine.seed,
                                random.nextLong(),
                                heightRegions,
                                mc.level.registryAccess().lookupOrThrow(Registries.BIOME));
                var localFine = new FineSampler(heightRegions, heightSampler.source, localChunks);
                int verified = 0, heightChecks = 0;
                for (var r : engine.results) {
                    var pos = new ChunkPos(Math.floorDiv(r.x(), 16), Math.floorDiv(r.z(), 16));
                    var stockData =
                            serverGenerator.chunkDataProvider().createAndGeneratePartial(pos);
                    String stockBiome =
                            stockSource
                                    .getBiomeExtension(
                                            net.minecraft.core.QuartPos.fromBlock(r.x()),
                                            net.minecraft.core.QuartPos.fromBlock(r.z()))
                                    .key()
                                    .location()
                                    .toString();
                    String stockRock =
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(
                                            stockData
                                                    .getRockData()
                                                    .getSurfaceRock(r.x(), r.z())
                                                    .raw())
                                    .toString();
                    Cell c = r.cell();
                    if (c.rain() != stockData.getRainfall(r.x(), r.z())
                            || c.temperature() != stockData.getAverageTemp(r.x(), r.z())
                            || !c.biome().equals(stockBiome)
                            || !c.rock().equals(stockRock)) {
                        throw new AssertionError("Fine generation mismatch");
                    }
                    verified++;
                    int expected =
                            (int)
                                    serverGenerator
                                            .createHeightFillerForChunk(pos)
                                            .sampleHeight(r.x(), r.z());
                    if (heightSampler.sample(r.x(), r.z()) != expected
                            || r.surfaceY() == null
                            || r.surfaceY() != expected) {
                        throw new AssertionError("Surface Y mismatch at " + r.x() + ", " + r.z());
                    }
                    for (int y : new int[] {-50, 0, 50}) {
                        if (y < expected) {
                            String rock =
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                            .getKey(
                                                    serverGenerator
                                                            .chunkDataProvider()
                                                            .generator()
                                                            .generateRock(
                                                                    r.x(), y, r.z(), expected, null)
                                                            .raw())
                                            .toString();
                            if (!rock.equals(localFine.rock(r.x(), y, r.z(), expected))) {
                                throw new AssertionError("Underground rock mismatch");
                            }
                        }
                    }
                    heightChecks++;
                }
                log("TFC_SURFACE_Y_COMPARISON_PASS " + heightChecks);
                log("TFC_SAMPLE_COMPARISON_PASS " + verified);
                log("SEARCH " + engine.searchStatus);
                screenshot("01-map-rocks");
                AtlasClient.profile.layer = "RAINFALL";
                phase = 4;
                ticks = 0;
            } else if (phase == 4 && ++ticks > 60) {
                screenshot("02-map-rainfall");
                AtlasClient.profile.mode = "Unexplored only";
                phase = 5;
                ticks = 0;
            } else if (phase == 5 && ++ticks > 100) {
                screenshot("03-map-unexplored");
                mc.setScreen(new AtlasScreen(map, 1));
                phase = 6;
                ticks = 0;
            } else if (phase == 6 && ++ticks > 20) {
                screenshot("04-search-ui");
                mc.setScreen(new AtlasScreen(map, 2));
                phase = 7;
                ticks = 0;
            } else if (phase == 7 && ++ticks > 20) {
                screenshot("05-results-ui");
                mc.setScreen(new AtlasScreen(map, 5));
                phase = 70;
                ticks = 0;
            } else if (phase == 70 && ++ticks > 20) {
                screenshot("08-cache-status");
                mc.setScreen(new LegendScreen(map));
                phase = 8;
                ticks = 0;
            } else if (phase == 8 && ++ticks > 20) {
                screenshot("06-legend-ui");
                var result = AtlasClient.engine.results.get(0);
                XaeroBridge.waypoint(map, result.x(), result.z(), "Granite test candidate");
                phase = 9;
                ticks = 0;
            } else if (phase == 9 && ++ticks > 20) {
                if (!mc.screen.getClass().getName().contains("GuiAddWaypoint")) {
                    throw new IllegalStateException(
                            "Waypoint editor not opened: " + AtlasClient.error);
                }
                screenshot("07-waypoint-ui");
                log("PASS in " + (System.currentTimeMillis() - start) + " ms");
                Files.writeString(
                        mc.gameDirectory.toPath().resolve("smoke/PASS.txt"),
                        "Minecraft client integration passed\n"
                                + AtlasClient.engine.searchStatus
                                + "\n");
                phase = -1;
                mc.stop();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            log("FAIL " + e);
            try {
                Files.writeString(
                        mc.gameDirectory.toPath().resolve("smoke/FAIL.txt"), e.toString());
            } catch (Exception ignored) {
            }
            phase = -1;
            mc.stop();
        }
    }
}
