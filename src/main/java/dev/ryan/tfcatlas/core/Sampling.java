package dev.ryan.tfcatlas.core;

/** Bounded adaptive detail, in world blocks. */
public final class Sampling {
    public static final int MAX_MAP_STEP = 256 / Tile.GRID;
    public static final int MAX_SEARCH_RESOLUTION = 512, MAX_SEARCH_RADIUS = 262_144;

    public static int mapStep(double scale, int width, int height) {
        return mapStep(scale, width, height, false);
    }

    public static int mapStep(double scale, int width, int height, boolean rapidZoomOut) {
        if (scale >= .5) {
            return 1;
        }
        if (scale >= .25) {
            return 2;
        }
        if (scale < 1. / 1024) {
            return 256 / Tile.GRID;
        }
        if (scale < 1. / 256) {
            return 128 / Tile.GRID;
        }
        if (scale < 1. / 64) {
            return 64 / Tile.GRID;
        }
        return 32 / Tile.GRID;
    }

    public static int stableMapStep(double scale, int width, int height, int previous) {
        int next = mapStep(scale, width, height);
        // Leave the established close zoom alone; avoid toggling wide levels at an edge.
        if (next < 4 || previous < 4 || previous > MAX_MAP_STEP) {
            return next;
        }
        if (next == previous * 2 && scale >= boundary(previous) * .9) {
            return previous;
        }
        if (previous == next * 2 && scale < boundary(next) * 1.1) {
            return previous;
        }
        return next;
    }

    private static double boundary(int step) {
        return step == 4 ? 1. / 64 : step == 8 ? 1. / 256 : 1. / 1024;
    }

    public static boolean backgroundDetail(int step) {
        return step <= 64 / Tile.GRID;
    }

    public static int texturePixels(int step, double scale) {
        int pixels = 1;
        double screen = Tile.SIDE * Tile.GRID * step * scale;
        while (pixels < 32 && pixels < screen) {
            pixels *= 2;
        }
        return Math.max(pixels, step / (32 / Tile.GRID));
    }

    public static long estimatedSamples(int radius, int resolution) {
        long side = 2L * radius / resolution + 1;
        return side * side;
    }

    public static boolean expensiveSearch(String choice, int radius) {
        return !choice.equals("Auto") && radius > searchResolution(choice, radius) * 512;
    }

    public static int searchResolution(String choice, int radius) {
        int value = 16;
        if (choice.equals("Auto")) {
            while (value < MAX_SEARCH_RESOLUTION && radius > value * 512) {
                value *= 2;
            }
        } else {
            value = Integer.parseInt(choice);
        }
        if (value < 16 || value > MAX_SEARCH_RESOLUTION || Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException(
                    "Search detail: Auto, 16, 32, 64, 128, 256 or 512 blocks");
        }
        if (choice.equals("Auto") && radius > MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException(
                    "Auto search radius must not exceed " + MAX_SEARCH_RADIUS);
        }
        return value;
    }
}
