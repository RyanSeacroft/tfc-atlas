package dev.ryan.tfcatlas.core;

import dev.ryan.tfcatlas.core.HudLayout.Box;

/** Fixed screen-space controls in their own gutter, outside text and panel content. */
public final class HudHandles {
    public static final int SIZE = 7, GAP = 2, GUTTER = 10;

    public static Box footprint(Box box) {
        return new Box(box.x(), box.y(), box.width() + GUTTER, box.height() + GUTTER);
    }

    public static Box corner(Box box) {
        return new Box(box.right() + GAP, box.bottom() + GAP, SIZE, SIZE);
    }

    public static Box width(Box box) {
        return new Box(
                box.right() + GAP, box.y() + Math.max(0, (box.height() - SIZE) / 2), SIZE, SIZE);
    }

    public static Box height(Box box) {
        return new Box(
                box.x() + Math.max(0, (box.width() - SIZE) / 2), box.bottom() + GAP, SIZE, SIZE);
    }
}
