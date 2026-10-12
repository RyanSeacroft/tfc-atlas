package dev.ryan.tfcatlas.client;

import dev.ryan.tfcatlas.core.Tile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Draw existing immutable matches into Xaero's clipped minimap framebuffer, without sampling. */
public final class MinimapSearchOverlay {
    private MinimapSearchOverlay() {}

    public static void render(
            GuiGraphics g,
            Vec3 centre,
            ResourceKey<Level> dimension,
            double sine,
            double cosine,
            double zoom,
            float halfWidth) {
        Profile p = AtlasClient.profile;
        RegionEngine engine = AtlasClient.engine;
        if (!p.minimapHighlights
                || !p.atlasEnabled
                || !p.highlights
                || engine == null
                || !Level.OVERWORLD.equals(dimension)
                || !Double.isFinite(zoom)
                || zoom <= 0
                || !Float.isFinite(halfWidth)
                || halfWidth <= 0) {
            return;
        }
        var overlay = engine.searchOverlay;
        if (overlay.count() == 0) {
            return;
        }
        int resolution = overlay.resolution();
        double radius = halfWidth * Math.sqrt(2) / zoom;
        int minX = (int) Math.floor(centre.x - radius), minZ = (int) Math.floor(centre.z - radius);
        int maxX = (int) Math.ceil(centre.x + radius), maxZ = (int) Math.ceil(centre.z + radius);
        var first = Tile.Key.at(minX, minZ, resolution / Tile.GRID);
        var last = Tile.Key.at(maxX, maxZ, resolution / Tile.GRID);
        int alpha = Math.max(0, Math.min(255, (int) Math.round(p.highlightOpacity * 255)));
        g.pose().pushPose();
        try {
            // Same world-to-map transform as Xaero's element renderer; its framebuffer handles
            // rotation and the round/square minimap crop. Symbols are drawn afterwards.
            g.pose().mulPose(new Quaternionf().rotationZ((float) Math.atan2(cosine, sine)));
            g.pose().scale((float) zoom, (float) zoom, 1);
            g.pose().translate(-centre.x, -centre.z, 0);
            for (int tz = first.z(); tz <= last.z(); tz++) {
                for (int tx = first.x(); tx <= last.x(); tx++) {
                    var key = new Tile.Key(tx, tz, first.step());
                    if (!overlay.keys().contains(key)) {
                        continue;
                    }
                    int startX = Math.max(0, Math.floorDiv(minX - key.blockX(), resolution));
                    int startZ = Math.max(0, Math.floorDiv(minZ - key.blockZ(), resolution));
                    int endX =
                            Math.min(Tile.SIDE - 1, Math.floorDiv(maxX - key.blockX(), resolution));
                    int endZ =
                            Math.min(Tile.SIDE - 1, Math.floorDiv(maxZ - key.blockZ(), resolution));
                    for (int row = startZ; row <= endZ; row++) {
                        for (int col = startX; col <= endX; col++) {
                            int index = col + row * Tile.SIDE;
                            if (!overlay.matches(key, index)) {
                                continue;
                            }
                            int colour = overlay.colour(key, index, p.highlightColor);
                            int end = col;
                            while (end < endX
                                    && overlay.matches(key, end + 1 + row * Tile.SIDE)
                                    && overlay.colour(
                                                    key,
                                                    end + 1 + row * Tile.SIDE,
                                                    p.highlightColor)
                                            == colour) {
                                end++;
                            }
                            int x = key.blockX() + col * resolution,
                                    z = key.blockZ() + row * resolution;
                            g.fill(
                                    x,
                                    z,
                                    key.blockX() + (end + 1) * resolution,
                                    z + resolution,
                                    alpha << 24 | colour & 0xFFFFFF);
                            col = end;
                        }
                    }
                }
            }
            double screenStroke =
                    dev.ryan.tfcatlas.core.SearchStroke.width(resolution, zoom, false);
            if (screenStroke == 0) {
                return;
            }
            double stroke = screenStroke / zoom;
            for (var edge : overlay.edgesIn(minX, minZ, maxX, maxZ)) {
                int colour =
                        0xFF000000
                                | (overlay.layered()
                                        ? dev.ryan.tfcatlas.core.RockLayer.colour(edge.layerMask())
                                        : p.highlightColor);
                g.pose().pushPose();
                g.pose()
                        .translate(
                                Math.min(edge.x0(), edge.x1()) - stroke / 2,
                                Math.min(edge.z0(), edge.z1()) - stroke / 2,
                                0);
                g.pose()
                        .scale(
                                (float) (Math.abs((double) edge.x1() - edge.x0()) + stroke),
                                (float) (Math.abs((double) edge.z1() - edge.z0()) + stroke),
                                1);
                g.fill(0, 0, 1, 1, colour);
                g.pose().popPose();
            }
        } finally {
            g.pose().popPose();
        }
    }
}
