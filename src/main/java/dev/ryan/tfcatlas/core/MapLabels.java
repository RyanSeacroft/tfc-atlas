package dev.ryan.tfcatlas.core;

/** Names may span adjacent matching native cells, but never a boundary or missing data. */
public final class MapLabels {
    public static boolean visible(String mode, Layer layer, double pixelsPerBlock, int sampleStep) {
        return !"Off".equals(mode)
                && (layer == Layer.CLIMATE_ZONES
                        ? pixelsPerBlock > 0
                        : (layer == Layer.ROCKS || layer == Layer.BIOMES)
                                && sampleStep <= 8
                                && pixelsPerBlock * 128 >= 12);
    }

    public static String text(Layer layer, Cell cell) {
        return switch (layer) {
            case ROCKS -> Cell.label(cell.rock());
            case BIOMES -> Cell.label(cell.biome());
            case CLIMATE_ZONES -> ClimateZones.label(cell.climateZone());
            default -> "";
        };
    }

    public static String name(Layer layer, String id) {
        return layer == Layer.CLIMATE_ZONES ? ClimateZones.label(id) : Cell.label(id);
    }

    public static String id(Layer layer, Cell c, RockLayer rocks, boolean continents) {
        if (continents && layer.continentFill() && !c.land()) {
            return null;
        }
        return switch (layer) {
            case ROCKS -> c.rock(rocks);
            case BIOMES -> c.biome();
            case CLIMATE_ZONES -> c.climateZone();
            default -> null;
        };
    }

    public static int cellCentre(int cell) {
        return cellCentre(cell, 1);
    }

    public static int cellCentre(int cell, int step) {
        return cell * Tile.GRID * step + Tile.GRID * step / 2;
    }

    public static boolean fits(int textWidth, double uiScale, double pixelsPerBlock) {
        return fitsCell(textWidth, uiScale, pixelsPerBlock, Tile.GRID);
    }

    public static boolean fitsCell(
            int textWidth, double uiScale, double pixelsPerBlock, int resolution) {
        return textWidth * uiScale + 8 <= resolution * pixelsPerBlock;
    }

    public static boolean fitsRegion(
            Layer layer,
            Cell anchor,
            int textWidth,
            double uiScale,
            double pixelsPerBlock,
            int gx,
            int gz,
            java.util.function.BiFunction<Integer, Integer, Cell> cells) {
        return fitsRegion(layer, anchor, textWidth, uiScale, pixelsPerBlock, gx, gz, 1, cells);
    }

    public static boolean fitsRegion(
            Layer layer,
            Cell anchor,
            int textWidth,
            double uiScale,
            double pixelsPerBlock,
            int gx,
            int gz,
            int step,
            java.util.function.BiFunction<Integer, Integer, Cell> cells) {
        int spacing = Tile.GRID * step;
        if (anchor == null
                || pixelsPerBlock <= 0
                || (layer != Layer.ROCKS && layer != Layer.BIOMES)) {
            return false;
        }
        double halfWidth = (textWidth + 8) * uiScale / (2 * pixelsPerBlock),
                halfHeight = 7 * uiScale / pixelsPerBlock;
        int minX = (int) Math.floor((cellCentre(gx, step) - halfWidth) / spacing),
                maxX = (int) Math.floor((cellCentre(gx, step) + halfWidth) / spacing);
        int minZ = (int) Math.floor((cellCentre(gz, step) - halfHeight) / spacing),
                maxZ = (int) Math.floor((cellCentre(gz, step) + halfHeight) / spacing);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                Cell c = cells.apply(x, z);
                if (c == null) {
                    return false;
                }
                if (layer == Layer.ROCKS
                        ? !anchor.rock().equals(c.rock())
                        : !anchor.biome().equals(c.biome())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int interval(double pixelsPerBlock, int spacing) {
        return interval(pixelsPerBlock, spacing, 1);
    }

    public static int interval(double pixelsPerBlock, int spacing, int step) {
        int n = 1;
        while (n * Tile.GRID * step * pixelsPerBlock < spacing) {
            n *= 2;
        }
        return n;
    }
}
