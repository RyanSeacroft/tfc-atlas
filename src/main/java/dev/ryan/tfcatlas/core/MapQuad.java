package dev.ryan.tfcatlas.core;

/** Adjacent page vertices project the same world edge, avoiding float-rounding seams. */
public record MapQuad(float left, float top, float right, float bottom) {
    public static MapQuad project(
            int x,
            int z,
            int span,
            double focusX,
            double focusZ,
            double scale,
            double centerX,
            double centerZ) {
        return new MapQuad(
                (float) (centerX + (x - focusX) * scale),
                (float) (centerZ + (z - focusZ) * scale),
                (float) (centerX + ((long) x + span - focusX) * scale),
                (float) (centerZ + ((long) z + span - focusZ) * scale));
    }
}
