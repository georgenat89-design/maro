package dev.maro.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.module.impl.visuals.HandShader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Draws {@link HandShader}: the frame is copied just before the hand is drawn (the world behind it)
 * and again once it is; then one pass over the screen reads both and the depth buffer, which the
 * game cleared before drawing the hand and so holds depth only where the hand is, and writes the
 * restyled hand and its glow back into the frame.
 */
public final class HandShaderRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/hand_shader"))
        .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
        .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/hand_shader"))
        .withSampler("u_Before")
        .withSampler("u_After")
        .withSampler("u_Depth")
        .withUniform("HandData", UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withCull(false)
        .build();

    private static final int DATA_BYTES = 4 * 16;
    private static final ByteBuffer SCREEN_VERTICES = BufferUtils.createByteBuffer(4 * 3 * Float.BYTES);
    private static final ByteBuffer SCREEN_INDICES = BufferUtils.createByteBuffer(6 * Integer.BYTES);

    static {
        float[] corners = {-1, -1, -1, 1, 1, 1, 1, -1};
        for (int i = 0; i < 4; i++) {
            SCREEN_VERTICES.putFloat(i * 12, corners[i * 2]);
            SCREEN_VERTICES.putFloat(i * 12 + 4, corners[i * 2 + 1]);
            SCREEN_VERTICES.putFloat(i * 12 + 8, 0f);
        }
        int[] quad = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < quad.length; i++) SCREEN_INDICES.putInt(i * Integer.BYTES, quad[i]);
    }

    private static GpuTexture before, after;
    private static GpuTextureView beforeView, afterView;
    private static GpuBuffer data;
    /** The frame was copied before the hand this frame, so the pass can run. */
    private static boolean captured;
    private static int applied;

    private HandShaderRenderer() {
    }

    /** Just before the hand is drawn: the world behind it. */
    public static void captureBefore() {
        if (HandShader.active() == null) return;
        Framebuffer fb = mc.getFramebuffer();
        GpuTexture src = fb.getColorAttachment();
        if (src == null) return;
        try {
            ensure(src.getWidth(0), src.getHeight(0));
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(src, before, 0, 0, 0, 0, 0, src.getWidth(0), src.getHeight(0));
            captured = true;
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    /** Once the hand is drawn, before the interface: the restyled hand and its glow. */
    public static void apply() {
        HandShader m = HandShader.active();
        if (!captured || m == null) {
            captured = false;
            return;
        }
        captured = false;
        Framebuffer fb = mc.getFramebuffer();
        GpuTexture src = fb.getColorAttachment();
        GpuTextureView depth = fb.getDepthAttachmentView();
        if (src == null || depth == null || fb.getColorAttachmentView() == null) return;
        int w = src.getWidth(0), h = src.getHeight(0);
        try {
            ensure(w, h);
            GpuDevice device = RenderSystem.getDevice();
            device.createCommandEncoder().copyTextureToTexture(src, after, 0, 0, 0, 0, 0, w, h);

            int color = m.color();
            GpuBufferSlice slice = data.slice(0, DATA_BYTES);
            ByteBuffer bytes = MemoryUtil.memCalloc(DATA_BYTES);
            try {
                bytes.putFloat(0, (color >> 16 & 0xFF) / 255f);
                bytes.putFloat(4, (color >> 8 & 0xFF) / 255f);
                bytes.putFloat(8, (color & 0xFF) / 255f);
                bytes.putFloat(12, m.fill());
                bytes.putFloat(16, m.outlinePx(h));
                bytes.putFloat(20, m.glowStrength());
                bytes.putFloat(24, m.time());
                bytes.putFloat(28, m.modeIndex());
                bytes.putFloat(32, m.rainbowOutline() ? 1f : 0f);
                bytes.putFloat(36, 1f / w);
                bytes.putFloat(40, 1f / h);
                bytes.putFloat(44, m.naturalFire() ? 1f : 0f);
                float[] flame = m.flame();
                for (int i = 0; i < 4; i++) bytes.putFloat(48 + i * 4, flame[i]);
                device.createCommandEncoder().writeToBuffer(slice, bytes);
            } finally {
                MemoryUtil.memFree(bytes);
            }

            GpuBuffer vertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(SCREEN_VERTICES);
            GpuBuffer indices = VertexFormats.POSITION.uploadImmediateIndexBuffer(SCREEN_INDICES);
            RenderPass pass = device.createCommandEncoder()
                .createRenderPass(() -> "maro hand shader", fb.getColorAttachmentView(), OptionalInt.empty());
            try {
                pass.setPipeline(PIPELINE);
                pass.setUniform("HandData", slice);
                pass.bindTexture("u_Before", beforeView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.bindTexture("u_After", afterView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.bindTexture("u_Depth", depth, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            } finally {
                pass.close();
            }
            applied++;
        } catch (RuntimeException e) {
            failed(e);
        }
    }

    private static void ensure(int w, int h) {
        GpuDevice device = RenderSystem.getDevice();
        if (before == null || before.getWidth(0) != w || before.getHeight(0) != h) {
            if (beforeView != null) beforeView.close();
            if (afterView != null) afterView.close();
            if (before != null) before.close();
            if (after != null) after.close();
            before = device.createTexture(() -> "maro hand shader before", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h, 1, 1);
            after = device.createTexture(() -> "maro hand shader after", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h, 1, 1);
            beforeView = device.createTextureView(before);
            afterView = device.createTextureView(after);
        }
        if (data == null) {
            data = device.createBuffer(() -> "maro hand shader", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 256);
        }
    }

    private static void failed(RuntimeException e) {
        Maro.LOGGER.error("Hand Shader could not draw", e);
        HandShader m = HandShader.active();
        if (m != null) m.setEnabled(false);
    }

    /** How many frames it has drawn; for tests. */
    public static int appliedFrames() {
        return applied;
    }
}
