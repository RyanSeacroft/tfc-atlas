package dev.ryan.tfcatlas.core;

/** One TFC map sample. Altitude and inland height are generator indices, not block Y. */
public record Cell(
        String rock,
        String biome,
        int rockType,
        float rain,
        float temperature,
        int altitude,
        int inland,
        int oceanDistance,
        int flags,
        String middleRock,
        String bottomRock) {
    public Cell(
            String rock,
            String biome,
            int rockType,
            float rain,
            float temperature,
            int altitude,
            int inland,
            int oceanDistance,
            int flags) {
        this(
                rock,
                biome,
                rockType,
                rain,
                temperature,
                altitude,
                inland,
                oceanDistance,
                flags,
                rock,
                rock);
    }

    public String rock(RockLayer layer) {
        return switch (layer) {
            case TOP -> rock;
            case MIDDLE -> middleRock;
            case BOTTOM -> bottomRock;
        };
    }

    public Cell withRock(String value) {
        return new Cell(
                value,
                biome,
                rockType,
                rain,
                temperature,
                altitude,
                inland,
                oceanDistance,
                flags,
                middleRock,
                bottomRock);
    }

    public boolean land() {
        return (flags & 1) != 0;
    }

    public boolean river() {
        return (flags & 2) != 0;
    }

    public boolean lake() {
        return (flags & 4) != 0;
    }

    public boolean mountain() {
        return (flags & 8) != 0;
    }

    public String typeName() {
        return TYPE_NAMES[Math.floorMod(rockType, 4)];
    }

    public static final String[] TYPE_NAMES = {"Oceanic", "Volcanic", "Land", "Uplift"};

    public static String label(String id) {
        String s =
                id.substring(Math.max(id.lastIndexOf(':'), id.lastIndexOf('/')) + 1)
                        .replace('_', ' ');
        return s.isEmpty() ? "Unknown" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
