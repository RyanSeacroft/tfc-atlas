package dev.ryan.tfcatlas.core;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

public final class SoilTest {
    public static void run() throws Exception {
        Cell sample =
                new Cell("granite", "plains", 2, 300, 2, 0, 0, 0, 1)
                        .withClimate(.7f, 40, "CFB")
                        .withSoil(Soil.MOLLISOL);
        check(sample.withRock("gneiss").soil() == Soil.MOLLISOL, "Rock view preserves soil");
        check(
                sample.withClimate(0, 0, "CFA").soil() == Soil.MOLLISOL,
                "Climate update preserves soil");
        check(Layer.SOIL.value(sample).equals("Mollisol"), "Soil layer uses readable names");
        check(
                Soil.MOLLISOL.nutrientBonus == 20 && Soil.OXISOL.nutrientBonus == -20,
                "Fertility key covers TFC's positive and negative nutrient bonuses");
        for (Soil soil : Soil.values()) {
            if (soil == Soil.UNKNOWN) {
                continue;
            }
            var entry =
                    Layer.SOIL.legend(false, Map.of()).stream()
                            .filter(e -> e.colourKey().equals(soil.label))
                            .findFirst()
                            .orElseThrow();
            check(
                    entry.colourAt(0) == Layer.SOIL.color(sample.withSoil(soil), false, Map.of()),
                    "Key matches map colour for " + soil.label);
        }
        Cell[] cells = new Cell[Tile.SIDE * Tile.SIDE];
        Arrays.fill(cells, sample);
        var tile = new Tile(new Tile.Key(-1, 1, 4), cells);
        var folder = Files.createTempDirectory("atlas-soil-test");
        var path = folder.resolve("sample.gz");
        try {
            tile.write(path);
            check(
                    Tile.read(path, tile.key()).cells()[0].equals(sample),
                    "Soil survives disk cache round trip");
            writeLegacy(path, tile);
            check(Tile.CACHE_GENERATION == 5, "Existing cache directory remains reachable");
            check(
                    Tile.read(path, tile.key()).cells()[0].equals(sample.withSoil(Soil.UNKNOWN)),
                    "Previous tiles retain terrain and climate for an in-place soil upgrade");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(folder);
        }
        System.out.println("PASS: soil names, fertility, colour key and persistent samples");
    }

    private static void writeLegacy(java.nio.file.Path path, Tile tile) throws Exception {
        try (var out =
                new java.io.DataOutputStream(
                        new java.util.zip.GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(0x54464154);
            out.writeInt(5);
            out.writeInt(tile.key().x());
            out.writeInt(tile.key().z());
            out.writeInt(tile.key().step());
            for (Cell c : tile.cells()) {
                out.writeUTF(c.rock());
                out.writeUTF(c.biome());
                out.writeByte(c.rockType());
                out.writeFloat(c.rain());
                out.writeFloat(c.temperature());
                out.writeInt(c.altitude());
                out.writeInt(c.inland());
                out.writeInt(c.oceanDistance());
                out.writeByte(c.flags());
                out.writeUTF(c.middleRock());
                out.writeUTF(c.bottomRock());
                out.writeFloat(c.rainVariance());
                out.writeFloat(c.baseGroundwater());
                out.writeUTF(c.climateZone());
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
