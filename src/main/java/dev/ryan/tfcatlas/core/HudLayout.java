package dev.ryan.tfcatlas.core;

import java.util.List;
import java.util.TreeSet;

/** Screen-space clearances do not shrink with Atlas's font/UI scale. */
public final class HudLayout {
    public record Box(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean overlaps(Box b) {
            return x < b.right() && right() > b.x && y < b.bottom() && bottom() > b.y;
        }

        public Box padded(int n) {
            return new Box(x - n, y - n, width + 2 * n, height + 2 * n);
        }

        public boolean contains(Box b) {
            return b.x >= x && b.y >= y && b.right() <= right() && b.bottom() <= bottom();
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public static Box safeArea(int width, int height) {
        return new Box(32, 40, Math.max(1, width - 64), Math.max(1, height - 48));
    }

    public static double scale(double value) {
        return Double.isFinite(value) ? Math.max(.2, Math.min(1.5, value)) : .5;
    }

    public record Drag(Box start, double scale, double mouseX, double mouseY, boolean resize) {
        public Box move(double x, double y, Box safe) {
            int dx = (int) Math.round(x - mouseX), dy = (int) Math.round(y - mouseY);
            return new Box(
                    Math.max(safe.x, Math.min(safe.right() - start.width, start.x + dx)),
                    Math.max(safe.y, Math.min(safe.bottom() - start.height, start.y + dy)),
                    start.width,
                    start.height);
        }

        public double resizedScale(double x, double y) {
            double ratio =
                    1
                            + ((x - mouseX) * start.width + (y - mouseY) * start.height)
                                    / ((double) start.width * start.width
                                            + (double) start.height * start.height);
            return HudLayout.scale(scale * ratio);
        }
    }

    /** Find the closest free position against actual native-widget bounds. Null means no room. */
    public static Box place(
            Box bounds,
            int width,
            int height,
            int preferredX,
            int preferredY,
            List<Box> obstacles) {
        if (width > bounds.width || height > bounds.height || width <= 0 || height <= 0) {
            return null;
        }
        TreeSet<Integer> xs = new TreeSet<>(), ys = new TreeSet<>();
        xs.add(Math.max(bounds.x, Math.min(bounds.right() - width, preferredX)));
        xs.add(bounds.x);
        xs.add(bounds.right() - width);
        ys.add(Math.max(bounds.y, Math.min(bounds.bottom() - height, preferredY)));
        ys.add(bounds.y);
        ys.add(bounds.bottom() - height);
        for (Box b : obstacles) {
            xs.add(b.x - width - 1);
            xs.add(b.right() + 1);
            ys.add(b.y - height - 1);
            ys.add(b.bottom() + 1);
        }
        Box best = null;
        long cost = Long.MAX_VALUE;
        for (int x : xs) {
            for (int y : ys) {
                Box candidate = new Box(x, y, width, height);
                if (!bounds.contains(candidate)
                        || obstacles.stream().anyMatch(b -> b.overlaps(candidate))) {
                    continue;
                }
                long distance = Math.abs((long) x - preferredX) + Math.abs((long) y - preferredY);
                if (distance < cost) {
                    best = candidate;
                    cost = distance;
                }
            }
        }
        return best;
    }
}
