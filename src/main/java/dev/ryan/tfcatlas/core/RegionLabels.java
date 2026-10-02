package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** One label per connected, visible patch. Anchors follow patch geometry, not a label lattice. */
public final class RegionLabels {
    public record View(double minX, double minZ, double maxX, double maxZ) {}

    public record Label(String id, double x, double z, double width, double height, double area) {
        public boolean overlaps(Label b, double gap) {
            return Math.abs(x - b.x) < (width + b.width) / 2 + gap
                    && Math.abs(z - b.z) < (height + b.height) / 2 + gap;
        }
    }

    /** Only label placement is padded; the actual map stencil remains pixel-exact. */
    public static void excludeExplored(
            String[] names,
            int width,
            int height,
            double x0,
            double z0,
            int spacing,
            List<TerrainCoverage.Rect> coverage,
            double padding) {
        for (var r : coverage) {
            int a = Math.max(0, (int) Math.floor((r.x0() - padding - x0) / spacing)),
                    b =
                            Math.min(
                                    width - 1,
                                    (int)
                                            Math.floor(
                                                    (Math.nextDown(r.x1() + padding) - x0)
                                                            / spacing));
            int c = Math.max(0, (int) Math.floor((r.z0() - padding - z0) / spacing)),
                    d =
                            Math.min(
                                    height - 1,
                                    (int)
                                            Math.floor(
                                                    (Math.nextDown(r.z1() + padding) - z0)
                                                            / spacing));
            for (int z = c; z <= d; z++) {
                for (int x = a; x <= b; x++) {
                    names[x + z * width] = null;
                }
            }
        }
    }

    public static boolean clearOfExplored(
            Label label, List<TerrainCoverage.Rect> coverage, double padding) {
        double left = label.x - label.width / 2 - padding,
                right = label.x + label.width / 2 + padding,
                top = label.z - label.height / 2 - padding,
                bottom = label.z + label.height / 2 + padding;
        for (var r : coverage) {
            if (left < r.x1() && right > r.x0() && top < r.z1() && bottom > r.z0()) {
                return false;
            }
        }
        return true;
    }

    public static List<Label> layout(
            String[] names,
            int width,
            int height,
            double x0,
            double z0,
            int spacing,
            View view,
            ToDoubleFunction<String> textWidth,
            double textHeight,
            double gap) {
        if (names.length != width * height || spacing < 1) {
            throw new IllegalArgumentException("Invalid region raster");
        }
        int[] regions = new int[names.length], queue = new int[names.length];
        int serial = 0;
        for (int i = 0; i < names.length; i++) {
            double left = x0 + (i % width) * spacing, top = z0 + (i / width) * spacing;
            if (left >= view.maxX
                    || left + spacing <= view.minX
                    || top >= view.maxZ
                    || top + spacing <= view.minZ) {
                regions[i] = -1;
            }
        }
        List<Label> result = new ArrayList<>();
        for (int first = 0; first < names.length; first++) {
            if (names[first] == null || regions[first] != 0) {
                continue;
            }
            String name = names[first];
            int head = 0, tail = 1, id = ++serial;
            queue[0] = first;
            regions[first] = id;
            double total = 0, sumX = 0, sumZ = 0;
            while (head < tail) {
                int at = queue[head++], x = at % width, z = at / width;
                double left = Math.max(view.minX, x0 + x * spacing),
                        right = Math.min(view.maxX, x0 + (x + 1) * spacing);
                double top = Math.max(view.minZ, z0 + z * spacing),
                        bottom = Math.min(view.maxZ, z0 + (z + 1) * spacing);
                double area = Math.max(0, right - left) * Math.max(0, bottom - top);
                total += area;
                sumX += (left + right) / 2 * area;
                sumZ += (top + bottom) / 2 * area;
                if (x > 0) {
                    tail = visit(at - 1, id, name, names, regions, queue, tail);
                }
                if (x + 1 < width) {
                    tail = visit(at + 1, id, name, names, regions, queue, tail);
                }
                if (z > 0) {
                    tail = visit(at - width, id, name, names, regions, queue, tail);
                }
                if (z + 1 < height) {
                    tail = visit(at + width, id, name, names, regions, queue, tail);
                }
            }
            if (total == 0) {
                continue;
            }
            double cx = sumX / total,
                    cz = sumZ / total,
                    w = textWidth.applyAsDouble(name),
                    h = textHeight;
            double bestX = cx, bestZ = cz, best = Double.POSITIVE_INFINITY;
            if (fits(cx, cz, w, h, id, regions, width, height, x0, z0, spacing, view)) {
                best = 0;
            } else {
                for (int n = 0; n < tail; n++) {
                    int at = queue[n];
                    double x = x0 + (at % width + .5) * spacing,
                            z = z0 + (at / width + .5) * spacing;
                    double cost = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    if (cost < best
                            && fits(
                                    x, z, w, h, id, regions, width, height, x0, z0, spacing,
                                    view)) {
                        best = cost;
                        bestX = x;
                        bestZ = z;
                    }
                }
            }
            if (Double.isFinite(best)) {
                result.add(new Label(name, bestX, bestZ, w, h, total));
            }
        }
        result.sort(
                Comparator.comparingDouble(Label::area)
                        .reversed()
                        .thenComparing(Label::id)
                        .thenComparingDouble(Label::x)
                        .thenComparingDouble(Label::z));
        List<Label> placed = new ArrayList<>();
        for (Label l : result) {
            if (placed.stream().noneMatch(p -> l.overlaps(p, gap))) {
                placed.add(l);
            }
        }
        return List.copyOf(placed);
    }

    private static int visit(
            int at, int id, String name, String[] names, int[] regions, int[] queue, int tail) {
        if (regions[at] == 0 && name.equals(names[at])) {
            regions[at] = id;
            queue[tail++] = at;
        }
        return tail;
    }

    private static boolean fits(
            double x,
            double z,
            double w,
            double h,
            int id,
            int[] regions,
            int width,
            int height,
            double x0,
            double z0,
            int spacing,
            View view) {
        double left = x - w / 2, right = x + w / 2, top = z - h / 2, bottom = z + h / 2;
        if (left < view.minX || right > view.maxX || top < view.minZ || bottom > view.maxZ) {
            return false;
        }
        int a = (int) Math.floor((left - x0) / spacing),
                b = (int) Math.floor((Math.nextDown(right) - x0) / spacing);
        int c = (int) Math.floor((top - z0) / spacing),
                d = (int) Math.floor((Math.nextDown(bottom) - z0) / spacing);
        if (a < 0 || c < 0 || b >= width || d >= height) {
            return false;
        }
        for (int iz = c; iz <= d; iz++) {
            for (int ix = a; ix <= b; ix++) {
                if (regions[ix + iz * width] != id) {
                    return false;
                }
            }
        }
        return true;
    }
}
