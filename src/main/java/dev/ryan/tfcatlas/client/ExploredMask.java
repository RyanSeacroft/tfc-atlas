package dev.ryan.tfcatlas.client;

import static dev.ryan.tfcatlas.client.XaeroBridge.call;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.ryan.tfcatlas.core.TerrainCoverage;
import dev.ryan.tfcatlas.core.TerrainCoverage.Rect;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Coverage comes from the same uploaded leaf/branch textures selected by GuiMap. Cached height
 * metadata survives chunk unloading. The texture version invalidates coverage immediately, so there
 * is no timer, per-frame refresh budget or cell halo.
 */
public final class ExploredMask {
    private record Cached(int version, int texture, List<Rect> pixels) {}

    private final Map<Object, Cached> cache = new LinkedHashMap<>(128, .75f, true);
    private boolean active, previousEnabled;
    private int previousClear;
    private int[] previousFront, previousBack;
    private List<Rect> currentCoverage = List.of();
    private long coverageRevision;

    private static int[] stencilState(boolean back) {
        return new int[] {
            GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FUNC : GL11.GL_STENCIL_FUNC),
            GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_REF : GL11.GL_STENCIL_REF),
            GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_VALUE_MASK : GL11.GL_STENCIL_VALUE_MASK),
            GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_WRITEMASK : GL11.GL_STENCIL_WRITEMASK),
            GL11.glGetInteger(back ? GL20.GL_STENCIL_BACK_FAIL : GL11.GL_STENCIL_FAIL),
            GL11.glGetInteger(
                    back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL11.GL_STENCIL_PASS_DEPTH_FAIL),
            GL11.glGetInteger(
                    back ? GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS : GL11.GL_STENCIL_PASS_DEPTH_PASS)
        };
    }

    private static void restore(int face, int[] state) {
        GL20.glStencilFuncSeparate(face, state[0], state[1], state[2]);
        GL20.glStencilMaskSeparate(face, state[3]);
        GL20.glStencilOpSeparate(face, state[4], state[5], state[6]);
    }

    public void clear() {
        cache.clear();
        currentCoverage = List.of();
        coverageRevision++;
    }

    public long coverageRevision() {
        return coverageRevision;
    }

    public static void prepare() {
        Minecraft.getInstance().getMainRenderTarget().enableStencil();
    }

    private List<Rect> pixels(Object texture) throws ReflectiveOperationException {
        int version = ((Number) call(texture, "getTextureVersion")).intValue();
        int gl = ((Number) call(texture, "getGlColorTexture")).intValue();
        Cached old = cache.get(texture);
        if (old != null && old.version == version && old.texture == gl) {
            return old.pixels;
        }
        Method height = texture.getClass().getMethod("getHeight", int.class, int.class),
                top = texture.getClass().getMethod("getTopHeight", int.class, int.class);
        List<Rect> pixels =
                TerrainCoverage.read(
                        (x, z) -> readHeight(texture, height, x, z),
                        (x, z) -> readHeight(texture, top, x, z));
        cache.put(texture, new Cached(version, gl, pixels));
        while (cache.size() > 4096) {
            var it = cache.keySet().iterator();
            it.next();
            it.remove();
        }
        return pixels;
    }

    private static int readHeight(Object texture, Method method, int x, int z) {
        try {
            return ((Number) method.invoke(texture, x, z)).intValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read Xaero terrain coverage", e);
        }
    }

    /** Pure selection pass also used by regression tests. Bounds are world blocks. */
    public List<Rect> collect(Object processor, int level, int minX, int minZ, int maxX, int maxZ)
            throws ReflectiveOperationException {
        return collect(processor, Integer.MAX_VALUE, level, minX, minZ, maxX, maxZ);
    }

    public List<Rect> collect(
            Object processor, int caveLayer, int level, int minX, int minZ, int maxX, int maxZ)
            throws ReflectiveOperationException {
        List<Rect> out = new ArrayList<>();
        int shift = 9 + level, texSpan = 64 << level;
        for (int rx = minX >> shift; rx <= maxX >> shift; rx++) {
            for (int rz = minZ >> shift; rz <= maxZ >> shift; rz++) {
                Object region = call(processor, "getLeveledRegion", caveLayer, rx, rz, level);
                if (region == null) {
                    continue;
                }
                Object root = call(region, "getRootRegion");
                if (root == region
                        || root != null && !Boolean.TRUE.equals(call(root, "isLoaded"))) {
                    root = null;
                }
                boolean own = Boolean.TRUE.equals(call(region, "hasTextures")),
                        fallback = root != null && Boolean.TRUE.equals(call(root, "hasTextures"));
                if (!own && !fallback) {
                    continue;
                }
                for (int tx = 0; tx < 8; tx++) {
                    for (int tz = 0; tz < 8; tz++) {
                        int bx = (rx << shift) + tx * texSpan, bz = (rz << shift) + tz * texSpan;
                        if (bx > maxX
                                || bz > maxZ
                                || bx + texSpan <= minX
                                || bz + texSpan <= minZ) {
                            continue;
                        }
                        Object texture = own ? call(region, "getTexture", tx, tz) : null;
                        int originX = bx, originZ = bz, pixel = 1 << level;
                        if (texture == null
                                || ((Number) call(texture, "getGlColorTexture")).intValue() == -1) {
                            if (!fallback) {
                                continue;
                            }
                            // Xaero's root cache is level 3 (8 blocks per texture pixel).
                            int rootTx = (bx >> 9) & 7, rootTz = (bz >> 9) & 7;
                            texture = call(root, "getTexture", rootTx, rootTz);
                            if (texture == null
                                    || ((Number) call(texture, "getGlColorTexture")).intValue()
                                            == -1) {
                                continue;
                            }
                            originX = Math.floorDiv(bx, 512) * 512;
                            originZ = Math.floorDiv(bz, 512) * 512;
                            pixel = 8;
                        }
                        for (Rect r : pixels(texture)) {
                            Rect world =
                                    new Rect(
                                            originX + r.x0() * pixel,
                                            originZ + r.z0() * pixel,
                                            originX + r.x1() * pixel,
                                            originZ + r.z1() * pixel);
                            Rect clipped = world.intersect(bx, bz, bx + texSpan, bz + texSpan);
                            if (clipped != null) {
                                out.add(clipped);
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    public List<Rect> coverage(XaeroBridge.View view, int width, int height)
            throws ReflectiveOperationException {
        if (active) {
            return currentCoverage;
        }
        int level =
                ((Number)
                                XaeroBridge.field(
                                        call(view.processor(), "getMapSaveLoad"),
                                        "mainTextureLevel"))
                        .intValue();
        if (level < 0 || level > 3) {
            throw new IllegalStateException("Unsupported Xaero texture level");
        }
        int minX = (int) Math.floor(view.x() - width / (2 * view.scale())),
                minZ = (int) Math.floor(view.z() - height / (2 * view.scale()));
        int maxX = (int) Math.ceil(view.x() + width / (2 * view.scale())),
                maxZ = (int) Math.ceil(view.z() + height / (2 * view.scale()));
        // Match the actual native imagery, including the selected cave layer. A surface
        // discovery does not imply that Xaero has imagery at this cave depth.
        List<Rect> rects =
                collect(
                        view.processor(),
                        ((Number) call(view.processor(), "getCurrentCaveLayer")).intValue(),
                        level,
                        minX,
                        minZ,
                        maxX,
                        maxZ);
        if (!rects.equals(currentCoverage)) {
            currentCoverage = List.copyOf(rects);
            coverageRevision++;
        }
        return currentCoverage;
    }

    public void begin(
            GuiGraphics g, XaeroBridge.View view, int width, int height, boolean exploredOnly)
            throws ReflectiveOperationException {
        if (!Minecraft.getInstance().getMainRenderTarget().isStencilEnabled()) {
            throw new IllegalStateException("Terrain stencil unavailable; reopen the map");
        }
        List<Rect> rects = coverage(view, width, height);
        g.flush();
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0
                || GL30.glGetFramebufferAttachmentParameteri(
                                GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_STENCIL_ATTACHMENT,
                                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
                        == GL11.GL_NONE
                || GL30.glGetFramebufferAttachmentParameteri(
                                GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_STENCIL_ATTACHMENT,
                                GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE)
                        < 8) {
            throw new IllegalStateException("Terrain stencil not attached; overlay hidden");
        }
        previousEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        previousClear = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
        previousFront = stencilState(false);
        previousBack = stencilState(true);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        active = true;
        // Use the high bit; preserve the other seven bits and restore GL state afterward.
        GL11.glStencilMask(0x80);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0x80, 0x80);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        try {
            g.drawManaged(
                    () -> {
                        for (Rect r : rects) {
                            g.pose().pushPose();
                            g.pose()
                                    .translate(
                                            view.centerX() + (r.x0() - view.x()) * view.scale(),
                                            view.centerZ() + (r.z0() - view.z()) * view.scale(),
                                            0);
                            g.pose().scale((float) view.scale(), (float) view.scale(), 1);
                            g.fill(0, 0, r.x1() - r.x0(), r.z1() - r.z0(), 0xFFFFFFFF);
                            g.pose().popPose();
                        }
                    });
            g.flush();
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
        }
        GL11.glStencilMask(0);
        GL11.glStencilFunc(GL11.GL_EQUAL, exploredOnly ? 0x80 : 0, 0x80);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    /** Reuse this frame's mask for disjoint colour passes and visible labels. */
    public void select(GuiGraphics g, Boolean known) {
        g.flush();
        GL11.glStencilFunc(
                known == null ? GL11.GL_ALWAYS : GL11.GL_EQUAL,
                Boolean.TRUE.equals(known) ? 0x80 : 0,
                0x80);
    }

    public void end(GuiGraphics g) {
        if (active) {
            g.flush();
            restore(GL11.GL_FRONT, previousFront);
            restore(GL11.GL_BACK, previousBack);
            GL11.glClearStencil(previousClear);
            if (!previousEnabled) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            active = false;
        }
    }
}
