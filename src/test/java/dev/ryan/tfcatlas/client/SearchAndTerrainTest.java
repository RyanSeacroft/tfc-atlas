package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.BackgroundPrecache;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.SearchWindow;
import dev.ryan.tfcatlas.core.TerrainDetail;
import dev.ryan.tfcatlas.core.Tile;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SearchAndTerrainTest {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        areas();
        terrain();
        manual();
        focus();
        if (Boolean.getBoolean("tfcatlas.gameTests")) {
            dikeApi();
            overview();
        }
        parallelCache();
        System.out.println(
                "PASS: "
                        + checks
                        + " distinct-area, terrain, overview, manual-search, dike-API and Enter-focus checks");
    }

    private static void dikeApi() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        check(
                DikeSampler.seedView(-991234567L).getSeed() == -991234567L,
                "TFC's seed view returns the exact signed seed through the actual Minecraft interface");
        Class<?> type = Class.forName("net.dries007.tfc.world.feature.vein.PipeVeinFeature$Vein");
        var method =
                net.dries007.tfc.world.feature.vein.PipeVeinFeature.class.getDeclaredMethod(
                        "getChanceToGenerate",
                        int.class,
                        int.class,
                        int.class,
                        type,
                        net.dries007.tfc.world.feature.vein.PipeVeinConfig.class);
        method.setAccessible(true);
        check(
                method.getReturnType() == float.class,
                "Installed TFC exposes the verified pipe geometry signature");
        check(
                net.dries007.tfc.world.feature.vein.IVein.class.isAssignableFrom(type),
                "Predicted pipe centers use the real TFC vein interface");
        var config =
                new net.dries007.tfc.world.feature.vein.PipeVeinConfig(
                        new net.dries007.tfc.world.feature.vein.VeinConfig(
                                Map.of(),
                                Optional.empty(),
                                1,
                                .98f,
                                -64,
                                180,
                                false,
                                false,
                                912L,
                                false),
                        150,
                        18,
                        7,
                        20,
                        2,
                        5,
                        0f);
        var feature =
                new net.dries007.tfc.world.feature.vein.PipeVeinFeature(
                        net.dries007.tfc.world.feature.vein.PipeVeinConfig.CODEC);
        var world = DikeSampler.seedView(918273L);
        var chunk = new net.minecraft.world.level.ChunkPos(-13, 21);
        java.util.function.Function<
                        net.minecraft.core.BlockPos,
                        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>
                biome =
                        pos -> {
                            throw new AssertionError(
                                    "Unrestricted standard dikes do not need world biome access");
                        };
        List<?> veins = feature.getNearbyVeins(world, null, chunk, 2, config);
        check(
                veins.size() == 25
                        && veins.equals(feature.getNearbyVeins(world, null, chunk, 2, config)),
                "Real TFC vein placement is deterministic with only the exact seed view");
        check(
                !veins.equals(
                        feature.getNearbyVeins(
                                DikeSampler.seedView(918274L), null, chunk, 2, config)),
                "Changing world seed changes predicted dike locations");
        Object vein = veins.get(0);
        check(
                (float) method.invoke(feature, 0, 0, 0, vein, config) == .98f,
                "Actual pipe geometry accepts its center with configured density");
        check(
                (float) method.invoke(feature, 1000, 0, 1000, vein, config) == 0f
                        && (float) method.invoke(feature, 0, 150, 0, vein, config) == 0f,
                "Actual pipe geometry rejects distant and out-of-height points");
    }

    private static void overview() {
        var settings = TestWorldgen.defaults();
        var generator =
                new net.dries007.tfc.world.region.RegionGenerator(
                        settings, net.dries007.tfc.world.Seed.of(18));
        var sampler = new OverviewSampler(generator);
        for (int z = -180; z <= 180; z += 12) {
            for (int x = -180; x <= 180; x += 12) {
                var direct = generator.getOrCreateRegionPoint(x, z);
                var fast = sampler.point(x, z);
                check(
                        direct.rock == fast.rock
                                && direct.biome == fast.biome
                                && direct.biomeAltitude == fast.biomeAltitude
                                && direct.rainfall == fast.rainfall
                                && direct.temperature == fast.temperature
                                && direct.distanceToOcean == fast.distanceToOcean
                                && direct.land() == fast.land()
                                && direct.river() == fast.river()
                                && direct.lake() == fast.lake()
                                && direct.mountain() == fast.mountain(),
                        "Overview shortcut preserves every displayed TFC regional value across region boundaries");
            }
        }
    }

    private static void parallelCache() {
        Deque<Runnable> jobs = new ArrayDeque<>();
        Set<Tile.Key> loaded = new HashSet<>();
        var cache =
                new BackgroundPrecache(
                        jobs::add,
                        loaded::contains,
                        (key, obsolete) -> {
                            check(
                                    loaded.add(key),
                                    "Parallel background workers must not duplicate tile generation");
                            return true;
                        },
                        () -> false,
                        e -> {
                            throw new AssertionError(e);
                        },
                        2);
        cache.update(0, 0, true);
        check(
                jobs.size() == 2,
                "Two background workers are scheduled for the initial overview cache");
        for (int i = 0; i < 24; i++) {
            jobs.remove().run();
            check(jobs.size() == 2, "Background pipeline stays filled while a worker completes");
        }
        cache.close();
        while (!jobs.isEmpty()) {
            jobs.remove().run();
        }
        check(loaded.size() == 24, "Queued background jobs stop after closing the session");
    }

    private static void areas() {
        Random random = new Random(18);
        for (int test = 0; test < 25; test++) {
            boolean[] grid = new boolean[64 * 64];
            Map<Tile.Key, byte[]> masks = new HashMap<>();
            for (int z = 0; z < 64; z++) {
                for (int x = 0; x < 64; x++) {
                    if (grid[x + 64 * z] = random.nextDouble() < (test + 1) / 26.) {
                        var key = new Tile.Key(x / 32 - 1, z / 32 - 1, 2);
                        masks.computeIfAbsent(key, k -> new byte[1024])[x % 32 + 32 * (z % 32)] =
                                (byte) (1 + random.nextInt(8));
                    }
                }
            }
            int expected = 0;
            int[] queue = new int[4096];
            for (int i = 0; i < grid.length; i++) {
                if (grid[i]) {
                    expected++;
                    int head = 0, tail = 0;
                    queue[tail++] = i;
                    grid[i] = false;
                    while (head < tail) {
                        int n = queue[head++];
                        for (int next :
                                new int[] {
                                    n % 64 > 0 ? n - 1 : -1,
                                    n % 64 < 63 ? n + 1 : -1,
                                    n >= 64 ? n - 64 : -1,
                                    n < 4032 ? n + 64 : -1
                                }) {
                            if (next >= 0 && grid[next]) {
                                grid[next] = false;
                                queue[tail++] = next;
                            }
                        }
                    }
                }
            }
            var overlay = new SearchOverlay(masks, List.of(), true);
            check(
                    overlay.areaCount() == expected,
                    "Distinct areas agree with independent flood fill across negative tile boundaries and highlight colours");
        }
        byte[] filled = new byte[1024];
        Arrays.fill(filled, (byte) 1);
        Map<Tile.Key, byte[]> large = new HashMap<>();
        large.put(new Tile.Key(-500, -500, 2), filled);
        large.put(new Tile.Key(500, 500, 2), filled);
        var sparse = new SearchOverlay(large, List.of(), false);
        check(
                sparse.areaCount() == 2 && sparse.overview().length <= 1024 * 1024,
                "Huge sparse detailed searches keep overview textures bounded");
        check(
                sparse.edges().size() == 8,
                "Sparse outline visits occupied tiles without filling the empty rectangle");
        var diagonal =
                new SearchOverlay(Map.of(new Tile.Key(0, 0, 2), new byte[1024]), List.of(), false);
        check(diagonal.areaCount() == 0, "Empty tiles aren't distinct areas");
        var joined =
                new SearchOverlay(
                        Map.of(new Tile.Key(-1, 0, 2), filled, new Tile.Key(0, 0, 2), filled),
                        List.of(),
                        false);
        check(
                joined.areaCount() == 1,
                "Adjacent tiles form one area independent of candidate count");
    }

    private static void terrain() {
        check(
                TerrainDetail.mountain("tfc:volcanic_oceanic_mountains")
                        && !TerrainDetail.mountain("tfc:highlands"),
                "Mountain mask uses detailed mountain biomes, not coarse regional squares");
        for (int x = -128; x <= 128; x += 8) {
            int a = TerrainDetail.altitude(x, 0, 0, 12, 0, 12);
            check(a >= 0 && a <= 12, "Altitude interpolates at eight-block sample positions");
        }
        check(
                TerrainDetail.altitude(64, 64, 0, 4, 8, 12) == 6,
                "Altitude samples all four regional corners");
        check(
                TerrainDetail.altitude(-64, -64, 0, 4, 8, 12) == 6,
                "Negative coordinates interpolate consistently");
        check(
                new Tile.Key(0, 0, 32).fileName().equals("0_0_32.gz")
                        && new Tile.Key(0, 0, 1).fileName().startsWith("fine3_"),
                "Existing overview cache survives while fine terrain refreshes");
    }

    private static void manual() {
        check(
                Sampling.searchResolution("16", 262144) == 16
                        && Sampling.expensiveSearch("16", 262144),
                "Fine sampling supports the full search radius after confirmation");
        check(
                Sampling.searchResolution("Auto", 262144) == 512
                        && !Sampling.expensiveSearch("Auto", 262144),
                "Auto remains the existing bounded default");
        check(
                Sampling.estimatedSamples(262144, 16) > 1_000_000_000L,
                "Warning estimate uses long arithmetic for very large searches");
        Profile wide = new Profile();
        wide.precision = "16";
        wide.radius = 1_000_000;
        wide.validate();
        check(
                wide.radius == 1_000_000 && wide.searchResolution() == 16,
                "Manual sampling has no former radius cap");
        var all = SearchWindow.of(29_999_984, -29_999_984, Integer.MAX_VALUE, 16);
        check(
                all.count() > Integer.MAX_VALUE && all.minX() < 0 && all.maxZ() > 0,
                "Huge manual search uses lazy bounds without overflow or allocating a tile list");
        Profile p = new Profile();
        p.searchRockLayer = "Dikes";
        p.rocks = "granite";
        p.validate();
        p.showSearchLayer();
        check(
                p.query().dikes() && p.query().layered() && p.selected() == Layer.ROCKS,
                "Dikes is an independent rock search mode");
        check(
                RockLayer.names(8).equals("Dikes") && RockLayer.colour(8) != RockLayer.colour(1),
                "Dikes have a named, separate highlight colour");
    }

    private static final class Menu extends AtlasMenuScreen {
        Menu() {
            super(Component.literal("Test"));
        }
    }

    private static void focus() {
        Menu menu = new Menu();
        EditBox input = new EditBox(null, 0, 0, 80, 20, Component.literal("Field"));
        menu.setFocused(input);
        check(
                menu.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
                        && menu.getFocused() == null
                        && !input.isFocused(),
                "Enter exits an edit field without activating another control");
        menu.setFocused(input);
        check(
                menu.keyPressed(GLFW.GLFW_KEY_KP_ENTER, 0, 0) && menu.getFocused() == null,
                "Keypad Enter has identical focus behavior");
    }
}
