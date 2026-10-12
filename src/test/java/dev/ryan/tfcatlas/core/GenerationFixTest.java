package dev.ryan.tfcatlas.core;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Reproduce a detached cached ring and page-edge rounding using the production ordering/math. */
public final class GenerationFixTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        for (int step : new int[] {8, 16, 32}) {
            var window = new TileWindow(-9, -9, 9, 9, 0, 0, step);
            var order = new CachedRestoreOrder(window);
            Set<Tile.Key> ready = new HashSet<>();
            for (Tile.Key tile : window) {
                if (window.distanceSquared(tile) > 25. * tile.span() * tile.span()) {
                    ready.add(tile);
                }
            }
            order.advance(ready::contains);
            for (Tile.Key tile : ready) {
                check(
                        !order.allows(tile),
                        "Resident outer ring waits for missing central coverage");
            }
            for (Tile.Key tile : window) {
                ready.add(tile);
                order.advance(ready::contains);
                check(order.allows(tile), "Overview restoration advances outwards from the focus");
            }
            for (Tile.Key tile : window) {
                check(order.allows(tile), "Completed overview restores its entire window");
            }
            var fine = new CachedRestoreOrder(new TileWindow(-1, -1, 1, 1, 0, 0, 4));
            check(
                    fine.allows(new Tile.Key(1, 1, 4)),
                    "Fine detail never waits behind a coarse restoration barrier");
        }
        var cache = new SampleCache<String>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.get(1);
        cache.put(3, "three");
        check(
                cache.size() == 2 && cache.get(2) == null && cache.get(1).equals("one"),
                "Sample caches have a bounded access-order lifetime");
        check(
                SampleCache.position(-1, 0) != SampleCache.position(0, -1),
                "Negative sample coordinates stay distinct");
        check(
                GenerationBudget.workers(12) == 4
                        && GenerationBudget.workers(8) == 4
                        && GenerationBudget.workers(2) == 1,
                "Workers scale up while leaving capacity to the game");
        Random random = new Random(109321);
        int oldSeams = 0;
        for (int i = 0; i < 20000; i++) {
            int x = random.nextInt(60000000) - 30000000, z = random.nextInt(60000000) - 30000000;
            int span = 256 << random.nextInt(12);
            double zoom = Math.pow(2, random.nextInt(22) - 16) * (.7 + random.nextDouble() * .6);
            double focusX = x + random.nextDouble() * span, focusZ = z + random.nextDouble() * span;
            var a = MapQuad.project(x, z, span, focusX, focusZ, zoom, 426.5, 239.5);
            var right = MapQuad.project(x + span, z, span, focusX, focusZ, zoom, 426.5, 239.5);
            var below = MapQuad.project(x, z + span, span, focusX, focusZ, zoom, 426.5, 239.5);
            check(
                    Float.floatToIntBits(a.right()) == Float.floatToIntBits(right.left()),
                    "Adjacent horizontal page edges share exact vertex bits");
            check(
                    Float.floatToIntBits(a.bottom()) == Float.floatToIntBits(below.top()),
                    "Adjacent vertical page edges share exact vertex bits");
            float oldBottom = (float) (239.5 + (z - focusZ) * zoom) + (float) (span * zoom);
            if (Float.floatToIntBits(oldBottom) != Float.floatToIntBits(below.top())) {
                oldSeams++;
            }
        }
        check(oldSeams > 100, "The test reproduces the old independently rounded edge mismatch");
        check(
                Math.ceil(DefaultHud.INFO_WIDTH * DefaultHud.INFO_SCALE) == 119
                        && Math.ceil(DefaultHud.INFO_HEIGHT * DefaultHud.INFO_SCALE) == 48,
                "Larger text preserves the accepted default box size");
        check(
                DefaultHud.INFO_HEIGHT - (11 * 10 + 8) <= 2,
                "The usual eleven-row readout fills the default height");
        System.out.println(
                "PASS cached-ring reproduction, fine-detail priority, bounded sampling and 40,000 shared page edges; old mismatches="
                        + oldSeams);
    }

    private GenerationFixTest() {}
}
