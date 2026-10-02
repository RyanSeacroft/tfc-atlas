package dev.ryan.tfcatlas.core;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The first three geological strata, counted downwards from the surface. */
public enum RockLayer {
    TOP("Top", 0xF3C95D),
    MIDDLE("Middle", 0x50C9DB),
    BOTTOM("Bottom", 0xD889E8);
    public final String label;
    public final int colour;

    RockLayer(String label, int colour) {
        this.label = label;
        this.colour = colour;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public RockLayer next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RockLayer of(String value) {
        if (value == null) {
            return TOP;
        }
        return switch (value) {
            case "Middle", "middle", "MIDDLE" -> MIDDLE;
            case "Bottom", "bottom", "BOTTOM" -> BOTTOM;
            default -> TOP;
        };
    }

    public static int mask(String selection) {
        return selection.equals("Dikes")
                ? 8
                : selection.equals("Any layer") ? 7 : of(selection).bit();
    }

    public static String names(int mask) {
        if (mask == 8) {
            return "Dikes";
        }
        return Arrays.stream(values())
                .filter(l -> (mask & l.bit()) != 0)
                .map(l -> l.label)
                .collect(Collectors.joining(" + "));
    }

    /** Overlapping matches get their own swatch rather than hiding a deeper match. */
    public static int colour(int mask) {
        return switch (mask) {
            case 1 -> TOP.colour;
            case 2 -> MIDDLE.colour;
            case 4 -> BOTTOM.colour;
            case 8 -> 0xEC9860;
            default -> 0xF1F1E5;
        };
    }

    public static String matches(Cell cell, int mask) {
        if (mask == 8) {
            return "Dike: " + Cell.label(cell.rock());
        }
        return Arrays.stream(values())
                .filter(l -> (mask & l.bit()) != 0)
                .map(l -> l.label + ": " + Cell.label(cell.rock(l)))
                .collect(Collectors.joining(" · "));
    }
}
