package dev.ryan.tfcatlas.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Shares the displayed colour texture, but never changes Minecraft's depth attachment. */
final class MaskFramebuffer {
    private int framebuffer, stencil, width, height;
    private int previousFramebuffer;
    private boolean bound;

    void bind() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int original = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // GUI rendering normally targets Minecraft's colour texture. Fail closed if a renderer
        // supplies a different target; attaching an unknown texture type could corrupt GL state.
        if (original == 0
                || GL11.glGetInteger(GL13.GL_SAMPLES) != 0
                || GL11.glGetInteger(GL20.GL_DRAW_BUFFER0) != GL30.GL_COLOR_ATTACHMENT0
                || GL30.glGetFramebufferAttachmentParameteri(
                                GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_COLOR_ATTACHMENT0,
                                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)
                        != GL11.GL_TEXTURE
                || GL30.glGetFramebufferAttachmentParameteri(
                                GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_COLOR_ATTACHMENT0,
                                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
                        != main.getColorTextureId()
                || GL30.glGetFramebufferAttachmentParameteri(
                                GL30.GL_DRAW_FRAMEBUFFER,
                                GL30.GL_COLOR_ATTACHMENT0,
                                GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)
                        != 0) {
            throw new IllegalStateException("Unsupported map framebuffer; Atlas masking hidden");
        }
        int oldRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        boolean success = false;
        try {
            if (framebuffer == 0) {
                framebuffer = GL30.glGenFramebuffers();
                stencil = GL30.glGenRenderbuffers();
            }
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencil);
            if (width != main.width || height != main.height) {
                GL30.glRenderbufferStorage(
                        GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, main.width, main.height);
                width = main.width;
                height = main.height;
            }
            GL30.glFramebufferRenderbuffer(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER,
                    stencil);
            GL30.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    main.getColorTextureId(),
                    0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Atlas mask framebuffer incomplete");
            }
            previousFramebuffer = original;
            bound = true;
            success = true;
        } finally {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, oldRenderbuffer);
            if (!success) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, original);
            }
        }
    }

    void unbind() {
        if (bound) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            bound = false;
        }
    }

    void close() {
        unbind();
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            GL30.glDeleteRenderbuffers(stencil);
            framebuffer = stencil = width = height = 0;
        }
    }
}
