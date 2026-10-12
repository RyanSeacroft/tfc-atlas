package dev.ryan.tfcatlas.core;

/** One-pixel end gutters remain visible at both ends, including on tiny tracks. */
public record ScrollbarGeometry(int thumb, int travel, int offset) {
    public static ScrollbarGeometry of(
            int height, double content, int minimumThumb, double position, double maximum) {
        height = Math.max(0, height);
        if (maximum <= 0 || height < 4) {
            return new ScrollbarGeometry(0, 0, 0);
        }
        int inside = height - 2;
        int minimum = Math.max(1, Math.min(minimumThumb, inside / 3));
        int thumb =
                Math.min(
                        inside - 1,
                        Math.max(
                                minimum,
                                (int) ((double) inside * height / Math.max(height, content))));
        int travel = inside - thumb;
        int offset = 1 + (int) Math.round(travel * Math.max(0, Math.min(1, position / maximum)));
        return new ScrollbarGeometry(thumb, travel, offset);
    }
}
