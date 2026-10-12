package dev.ryan.tfcatlas.core;

/** Default proportions measured from the map recording; positions use GUI coordinates. */
public final class DefaultHud {
    public static final double KEY_SCALE = .5, INFO_SCALE = .4, SEARCH_SCALE = .45;
    public static final int INFO_WIDTH = 296, INFO_HEIGHT = 120, SEARCH_WIDTH = 264;
    public static final int GAP = 3, LONG_KEY_HEIGHT = 104;

    public static double keyScale(String layer) {
        return layer.equals("CLIMATE_ZONES") ? .48 : KEY_SCALE;
    }

    public static int keyHeight(String layer) {
        return switch (layer) {
            case "ROCKS", "BIOMES", "CLIMATE_ZONES" -> LONG_KEY_HEIGHT;
            default -> 0;
        };
    }

    private DefaultHud() {}
}
