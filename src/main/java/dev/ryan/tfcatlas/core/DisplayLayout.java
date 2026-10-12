package dev.ryan.tfcatlas.core;

import java.util.List;

/** Display sections share their field ordering with the settings labels. */
public final class DisplayLayout {
    public static final List<String> SECTIONS = List.of("Map", "Panels", "Colours");
    private static final List<List<String>> KEYS =
            List.of(
                    List.of(
                            "atlasEnabled",
                            "mode",
                            "display",
                            "opacity",
                            "layer",
                            "climateContinents",
                            "spawn",
                            "mapLabels",
                            "highlights",
                            "minimapHighlights"),
                    List.of("legend", "hover", "showCriteria"),
                    List.of("highlightColor", "highlightOpacity"));

    public static List<String> keys(int section) {
        return KEYS.get(Math.max(0, Math.min(KEYS.size() - 1, section)));
    }

    private DisplayLayout() {}
}
