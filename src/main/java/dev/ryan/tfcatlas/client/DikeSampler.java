package dev.ryan.tfcatlas.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ryan.tfcatlas.core.SearchQuery;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dries007.tfc.world.feature.vein.IVein;
import net.dries007.tfc.world.feature.vein.PipeVeinConfig;
import net.dries007.tfc.world.feature.vein.PipeVeinFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/** Search-only prediction using TFC's own seeded vein placement and pipe geometry. */
final class DikeSampler {
    record Footprint(String rock, int x, int z, int reach) {}

    record Hit(String rock, int y, Footprint footprint) {}

    private record Kind(
            String rock,
            PipeVeinFeature feature,
            PipeVeinConfig config,
            java.util.function.Predicate<BlockPos> enabled) {}

    private record Vein(Kind kind, Object value, BlockPos center) {}

    private final List<Kind> kinds = new ArrayList<>();
    private final Map<Long, List<Vein>> cache = new LinkedHashMap<>(256, .75f, true);
    private final Method chance;
    private final WorldGenLevel seedView;
    private final TerrainHeightSampler heights;
    private final FineSampler fine;

    DikeSampler(long seed, TerrainHeightSampler heights, FineSampler fine) throws Exception {
        this.heights = heights;
        this.fine = fine;
        Class<?> vein = Class.forName("net.dries007.tfc.world.feature.vein.PipeVeinFeature$Vein");
        chance =
                PipeVeinFeature.class.getDeclaredMethod(
                        "getChanceToGenerate",
                        int.class,
                        int.class,
                        int.class,
                        vein,
                        PipeVeinConfig.class);
        chance.setAccessible(true);
        seedView = seedView(seed);
        var server = Minecraft.getInstance().getSingleplayerServer();
        for (String rock : List.of("granite", "diorite", "gabbro")) {
            PipeVeinFeature feature;
            PipeVeinConfig config;
            java.util.function.Predicate<BlockPos> enabled = pos -> true;
            if (server != null && server.getLevel(Level.OVERWORLD) != null) {
                var configured =
                        server.getLevel(Level.OVERWORLD)
                                .registryAccess()
                                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                                .get(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "tfc", "vein/" + rock + "_dike"));
                if (configured == null) {
                    continue;
                }
                if (!(configured.feature() instanceof PipeVeinFeature f)
                        || !(configured.config() instanceof PipeVeinConfig c)) {
                    throw new IllegalStateException(
                            "Unsupported custom " + rock + " dike generator");
                }
                feature = f;
                config = c;
                var registry =
                        server.getLevel(Level.OVERWORLD)
                                .registryAccess()
                                .registryOrThrow(Registries.BIOME);
                enabled =
                        pos -> {
                            var biome =
                                    registry.get(
                                            heights.source
                                                    .getBiomeExtension(
                                                            pos.getX() >> 2, pos.getZ() >> 2)
                                                    .key()
                                                    .location());
                            return biome != null
                                    && biome.getGenerationSettings().features().stream()
                                            .flatMap(holders -> holders.stream())
                                            .flatMap(holder -> holder.value().getFeatures())
                                            .anyMatch(value -> value == configured);
                        };
            } else {
                try (var in =
                        PipeVeinFeature.class.getResourceAsStream(
                                "/data/tfc/worldgen/configured_feature/vein/"
                                        + rock
                                        + "_dike.json")) {
                    if (in == null) {
                        throw new IOException("Missing " + rock + " dike settings");
                    }
                    var data =
                            JsonParser.parseReader(new InputStreamReader(in))
                                    .getAsJsonObject()
                                    .get("config");
                    config =
                            PipeVeinConfig.CODEC
                                    .parse(JsonOps.INSTANCE, data)
                                    .getOrThrow(IllegalArgumentException::new);
                }
                feature = new PipeVeinFeature(PipeVeinConfig.CODEC);
            }
            if (config.config().projectToSurface() || config.config().nearLava()) {
                throw new IllegalStateException(
                        "Projected or lava-dependent custom dikes are not supported");
            }
            String output = "tfc:rock/raw/" + rock;
            for (var weighted : config.config().states().values()) {
                for (var state : weighted.values()) {
                    if (!Set.of(output, "tfc:rock/hardened/" + rock)
                            .contains(
                                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
                        throw new IllegalStateException(
                                "Unsupported custom dike replacement: " + rock);
                    }
                }
            }
            kinds.add(new Kind(output, feature, config, enabled));
        }
    }

    static WorldGenLevel seedView(long seed) throws Exception {
        // getNearbyVeins only reads the seed from this view; it never generates or edits chunks.
        Method seedMethod = findSeedMethod();
        return (WorldGenLevel)
                Proxy.newProxyInstance(
                        WorldGenLevel.class.getClassLoader(),
                        new Class<?>[] {WorldGenLevel.class},
                        (proxy, method, args) -> {
                            // Match the production-remapped interface method without spelling its
                            // mapped name.
                            if (method.equals(seedMethod)) {
                                return seed;
                            }
                            if (method.getName().equals("toString")) {
                                return "Atlas dike seed view";
                            }
                            throw new UnsupportedOperationException(
                                    "Unexpected TFC vein world access: " + method.getName());
                        });
    }

    private static Method findSeedMethod() throws NoSuchMethodException {
        for (String name : List.of("getSeed", "m_7328_")) {
            try {
                return WorldGenLevel.class.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("WorldGenLevel seed getter");
    }

    Hit at(int x, int z, SearchQuery query) throws Exception {
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        long key = ((long) cx << 32) | (cz & 0xffffffffL);
        List<Vein> nearby = cache.get(key);
        if (nearby == null) {
            nearby = new ArrayList<>();
            for (Kind kind : kinds) {
                for (Object vein :
                        kind.feature.getNearbyVeins(
                                seedView,
                                null,
                                new ChunkPos(cx, cz),
                                kind.config.chunkRadius(),
                                kind.config)) {
                    BlockPos center = ((IVein) vein).pos();
                    if (kind.enabled.test(center)) {
                        nearby.add(new Vein(kind, vein, center));
                    }
                }
            }
            nearby = List.copyOf(nearby);
            cache.put(key, nearby);
            if (cache.size() > 4096) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
        int surface = Integer.MIN_VALUE;
        for (Vein vein : nearby) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException();
            }
            if (!query.matchesRock(vein.kind.rock)) {
                continue;
            }
            int reach = vein.kind.config.radius() + vein.kind.config.maxSkew();
            if (Math.abs((long) x - vein.center.getX()) > reach
                    || Math.abs((long) z - vein.center.getZ()) > reach) {
                continue;
            }
            int
                    low =
                            Math.max(
                                    -63,
                                    Math.max(
                                            vein.kind.config.minY(),
                                            vein.center.getY() - vein.kind.config.height())),
                    high =
                            Math.min(
                                    319,
                                    Math.min(
                                            vein.kind.config.maxY(),
                                            vein.center.getY() + vein.kind.config.height()));
            for (int y = high; y >= low; y--) {
                float density =
                        (float)
                                chance.invoke(
                                        vein.kind.feature,
                                        x - vein.center.getX(),
                                        y - vein.center.getY(),
                                        z - vein.center.getZ(),
                                        vein.value,
                                        vein.kind.config);
                if (density > 0) {
                    if (surface == Integer.MIN_VALUE) {
                        surface = heights.sample(x, z);
                    }
                    if (y >= surface) {
                        y = Math.min(y, surface);
                        continue;
                    }
                    var host =
                            BuiltInRegistries.BLOCK.get(
                                    ResourceLocation.parse(fine.rock(x, y, z, surface)));
                    if (vein.kind.config.config().states().containsKey(host)) {
                        return new Hit(
                                vein.kind.rock,
                                y,
                                new Footprint(
                                        vein.kind.rock,
                                        vein.center.getX(),
                                        vein.center.getZ(),
                                        reach));
                    }
                }
            }
        }
        return null;
    }
}
