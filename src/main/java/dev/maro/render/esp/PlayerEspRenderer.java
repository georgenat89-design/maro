package dev.maro.render.esp;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.PlayerESP;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Draws the Player ESP.
 *
 * <p>Each frame, the players the module tracks are rendered a second time as flat silhouettes into
 * a framebuffer of our own ({@link dev.maro.mixin.PlayerEspWorldRendererMixin} does that, through
 * the game's outline render layers). Just before the GUI is drawn, {@link #composite()} runs
 * {@code player_esp.fsh} over the frame: it reads the silhouettes and paints the fill, contour and
 * glow on top of the world. The silhouettes ignore the world's depth, so players show through walls.
 *
 * <p>The pass only covers the screen rectangle around the tracked players (plus the contour and
 * glow), so the per-pixel distance search costs nothing elsewhere on screen.
 */
public final class PlayerEspRenderer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/player_esp"))
        .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
        .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
        .withSampler("u_Mask")
        .withUniform("EspData", UniformType.UNIFORM_BUFFER)
        .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
        .withDepthWrite(false)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .build();

    /** Players whose contour and glow are sized individually; matches MAX_PLAYERS in player_esp.fsh. */
    private static final int MAX_PLAYERS = 16;
    /** EspData in player_esp.fsh: eight vec4s of settings, a rect per player, a scale per player. */
    private static final int UNIFORM_BYTES = (8 + MAX_PLAYERS + MAX_PLAYERS / 4) * 16;
    /** On-screen player height, as a share of the screen, below which contour and glow start to thin. */
    private static final float FULL_SIZE_HEIGHT = 0.2f;
    private static final float MIN_SIZE_SCALE = 0.3f;

    private static final SilhouetteBuffers.Provider PROVIDER = new SilhouetteBuffers.Provider();

    private static final ByteBuffer VERTICES = BufferUtils.createByteBuffer(4 * 3 * Float.BYTES);
    private static final ByteBuffer INDICES = BufferUtils.createByteBuffer(6 * Integer.BYTES);

    static {
        int[] quad = {0, 1, 2, 0, 2, 3};
        for (int i = 0; i < quad.length; i++) INDICES.putInt(i * Integer.BYTES, quad[i]);
    }

    private static SimpleFramebuffer mask;
    private static GpuBuffer uniforms;
    private static PlayerESP module;

    /** This frame's matrices, from the start of world rendering. */
    private static final Matrix4f viewProjection = new Matrix4f();
    private static boolean haveMatrices;

    /** Silhouettes were drawn this frame and are waiting to be composited. */
    private static boolean pending;

    /** Screen rectangle around the tracked players, in normalized device coordinates. */
    private static float minX, minY, maxX, maxY;
    private static boolean fullscreen;

    /** Each drawn player's rect in NDC (x0, y0, x1, y1) and its contour/glow size scale. */
    private static final float[] playerRects = new float[MAX_PLAYERS * 4];
    private static final float[] playerScales = new float[MAX_PLAYERS];
    private static int playerCount;

    private PlayerEspRenderer() {
    }

    private static PlayerESP module() {
        if (module == null) module = ModuleManager.get(PlayerESP.class);
        return module;
    }

    public static boolean active() {
        PlayerESP m = module();
        return m != null && m.isEnabled() && mc.world != null && mc.player != null;
    }

    // ---- frame ------------------------------------------------------------------------------

    /** Start of world rendering: clear last frame's silhouettes and remember the matrices. */
    public static void beginFrame(Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        pending = false;
        if (!active()) return;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;

        // Silhouettes are drawn supersampled so the composite can read real edge coverage; above
        // 1440p the screen's own pixels are fine enough and the memory is not worth it.
        int maskScale = height <= 1440 ? 2 : 1;
        int maskWidth = width * maskScale, maskHeight = height * maskScale;
        if (mask == null) mask = new SimpleFramebuffer("maro player esp", maskWidth, maskHeight, true);
        else if (mask.textureWidth != maskWidth || mask.textureHeight != maskHeight) mask.resize(maskWidth, maskHeight);

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorAttachment(), 0);

        viewProjection.set(projectionMatrix).mul(positionMatrix);
        haveMatrices = true;
        minX = minY = Float.POSITIVE_INFINITY;
        maxX = maxY = Float.NEGATIVE_INFINITY;
        fullscreen = false;
        playerCount = 0;
    }

    /** The framebuffer silhouettes are drawn into, or null before the first frame. */
    public static Framebuffer mask() {
        return mask;
    }

    public static SilhouetteBuffers.Provider provider() {
        return PROVIDER;
    }

    public static boolean shouldDraw(Entity entity) {
        return module().shouldDraw(entity);
    }

    public static int color(Entity entity) {
        return module().playerColor(entity);
    }

    /**
     * Grows the composite rectangle to cover {@code entity}, drawn at the interpolated
     * {@code (x, y, z)}. The box is padded for swinging limbs, held items and capes.
     */
    public static void include(Entity entity, double x, double y, double z, Vec3d camera) {
        if (!haveMatrices) {
            fullscreen = true;
            return;
        }

        Box body = entity.getBoundingBox().offset(x - entity.getX(), y - entity.getY(), z - entity.getZ());
        Box box = body.expand(0.7, 0.4, 0.7);
        float x0 = Float.POSITIVE_INFINITY, y0 = Float.POSITIVE_INFINITY;
        float x1 = Float.NEGATIVE_INFINITY, y1 = Float.NEGATIVE_INFINITY;
        boolean behind = false;
        Vector4f corner = new Vector4f();
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? box.minX : box.maxX;
            double cy = (i & 2) == 0 ? box.minY : box.maxY;
            double cz = (i & 4) == 0 ? box.minZ : box.maxZ;
            corner.set((float) (cx - camera.x), (float) (cy - camera.y), (float) (cz - camera.z), 1f);
            viewProjection.transform(corner);

            // A corner at or behind the camera has no meaningful projection; cover the screen.
            if (corner.w <= 0.05f) {
                behind = true;
                break;
            }

            float nx = corner.x / corner.w, ny = corner.y / corner.w;
            x0 = Math.min(x0, nx);
            y0 = Math.min(y0, ny);
            x1 = Math.max(x1, nx);
            y1 = Math.max(y1, ny);
        }

        float scale = 1f;
        if (behind) {
            fullscreen = true;
            x0 = y0 = -1;
            x1 = y1 = 1;
        } else {
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            maxX = Math.max(maxX, x1);
            maxY = Math.max(maxY, y1);
            // NDC spans 2 per screen; the padded box is about 1.45x the body's height.
            float share = (y1 - y0) / 2f * (float) (body.getLengthY() / box.getLengthY());
            scale = Math.max(MIN_SIZE_SCALE, Math.min(1f, share / FULL_SIZE_HEIGHT));
        }

        if (playerCount < MAX_PLAYERS) {
            int at = playerCount * 4;
            playerRects[at] = x0;
            playerRects[at + 1] = y0;
            playerRects[at + 2] = x1;
            playerRects[at + 3] = y1;
            playerScales[playerCount++] = scale;
        }
    }

    /** Whether a point in the world is in front of the camera and roughly on screen this frame. */
    public static boolean onScreen(double x, double y, double z, Vec3d camera) {
        if (!haveMatrices) return true;
        Vector4f p = new Vector4f((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1f);
        viewProjection.transform(p);
        if (p.w <= 0.05f) return false;
        return Math.abs(p.x / p.w) < 1.6f && Math.abs(p.y / p.w) < 1.6f;
    }

    public static void markDrawn() {
        pending = true;
    }

    public static boolean pending() {
        return pending;
    }

    // ---- composite --------------------------------------------------------------------------

    /** Paints the effect over the frame. Called after the world and hand, before the GUI. */
    public static void composite() {
        if (!pending || mask == null || !active()) return;
        pending = false;

        Framebuffer target = mc.getFramebuffer();
        if (target == null || target.getColorAttachmentView() == null) return;

        float[] rect = rect(target.textureWidth, target.textureHeight);
        if (rect == null) return;

        try {
            writeQuad(rect);
            GpuBufferSlice data = writeUniforms();

            GpuBuffer vertices = VertexFormats.POSITION.uploadImmediateVertexBuffer(VERTICES);
            GpuBuffer indices = VertexFormats.POSITION.uploadImmediateIndexBuffer(INDICES);

            RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "maro player esp", target.getColorAttachmentView(), OptionalInt.empty());
            try {
                pass.setPipeline(PIPELINE);
                pass.setUniform("EspData", data);
                pass.bindTexture("u_Mask", mask.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            } finally {
                pass.close();
            }
        } catch (RuntimeException e) {
            // One bad frame must not take the renderer down; the module reports and switches off.
            module().renderFailed(e);
        }
    }

    /** The rectangle to shade in NDC, padded for the contour and glow, or null if off screen. */
    private static float[] rect(int width, int height) {
        if (fullscreen || !haveMatrices) return new float[] {-1, -1, 1, 1};
        if (minX > maxX || minY > maxY) return null;

        float padPx = module().effectReach() + 4;
        float padX = padPx * 2f / width, padY = padPx * 2f / height;
        float x0 = Math.max(-1, minX - padX), y0 = Math.max(-1, minY - padY);
        float x1 = Math.min(1, maxX + padX), y1 = Math.min(1, maxY + padY);
        if (x0 >= x1 || y0 >= y1) return null;
        return new float[] {x0, y0, x1, y1};
    }

    private static void writeQuad(float[] r) {
        float[] xy = {r[0], r[1], r[0], r[3], r[2], r[3], r[2], r[1]};
        for (int i = 0; i < 4; i++) {
            int at = i * 3 * Float.BYTES;
            VERTICES.putFloat(at, xy[i * 2]);
            VERTICES.putFloat(at + Float.BYTES, xy[i * 2 + 1]);
            VERTICES.putFloat(at + 2 * Float.BYTES, 0f);
        }
    }

    private static GpuBufferSlice writeUniforms() {
        if (uniforms == null) {
            uniforms = RenderSystem.getDevice().createBuffer(() -> "maro player esp uniforms",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_BYTES);
        }

        ByteBuffer data = MemoryUtil.memCalloc(UNIFORM_BYTES);
        try {
            Framebuffer target = mc.getFramebuffer();
            float[] values = module().uniformValues();
            values[values.length - 1] = mask.textureHeight / (float) target.textureHeight;
            for (int i = 0; i < values.length; i++) data.putFloat(i * Float.BYTES, values[i]);

            // PlayerInfo, then a pixel rect per player, then the scales packed four to a vec4.
            int info = 7 * 16, rects = info + 16, scales = rects + MAX_PLAYERS * 16;
            data.putFloat(info, playerCount);
            float halfW = target.textureWidth / 2f, halfH = target.textureHeight / 2f;
            for (int i = 0; i < playerCount; i++) {
                int at = rects + i * 16;
                data.putFloat(at, (playerRects[i * 4] + 1) * halfW - 4);
                data.putFloat(at + 4, (playerRects[i * 4 + 1] + 1) * halfH - 4);
                data.putFloat(at + 8, (playerRects[i * 4 + 2] + 1) * halfW + 4);
                data.putFloat(at + 12, (playerRects[i * 4 + 3] + 1) * halfH + 4);
                data.putFloat(scales + i * 4, playerScales[i]);
            }
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(0, UNIFORM_BYTES), data);
        } finally {
            MemoryUtil.memFree(data);
        }

        return uniforms.slice(0, UNIFORM_BYTES);
    }
}
