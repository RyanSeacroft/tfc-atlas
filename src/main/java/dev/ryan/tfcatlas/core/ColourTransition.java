package dev.ryan.tfcatlas.core;

/** Crossfade inside one opaque texture, so map opacity and the terrain stencil stay exact. */
public final class ColourTransition {
    public static final long DURATION_MS = 250;

    public static int blend(int from, int to, long elapsed) {
        if ((from >>> 24) == 0 || elapsed >= DURATION_MS) {
            return to;
        }
        int t = (int) Math.max(0, elapsed), s = (int) DURATION_MS - t;
        int r = ((from & 255) * s + (to & 255) * t) / (int) DURATION_MS;
        int g = (((from >>> 8) & 255) * s + ((to >>> 8) & 255) * t) / (int) DURATION_MS;
        int b = (((from >>> 16) & 255) * s + ((to >>> 16) & 255) * t) / (int) DURATION_MS;
        return 0xff000000 | (b << 16) | (g << 8) | r;
    }
}
