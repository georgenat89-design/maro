package dev.maro.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.module.impl.visuals.Geeked;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/** One fullscreen pass with reused buffers; never samples the attachment it writes. */
public final class GeekedRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/geeked"))
            .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
            .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/geeked"))
            .withSampler("Scene")
            .withUniform("GeekedData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false).withCull(false).build();
    private static final ByteBuffer DATA = BufferUtils.createByteBuffer(48);
    private static final ByteBuffer VERTICES = BufferUtils.createByteBuffer(48);
    private static final ByteBuffer INDICES = BufferUtils.createByteBuffer(24);
    static {
        float[] xy = {-1, -1, -1, 1, 1, 1, 1, -1};
        for (int i = 0; i < 4; i++) {
            VERTICES.putFloat(i * 12, xy[i * 2]);
            VERTICES.putFloat(i * 12 + 4, xy[i * 2 + 1]);
            VERTICES.putFloat(i * 12 + 8, 0);
        }
        int[] indices = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < indices.length; i++) INDICES.putInt(i * 4, indices[i]);
    }
    private static GpuTexture scene;
    private static GpuTextureView sceneView;
    private static GpuBuffer uniforms, vertices, indices;
    private static int applied;

    private GeekedRenderer() { }

    /** Called once per frame after the other world effects, before any HUD or menu pixels. */
    public static void apply() {
        Geeked module = Geeked.active();
        if (module == null || module.strength() <= 0) {
            release();
            return;
        }
        var framebuffer = MinecraftClient.getInstance().getFramebuffer();
        var source = framebuffer.getColorAttachment();
        if (source == null || framebuffer.getColorAttachmentView() == null) return;
        int width = source.getWidth(0), height = source.getHeight(0);
        if (width <= 0 || height <= 0) return;
        try {
            ensure(width, height, source.getFormat());
            var device = RenderSystem.getDevice();
            var encoder = device.createCommandEncoder();
            encoder.copyTextureToTexture(source, scene, 0, 0, 0, 0, 0, width, height);
            DATA.putFloat(0, module.time());
            DATA.putFloat(4, module.strength());
            DATA.putFloat(8, module.modeIndex());
            DATA.putFloat(12, width / (float) height);
            DATA.putFloat(16, module.wobble());
            DATA.putFloat(20, module.colors());
            DATA.putFloat(24, module.haze());
            DATA.putFloat(28, module.separation());
            DATA.putFloat(32, module.vignette());
            DATA.putFloat(36, 1f / width);
            DATA.putFloat(40, 1f / height);
            DATA.putFloat(44, 0);
            encoder.writeToBuffer(uniforms.slice(0, 48), DATA);
            var sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
            try (var pass = encoder.createRenderPass(() -> "maro geeked vision", framebuffer.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("GeekedData", uniforms.slice(0, 48));
                pass.bindTexture("Scene", sceneView, sampler);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            }
            applied++;
        } catch (RuntimeException error) {
            Maro.LOGGER.error("Geeked could not draw its visual effect", error);
            module.setEnabled(false);
        }
    }

    private static void ensure(int width, int height, TextureFormat format) {
        var device = RenderSystem.getDevice();
        if (scene == null || scene.getWidth(0) != width || scene.getHeight(0) != height || scene.getFormat() != format) {
            if (sceneView != null) { sceneView.close(); sceneView = null; }
            if (scene != null) { scene.close(); scene = null; }
            scene = device.createTexture(() -> "maro geeked scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    format, width, height, 1, 1);
            sceneView = device.createTextureView(scene);
        }
        if (uniforms == null) uniforms = device.createBuffer(() -> "maro geeked uniforms", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 256);
        if (vertices == null) vertices = device.createBuffer(() -> "maro geeked quad", GpuBuffer.USAGE_VERTEX, VERTICES);
        if (indices == null) indices = device.createBuffer(() -> "maro geeked indices", GpuBuffer.USAGE_INDEX, INDICES);
    }

    /** Disabling, leaving a world or stopping the client releases all owned GPU allocations. */
    public static void release() {
        if (sceneView != null) { sceneView.close(); sceneView = null; }
        if (scene != null) { scene.close(); scene = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (vertices != null) { vertices.close(); vertices = null; }
        if (indices != null) { indices.close(); indices = null; }
    }

    public static int appliedFrames() { return applied; }
    public static boolean hasResources() { return scene != null || uniforms != null || vertices != null || indices != null; }
}
