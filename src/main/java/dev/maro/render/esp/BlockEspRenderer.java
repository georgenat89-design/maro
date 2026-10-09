package dev.maro.render.esp;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BlockESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Draws Block ESP's tracers and its bloom.
 *
 * <p>The boxes are ordinary world geometry ({@link Renderer3D}). With bloom on they are drawn a
 * second time into {@link #glowTarget()}, a framebuffer of our own cleared to transparent black each
 * frame. Just before the interface, {@link #composite()} adds the tracers' bright cores to it too,
 * blurs it at half resolution (across, down, then across and down again twice as wide), adds the
 * result onto the frame as light, and last draws the tracers themselves over it, so they stay crisp
 * inside their own glow. Tracers are worked out per pixel in {@code block_esp_tracers.fsh}.
 */
public final class BlockEspRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Tracers in one draw; matches MAX_TRACERS in block_esp_tracers.fsh. More are drawn in further batches. */
    private static final int BATCH = 128;
    /** The most tracers in a frame. */
    public static final int MAX_TRACERS = 2000;

    private static final RenderPipeline TRACERS = RenderPipeline.builder()
        .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/block_esp_tracers"))
        .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
        .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/block_esp_tracers"))
        .withUniform("TracerData", UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();
    private static final RenderPipeline BLUR = glowPipeline("pipeline/block_esp_blur", null);
    private static final RenderPipeline BLOOM = glowPipeline("pipeline/block_esp_bloom", new BlendFunction(SourceFactor.ONE, DestFactor.ONE));

    private static RenderPipeline glowPipeline(String location, BlendFunction blend) {
        var builder = RenderPipeline.builder()
            .withLocation(Identifier.of(Maro.MOD_ID, location))
            .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
            .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/block_esp_glow"))
            .withSampler("u_Source")
            .withUniform("GlowData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false);
        if (blend != null) builder.withBlend(blend);
        return builder.build();
    }

    /** TracerData: Info, Style, then a line and a colour per tracer. */
    private static final int TRACER_BYTES = (2 + BATCH * 2) * 16;
    private static final int LINES_AT = 2 * 16, COLORS_AT = LINES_AT + BATCH * 16;
    /** Uniform slices start on 256-byte boundaries, the widest alignment drivers ask for. */
    private static final int TRACER_STRIDE = (TRACER_BYTES + 255) / 256 * 256;
    private static final int GLOW_PASSES = 5, GLOW_STRIDE = 256;

    // Vertex z is a tag, as in player_esp.vsh: n marks the quad of tracer n - 1.
    private static final ByteBuffer TRACER_VERTICES = BufferUtils.createByteBuffer(BATCH * 4 * 3 * Float.BYTES);
    private static final ByteBuffer TRACER_INDICES = BufferUtils.createByteBuffer(BATCH * 6 * Integer.BYTES);
    private static final ByteBuffer SCREEN_VERTICES = BufferUtils.createByteBuffer(4 * 3 * Float.BYTES);
    private static final ByteBuffer SCREEN_INDICES = BufferUtils.createByteBuffer(6 * Integer.BYTES);

    static {
        int[] quad = {0, 1, 2, 0, 2, 3};
        for (int q = 0; q < BATCH; q++) {
            for (int i = 0; i < quad.length; i++) TRACER_INDICES.putInt((q * 6 + i) * Integer.BYTES, q * 4 + quad[i]);
        }
        float[] corners = {-1, -1, -1, 1, 1, 1, 1, -1};
        for (int i = 0; i < 4; i++) {
            SCREEN_VERTICES.putFloat(i * 12, corners[i * 2]);
            SCREEN_VERTICES.putFloat(i * 12 + 4, corners[i * 2 + 1]);
            SCREEN_VERTICES.putFloat(i * 12 + 8, 0f);
        }
        for (int i = 0; i < quad.length; i++) SCREEN_INDICES.putInt(i * Integer.BYTES, quad[i]);
    }

    private static final Matrix4f viewProjection = new Matrix4f();
    private static final FrustumIntersection frustum = new FrustumIntersection();
    private static boolean haveMatrices;

    /** This frame's tracers: target in NDC (off screen for blocks behind you), colour, weight. */
    private static final float[] targets = new float[MAX_TRACERS * 2];
    private static final int[] colors = new int[MAX_TRACERS];
    private static final float[] weights = new float[MAX_TRACERS];
    private static int count;

    private static SimpleFramebuffer glow, halfA, halfB;
    /** The glow target was cleared and drawn into this frame. */
    private static boolean glowDrawn;
    private static GpuBuffer tracerUniforms, glowUniforms;
    /** How many batches tracerUniforms has room for, two slices each (the bloom pass and the frame). */
    private static int tracerBatches;
    private static BlockESP module;

    private BlockEspRenderer() {
    }

    private static BlockESP module() {
        if (module == null) module = ModuleManager.get(BlockESP.class);
        return module;
    }

    /** Start of world rendering: forget last frame's tracers and remember the matrices. */
    public static void beginFrame(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        count = 0;
        glowDrawn = false;
        viewProjection.set(projectionMatrix).mul(positionMatrix);
        frustum.set(viewProjection);
        haveMatrices = true;
    }

    /**
     * Where a point in the world lands on screen this frame, in GUI coordinates {x, y} for a screen
     * {@code guiWidth} by {@code guiHeight}, or null if it is behind the camera.
     */
    public static float[] toScreen(double x, double y, double z, Vec3d camera, int guiWidth, int guiHeight) {
        if (!haveMatrices) return null;
        Vector4f p = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
        viewProjection.transform(p);
        if (p.w <= 0.05f) return null;
        return new float[] {(p.x / p.w + 1f) / 2f * guiWidth, (1f - p.y / p.w) / 2f * guiHeight};
    }

    /** Whether a box, in world coordinates, is at least partly on screen this frame. */
    public static boolean inView(double x1, double y1, double z1, double x2, double y2, double z2, Vec3d camera) {
        if (!haveMatrices) return true;
        return frustum.testAab((float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z),
                (float) (x2 - camera.x), (float) (y2 - camera.y), (float) (z2 - camera.z));
    }

    /** The framebuffer the boxes' glow is drawn into, or null before bloom is first used. */
    public static Framebuffer glowTarget() {
        return glow;
    }

    /** Makes the glow target the size of the frame and clears it, ready for this frame's boxes. */
    public static void prepareGlow() {
        Framebuffer main = mc.getFramebuffer();
        int width = main.textureWidth, height = main.textureHeight;
        if (width <= 0 || height <= 0) return;
        glow = sized(glow, "maro block esp glow", width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glow.getColorAttachment(), 0);
        glowDrawn = true;
    }

    public static boolean glowReady() {
        return glowDrawn;
    }

    private static SimpleFramebuffer sized(SimpleFramebuffer target, String name, int width, int height) {
        if (target == null) return new SimpleFramebuffer(name, width, height, false);
        if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
        return target;
    }

    /** Queues a tracer to the point {@code (x, y, z)}. */
    public static void addTracer(double x, double y, double z, Vec3d camera, int color, float weight) {
        if (!haveMatrices || count >= MAX_TRACERS) return;
        Vector4f p = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
        viewProjection.transform(p);
        float nx, ny;
        if (p.w > 0.05f) {
            nx = p.x / p.w;
            ny = p.y / p.w;
        } else {
            // Behind the camera: point off the screen edge on the side it is on.
            float len = (float) Math.hypot(p.x, p.y);
            nx = len < 1e-4f ? 0f : p.x / len * 3f;
            ny = len < 1e-4f ? -3f : p.y / len * 3f;
        }
        float far = Math.max(Math.abs(nx), Math.abs(ny));
        if (far > 3f) {
            nx *= 3f / far;
            ny *= 3f / far;
        }
        targets[count * 2] = nx;
        targets[count * 2 + 1] = ny;
        colors[count] = color;
        weights[count++] = weight;
    }

    /** How many tracers are queued this frame; for tests. */
    public static int tracerCount() {
        return count;
    }

    // ---- composite --------------------------------------------------------------------------

    /** Bloom, then tracers, over the frame. Called after the world and hand, before the interface. */
    public static void composite() {
        BlockESP m = module();
        int tracers = count;
        boolean bloom = glowDrawn;
        count = 0;
        glowDrawn = false;
        if (m == null || !m.isEnabled() || mc.world == null || (tracers == 0 && !bloom)) return;

        Framebuffer main = mc.getFramebuffer();
        if (main == null || main.getColorAttachmentView() == null) return;
        try {
            float[][] lines = tracers > 0 ? lines(tracers, main.textureWidth, main.textureHeight) : null;
            if (bloom && glow != null) {
                if (tracers > 0 && m.tracerBloom()) drawTracers(glow, lines, tracers, true);
                bloom(main, m);
            }
            if (tracers > 0) drawTracers(main, lines, tracers, false);
        } catch (RuntimeException e) {
            // One bad frame must not take the renderer down; the module reports and switches off.
            m.renderFailed(e);
        }
    }

    /** Each tracer as {x0, y0, x1, y1} in framebuffer pixels, from the start point to the block. */
    private static float[][] lines(int n, int width, int height) {
        float halfW = width / 2f, halfH = height / 2f;
        float startX = halfW, startY = module().tracersFromBottom() ? 0f : halfH;
        float[][] lines = new float[n][];
        for (int i = 0; i < n; i++) {
            lines[i] = new float[] {startX, startY, (targets[i * 2] + 1) * halfW, (targets[i * 2 + 1] + 1) * halfH};
        }
        return lines;
    }

    private static void drawTracers(Framebuffer target, float[][] lines, int n, boolean coreOnly) {
        int height = target.textureHeight;
        BlockESP m = module();
        float width = m.tracerWidthPx(height);
        int batches = (n + BATCH - 1) / BATCH;
        if (tracerUniforms == null || tracerBatches < batches) {
            if (tracerUniforms != null) tracerUniforms.close();
            tracerBatches = Math.max(batches, 4);
            tracerUniforms = RenderSystem.getDevice().createBuffer(() -> "maro block esp tracers",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) TRACER_STRIDE * 2 * tracerBatches);
        }
        // Tracers go in batches of BATCH, each its own draw with its own slice of the uniforms.
        for (int batch = 0; batch < batches; batch++) {
            int first = batch * BATCH, k = Math.min(BATCH, n - first);
            for (int i = 0; i < k; i++) writeTracerQuad(i, lines[first + i], width, target.textureWidth, height);

            GpuBufferSlice data = tracerUniforms.slice((long) (batch * 2 + (coreOnly ? 1 : 0)) * TRACER_STRIDE, TRACER_BYTES);
            ByteBuffer bytes = MemoryUtil.memCalloc(TRACER_BYTES);
            try {
                bytes.putFloat(0, k);
                bytes.putFloat(4, width);
                bytes.putFloat(8, m.tracerHalo());
                bytes.putFloat(12, m.seconds());
                bytes.putFloat(16, m.tracerPackets() ? 1f : 0f);
                bytes.putFloat(20, m.packetSpeed());
                bytes.putFloat(24, 14f * height / 1080f);
                bytes.putFloat(28, coreOnly ? 1f : 0f);
                for (int i = 0; i < k; i++) {
                    for (int c = 0; c < 4; c++) bytes.putFloat(LINES_AT + i * 16 + c * 4, lines[first + i][c]);
                    int color = colors[first + i];
                    bytes.putFloat(COLORS_AT + i * 16, (color >> 16 & 0xFF) / 255f);
                    bytes.putFloat(COLORS_AT + i * 16 + 4, (color >> 8 & 0xFF) / 255f);
                    bytes.putFloat(COLORS_AT + i * 16 + 8, (color & 0xFF) / 255f);
                    bytes.putFloat(COLORS_AT + i * 16 + 12, weights[first + i] * (color >>> 24) / 255f);
                }
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(data, bytes);
            } finally {
                MemoryUtil.memFree(bytes);
            }

            GpuBuffer vertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(TRACER_VERTICES);
            GpuBuffer indices = VertexFormats.POSITION.uploadImmediateIndexBuffer(TRACER_INDICES);
            RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "maro block esp tracers", target.getColorAttachmentView(), OptionalInt.empty());
            try {
                pass.setPipeline(TRACERS);
                pass.setUniform("TracerData", data);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, k * 6, 1);
            } finally {
                pass.close();
            }
        }
    }

    /** A quad round tracer {@code i}, wide enough for its glow and the dot at its end. */
    private static void writeTracerQuad(int i, float[] line, float width, int screenWidth, int screenHeight) {
        float dx = line[2] - line[0], dy = line[3] - line[1];
        float length = (float) Math.hypot(dx, dy);
        float[] corners;
        if (length < 1f) {
            corners = new float[8];
        } else {
            float sigma = width * 1.4f + 1.5f;
            float pad = Math.max(sigma * 3f, width * 3.75f + 2f) + 2f;
            float ux = dx / length, uy = dy / length;
            float nx = -uy * pad, ny = ux * pad, ex = ux * pad, ey = uy * pad;
            corners = new float[] {
                line[0] - ex + nx, line[1] - ey + ny,
                line[0] - ex - nx, line[1] - ey - ny,
                line[2] + ex - nx, line[3] + ey - ny,
                line[2] + ex + nx, line[3] + ey + ny};
            for (int k = 0; k < 8; k += 2) {
                corners[k] = corners[k] / screenWidth * 2f - 1f;
                corners[k + 1] = corners[k + 1] / screenHeight * 2f - 1f;
            }
        }
        for (int v = 0; v < 4; v++) {
            int at = (i * 4 + v) * 3 * Float.BYTES;
            TRACER_VERTICES.putFloat(at, corners[v * 2]);
            TRACER_VERTICES.putFloat(at + Float.BYTES, corners[v * 2 + 1]);
            TRACER_VERTICES.putFloat(at + 2 * Float.BYTES, i + 1);
        }
    }

    /** Blurs the glow target at half size, twice and wider the second time, and adds it onto the frame. */
    private static void bloom(Framebuffer main, BlockESP m) {
        int width = Math.max(1, glow.textureWidth / 2), height = Math.max(1, glow.textureHeight / 2);
        halfA = sized(halfA, "maro block esp bloom a", width, height);
        halfB = sized(halfB, "maro block esp bloom b", width, height);
        if (glowUniforms == null) {
            glowUniforms = RenderSystem.getDevice().createBuffer(() -> "maro block esp bloom",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) GLOW_STRIDE * GLOW_PASSES);
        }
        // The blur's reach follows the screen's height, so it looks the same at any resolution.
        float spread = m.bloomSize() * main.textureHeight / 1080f;
        glowPass(0, BLUR, glow, halfA, spread / width, 0, 0, 0);
        glowPass(1, BLUR, halfA, halfB, 0, spread / height, 0, 0);
        glowPass(2, BLUR, halfB, halfA, 2 * spread / width, 0, 0, 0);
        glowPass(3, BLUR, halfA, halfB, 0, 2 * spread / height, 0, 0);
        glowPass(4, BLOOM, halfB, main, 0, 0, 1, m.bloomStrength());
    }

    private static void glowPass(int slot, RenderPipeline pipeline, Framebuffer source, Framebuffer target,
                                 float stepX, float stepY, float mode, float strength) {
        GpuBufferSlice data = glowUniforms.slice((long) slot * GLOW_STRIDE, 16);
        ByteBuffer bytes = MemoryUtil.memCalloc(16);
        try {
            bytes.putFloat(0, stepX);
            bytes.putFloat(4, stepY);
            bytes.putFloat(8, mode);
            bytes.putFloat(12, strength);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(data, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
        GpuBuffer vertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(SCREEN_VERTICES);
        GpuBuffer indices = VertexFormats.POSITION.uploadImmediateIndexBuffer(SCREEN_INDICES);
        RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(() -> "maro block esp bloom", target.getColorAttachmentView(), OptionalInt.empty());
        try {
            pass.setPipeline(pipeline);
            pass.setUniform("GlowData", data);
            pass.bindTexture("u_Source", source.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
            pass.drawIndexed(0, 0, 6, 1);
        } finally {
            pass.close();
        }
    }
}
