package dev.maro.render.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.module.impl.visuals.CustomSky;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Paints {@link CustomSky}'s sky. Called from the game's sky pass in place of the sky dome (see
 * {@link dev.maro.mixin.CustomSkyMixins}), so the world is drawn over it as usual: one quad over the
 * whole screen, where {@code custom_sky.fsh} works out the view direction of every pixel from the
 * inverse of this frame's view-projection and paints the sky for it.
 */
public final class CustomSkyRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/custom_sky"))
        .withVertexShader(Identifier.of(Maro.MOD_ID, "core/custom_sky"))
        .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/custom_sky"))
        .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    /** SkyData in custom_sky.fsh: the inverse view-projection, then Params and View. */
    private static final int UNIFORM_BYTES = (4 + 2) * 16;

    private static final ByteBuffer VERTICES = BufferUtils.createByteBuffer(4 * 3 * Float.BYTES);
    private static final ByteBuffer INDICES = BufferUtils.createByteBuffer(6 * Integer.BYTES);

    static {
        float[] corners = {-1, -1, -1, 1, 1, 1, 1, -1};
        for (int i = 0; i < 4; i++) {
            VERTICES.putFloat(i * 3 * Float.BYTES, corners[i * 2]);
            VERTICES.putFloat((i * 3 + 1) * Float.BYTES, corners[i * 2 + 1]);
            VERTICES.putFloat((i * 3 + 2) * Float.BYTES, 0f);
        }
        int[] quad = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < quad.length; i++) INDICES.putInt(i * Integer.BYTES, quad[i]);
    }

    private static GpuBuffer uniforms;

    /** This frame's inverse view-projection, from the start of world rendering. */
    private static final Matrix4f inverse = new Matrix4f();
    /** The projection's vertical scale, 1 / tan(half the vertical field of view). */
    private static float projectionScale = 1f;
    private static boolean haveMatrices;

    /** Frames of world rendering started, and the last one the sky was painted in. */
    private static long frame;
    private static long drawnFrame = -1;

    private CustomSkyRenderer() {
    }

    /** Start of world rendering: remember the matrices the sky is seen through. */
    public static void beginFrame(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        frame++;
        if (!CustomSky.active()) {
            haveMatrices = false;
            return;
        }
        inverse.set(projectionMatrix).mul(positionMatrix).invert();
        projectionScale = Math.abs(projectionMatrix.m11());
        haveMatrices = projectionScale > 1e-6f && Float.isFinite(inverse.m00());
    }

    /**
     * Whether the sky was painted in the frame before the one about to start. The fog colour is
     * worked out before a frame's sky pass, so it goes by the last frame.
     */
    public static boolean drewLastFrame() {
        return drawnFrame == frame;
    }

    /** Paints the sky over the whole frame. Returns false if it could not, so the game's sky is kept. */
    public static boolean draw() {
        if (!haveMatrices || !shaderBuilt()) return false;
        Framebuffer target = mc.getFramebuffer();
        if (target == null || target.getColorAttachmentView() == null || target.textureHeight <= 0) return false;

        try {
            GpuBufferSlice data = writeUniforms(2f / (projectionScale * target.textureHeight));
            GpuBuffer vertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(VERTICES);
            GpuBuffer indices = VertexFormats.POSITION.uploadImmediateIndexBuffer(INDICES);

            RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "maro custom sky", target.getColorAttachmentView(), OptionalInt.empty());
            try {
                pass.setPipeline(PIPELINE);
                pass.setUniform("SkyData", data);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            } finally {
                pass.close();
            }
            drawnFrame = frame;
            return true;
        } catch (RuntimeException e) {
            // One bad frame must not take the renderer down; the module reports and switches off.
            CustomSky.renderFailed(e);
            return false;
        }
    }

    /**
     * Whether the graphics driver compiled the sky shader. If it did not, the draw would silently do
     * nothing and leave the cleared sky showing, so keep the game's sky and switch the module off.
     */
    private static boolean shaderBuilt() {
        if (RenderSystem.getDevice().precompilePipeline(PIPELINE).isValid()) return true;
        CustomSky.shaderFailed();
        return false;
    }

    private static GpuBufferSlice writeUniforms(float pixelAngle) {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "maro custom sky uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
        }

        ByteBuffer data = MemoryUtil.memCalloc(UNIFORM_BYTES);
        try {
            // std140 mat4: four columns of four floats.
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) data.putFloat((column * 4 + row) * Float.BYTES, inverse.get(column, row));
            }
            float[] values = CustomSky.uniformValues(pixelAngle);
            for (int i = 0; i < values.length; i++) data.putFloat((16 + i) * Float.BYTES, values[i]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(0, UNIFORM_BYTES), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        return uniforms.slice(0, UNIFORM_BYTES);
    }
}
