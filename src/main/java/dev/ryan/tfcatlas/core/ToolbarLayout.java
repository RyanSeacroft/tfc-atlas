package dev.ryan.tfcatlas.core;

/** Text scales; button padding stays in physical GUI pixels so small labels are never cropped. */
public final class ToolbarLayout {
    public record Fit(float scale, int height, int width, int[] widths) {}

    public static Fit fit(int[] textWidths, double preferred, int available) {
        double low = 0, high = preferred;
        for (int i = 0; i < 24; i++) {
            double mid = (low + high) / 2;
            if (measure(textWidths, mid).width() > available) {
                high = mid;
            } else {
                low = mid;
            }
        }
        return measure(textWidths, low);
    }

    private static Fit measure(int[] text, double scale) {
        int height = Math.max(10, (int) Math.ceil(18 * scale)), total = height;
        int[] widths = new int[text.length];
        for (int i = 0; i < text.length; i++) {
            if (text[i] >= 0) {
                widths[i] = (int) Math.ceil(text[i] * scale) + 8;
                total += widths[i] + 3;
            }
        }
        return new Fit((float) scale, height, total, widths);
    }
}
