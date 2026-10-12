package dev.ryan.tfcatlas.core;

/** A loaded-preset notice holds for two seconds, then gently fades. */
public final class PresetNotice {
    public static int alpha(long elapsed) {
        if (elapsed < 0 || elapsed >= 2600) {
            return 0;
        }
        int alpha = elapsed <= 2000 ? 255 : (int) Math.round(255 * (2600 - elapsed) / 600.);
        // Minecraft treats alpha values below four as an unspecified (opaque) colour.
        return alpha < 4 ? 0 : alpha;
    }

    private PresetNotice() {}
}
