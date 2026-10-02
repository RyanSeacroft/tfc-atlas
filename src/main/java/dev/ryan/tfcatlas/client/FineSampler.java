package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.TerrainDetail;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.RegionBiomeSource;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.region.RegionGenerator;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;

/** Stateless sampling; TFC's layer areas and region caches support both Atlas workers. */
final class FineSampler {
    private final java.util.Map<net.minecraft.world.level.block.Block, String> rockNames =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<BiomeExtension, String> biomeNames =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final RegionGenerator regions;
    private final RegionBiomeSource source;
    private final RegionChunkDataGenerator chunks;
    private final ThreadLocal<TerrainShapeSampler> terrainShape;

    FineSampler(
            RegionGenerator regions, RegionBiomeSource source, RegionChunkDataGenerator chunks) {
        this.regions = regions;
        this.source = source;
        this.chunks = chunks;
        terrainShape = ThreadLocal.withInitial(() -> new TerrainShapeSampler(source));
    }

    Cell sample(int x, int z) {
        return sample(x, z, 8);
    }

    Cell sample(int x, int z, int resolution) {
        var point = regions.getOrCreateRegionPoint(Math.floorDiv(x, 128), Math.floorDiv(z, 128));
        var biome = source.getBiomeExtension(QuartPos.fromBlock(x), QuartPos.fromBlock(z));
        String id = biomeNames.computeIfAbsent(biome, b -> b.key().location().toString());
        var data = new ChunkData(chunks, new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        chunks.generate(data);
        String rock = rock(x, 0, z, 0);
        var terrain = source.getBiomeExtensionNoRiver(QuartPos.fromBlock(x), QuartPos.fromBlock(z));
        int gx = Math.floorDiv(x, 128), gz = Math.floorDiv(z, 128);
        int altitude =
                TerrainDetail.altitude(
                        x,
                        z,
                        point.biomeAltitude,
                        regions.getOrCreateRegionPoint(gx + 1, gz).biomeAltitude,
                        regions.getOrCreateRegionPoint(gx, gz + 1).biomeAltitude,
                        regions.getOrCreateRegionPoint(gx + 1, gz + 1).biomeAltitude);
        boolean mountain =
                resolution <= 32
                        ? terrainShape.get().mountain(x, z)
                        : TerrainDetail.mountain(terrain.key().location().toString());
        altitude = mountain ? 12 : Math.min(11, altitude);
        int flags =
                (biome.biomeBlendType() != BiomeBlendType.OCEAN ? 1 : 0)
                        | (biome == TFCBiomes.RIVER ? 2 : 0)
                        | (biome.biomeBlendType() == BiomeBlendType.LAKE ? 4 : 0)
                        | (mountain ? 8 : 0);
        return new Cell(
                rock,
                id,
                RockStrata.surfaceRegion(chunks, x, z),
                data.getRainfall(x, z),
                data.getAverageTemp(x, z),
                altitude,
                point.baseLandHeight,
                point.distanceToOcean,
                flags,
                rock(x, RockStrata.referenceY(chunks, x, z, 1), z, 0),
                rock(x, RockStrata.referenceY(chunks, x, z, 2), z, 0));
    }

    String rock(int x, int y, int z, int surfaceY) {
        return rockNames.computeIfAbsent(
                chunks.generateRock(x, y, z, surfaceY, null).raw(),
                b -> BuiltInRegistries.BLOCK.getKey(b).toString());
    }
}
