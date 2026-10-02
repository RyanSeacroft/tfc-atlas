package dev.ryan.tfcatlas.core;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.dries007.tfc.util.climate.OverworldClimateModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.world.level.biome.BiomeManager;

public final class SeedRecoveryTest {
    public static void run() {
        List<Long> seeds =
                new ArrayList<>(List.of(0L, 1L, -1L, 2L, -2L, Long.MIN_VALUE, Long.MAX_VALUE));
        Random random = new Random(49127);
        for (int i = 0; i < 10000; i++) {
            seeds.add(random.nextLong());
        }
        for (long seed : seeds) {
            long climate = LinearCongruentialGenerator.next(seed, 719283741234L);
            long hash = BiomeManager.obfuscateSeed(seed);
            if (SeedRecovery.biomeHash(seed) != hash) {
                throw new AssertionError("Minecraft hash encoding mismatch");
            }
            long[] candidates = SeedRecovery.candidates(climate);
            if (candidates.length != 2
                    || candidates[0] == candidates[1]
                    || Arrays.stream(candidates).noneMatch(s -> s == seed)) {
                throw new AssertionError("Missing original seed");
            }
            for (long candidate : candidates) {
                if (LinearCongruentialGenerator.next(candidate, 719283741234L) != climate) {
                    throw new AssertionError("Wrong climate inverse");
                }
            }
            var recovered = SeedRecovery.recover(climate, hash);
            if (recovered.isEmpty() || recovered.getAsLong() != seed) {
                throw new AssertionError("Verified recovery mismatch");
            }
            if (SeedRecovery.recover(climate, hash ^ 123).isPresent()) {
                throw new AssertionError("Unverified seed accepted");
            }
        }
        if (SeedRecovery.recover(719283741235L, 0).isPresent()) {
            throw new AssertionError("Impossible climate value accepted");
        }
        for (long seed : new long[] {0, -42, Long.MIN_VALUE}) {

            FriendlyByteBuf incoming = new FriendlyByteBuf(Unpooled.buffer()),
                    outgoing = new FriendlyByteBuf(Unpooled.buffer());
            try {
                incoming.writeVarLong(LinearCongruentialGenerator.next(seed, 719283741234L));
                incoming.writeFloat(12000);
                var model = OverworldClimateModel.STREAM_CODEC.decode(incoming);
                OverworldClimateModel.STREAM_CODEC.encode(outgoing, model);
                long climateSeed = outgoing.readVarLong();
                if (outgoing.readFloat() != 12000 || outgoing.isReadable()) {
                    throw new AssertionError("Unexpected TFC climate payload");
                }
                if (SeedRecovery.recover(climateSeed, BiomeManager.obfuscateSeed(seed))
                                .orElseThrow()
                        != seed) {
                    throw new AssertionError("TFC climate round trip failed");
                }
            } finally {
                incoming.release();
                outgoing.release();
            }
        }
        System.out.println(
                "PASS: 10,007 seed round trips against Minecraft's LCG and seed hash; invalid hashes and climate values rejected");
        System.out.println(
                "PASS: TFC climate receive/serialize round trips, including negative seeds and custom temperature scale");
    }
}
