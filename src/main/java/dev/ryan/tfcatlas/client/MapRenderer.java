package dev.ryan.tfcatlas.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryan.tfcatlas.core.Cell;
import dev.ryan.tfcatlas.core.Layer;
import dev.ryan.tfcatlas.core.MapLabels;
import dev.ryan.tfcatlas.core.PrecacheArea;
import dev.ryan.tfcatlas.core.RegionLabels;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.Sampling;
import dev.ryan.tfcatlas.core.SearchOverlay;
import dev.ryan.tfcatlas.core.Tile;
import dev.ryan.tfcatlas.core.TileLoadFrontier;
import dev.ryan.tfcatlas.core.TileWindow;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public final class MapRenderer {
    private static final class Texture {
        final DynamicTexture texture;
        final ResourceLocation id;

        Texture(NativeImage image) {
            texture = new DynamicTexture(image);
            texture.setFilter(false, false);
            id = Minecraft.getInstance().getTextureManager().register("tfcatlas", texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    private final MapPages pages = new MapPages();
    private TileWindow window, detailWindow, previewWindow;
    private Iterator<Tile.Key> previewCursor = Collections.emptyIterator();
    private Iterator<Tile.Key> detailCursor = Collections.emptyIterator(),
            cacheCursor = Collections.emptyIterator(),
            inheritCursor = Collections.emptyIterator();
    private int samplingStep = 4;
    private TileLoadFrontier frontier;
    private Layer loadingLayer;
    private int styleHash, searchStyle;
    private final java.util.concurrent.ExecutorService labelWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "TFC Atlas region labels");
                        t.setDaemon(true);
                        return t;
                    });
    private java.util.concurrent.CompletableFuture<List<RegionLabels.Label>> labelFuture;
    private int labelWidth, labelHeight;
    private long labelTime, labelRevision = -1, labelCoverageRevision = -1;
    private double labelX = Double.NaN, labelZ, labelZoom;
    private Layer labelLayer;
    private double labelScale;
    private String labelMode = "";
    private List<RegionLabels.Label> regionLabels = List.of();
    private final SearchOutlines searchOutlines = new SearchOutlines();
    private SearchOverlay snapshot = SearchOverlay.EMPTY;
    private Texture searchTexture;
    private final Map<Tile.Key, Texture> searchPages = new LinkedHashMap<>(64, .75f, true);
    private final ExploredMask explored = new ExploredMask();
    public String notice = "";

    public void clear() {
        if (labelFuture != null) {
            labelFuture.cancel(false);
        }
        labelFuture = null;
        regionLabels = List.of();
        labelRevision = -1;
        labelX = Double.NaN;
        pages.clear();
        window = detailWindow = previewWindow = null;
        previewCursor = Collections.emptyIterator();
        detailCursor = cacheCursor = inheritCursor = Collections.emptyIterator();
        frontier = null;
        loadingLayer = null;
        samplingStep = 4;
        clearSearchTextures();
        searchOutlines.clear();
        snapshot = SearchOverlay.EMPTY;
        explored.clear();
    }

    public void render(GuiGraphics g, XaeroBridge.View v, Profile p, RegionEngine engine) {
        int newStyle = p.mapStyleHash();
        if (styleHash != newStyle) {
            clear();
            styleHash = newStyle;
        }
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int step = Sampling.stableMapStep(v.scale(), width, height, samplingStep);
        samplingStep = step;
        engine.displayStep(step);
        int resolution = Tile.GRID * step;
        boolean backgroundDetail = Sampling.backgroundDetail(step);
        TileWindow next = window(v, width, height, step),
                fineWindow = window(v, width, height, 32 / Tile.GRID),
                preview = window(v, width, height, PrecacheArea.STEP);
        if (!next.equals(window)
                || !fineWindow.equals(detailWindow)
                || !preview.equals(previewWindow)
                || loadingLayer != p.selected()) {
            window = next;
            detailWindow = fineWindow;
            previewWindow = preview;
            loadingLayer = p.selected();
            previewCursor = previewWindow.iterator();
            frontier = new TileLoadFrontier(window);
            inheritCursor = window.iterator();
            detailCursor = detailWindow.iterator();
            cacheCursor = detailWindow.iterator();
            engine.prioritize(window, detailWindow, previewWindow);
        }
        int pixels = MapPages.pixels(window, v.scale()),
                finePixels = MapPages.pixels(detailWindow, v.scale());
        // Carry existing colours across the resolution boundary before new native tiles arrive.
        pages.inheritChildren(window, p.selected(), pixels);
        if (step < Sampling.MAX_MAP_STEP) {
            for (int checked = 0; checked < 128; checked++) {
                if (!inheritCursor.hasNext()) {
                    inheritCursor = window.iterator();
                }
                if (!inheritCursor.hasNext()) {
                    break;
                }
                pages.inherit(inheritCursor.next(), p.selected(), pixels);
                if (checked + 1 >= window.count()) {
                    break;
                }
            }
        }
        boolean needLabelData = MapLabels.visible(p.mapLabels, p.selected(), v.scale(), step);
        List<Tile.Key> batch =
                frontier.next(
                        k ->
                                engine.failed(k)
                                        || pages.ready(k, p.selected(), pixels)
                                                && (!needLabelData || engine.peek(k) != null));
        Set<Tile.Key> readyPrefix = frontier.readyPrefix(k -> engine.peek(k) != null);
        int previewPixels = MapPages.pixels(previewWindow, v.scale()), uploads = 0;
        List<Tile> loaded = new ArrayList<>(engine.loaded());
        loaded.sort(Comparator.comparingDouble(tile -> window.distanceSquared(tile.key())));
        for (Tile tile : loaded) {
            int target =
                    window.contains(tile.key())
                            ? pixels
                            : detailWindow.contains(tile.key())
                                    ? finePixels
                                    : previewWindow.contains(tile.key()) ? previewPixels : 0;
            boolean central =
                    tile.key().step() == step
                            ? readyPrefix.contains(tile.key())
                            : frontier.allowsUpload(tile.key());
            if (target > 0 && central && !pages.ready(tile.key(), p.selected(), target)) {
                pages.add(tile, p, target);
                if (++uploads >= 24) {
                    break;
                }
            }
        }
        pages.preferFine(window, detailWindow, p.selected(), pixels);
        for (Tile.Key key : batch) {
            if (engine.pendingCount() >= 24) {
                break;
            }
            engine.request(key);
        }
        // Restore the all-layer background cache before asking for more fine generation.
        if (step < PrecacheArea.STEP) {
            for (int checked = 0; checked < 128 && engine.pendingCount() < 28; checked++) {
                if (!previewCursor.hasNext()) {
                    previewCursor = previewWindow.iterator();
                }
                if (!previewCursor.hasNext()) {
                    break;
                }
                Tile.Key key = previewCursor.next();
                if (engine.cached(key) && !pages.ready(key, p.selected(), previewPixels)) {
                    engine.request(key);
                }
                if (checked + 1 >= previewWindow.count()) {
                    break;
                }
            }
        }
        // Existing disk detail is read before spending time generating fallback for that area.
        if (backgroundDetail && step > 32 / Tile.GRID) {
            for (int checked = 0; checked < 96 && engine.pendingCount() < 30; checked++) {
                if (!cacheCursor.hasNext()) {
                    cacheCursor = detailWindow.iterator();
                }
                if (!cacheCursor.hasNext()) {
                    break;
                }
                Tile.Key key = cacheCursor.next();
                if (engine.cached(key) && !pages.ready(key, p.selected(), finePixels)) {
                    engine.request(key);
                }
                if (checked + 1 >= detailWindow.count()) {
                    break;
                }
            }
        }
        // A few 32-block jobs remain in the same bounded queue; they cannot flood the foreground.
        if (backgroundDetail && step != 32 / Tile.GRID) {
            for (int checked = 0; checked < 96 && engine.pendingCount() < 32; checked++) {
                if (!detailCursor.hasNext()) {
                    detailCursor = detailWindow.iterator();
                }
                if (!detailCursor.hasNext()) {
                    break;
                }
                Tile.Key key = detailCursor.next();
                if (!pages.ready(key, p.selected(), finePixels)) {
                    engine.request(key);
                }
                if (checked + 1 >= detailWindow.count()) {
                    break;
                }
            }
        }
        engine.prioritize(window, detailWindow, previewWindow);
        boolean mask = p.maskedCoverage();
        notice =
                !backgroundDetail
                        ? resolution + "-block overview"
                        : resolution > 32
                                ? "32-block detail · " + resolution + "-block fallback"
                                : resolution + "-block terrain prediction";
        if (engine.pendingCount() > 0) {
            notice += " · loading…";
        }
        g.flush();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        float[] shaderColour = RenderSystem.getShaderColor().clone();
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        g.pose().pushPose();
        g.pose().last().pose().identity();
        g.pose().last().normal().identity();
        // Xaero's map elements start at -980 relative to its map pose. GUI batches can
        // re-enable depth writes, so all Atlas vertices must also stay behind that pass.
        g.pose().translate(0, 0, -1000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            if (mask) {
                explored.begin(g, v, width, height, false);
            }
            // GuiGraphics ends its stencil-fill batch by clearing the blend state.
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            for (int pass = 0; pass < (mask ? 2 : 1); pass++) {
                float alpha =
                        mask
                                ? (pass == 0 ? p.unexploredOpacity() : p.exploredOpacity())
                                : p.unexploredOpacity();
                if (alpha == 0) {
                    continue;
                }
                if (mask) {
                    explored.select(g, pass == 1);
                }
                RenderSystem.setShaderColor(1, 1, 1, alpha);
                if (step >= 32 / Tile.GRID) {
                    // Wide views need one draw per packed page, rather than thousands of tile
                    // draws.
                    for (var page : pages.visible(window, p.selected())) {
                        drawPage(g, v, page, page.blockX(), page.blockZ(), page.span());
                    }
                } else {
                    for (Tile.Key key : window) {
                        MapPages.Page page = pages.get(key, p.selected());
                        if (page == null || !page.shown(key)) {
                            for (int parentStep = step * 2;
                                    parentStep <= Sampling.MAX_MAP_STEP;
                                    parentStep *= 2) {
                                Tile.Key parent =
                                        Tile.Key.at(key.blockX(), key.blockZ(), parentStep);
                                page = pages.get(parent, p.selected());
                                if (page != null && page.shown(parent)) {
                                    break;
                                }
                                page = null;
                            }
                        }
                        if (page != null) {
                            drawPage(g, v, page, key.blockX(), key.blockZ(), key.span());
                        }
                    }
                }
                g.flush();
            }
            if (mask) {
                explored.select(
                        g,
                        p.exploredOpacity() == 0
                                ? Boolean.FALSE
                                : p.unexploredOpacity() == 0 ? Boolean.TRUE : null);
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);
            SearchOverlay search = engine.searchOverlay;
            mapLabels(g, v, p, engine, width, height, step);
            explored.end(g);
            // Search results describe candidates on both explored and unexplored terrain.
            searchTint(g, v, p, search, width, height);
            if (p.highlights) {
                searchOutline(g, v, p, search, width, height);
            }
            if (p.spawn) {
                int r = engine.settings.spawnDistance();
                int x = engine.settings.spawnCenterX(), z = engine.settings.spawnCenterZ();
                if (p.outline > 0) {
                    outline(g, v, width, height, x - r, z - r, x + r, z + r, 0xFFFFD65A, p.outline);
                }
                int sx = screenX(v, width, x), sy = screenZ(v, height, z);
                g.fill(sx - 4, sy, sx + 5, sy + 1, 0xFFFFD65A);
                g.fill(sx, sy - 4, sx + 1, sy + 5, 0xFFFFD65A);
                var level = Minecraft.getInstance().level;
                if (level != null
                        && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
                    var spawn = level.getSharedSpawnPos();
                    int wx = screenX(v, width, spawn.getX()), wz = screenZ(v, height, spawn.getZ());
                    g.renderOutline(wx - 4, wz - 4, 9, 9, 0xFFFFFFFF);
                    g.drawString(
                            Minecraft.getInstance().font,
                            "World spawn",
                            wx + 6,
                            wz - 4,
                            0xFFFFFFFF);
                }
            }
            if (p.searchCircle && engine.activeQuery != null) {
                searchCircle(g, v, engine, width, height);
            }
            g.flush();
        } catch (ReflectiveOperationException ex) {
            notice = "Coverage unavailable; overlay hidden";
            throw new IllegalStateException("Xaero terrain coverage unavailable", ex);
        } finally {
            explored.end(g);
            g.flush();
            RenderSystem.setShaderColor(
                    shaderColour[0], shaderColour[1], shaderColour[2], shaderColour[3]);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.depthMask(depthWrite);
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            g.pose().popPose();
        }
        pages.trim(window, p.selected());
    }

    private static TileWindow window(XaeroBridge.View v, int width, int height, int step) {
        return TileWindow.visible(v.x(), v.z(), v.scale(), width, height, step);
    }

    private static void drawPage(
            GuiGraphics g, XaeroBridge.View v, MapPages.Page page, int bx, int bz, int span) {
        page.upload();
        float x = (float) (v.centerX() + (bx - v.x()) * v.scale()),
                y = (float) (v.centerZ() + (bz - v.z()) * v.scale());
        float size = (float) (span * v.scale()),
                u = (bx - page.blockX()) / (float) page.span(),
                vv = (bz - page.blockZ()) / (float) page.span(),
                uvSpan = span / (float) page.span();
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, page.id);
        var b =
                Tesselator.getInstance()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var pose = g.pose().last().pose();
        b.addVertex(pose, x, y + size, 0).setUv(u, vv + uvSpan);
        b.addVertex(pose, x + size, y + size, 0).setUv(u + uvSpan, vv + uvSpan);
        b.addVertex(pose, x + size, y, 0).setUv(u + uvSpan, vv);
        b.addVertex(pose, x, y, 0).setUv(u, vv);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private long exclusionRevision = -1;
    private dev.ryan.tfcatlas.core.TerrainCoverage.Rect exclusionBounds;
    private List<dev.ryan.tfcatlas.core.TerrainCoverage.Rect> unknownCoverage = List.of();

    private List<dev.ryan.tfcatlas.core.TerrainCoverage.Rect> labelExclusions(
            XaeroBridge.View v, Profile p, int width, int height)
            throws ReflectiveOperationException {
        if (p.exploredOpacity() > 0 && p.unexploredOpacity() > 0) {
            return List.of();
        }
        var known = explored.coverage(v, width, height);
        if (p.exploredOpacity() == 0) {
            return known;
        }
        var bounds =
                new dev.ryan.tfcatlas.core.TerrainCoverage.Rect(
                        (int) Math.floor(v.x() - v.centerX() / v.scale()),
                        (int) Math.floor(v.z() - v.centerZ() / v.scale()),
                        (int) Math.ceil(v.x() + (width - v.centerX()) / v.scale()),
                        (int) Math.ceil(v.z() + (height - v.centerZ()) / v.scale()));
        if (exclusionRevision != explored.coverageRevision() || !bounds.equals(exclusionBounds)) {
            unknownCoverage =
                    dev.ryan.tfcatlas.core.TerrainCoverage.complement(
                            known, bounds.x0(), bounds.z0(), bounds.x1(), bounds.z1());
            exclusionRevision = explored.coverageRevision();
            exclusionBounds = bounds;
        }
        return unknownCoverage;
    }

    private void mapLabels(
            GuiGraphics g,
            XaeroBridge.View v,
            Profile p,
            RegionEngine e,
            int width,
            int height,
            int step)
            throws ReflectiveOperationException {
        Layer layer = p.selected();
        if (!MapLabels.visible(p.mapLabels, layer, v.scale(), step)) {
            return;
        }
        var coverage = labelExclusions(v, p, width, height);
        long coverageRevision = explored.coverageRevision();
        var font = Minecraft.getInstance().font;
        float scale = (float) p.labelScale;
        long now = System.nanoTime() / 1_000_000;
        if (labelFuture != null && labelFuture.isDone()) {
            if (labelLayer == layer
                    && labelZoom == v.scale()
                    && labelScale == p.labelScale
                    && labelCoverageRevision == coverageRevision
                    && labelMode.equals(p.mode + ":" + p.effectiveDisplay())) {
                try {
                    regionLabels = labelFuture.join();
                } catch (java.util.concurrent.CompletionException
                        | java.util.concurrent.CancellationException ignored) {
                    regionLabels = List.of();
                }
            }
            labelFuture = null;
        }
        if (labelLayer != layer
                || labelZoom != v.scale()
                || labelScale != p.labelScale
                || !labelMode.equals(p.mode + ":" + p.effectiveDisplay())) {
            regionLabels = List.of();
        }
        boolean changed =
                labelWidth != width
                        || labelHeight != height
                        || labelX != v.x()
                        || labelZ != v.z()
                        || labelZoom != v.scale()
                        || labelLayer != layer
                        || labelRevision != e.revision()
                        || labelScale != p.labelScale
                        || labelCoverageRevision != coverageRevision
                        || !labelMode.equals(p.mode + ":" + p.effectiveDisplay());
        if (labelFuture == null
                && changed
                && (now - labelTime >= 100 || labelLayer != layer || labelZoom != v.scale())) {
            int spacing = Tile.GRID * step;
            double left = v.x() - v.centerX() / v.scale(), top = v.z() - v.centerZ() / v.scale();
            double right = left + width / v.scale(), bottom = top + height / v.scale();
            int gx = (int) Math.floor(left / spacing), gz = (int) Math.floor(top / spacing);
            int nx = (int) Math.ceil(right / spacing) - gx,
                    nz = (int) Math.ceil(bottom / spacing) - gz;
            if ((long) nx * nz <= 1_000_000) {
                String[] names = new String[nx * nz];
                for (Tile tile : e.loaded()) {
                    if (tile.key().step() == step) {
                        int ox = tile.key().x() * Tile.SIDE - gx,
                                oz = tile.key().z() * Tile.SIDE - gz;
                        for (int z = Math.max(0, -oz); z < Math.min(Tile.SIDE, nz - oz); z++) {
                            for (int x = Math.max(0, -ox); x < Math.min(Tile.SIDE, nx - ox); x++) {
                                Cell c = tile.cells()[x + z * Tile.SIDE];
                                names[x + ox + (z + oz) * nx] =
                                        MapLabels.id(
                                                layer,
                                                c,
                                                p.selectedRockLayer(),
                                                p.climateContinents);
                            }
                        }
                    }
                }
                double pad = 3 / v.scale();
                Map<String, Double> textWidths = new HashMap<>();
                for (String id : names) {
                    if (id != null && !textWidths.containsKey(id)) {
                        textWidths.put(
                                id,
                                (font.width(MapLabels.name(layer, id)) + 8) * scale / v.scale());
                    }
                }
                var clip = new RegionLabels.View(left + pad, top + pad, right - pad, bottom - pad);
                labelFuture =
                        java.util.concurrent.CompletableFuture.supplyAsync(
                                () -> {
                                    RegionLabels.excludeExplored(
                                            names,
                                            nx,
                                            nz,
                                            gx * (double) spacing,
                                            gz * (double) spacing,
                                            spacing,
                                            coverage,
                                            1 / v.scale());
                                    return RegionLabels.layout(
                                            names,
                                            nx,
                                            nz,
                                            gx * (double) spacing,
                                            gz * (double) spacing,
                                            spacing,
                                            clip,
                                            textWidths::get,
                                            14 * scale / v.scale(),
                                            2 / v.scale());
                                },
                                labelWorker);
            } else {
                regionLabels = List.of();
            }
            labelWidth = width;
            labelHeight = height;
            labelTime = now;
            labelX = v.x();
            labelZ = v.z();
            labelZoom = v.scale();
            labelLayer = layer;
            labelScale = p.labelScale;
            labelRevision = e.revision();
            labelCoverageRevision = coverageRevision;
            labelMode = p.mode + ":" + p.effectiveDisplay();
        }
        for (var label : regionLabels) {
            // Coverage can change while the worker is placing labels. Never draw a stale clipped
            // word.
            if (!RegionLabels.clearOfExplored(label, coverage, 1 / v.scale())) {
                continue;
            }
            String text = MapLabels.name(layer, label.id());
            int w = font.width(text);
            double x = v.centerX() + (label.x() - v.x()) * v.scale(),
                    y = v.centerZ() + (label.z() - v.z()) * v.scale();
            if (x - w * scale / 2 < 0 || x + w * scale / 2 > width || y < 6 || y > height - 6) {
                continue;
            }
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            g.fill(-w / 2 - 3, -6, w - w / 2 + 3, 6, 0x80161B1D);
            g.drawCenteredString(font, text, 0, -4, 0xFFE9E2CF);
            g.pose().popPose();
        }
    }

    public static int screenX(XaeroBridge.View v, int w, double x) {
        return (int) Math.round(v.centerX() + (x - v.x()) * v.scale());
    }

    public static int screenZ(XaeroBridge.View v, int h, double z) {
        return (int) Math.round(v.centerZ() + (z - v.z()) * v.scale());
    }

    private static void outline(
            GuiGraphics g,
            XaeroBridge.View v,
            int w,
            int h,
            int x0,
            int z0,
            int x1,
            int z1,
            int color,
            int line) {
        int a = Math.max(-32768, Math.min(32768, screenX(v, w, x0))),
                b = Math.max(-32768, Math.min(32768, screenZ(v, h, z0))),
                c = Math.max(-32768, Math.min(32768, screenX(v, w, x1))),
                d = Math.max(-32768, Math.min(32768, screenZ(v, h, z1)));
        g.fill(a, b, c, b + line, color);
        g.fill(a, d - line, c, d, color);
        g.fill(a, b, a + line, d, color);
        g.fill(c - line, b, c, d, color);
    }

    private void clearSearchTextures() {
        if (searchTexture != null) {
            searchTexture.close();
        }
        searchTexture = null;
        searchPages.values().forEach(Texture::close);
        searchPages.clear();
    }

    private void searchTint(
            GuiGraphics g,
            XaeroBridge.View v,
            Profile p,
            SearchOverlay next,
            int width,
            int height) {
        int style = Objects.hash(p.highlightColor, p.highlightOpacity);
        if (next.series() != snapshot.series() || style != searchStyle) {
            clearSearchTextures();
            searchStyle = style;
        } else if (next != snapshot) {
            if (searchTexture != null) {
                searchTexture.close();
            }
            searchTexture = null;
            // Live updates only invalidate pages whose actual sampled coverage changed.
            for (var it = searchPages.entrySet().iterator(); it.hasNext(); ) {
                var page = it.next();
                if (next.pageIdentity(page.getKey()) != snapshot.pageIdentity(page.getKey())) {
                    page.getValue().close();
                    it.remove();
                }
            }
        }
        snapshot = next;
        if (!p.highlights || next.keys().isEmpty()) {
            return;
        }
        var bounds = next.bounds();
        int reduction = next.overviewScale(),
                w = (bounds.pixelWidth() + reduction - 1) / reduction,
                h = (bounds.pixelHeight() + reduction - 1) / reduction;
        double x = v.centerX() + (bounds.blockX() - v.x()) * v.scale(),
                y = v.centerZ() + (bounds.blockZ() - v.z()) * v.scale(),
                pixel = next.resolution() * v.scale() * reduction;
        if (x + w * pixel < 0 || y + h * pixel < 0 || x > width || y > height) {
            return;
        }
        if (searchTexture == null) {
            NativeImage image = new NativeImage(w, h, true);
            byte[] overview = next.overview();
            for (int i = 0; i < overview.length; i++) {
                if (overview[i] != 0) {
                    image.setPixelRGBA(i % w, i / w, searchRgba(p, next, overview[i]));
                }
            }
            searchTexture = new Texture(image);
        }
        var visible =
                TileWindow.visible(
                        v.x(), v.z(), v.scale(), width, height, next.resolution() / Tile.GRID);
        if (reduction == 1 || visible.count() > 512) {
            drawSearchTexture(g, searchTexture, x, y, pixel, w, h);
            return;
        }
        // Large manual searches use a bounded overview plus exact sample pages when zoomed in.

        int uploads = 0;
        for (Tile.Key key : visible) {
            if (!next.keys().contains(key)) {
                continue;
            }
            Texture texture = searchPages.get(key);
            if (texture == null) {
                if (uploads++ >= 24) {
                    continue;
                }
                NativeImage image = new NativeImage(32, 32, true);
                for (int i = 0; i < 1024; i++) {
                    if (next.matches(key, i)) {
                        image.setPixelRGBA(
                                i % 32, i / 32, searchRgba(p, next, next.layerMask(key, i)));
                    }
                }
                texture = new Texture(image);
                searchPages.put(key, texture);
                while (searchPages.size() > 512) {
                    var first = searchPages.entrySet().iterator();
                    var old = first.next();
                    old.getValue().close();
                    first.remove();
                }
            }
            drawSearchTexture(
                    g,
                    texture,
                    v.centerX() + (key.blockX() - v.x()) * v.scale(),
                    v.centerZ() + (key.blockZ() - v.z()) * v.scale(),
                    next.resolution() * v.scale(),
                    32,
                    32);
        }
    }

    private static int searchRgba(Profile p, SearchOverlay overlay, int mask) {
        int rgb = overlay.layered() ? RockLayer.colour(mask) : p.highlightColor;
        return (int) Math.round(p.highlightOpacity * 255) << 24
                | ((rgb & 255) << 16)
                | (rgb & 0xff00)
                | ((rgb >>> 16) & 255);
    }

    private static void drawSearchTexture(
            GuiGraphics g, Texture texture, double x, double y, double pixel, int w, int h) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale((float) pixel, (float) pixel, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        g.blit(texture.id, 0, 0, 0, 0, w, h, w, h);
        g.flush();
        g.pose().popPose();
    }

    private static void segment(
            BufferBuilder b,
            org.joml.Matrix4f pose,
            double x0,
            double y0,
            double x1,
            double y1,
            double thickness,
            int colour) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        if (length == 0) {
            return;
        }
        double dx = -(y1 - y0) / length * thickness / 2, dy = (x1 - x0) / length * thickness / 2;
        b.addVertex(pose, (float) (x0 + dx), (float) (y0 + dy), 0).setColor(colour);
        b.addVertex(pose, (float) (x0 - dx), (float) (y0 - dy), 0).setColor(colour);
        b.addVertex(pose, (float) (x1 - dx), (float) (y1 - dy), 0).setColor(colour);
        b.addVertex(pose, (float) (x1 + dx), (float) (y1 + dy), 0).setColor(colour);
    }

    private static BufferBuilder lines(GuiGraphics g) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        return Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void finishLines(BufferBuilder b) {
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
    }

    private void searchOutline(
            GuiGraphics g,
            XaeroBridge.View v,
            Profile p,
            SearchOverlay search,
            int width,
            int height) {
        if (search.groups().isEmpty()) {
            searchOutlines.clear();
            return;
        }
        if (!searchOutlines.render(g, v, p, search, width, height)) {
            var b = lines(g);
            var pose = g.pose().last().pose();
            double halfW = (width / 2. + 8) / v.scale(), halfH = (height / 2. + 8) / v.scale();
            var edges = search.edgesIn(v.x() - halfW, v.z() - halfH, v.x() + halfW, v.z() + halfH);
            for (int pass = 0; pass < 2; pass++) {
                for (var edge : edges) {
                    double x0 = v.centerX() + (edge.x0() - v.x()) * v.scale(),
                            y0 = v.centerZ() + (edge.z0() - v.z()) * v.scale();
                    double x1 = v.centerX() + (edge.x1() - v.x()) * v.scale(),
                            y1 = v.centerZ() + (edge.z1() - v.z()) * v.scale();
                    if (Math.max(x0, x1) < -4
                            || Math.min(x0, x1) > width + 4
                            || Math.max(y0, y1) < -4
                            || Math.min(y0, y1) > height + 4) {
                        continue;
                    }
                    segment(
                            b,
                            pose,
                            x0,
                            y0,
                            x1,
                            y1,
                            pass == 0 ? 3 : 1.25,
                            pass == 0
                                    ? 0xD0182023
                                    : 0xFF000000
                                            | (search.layered()
                                                    ? RockLayer.colour(edge.layerMask())
                                                    : p.highlightColor));
                }
            }
            finishLines(b);
        }
        if (p.labels
                && p.mapLabels.equals("Off")
                && MapLabels.visible("Active layer", p.selected(), v.scale(), 1)) {
            var font = Minecraft.getInstance().font;
            List<double[]> used = new ArrayList<>();
            for (var r : search.candidates()) {
                double x = v.centerX() + (r.x() - v.x()) * v.scale(),
                        y = v.centerZ() + (r.z() - v.z()) * v.scale();
                if (x < 0
                        || y < 0
                        || x > width
                        || y > height
                        || used.stream()
                                .anyMatch(a -> Math.hypot(x - a[0], y - a[1]) < p.labelSpacing)) {
                    continue;
                }
                String text =
                        p.selected() == Layer.ROCKS
                                ? r.rocks()
                                : MapLabels.text(p.selected(), r.cell());
                if (!MapLabels.fitsCell(
                        font.width(text), p.labelScale, v.scale(), search.resolution())) {
                    continue;
                }
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale((float) p.labelScale, (float) p.labelScale, 1);
                g.drawCenteredString(font, text, 0, -4, 0xFF000000 | p.highlightColor);
                g.pose().popPose();
                used.add(new double[] {x, y});
            }
        }
    }

    private static void searchCircle(
            GuiGraphics g, XaeroBridge.View v, RegionEngine e, int width, int height) {
        double cx = v.centerX() + (e.searchX - v.x()) * v.scale(),
                cy = v.centerZ() + (e.searchZ - v.z()) * v.scale(),
                radius = e.searchRadius * v.scale();
        if (radius <= 0
                || cx + radius < 0
                || cy + radius < 0
                || cx - radius > width
                || cy - radius > height) {
            return;
        }
        int count = Math.max(96, Math.min(2048, (int) Math.ceil(2 * Math.PI * radius / 4)));
        var b = lines(g);
        var pose = g.pose().last().pose();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < count; i++) {
                double a = 2 * Math.PI * i / count, c = 2 * Math.PI * (i + 1) / count;
                segment(
                        b,
                        pose,
                        cx + Math.cos(a) * radius,
                        cy + Math.sin(a) * radius,
                        cx + Math.cos(c) * radius,
                        cy + Math.sin(c) * radius,
                        pass == 0 ? 3.5 : 1.5,
                        pass == 0 ? 0xC0182023 : 0xE07EDBDD);
            }
        }
        finishLines(b);
    }
}
