package dev.ryan.tfcatlas.core;

/** Bounded pixel scrolling shared by lists and multiline search fields. */
public final class SmoothScroll {
    private double position, target, maximum;

    public double position() {
        return position;
    }

    public double maximum() {
        return maximum;
    }

    public void range(double content, double viewport) {
        maximum = Math.max(0, content - viewport);
        position = clamp(position);
        target = clamp(target);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(maximum, value));
    }

    public void wheel(double amount) {
        target = clamp(target - amount * 28);
    }

    public void move(double value) {
        position = target = clamp(value);
    }

    public void tick(double seconds) {
        position += (target - position) * (1 - Math.exp(-18 * Math.max(0, Math.min(.1, seconds))));
        if (Math.abs(position - target) < .05) {
            position = target;
        }
    }

    public void reveal(double top, double bottom, double viewport) {
        if (top < target) {
            target = clamp(top);
        }
        if (bottom > target + viewport) {
            target = clamp(bottom - viewport);
        }
    }
}
