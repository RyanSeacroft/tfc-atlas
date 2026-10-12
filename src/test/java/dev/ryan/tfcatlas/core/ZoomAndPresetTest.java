package dev.ryan.tfcatlas.core;

import java.util.Arrays;
import java.util.List;

/** Quality transitions retain coverage, while compact controls keep consistent bounds. */
public final class ZoomAndPresetTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static CachedColours.Raster raster(int x, int z, int span, int size, int[] data) {
        return new CachedColours.Raster(
                x,
                z,
                span,
                size,
                new CachedColours.Pixels() {
                    @Override
                    public int get(int px, int pz) {
                        return data[px + pz * size];
                    }

                    @Override
                    public void put(int px, int pz, int colour) {
                        data[px + pz * size] = colour;
                    }
                });
    }

    public static void run() {
        int[] coarse = new int[64];
        Arrays.fill(coarse, 0xFF445566);
        var parent = raster(-4096, -4096, 8192, 8, coarse);
        // A complete visible window, not just the first 128 tiles, inherits coarse pixels.
        for (int z = -8; z < 8; z++) {
            for (int x = -8; x < 8; x++) {
                int[] fine = new int[256];
                fine[0] = 0xFF112233;
                var target = raster(x * 512, z * 512, 512, 16, fine);
                check(
                        CachedColours.fillMissing(target, parent),
                        "Skipped quality levels inherit colours");
                check(fine[0] == 0xFF112233, "Finer colours survive inheritance");
                for (int i = 1; i < fine.length; i++) {
                    check(
                            fine[i] == 0xFF445566,
                            "Previously covered terrain never becomes transparent");
                }
            }
        }
        int[] holes = new int[64];
        check(
                !CachedColours.fillMissing(
                        raster(0, 0, 512, 8, holes), raster(0, 0, 512, 8, new int[64])),
                "Missing source data never invents terrain");
        check(
                PresetNotice.alpha(1999) == 255 && PresetNotice.alpha(2000) == 255,
                "Preset notice holds for two seconds");
        check(
                PresetNotice.alpha(2300) > 0 && PresetNotice.alpha(2300) < 255,
                "Notice gently fades");
        check(
                PresetNotice.alpha(2599) == 0,
                "Very faint text cannot flash opaque on the final frame");
        check(PresetNotice.alpha(2600) == 0, "Notice disappears after fade");
        for (int width : new int[] {320, 480, 854}) {
            var groups = SearchLayout.groups(width, 280, false, 0);
            var pairs = groups.stream().filter(g -> g.keys().size() == 2).toList();
            check(pairs.size() == 3, "Terrain has three min/max pairs");
            for (var pair : pairs) {
                check(pair.bounds().y() == pairs.get(0).bounds().y(), "All pairs share one row");
                check(
                        pair.bounds().width() == pairs.get(0).bounds().width(),
                        "All pairs share a width");
                check(
                        pair.field(0).height() == pairs.get(0).field(0).height(),
                        "All paired fields share a height");
            }
            check(
                    groups.stream()
                                    .filter(g -> g.keys().equals(List.of("feature")))
                                    .findFirst()
                                    .orElseThrow()
                                    .bounds()
                                    .y()
                            > pairs.get(0).bounds().y(),
                    "Terrain features sit below paired limits");
        }
        check(!DisplayLayout.keys(2).contains("accessible"), "Alternate palette option is removed");
        System.out.println("PASS full-window cached coverage, preset fade and terrain layout");
    }

    private ZoomAndPresetTest() {}
}
