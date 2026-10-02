package dev.ryan.tfcatlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryan.tfcatlas.core.RockLayer;
import dev.ryan.tfcatlas.core.SearchOverlay;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/** Exact outline vertices are built once off-thread; camera movement only changes GPU matrices. */
final class SearchOutlines {
    private static ShaderInstance shader;

    static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath("tfcatlas", "search_outline"),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> shader = loaded);
        } catch (IOException ex) {
            shader = null;
            com.mojang.logging.LogUtils.getLogger()
                    .error("Atlas outline shader unavailable; using CPU outlines", ex);
        }
    }

    private record Mesh(SearchOverlay.OutlineGroup group, VertexBuffer buffer) {}

    private record Prepared(SearchOverlay.OutlineGroup group, MeshData data) {}

    private final Map<Long, Mesh> meshes = new HashMap<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "TFC Atlas search outlines");
                        t.setDaemon(true);
                        return t;
                    });
    // BufferBuilder owns native memory. Reuse one builder, and release each rendered view before
    // reuse.
    private ByteBufferBuilder storage;
    private CompletableFuture<Prepared> pending;
    private Object series;

    void clear() {
        if (pending != null) {
            pending.thenAccept(p -> p.data.close());
            pending = null;
        }
        meshes.values().forEach(m -> m.buffer.close());
        meshes.clear();
        series = null;
    }

    boolean render(
            GuiGraphics g,
            XaeroBridge.View v,
            Profile p,
            SearchOverlay overlay,
            int width,
            int height) {
        if (shader == null) {
            return false;
        }
        if (series != overlay.series()) {
            clear();
            series = overlay.series();
        }
        if (pending != null && pending.isDone()) {
            Prepared ready = pending.join();
            pending = null;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(ready.data);
            VertexBuffer.unbind();
            Mesh old = meshes.put(ready.group.key(), new Mesh(ready.group, buffer));
            if (old != null) {
                old.buffer.close();
            }
        }
        List<SearchOverlay.OutlineGroup> visible = new ArrayList<>();
        for (var group : overlay.groups()) {
            double x = v.centerX() + (group.blockX() - v.x()) * v.scale(),
                    z = v.centerZ() + (group.blockZ() - v.z()) * v.scale(),
                    size = group.span() * v.scale();
            if (x <= width + 4 && z <= height + 4 && x + size >= -4 && z + size >= -4) {
                visible.add(group);
            }
        }
        if (pending == null) {
            SearchOverlay.OutlineGroup next =
                    visible.stream()
                            .filter(
                                    group -> {
                                        Mesh mesh = meshes.get(group.key());
                                        return mesh == null || mesh.group != group;
                                    })
                            .min(
                                    Comparator.comparingDouble(
                                            group ->
                                                    Math.hypot(
                                                            group.blockX()
                                                                    + group.span() / 2.
                                                                    - v.x(),
                                                            group.blockZ()
                                                                    + group.span() / 2.
                                                                    - v.z())))
                            .orElse(null);
            if (next != null) {
                boolean layered = overlay.layered();
                pending = CompletableFuture.supplyAsync(() -> prepare(next, layered), worker);
            }
        }
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        int colour = overlay.layered() ? 0xFFFFFF : p.highlightColor;
        shader.safeGetUniform("TintColor")
                .set(
                        (colour >> 16 & 255) / 255f,
                        (colour >> 8 & 255) / 255f,
                        (colour & 255) / 255f);
        for (int pass = 0; pass < 2; pass++) {
            shader.safeGetUniform("StrokeWidth").set(pass == 0 ? 3f : 1.25f);
            shader.safeGetUniform("ShadowPass").set(pass == 0 ? 1f : 0f);
            for (var group : visible) {
                Mesh mesh = meshes.get(group.key());
                if (mesh == null) {
                    continue;
                }
                // Subtract the double-precision camera before converting to floats, including near
                // world borders.
                Matrix4f pose =
                        new Matrix4f(g.pose().last().pose())
                                .translate(
                                        (float)
                                                (v.centerX()
                                                        + (group.blockX() - v.x()) * v.scale()),
                                        (float)
                                                (v.centerZ()
                                                        + (group.blockZ() - v.z()) * v.scale()),
                                        0)
                                .scale((float) v.scale(), (float) v.scale(), 1);
                mesh.buffer.bind();
                mesh.buffer.drawWithShader(pose, RenderSystem.getProjectionMatrix(), shader);
            }
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        return true;
    }

    private Prepared prepare(SearchOverlay.OutlineGroup group, boolean layered) {
        if (storage == null) {
            storage = new ByteBufferBuilder(4096);
        }
        BufferBuilder builder =
                new BufferBuilder(
                        storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (var edge : group.edges()) {
            float x0 = edge.x0() - group.blockX(),
                    z0 = edge.z0() - group.blockZ(),
                    x1 = edge.x1() - group.blockX(),
                    z1 = edge.z1() - group.blockZ();
            float dx = x1 - x0, dz = z1 - z0, length = (float) Math.hypot(dx, dz);
            if (length == 0) {
                continue;
            }
            float nx = -dz / length * .5f, nz = dx / length * .5f;
            int colour = 0xFF000000 | (layered ? RockLayer.colour(edge.layerMask()) : 0xFFFFFF);
            builder.addVertex(x0, z0, 0).setUv(nx, nz).setColor(colour);
            builder.addVertex(x0, z0, 0).setUv(-nx, -nz).setColor(colour);
            builder.addVertex(x1, z1, 0).setUv(-nx, -nz).setColor(colour);
            builder.addVertex(x1, z1, 0).setUv(nx, nz).setColor(colour);
        }
        return new Prepared(group, builder.buildOrThrow());
    }
}
