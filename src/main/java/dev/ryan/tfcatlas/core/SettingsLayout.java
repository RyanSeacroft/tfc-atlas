package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;

/** Single-page settings: compact columns instead of hidden pages at small GUI sizes. */
public final class SettingsLayout {
    public record Field(HudLayout.Box label, HudLayout.Box input) {}

    public static List<Field> fields(int width, int height, int count) {
        return fields(width, height, count, 52);
    }

    public static List<Field> fields(int width, int height, int count, int top) {
        int content = Math.min(width - 24, 620),
                left = (width - content) / 2,
                columns = count >= 10 ? 3 : 2;
        int rows = (count + columns - 1) / columns,
                pitch = Math.min(37, (height - top - 74) / Math.max(1, rows));
        int h = Math.min(16, pitch - 10);
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = left + (i % columns) * content / columns,
                    y = top + (i / columns) * pitch,
                    w = content / columns - 8;
            fields.add(new Field(new HudLayout.Box(x, y, w, 8), new HudLayout.Box(x, y + 9, w, h)));
        }
        return List.copyOf(fields);
    }
}
