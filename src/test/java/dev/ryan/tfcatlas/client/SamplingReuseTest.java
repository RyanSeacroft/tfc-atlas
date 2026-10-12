package dev.ryan.tfcatlas.client;

import java.util.concurrent.atomic.AtomicInteger;
import net.dries007.tfc.world.biome.BiomeBlendType;

/** Count real raw-source calls for a full fine tile; accuracy is also checked against stock TFC. */
public final class SamplingReuseTest {
    public static void run() {
        int[] resolutions = {8, 16, 32}, previousReads = {106353, 171153, 165888};
        for (int i = 0; i < resolutions.length; i++) {
            AtomicInteger reads = new AtomicInteger();
            var weights =
                    new HeightWeights<BiomeBlendType>(
                            (x, z) -> {
                                reads.incrementAndGet();
                                return BiomeBlendType.values()[
                                        Math.floorMod(
                                                Math.floorDiv(x, 29) * 7
                                                        + Math.floorDiv(z, 43) * 13,
                                                BiomeBlendType.SIZE)];
                            },
                            value -> value);
            int resolution = resolutions[i];
            for (int z = 0; z < 32; z++) {
                for (int x = 0; x < 32; x++) {
                    weights.point(x * resolution + resolution / 2, z * resolution + resolution / 2);
                }
            }
            if (reads.get() >= previousReads[i] * .5) {
                throw new AssertionError(
                        "Exact raw-sample reuse must reduce redundant calls at " + resolution);
            }
            System.out.println(
                    "Exact biome-source calls at "
                            + resolution
                            + " blocks: "
                            + reads.get()
                            + " (previous "
                            + previousReads[i]
                            + ")");
        }
    }

    private SamplingReuseTest() {}
}
