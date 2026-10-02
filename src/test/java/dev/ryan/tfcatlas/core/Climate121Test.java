package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.dries007.tfc.client.overworld.SolarCalculator;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.climate.KoppenClimateClassification;

public final class Climate121Test {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() throws Exception {
        check(
                ClimateZones.CODES.equals(
                        Arrays.stream(KoppenClimateClassification.values())
                                .map(Enum::name)
                                .toList()),
                "Zone catalogue matches installed TFC");
        for (float rain : new float[] {0, 75, 149, 200, 499, 500}) {
            for (float variance : new float[] {-1, -.5f, 0, .5f, 1}) {
                Cell c =
                        new Cell("granite", "plains", 2, rain, 20, 0, 0, 0, 1)
                                .withClimate(variance, 80, "AF");
                check(
                        Math.abs(c.januaryRain() - Helpers.triangle(variance * rain, rain, 1, .75f))
                                < .001,
                        "January uses TFC seasonal curve");
                check(
                        Math.abs(c.julyRain() - Helpers.triangle(variance * rain, rain, 1, 1.25f))
                                < .001,
                        "July uses TFC seasonal curve");
                check(
                        c.groundwaterPotential() == Math.min(500, rain + 80),
                        "Potential combines annual rain and river influence");
                check(
                        c.withRock("diorite").rainVariance() == variance
                                && c.withRock("diorite").climateZone().equals("AF"),
                        "Changing rock display preserves climate");
            }
        }
        check(
                SolarCalculator.getInNorthernHemisphere(0, 0),
                "Constant climate has a defined hemisphere");
        check(
                SolarCalculator.getInNorthernHemisphere(0, 20000)
                        != SolarCalculator.getInNorthernHemisphere(40000, 20000),
                "Classification uses alternating hemispheres, not the sign of Z");
        Profile p = new Profile();
        p.climateZones = "AF, CFB";
        p.minGroundwater = 240;
        p.maxGroundwater = 300;
        Cell c = new Cell("granite", "plains", 2, 200, 20, 0, 0, 0, 1).withClimate(.5f, 80, "AF");
        check(p.query().matchesRegion(c), "Climate and water filters work without a rock filter");
        check(
                !p.query().matchesRegion(c.withClimate(.5f, 80, "BWH")),
                "Climate choices use OR within the field");
        check(
                !p.query().matchesRegion(c.withClimate(.5f, 0, "AF")),
                "Water and zone filters combine with AND");
        p.clearSearchSettings();
        check(
                p.query().matchesRegion(c) && p.climateZones.isEmpty(),
                "Clear removes new climate filters");
        Cell[] samples = new Cell[1024];
        Arrays.fill(samples, c);
        Tile.Key key = new Tile.Key(0, 0, 1);
        Path dir = Files.createTempDirectory("atlas-climate-121");
        Path path = dir.resolve(key.fileName());
        try {
            new Tile(key, samples).write(path);
            check(
                    Arrays.equals(Tile.read(path, key).cells(), samples),
                    "Cache preserves signed rainfall variance, river water and climate codes");
        } finally {
            Files.deleteIfExists(path);
            Files.delete(dir);
        }
        for (Layer layer :
                List.of(
                        Layer.CLIMATE_ZONES,
                        Layer.RAIN_VARIANCE,
                        Layer.JANUARY_RAIN,
                        Layer.JULY_RAIN,
                        Layer.GROUNDWATER)) {
            check(
                    !layer.legend(false, Map.of()).isEmpty() && layer.continentFill(),
                    "New climate layers have legends and continent masking");
        }
        System.out.println(
                "PASS: TFC 1.21 climate formula, hemisphere, filter, legend and cache checks");
    }
}
