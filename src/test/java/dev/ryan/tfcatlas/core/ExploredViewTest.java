package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.client.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ExploredViewTest {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        var random = new Random(20261004);
        for (int trial = 0; trial < 100; trial++) {
            List<TerrainCoverage.Rect> explored = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                int x = random.nextInt(60) - 30, z = random.nextInt(60) - 30;
                explored.add(
                        new TerrainCoverage.Rect(
                                x, z, x + random.nextInt(18), z + random.nextInt(18)));
            }
            var unknown = TerrainCoverage.complement(explored, -20, -20, 20, 20);
            for (int z = -20; z < 20; z++) {
                for (int x = -20; x < 20; x++) {
                    int xx = x, zz = z;
                    boolean known = explored.stream().anyMatch(r -> contains(r, xx, zz));
                    long count = unknown.stream().filter(r -> contains(r, xx, zz)).count();
                    check(
                            count == (known ? 0 : 1),
                            "Explored/unknown coverage partitions every pixel exactly once");
                }
            }
        }
        var full = List.of(new TerrainCoverage.Rect(-16, -16, 16, 16));
        check(
                TerrainCoverage.complement(full, -16, -16, 16, 16).isEmpty(),
                "Fully explored viewport has no exclusions");
        var empty = TerrainCoverage.complement(List.of(), -16, -16, 16, 16);
        check(empty.equals(full), "Unexplored viewport excludes all explored-only labels");
        Profile p = new Profile();
        check(
                p.mode.equals("Full map") && p.display.equals("Xaero Map") && p.atlasEnabled,
                "New profiles start with the requested non-destructive view");
        check(p.opacity == .5, "Default overlay opacity is 50 percent");
        for (String expected : List.of("Explored only", "Full map", "Explored only")) {
            p.cycleCoverage();
            check(p.mode.equals(expected), "Coverage follows the requested order");
            p.validate();
            check(p.mode.equals(expected), "Coverage survives validation");
        }
        for (double opacity : new double[] {.25, .5, .75}) {
            p.opacity = opacity;
            // Expected discovered alpha, undiscovered alpha, and whether Display is useful.
            Object[][] cases = {
                {"Full map", "TFC Layers Only", 1f, 1f, true},
                {"Full map", "Xaero Map", 0f, 1f, true},
                {"Full map", "Overlay", (float) opacity, 1f, true},
                {"Explored only", "TFC Layers Only", 1f, 0f, true},
                {"Explored only", "Overlay", (float) opacity, 0f, true},
            };
            for (Object[] row : cases) {
                p.mode = (String) row[0];
                p.display = (String) row[1];
                check(
                        p.exploredOpacity() == (float) row[2],
                        "Discovered compositing matches table");
                check(
                        p.unexploredOpacity() == (float) row[3],
                        "Undiscovered TFC is opaque or absent");
                check(
                        p.displayEnabled() == (boolean) row[4],
                        "Only useful Display controls enabled");
                check(
                        p.maskedCoverage() == ((float) row[2] != (float) row[3]),
                        "Stencil separates unequal alpha passes");
            }
        }
        p.mode = "Full map";
        p.display = "TFC Layers Only";
        for (String expected : List.of("Overlay", "Xaero Map", "TFC Layers Only")) {
            p.cycleDisplay();
            check(p.effectiveDisplay().equals(expected), "Full Map exposes all three displays");
        }
        p.mode = "Explored only";
        p.display = "Xaero Map";
        check(
                p.effectiveDisplay().equals("TFC Layers Only"),
                "Explored Only cannot select the redundant Xaero-only view");
        p.cycleDisplay();
        check(p.effectiveDisplay().equals("Overlay"), "Explored Only skips Xaero Map");
        p.cycleDisplay();
        check(
                p.effectiveDisplay().equals("TFC Layers Only"),
                "Explored Only cycles two useful displays");
        p.atlasEnabled = false;
        check(
                p.exploredOpacity() == 0 && p.unexploredOpacity() == 0 && !p.overlayVisible(),
                "The independent Atlas switch removes all Atlas rendering");
        p.opacity = .65;
        p.validate();
        check(p.opacity == .5, "Legacy freeform opacity migrates to 50 percent");
        System.out.println(
                "PASS: 160,000 explored/unknown pixel checks, viewport holes and coverage choices");
    }

    private static boolean contains(TerrainCoverage.Rect r, int x, int z) {
        return x >= r.x0() && x < r.x1() && z >= r.z0() && z < r.z1();
    }
}
