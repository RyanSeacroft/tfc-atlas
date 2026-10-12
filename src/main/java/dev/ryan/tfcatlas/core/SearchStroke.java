package dev.ryan.tfcatlas.core;

/** Thin opaque boundaries, capped below a map pixel instead of expanding at distant zooms. */
public final class SearchStroke {
    public static double width(int resolution, double zoom, boolean shadow) {
        if (shadow || resolution <= 0 || !Double.isFinite(zoom) || zoom <= 0) {
            return 0;
        }
        // Keep distant outlines visible, but narrower than a one-pixel river. At closer zooms
        // the cap represents an increasingly small part of each world-space sample.
        return Math.min(.75, Math.max(.35, resolution * zoom * .2));
    }

    private SearchStroke() {}
}
