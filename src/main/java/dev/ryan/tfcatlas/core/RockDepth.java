package dev.ryan.tfcatlas.core;

import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Search a predicted host-rock column, keeping above-ground positions out of results. */
public final class RockDepth {
    public record Match(int y, String rock) {}

    public static Match first(
            int min,
            int max,
            int surfaceY,
            IntFunction<String> sample,
            Predicate<String> accepts,
            BooleanSupplier cancelled) {
        for (int y = Math.min(max, surfaceY - 1); y >= Math.max(-63, min); y--) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            String rock = sample.apply(y);
            if (accepts.test(rock)) {
                return new Match(y, rock);
            }
        }
        return null;
    }
}
