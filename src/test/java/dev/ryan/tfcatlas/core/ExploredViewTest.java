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
        check(p.mode.equals("Unexplored only"), "New profiles keep the existing default");
        p.cycleCoverage();
        check(
                p.mode.equals("Explored only") && p.maskedCoverage(),
                "Explored-only mode follows unexplored-only");
        p.validate();
        check(p.mode.equals("Explored only"), "Explored-only mode survives validation");
        p.cycleCoverage();
        check(!p.overlayVisible(), "Off follows explored-only");
        p.cycleCoverage();
        check(p.mode.equals("Full map") && !p.maskedCoverage(), "Full map needs no mask");
        System.out.println(
                "PASS: 160,000 explored/unknown pixel checks, viewport holes and coverage choices");
    }

    private static boolean contains(TerrainCoverage.Rect r, int x, int z) {
        return x >= r.x0() && x < r.x1() && z >= r.z0() && z < r.z1();
    }
}
