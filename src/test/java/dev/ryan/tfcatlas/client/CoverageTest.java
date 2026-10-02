package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.TerrainCoverage;
import dev.ryan.tfcatlas.core.TerrainCoverage.Rect;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Regression fixtures model Xaero's retained texture metadata, not live chunk objects. */
public final class CoverageTest {
    private static int checks;

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) {
            throw new AssertionError(message);
        }
    }

    private static long area(List<Rect> rects) {
        return rects.stream().mapToLong(r -> (long) (r.x1() - r.x0()) * (r.z1() - r.z0())).sum();
    }

    public static final class Texture {
        final int[] height = new int[4096], top = new int[4096];
        int version = 1, gl = 1, reads;

        Texture() {
            Arrays.fill(height, Short.MAX_VALUE);
            Arrays.fill(top, Short.MAX_VALUE);
        }

        public int getHeight(int x, int z) {
            reads++;
            return height[x + 64 * z];
        }

        public int getTopHeight(int x, int z) {
            reads++;
            return top[x + 64 * z];
        }

        public int getTextureVersion() {
            return version;
        }

        public int getGlColorTexture() {
            return gl;
        }

        void mark(int x0, int z0, int x1, int z1, int value) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    height[x + 64 * z] = value;
                }
            }
            version++;
        }
    }

    public static final class Region {
        final Texture[][] textures = new Texture[8][8];
        Region root = this;
        boolean loaded = true;

        public Region getRootRegion() {
            return root;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public boolean hasTextures() {
            return true;
        }

        public Texture getTexture(int x, int z) {
            return textures[x][z];
        }
    }

    public static final class Processor {
        final Map<String, Region> regions = new HashMap<>();

        public Region getLeveledRegion(int cave, int x, int z, int level) {
            check(cave == Integer.MAX_VALUE, "Surface coverage only");
            return regions.get(x + ":" + z + ":" + level);
        }

        void put(int x, int z, int level, Region region) {
            regions.put(x + ":" + z + ":" + level, region);
        }
    }

    public static void run() throws Exception {
        // Exact arbitrary boundaries, holes, zero/negative heights and top-only data.
        Random random = new Random(416);
        for (int trial = 0; trial < 16; trial++) {
            boolean[] known = new boolean[4096];
            for (int i = 0; i < known.length; i++) {
                known[i] = random.nextBoolean();
            }
            List<Rect> rects =
                    TerrainCoverage.read(
                            (x, z) -> known[x + 64 * z] ? -64 : Short.MAX_VALUE,
                            (x, z) -> Short.MAX_VALUE);
            int[] count = new int[4096];
            for (Rect r : rects) {
                for (int z = r.z0(); z < r.z1(); z++) {
                    for (int x = r.x0(); x < r.x1(); x++) {
                        count[x + 64 * z]++;
                    }
                }
            }
            check(
                    java.util.stream.IntStream.range(0, 4096)
                            .allMatch(i -> count[i] == (known[i] ? 1 : 0)),
                    "No covered unknown pixel, hole or overlap");
        }
        check(
                area(
                                TerrainCoverage.read(
                                        (x, z) -> Short.MAX_VALUE,
                                        (x, z) -> x == 1 && z == 2 ? 0 : Short.MAX_VALUE))
                        == 1,
                "Top height protects terrain too");
        Processor processor = new Processor();
        ExploredMask mask = new ExploredMask();
        check(
                mask.collect(processor, 0, 0, 0, 127, 127).isEmpty(),
                "Unknown regions remain colourable");
        Region leaf = new Region();
        Texture texture = new Texture();
        leaf.textures[0][0] = texture;
        processor.put(0, 0, 0, leaf);
        texture.mark(0, 0, 16, 16, 0);
        leaf.loaded = false;
        List<Rect> rects = mask.collect(processor, 0, 0, 0, 127, 127);
        check(
                area(rects) == 256 && rects.contains(new Rect(0, 0, 16, 16)),
                "Unloaded source chunks keep exact saved coverage, not a 128-block halo");
        int reads = texture.reads;
        mask.collect(processor, 0, 0, 0, 127, 127);
        check(texture.reads == reads, "Unchanged coverage is cached");
        texture.mark(16, 0, 32, 16, -64);
        check(
                area(mask.collect(processor, 0, 0, 0, 127, 127)) == 512,
                "New explored terrain changes in next frame without a timer/budget");
        processor = new Processor();
        leaf = new Region();
        Region root = new Region();
        leaf.root = root;
        processor.put(-1, -1, 0, leaf);
        Texture fallback = new Texture();
        fallback.mark(62, 62, 64, 64, 80);
        root.textures[7][7] = fallback;
        rects = mask.collect(processor, 0, -128, -128, -1, -1);
        check(
                area(rects) == 256 && rects.contains(new Rect(-16, -16, 0, 0)),
                "Root-cache crop and negative world coordinates");
        leaf.textures[7][7] = new Texture();
        check(
                mask.collect(processor, 0, -128, -128, -1, -1).isEmpty(),
                "Current leaf texture takes precedence over stale root");
        leaf.textures[7][7] = null;
        root.loaded = false;
        check(
                mask.collect(processor, 0, -128, -128, -1, -1).isEmpty(),
                "Unavailable root is not rendered/masked");
        for (int level = 1; level <= 3; level++) {
            processor = new Processor();
            Region branch = new Region();
            texture = new Texture();
            branch.textures[0][0] = texture;
            processor.put(0, 0, level, branch);
            int pixel = 1 << level;
            texture.mark(16 / pixel, 32 / pixel, 32 / pixel, 48 / pixel, 12);
            rects = mask.collect(processor, level, 0, 0, 511, 511);
            check(
                    area(rects) == 256 && rects.contains(new Rect(16, 32, 32, 48)),
                    "Overview level " + level + " preserves a single explored chunk boundary");
        }
        Profile p = new Profile();
        check(
                p.toolbarScale == .5 && p.legend && p.mapLabels.equals("Off"),
                "Compact defaults and optional map labels");
        p.toolbarScale = Double.NaN;
        p.mapLabels = "Invalid";
        p.validate();
        check(
                p.toolbarScale == .5 && p.mapLabels.equals("Off"),
                "Damaged display settings recover");
        System.out.println(
                "PASS: "
                        + checks
                        + " coverage/display checks, including 65,536 arbitrary coverage pixels");
    }
}
