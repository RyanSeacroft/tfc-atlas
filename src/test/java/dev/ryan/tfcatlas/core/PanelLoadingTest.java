package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import dev.ryan.tfcatlas.client.Profiles;
import java.util.Arrays;

/** Protect the limited recovery patch: identical defaults, native detail, and hole-only copying. */
public final class PanelLoadingTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        Profile defaults = new Profile();
        check(
                defaults.infoScale == .4 && defaults.criteriaScale == .45,
                "Recording readout scales");
        check(
                Math.ceil(DefaultHud.INFO_WIDTH * defaults.infoScale)
                        == Math.ceil(DefaultHud.SEARCH_WIDTH * defaults.criteriaScale),
                "Readout panels share the recorded width");
        for (Layer layer : Layer.values()) {
            defaults.layer = layer.name();
            check(
                    defaults.keyScale() == DefaultHud.keyScale(layer.name()),
                    "Each supported key gets the recorded font scale");
            check(
                    defaults.keyHeight() == DefaultHud.keyHeight(layer.name()),
                    "Only long lists use the recorded scrolling viewport");
            defaults.keySize(0, 17);
            defaults.keyScale(.72);
            var stored = Profiles.decode(Profiles.JSON.toJsonTree(defaults).getAsJsonObject());
            check(
                    stored.keyHeight() == 17 && stored.keyScale() == .72,
                    "User resizing survives saving");
            defaults.resetHud();
            check(
                    defaults.keyHeight() == DefaultHud.keyHeight(layer.name()),
                    "Reset returns each layer to its own default");
        }
        for (int screenHeight : new int[] {240, 480, 854}) {
            var safe = HudLayout.safeArea(854, screenHeight);
            var toolbar = new HudLayout.Box(safe.x(), safe.y(), 600, 10);
            var info = new HudLayout.Box(safe.x(), safe.bottom() - 48, 119, 48);
            var search = HudLayout.above(safe, info, 119, 32, DefaultHud.GAP);
            var key = HudLayout.keyArea(safe, toolbar, search, info);
            check(
                    search.bottom() + 3 == info.y() && key.y() == toolbar.bottom() + 3,
                    "Recorded vertical gaps");
            check(
                    key.bottom() + 3 == search.y() && key.x() == info.x() && search.x() == info.x(),
                    "All default panels stay aligned and do not overlap");
        }
        for (int step : new int[] {1, 2, 4}) {
            for (double zoom : new double[] {.001, .01, .1, .25, .5, 1, 2}) {
                check(
                        Sampling.cachedTexturePixels(step, zoom) == 32,
                        "Fine cached colours remain native through all zoom changes");
            }
        }
        for (int step : new int[] {8, 16, 32}) {
            check(
                    Sampling.cachedTexturePixels(step, .001) == Sampling.texturePixels(step, .001),
                    "Wide overview textures stay compact");
        }
        int[] fine = new int[256 * 256], coarse = new int[32 * 32];
        Arrays.fill(coarse, 0xFF445566);
        fine[0] = 0xFF112233;
        var destination = raster(256, fine);
        check(
                CachedColours.fillMissing(destination, raster(32, coarse), 0, 0, 128, 128),
                "Only visible holes inherit coarse colours");
        check(
                fine[0] == 0xFF112233 && fine[31 + 31 * 256] == 0xFF445566 && fine[32] == 0,
                "Fine colours survive, visible holes fill, offscreen pixels are untouched");
        Arrays.fill(fine, 0xFF112233);
        check(
                !CachedColours.fillMissing(destination, raster(32, coarse)),
                "Preview never overwrites exact detail");
        var unchanged = Profiles.JSON.toJsonTree(new Profile()).getAsJsonObject();
        unchanged.addProperty("uiRevision", 6);
        unchanged.addProperty("infoScale", .5);
        unchanged.addProperty("criteriaScale", .5);
        var migrated = Profiles.decode(unchanged);
        check(
                migrated.infoScale == .4 && migrated.criteriaScale == .45,
                "Only old default scales migrate");
        unchanged.addProperty("infoScale", .8);
        check(Profiles.decode(unchanged).infoScale == .8, "Custom scales are preserved");
        System.out.println(
                "PASS shared recording defaults, every key, native cached detail and visible-only preview coverage");
    }

    private static CachedColours.Raster raster(int size, int[] colours) {
        return new CachedColours.Raster(
                0,
                0,
                1024,
                size,
                new CachedColours.Pixels() {
                    public int get(int x, int z) {
                        return colours[x + z * size];
                    }

                    public void put(int x, int z, int colour) {
                        colours[x + z * size] = colour;
                    }
                });
    }

    private PanelLoadingTest() {}
}
