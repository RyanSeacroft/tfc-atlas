package dev.ryan.tfcatlas.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.OptionalLong;

/** Invert TFC 3.2's synced climate seed and verify against the login biome seed hash. */
public final class SeedRecovery {
    private static final long A = 6364136223846793005L,
            B = 1442695040888963407L,
            SALT = 719283741234L;

    public static long climateSeed(long seed) {
        return seed * (seed * A + B) + SALT;
    }

    public static long[] candidates(long climateSeed) {
        if (((climateSeed - SALT) & 1) != 0) {
            return new long[0];
        }
        long[] roots = {0, 1};
        // Once parity is chosen, the odd derivative fixes one bit per iteration.
        // Java overflow supplies arithmetic modulo 2^64, including the sign bit.
        for (int n = 0; n < roots.length; n++) {
            for (int bit = 1; bit < 64; bit++) {
                long mask = 1L << bit;
                if (((climateSeed(roots[n]) ^ climateSeed) & mask) != 0) {
                    roots[n] |= mask;
                }
            }
        }
        return roots;
    }

    public static long biomeHash(long seed) {
        try {
            byte[] bytes =
                    ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
            return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getLong();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static OptionalLong recover(long climateSeed, long biomeHash) {
        Long match = null;
        for (long seed : candidates(climateSeed)) {
            if (climateSeed(seed) == climateSeed && biomeHash(seed) == biomeHash) {
                if (match != null) {
                    return OptionalLong.empty();
                }
                match = seed;
            }
        }
        return match == null ? OptionalLong.empty() : OptionalLong.of(match);
    }
}
