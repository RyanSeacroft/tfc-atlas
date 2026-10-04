package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.Soil;

public final class SoilSamplerTest {
    public static void run() throws Exception {
        var sampler = new SoilSampler();
        var trees = (java.util.List<?>) XaeroBridge.field(sampler, "trees");
        check(trees.size() == 19, "Read all tree preferences from the pinned TFC dependency");
        check(
                sample(sampler, 300, 0, 0, 0, "plains") == Soil.MOLLISOL,
                "Cold wet grassland favours mollisol");
        check(
                sample(sampler, 350, 25, 0, 0, "plains") == Soil.OXISOL,
                "Wet tropical soil is oxisol");
        check(
                sample(sampler, 300, 8, 0, 0, "plains") == Soil.ENTISOL,
                "Ordinary temperate grassland is entisol");
        check(sample(sampler, 150, 20, 0, 0, "plains") == Soil.ARIDISOL, "Dry soil is aridisol");
        check(
                sample(sampler, 400, 15, .7f, 50, "plains") == Soil.FLUVISOL,
                "Seasonal river groundwater favours fluvisol");
        check(
                sample(sampler, 300, 15, 0, 0, "volcanic_mountains") == Soil.ANDISOL,
                "Volcanic terrain favours andisol");
        check(
                sample(sampler, 10, 20, 0, 0, "plains") == Soil.BARE,
                "Very dry terrain is not advertised as fertile farmland");
        var found = java.util.EnumSet.noneOf(Soil.class);
        for (int temp = -10; temp <= 20; temp += 2) {
            for (int rain = 210; rain <= 490; rain += 20) {
                for (boolean northern : new boolean[] {true, false}) {
                    var cell = cell(rain, temp, .25f, 0, "plains");
                    found.add(sampler.sample(cell, 4, 1, 0, 0, 0, northern));
                }
            }
        }
        check(
                found.contains(Soil.PODZOL) && found.contains(Soil.ALFISOL),
                "Forest tree climates produce both forest soils");
        System.out.println(
                "PASS: TFC soil resources, farming regions and northern/southern forest sampling");
    }

    private static Soil sample(
            SoilSampler sampler,
            float rain,
            float temp,
            float variance,
            float water,
            String biome) {
        return sampler.sample(cell(rain, temp, variance, water, biome), 0, 2, 0, 0, 0, true);
    }

    private static Cell cell(float rain, float temp, float variance, float water, String biome) {
        return new Cell("granite", biome, 2, rain, temp, 0, 0, 0, 1)
                .withClimate(variance, water, "CFB");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
