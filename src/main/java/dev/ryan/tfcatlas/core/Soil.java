package dev.ryan.tfcatlas.core;

/** Regional soil predictions, not measurements of placed blocks or a farm's current nutrients. */
public enum Soil {
    UNKNOWN("Unknown", 0, 0x777777),
    BARE("Sparse soil / bare ground", 0, 0xA8A399),
    ENTISOL("Entisol", 0, 0xBC9C72),
    ANDISOL("Andisol", 10, 0x8B6BAD),
    FLUVISOL("Fluvisol", 10, 0x59A8AC),
    ALFISOL("Alfisol", 10, 0xCAAB4F),
    MOLLISOL("Mollisol", 20, 0x548F54),
    PODZOL("Podzol", -10, 0x7C97B3),
    ARIDISOL("Aridisol", -10, 0xDEBE80),
    OXISOL("Oxisol", -20, 0xC16846);

    public final String label;
    public final int nutrientBonus;
    public final int colour;

    Soil(String label, int nutrientBonus, int colour) {
        this.label = label;
        this.nutrientBonus = nutrientBonus;
        this.colour = colour;
    }

    public String fertility() {
        return this == UNKNOWN || this == BARE
                ? ""
                : nutrientBonus == 0
                        ? "standard nutrient gain"
                        : (nutrientBonus > 0 ? "+" : "") + nutrientBonus + "% nutrient gain";
    }

    public static Soil named(String id) {
        for (Soil soil : values()) {
            if (id.equalsIgnoreCase(soil.name())
                    || id.endsWith("/" + soil.name().toLowerCase(java.util.Locale.ROOT))) {
                return soil;
            }
        }
        return UNKNOWN;
    }
}
