package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.ScrollbarGeometry;
import dev.ryan.tfcatlas.core.SmoothScroll;

/** Exercises capture, resize and tiny-key edge cases without a game window. */
public final class KeyScrollbarTest {
    private static void check(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void run() {
        for (int height = 0; height < 400; height++) {
            for (int content : new int[] {0, 1, 11, 300, 1000000}) {
                var scroll = new SmoothScroll();
                scroll.range(content, height);
                for (double position : new double[] {0, scroll.maximum() / 2, scroll.maximum()}) {
                    var geometry =
                            ScrollbarGeometry.of(height, content, 18, position, scroll.maximum());
                    check(
                            geometry.thumb() >= 0 && geometry.thumb() <= height,
                            "Thumb is inside its track");
                    check(
                            geometry.offset() >= 0
                                    && geometry.offset() + geometry.thumb() <= height,
                            "Thumb cannot spill outside track at either end");
                    if (height >= 4 && scroll.maximum() > 0) {
                        check(
                                geometry.thumb() >= 1
                                        && geometry.travel() >= 1
                                        && geometry.offset() >= 1
                                        && geometry.offset() + geometry.thumb() <= height - 1,
                                "Tiny overflowing keys still have a visible thumb and travel");
                    }
                }
            }
        }
        var key = new MapKeyView();
        key.layout("Rocks top", 100, 50, 6, 11, 200, 1);
        check(
                !key.wheel(99, 55, -1) && !key.wheel(106, 55, -1),
                "Wheel outside the scrollbar passes through to the map");
        check(key.wheel(101, 55, -.25), "Fractional wheel input is captured over the scrollbar");
        check(key.click(102, 59), "Track clicks start a drag");
        check(
                key.drag(9999) && key.offset() == 189,
                "Captured dragging outside the track reaches the last entry");
        check(key.drag(-9999) && key.offset() == 0, "Captured dragging reaches the first entry");
        check(key.release() && !key.drag(60), "Release ends capture");
        check(key.click(102, 59), "Small-key thumb can be grabbed again");
        key.layout("Rocks bottom", 100, 50, 6, 11, 200, 1);
        check(
                key.offset() == 0 && !key.drag(60),
                "Changing rock layer resets scroll and releases capture");
        key.click(102, 59);
        key.drag(9999);
        key.layout("Rocks bottom", 100, 50, 6, 30, 200, 1);
        check(key.offset() == 170, "Enlarging a key clamps its scroll to the new bottom");
        key.layout("Rocks bottom", 100, 50, 6, 240, 200, 1);
        check(
                key.offset() == 0 && !key.wheel(102, 59, -1) && !key.drag(60),
                "A key that fits all rows stops capturing input");
        for (int height : new int[] {0, 1, 2, 3}) {
            key.layout("Rocks bottom", 100, 50, 6, height, 200, 1);
            check(!key.click(102, 50), "Degenerate tracks never capture an unusable drag");
        }
        key.layout("Rocks bottom", 100, 50, 6, 11, 200, 1);
        key.click(102, 59);
        key.hide();
        check(
                !key.click(102, 59) && !key.wheel(102, 59, -1) && !key.drag(60),
                "Hidden keys release capture and cannot intercept map input");
        for (float scale : new float[] {.25f, .5f, .75f, 1, 1.5f}) {
            key.layout("Biomes", 100, 50, Math.max(3, (int) Math.ceil(12 * scale)), 4, 200, scale);
            check(key.click(101, 51), "Scaled tiny tracks are still clickable");
            key.drag(9999);
            check(
                    Math.abs(key.offset() - (200 - 4 / (double) scale)) < .001,
                    "Scaled dragging reaches the last row without overscrolling");
            key.release();
            key.layout("Biomes", 100, 50, 6, 20, 0, scale);
            check(
                    key.offset() == 0 && !key.wheel(101, 51, -1),
                    "An empty or fully visible key never steals map zoom");
        }
        System.out.println(
                "PASS key scrollbar geometry, map input, dragging, resizing and layer changes");
    }
}
