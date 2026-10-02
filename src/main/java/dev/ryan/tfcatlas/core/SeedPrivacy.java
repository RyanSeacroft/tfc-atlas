package dev.ryan.tfcatlas.core;

/** Shared UI/export policy; the generation seed itself remains available to prediction. */
public final class SeedPrivacy {
    public static String input(String seed, boolean multiplayer) {
        return multiplayer ? "" : seed;
    }

    public static String exportValue(long seed, boolean multiplayer) {
        return multiplayer ? "Hidden" : Long.toString(seed);
    }

    public static String fileLabel(long seed, boolean multiplayer) {
        return multiplayer ? "map" : Long.toString(seed);
    }
}
