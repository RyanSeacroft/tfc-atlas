package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.ClimateZones;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.MapLabels;
import dev.ryan.tfcatlas.core.SearchDetails;
import java.util.Locale;
import java.util.Set;
import net.dries007.tfc.world.noise.FastNoiseLite;
import net.dries007.tfc.world.region.RegionGenerator;

public final class ClimateUiFixTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        for (String code : ClimateZones.CODES) {
            check(
                    ClimateZones.code(ClimateZones.label(code))
                            .equals(code.toLowerCase(Locale.ROOT)),
                    "Every readable name resolves uniquely");
        }
        Profile p = new Profile();
        p.climateZones = "Hot Desert, Oceanic";
        check(
                p.query().climateZones().equals(Set.of("bwh", "cfb")),
                "Name searches resolve to TFC classifications");
        check(
                ClimateZones.displayList("AF, CFB").equals("Humid Tropical, Oceanic"),
                "Old searches display full names");
        check(
                MapLabels.visible("Active layer", Layer.CLIMATE_ZONES, .001, 32),
                "Climate labels available at continent zoom");
        Cell c = new Cell("granite", "plains", 2, 200, 20, 0, 0, 0, 1).withClimate(0, 0, "CFB");
        check(MapLabels.text(Layer.CLIMATE_ZONES, c).equals("Oceanic"), "Climate labels use names");
        check(
                new SearchDetails(p.query(), 0, 0, 16384, 5, 100)
                        .lines()
                        .contains("Climate: Hot Desert, Oceanic"),
                "Criteria panel uses names");
        if (Boolean.getBoolean("tfcatlas.gameTests")) {
            regionBounds();
        }
        System.out.println(
                "PASS: readable climate inputs, legacy searches, labels and region boundary checks");
    }

    private static void regionBounds() {
        var settings = TestWorldgen.defaults();
        var stock = new RegionGenerator(settings, net.dries007.tfc.world.Seed.of(123456789));
        var atlas = new RegionGenerator(settings, net.dries007.tfc.world.Seed.of(123456789));
        AtlasRegionRecovery.register(atlas);
        int found = 0;
        outer:
        for (int cz = -80; cz <= 80; cz++) {
            for (int cx = -80; cx <= 80; cx++) {
                var centre = stock.sampleCell(cx * 96, cz * 96);
                int centreX = FastNoiseLite.FastRound(centre.x()),
                        centreZ = FastNoiseLite.FastRound(centre.y());
                for (int edge = 0; edge < 4; edge++) {
                    for (int t = -101; t <= 101; t++) {
                        int x = centreX + (edge < 2 ? (edge == 0 ? -102 : 102) : t),
                                z = centreZ + (edge >= 2 ? (edge == 2 ? -102 : 102) : t);
                        var cell = stock.sampleCell(x, z);
                        if (cell.x() == centre.x() && cell.y() == centre.y()) {
                            check(
                                    stock.getOrCreateRegion(x, z).at(x, z) == null,
                                    "Reproduce missing point with unchanged TFC generator");
                            var repaired = atlas.getOrCreateRegion(x, z);
                            check(repaired.at(x, z) != null, "Atlas includes the missing cell tip");
                            for (int dz = -1; dz <= 1; dz++) {
                                for (int dx = -1; dx <= 1; dx++) {
                                    check(
                                            atlas.getOrCreateRegionPoint(x + dx, z + dz) != null,
                                            "Neighbouring interpolation samples are complete");
                                }
                            }
                            System.out.println("REGION_BOUNDARY_RECOVERED " + x + ", " + z);
                            if (++found == 3) {
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        check(found == 3, "Exercise three natural TFC region-boundary failures");
        var original = stock.getOrCreateRegion(0, 0);
        var same = atlas.getOrCreateRegion(0, 0);
        for (var point : original.points()) {
            var other = same.at(point.x, point.z);
            check(
                    other != null
                            && point.biome == other.biome
                            && point.rock == other.rock
                            && point.rainfall == other.rainfall
                            && point.temperature == other.temperature,
                    "Ordinary regions preserve TFC generation");
        }
    }
}
