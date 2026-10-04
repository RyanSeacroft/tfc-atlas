package dev.ryan.tfcatlas.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.Soil;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.surface.SoilSurfaceState;

/**
 * Cheap soil-region guidance using TFC's climate, forest types and bundled tree preferences. Local
 * elevation, surface blending and decoration can change the soil at an individual block.
 */
final class SoilSampler {
    private record Tree(
            float minTemp,
            float maxTemp,
            float minWater,
            float maxWater,
            float minVariance,
            float maxVariance,
            float minY,
            float maxY,
            boolean absolute,
            Soil soil) {
        boolean matches(float temperature, float water, float variance) {
            float rain = absolute ? Math.abs(variance) : variance;
            int seaLevel = net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y;
            return temperature >= minTemp
                    && temperature <= maxTemp
                    && water >= minWater
                    && water <= maxWater
                    && rain >= minVariance
                    && rain <= maxVariance
                    && seaLevel >= minY
                    && seaLevel <= maxY;
        }

        // Follow the preference ordering used by TFC's ForestConfig.Entry at sea level.
        double preference(float temperature, float water, float variance) {
            float rain = absolute ? Math.abs(variance) : variance;
            return (temperature - (maxTemp - minTemp) / 2) * 10
                    + water
                    - (maxWater - minWater) / 2
                    + (rain - (maxVariance - minVariance) / 2) * 250
                    + (net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y - (maxY - minY) / 2)
                            * 5;
        }

        static Tree read(JsonObject climate, Soil soil) {
            return new Tree(
                    number(climate, "min_temperature", Float.NEGATIVE_INFINITY),
                    number(climate, "max_temperature", Float.POSITIVE_INFINITY),
                    number(climate, "min_groundwater", Float.NEGATIVE_INFINITY),
                    number(climate, "max_groundwater", Float.POSITIVE_INFINITY),
                    number(climate, "min_rain_variance", -1),
                    number(climate, "max_rain_variance", 1),
                    number(climate, "min_elevation", -64),
                    number(climate, "max_elevation", 320),
                    climate.has("rain_variance_absolute")
                            && climate.get("rain_variance_absolute").getAsBoolean(),
                    soil);
        }

        private static float number(JsonObject data, String key, float fallback) {
            return data.has(key) ? data.get(key).getAsFloat() : fallback;
        }
    }

    private final List<Tree> trees = loadTrees();

    Soil sample(Cell c, ForestType forest, int x, int z, boolean northern) {
        return sample(
                c,
                forest.getDensity(),
                forest.getMaxTreeTypes(),
                forest.getAlternateSize(),
                x,
                z,
                northern);
    }

    Soil sample(
            Cell c, int density, int maxTrees, int alternateTrees, int x, int z, boolean northern) {
        if (!c.land()) {
            return Soil.BARE;
        }
        float water = c.rain() + c.baseGroundwater(), temperature = c.temperature();
        double patch = SoilSurfaceState.PATCH_NOISE.noise(x, z);
        // SurfaceState's broad rain bands, without pretending to know local slopes or snow cover.
        if (water + 15 * patch < 90 || temperature < -17) {
            return Soil.BARE;
        }
        String biome = c.biome();
        if (biome.contains("volcanic")
                || biome.contains("volcano")
                || biome.contains("cinder_cone")
                || biome.contains("tuff_ring")) {
            return Soil.ANDISOL;
        }
        Soil base =
                water + 15 * patch < 201
                        ? Soil.ARIDISOL
                        : c.baseGroundwater() > 25 && Math.abs(c.rainVariance()) > .5
                                ? Soil.FLUVISOL
                                : temperature > 16.7 || temperature >= 16 && patch > 0
                                        ? Soil.OXISOL
                                        : Soil.ENTISOL;
        if (base == Soil.FLUVISOL) {
            return base;
        }
        // Mollisol patches favour cold, wet, open land. Forest soils replace ordinary base soils.
        if (temperature >= -9 && temperature <= 3 && c.rain() >= 250 && density < 2) {
            return Soil.MOLLISOL;
        }
        if (temperature > 20 || density < 2) {
            return base;
        }
        float variance = c.rainVariance() * (northern ? 1 : -1);
        List<Tree> candidates =
                trees.stream()
                        .filter(t -> t.matches(temperature, water, variance))
                        .sorted(
                                Comparator.comparingDouble(
                                        t -> t.preference(temperature, water, variance)))
                        .limit(maxTrees)
                        .toList();
        Soil result = base;
        int start = Math.min(Math.max(0, candidates.size() - 1), alternateTrees);
        for (int i = start; i < candidates.size(); i++) {
            Soil soil = candidates.get(i).soil();
            if (soil == Soil.PODZOL || soil == Soil.ALFISOL && result != Soil.PODZOL) {
                result = soil;
            }
        }
        return result;
    }

    private static List<Tree> loadTrees() {
        List<Tree> trees = new ArrayList<>();
        try {
            JsonObject tag = resource("tags/worldgen/configured_feature/forest_trees.json");
            for (var value : tag.getAsJsonArray("values")) {
                String id = value.getAsString();
                if (!id.startsWith("tfc:")) {
                    continue;
                }
                JsonObject config =
                        resource("worldgen/configured_feature/" + id.substring(4) + ".json")
                                .getAsJsonObject("config");
                String disc = config.has("soil_disc") ? config.get("soil_disc").getAsString() : "";
                Soil soil =
                        disc.equals("tfc:podzol_disc")
                                ? Soil.PODZOL
                                : disc.equals("tfc:alfisol_disc") ? Soil.ALFISOL : Soil.UNKNOWN;
                // Non-soil trees still participate in TFC's preference ordering.
                trees.add(Tree.read(config.getAsJsonObject("climate"), soil));
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Atlas soil regions: tree preferences unavailable", ex);
        }
        return List.copyOf(trees);
    }

    private static JsonObject resource(String name) throws java.io.IOException {
        try (var stream = SoilSurfaceState.class.getResourceAsStream("/data/tfc/" + name)) {
            if (stream == null) {
                throw new java.io.IOException("Missing TFC soil resource: " + name);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
