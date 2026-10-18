package dev.maro.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalInt;

/** Render the whole menu once, then project it. Text, textures and scissors share one plane. */
public final class ScreenGuiSurface {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/screen_gui"))
            .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
            .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/screen_gui"))
            .withSampler("Menu").withUniform("ScreenGuiData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();
    private static final GuiRenderState STATE = new GuiRenderState();
    private static final ByteBuffer DATA = BufferUtils.createByteBuffer(48);
    private static GuiRenderer renderer;
    private static SimpleFramebuffer target;
    private static GpuBuffer uniforms, vertices, indices;
    private static ScreenGuiPose pending;
    private static boolean drawing;
    private static float rasterScale = 1;
    private static int frames;

    private ScreenGuiSurface() { }

    public static DrawContext begin(MinecraftClient client, int mouseX, int mouseY, ScreenGuiPose pose) {
        STATE.clear();
        var main = client.getFramebuffer();
        // Bound GPU memory at high resolutions while retaining 2x detail at 1080p.
        rasterScale = qualityScale(main.textureWidth, main.textureHeight);
        pending = pose;
        return new DrawContext(client, STATE, mouseX, mouseY);
    }

    /** Only our renderer's final GUI pass is redirected; the world, HUD and item atlas stay vanilla. */
    public static Framebuffer targetOr(Framebuffer original) { return drawing ? target : original; }
    public static float textScale(DrawContext ctx) {
        return ((dev.maro.mixin.DrawContextAccessor)ctx).maro$getState() == STATE ? rasterScale : dev.maro.gui.spatial.SpatialCanvas.textScale(ctx);
    }
    public static float qualityScale(int w, int h) {
        return (float)Math.max(1, Math.min(2, Math.min(4096d / Math.max(w,h), Math.sqrt(10_000_000d / ((double)w*h)))));
    }
    public static boolean scissor(net.minecraft.client.gui.ScreenRect area, com.mojang.blaze3d.systems.RenderPass pass) {
        if (!drawing || target == null) return false;
        var window = MinecraftClient.getInstance().getWindow();
        double sx = (double)target.textureWidth / window.getFramebufferWidth() * window.getScaleFactor();
        double sy = (double)target.textureHeight / window.getFramebufferHeight() * window.getScaleFactor();
        int left = (int)Math.floor(area.getLeft()*sx), right = (int)Math.ceil(area.getRight()*sx);
        int top = (int)Math.floor(area.getTop()*sy), bottom = (int)Math.ceil(area.getBottom()*sy);
        pass.enableScissor(left, target.textureHeight-bottom, Math.max(0,right-left), Math.max(0,bottom-top));
        return true;
    }

    /** Called after vanilla's GUI pass, with its current fog buffer. Recursion is explicitly guarded. */
    public static void finish(GpuBufferSlice fog) {
        if (drawing || pending == null) return;
        ScreenGuiPose pose = pending;
        pending = null;
        MinecraftClient client = MinecraftClient.getInstance();
        var main = client.getFramebuffer();
        try {
            if (renderer == null) renderer = new GuiRenderer(STATE, client.getBufferBuilders().getEntityVertexConsumers(),
                    client.gameRenderer.getEntityRenderCommandQueue(), client.gameRenderer.getEntityRenderDispatcher(), List.of());
            int w = main.textureWidth, h = main.textureHeight;
            int tw = (int)Math.ceil(w*rasterScale), th = (int)Math.ceil(h*rasterScale);
            if (target == null) target = new SimpleFramebuffer("Maro Screen GUI", tw, th, true);
            else if (target.textureWidth != tw || target.textureHeight != th) target.resize(tw, th);
            var device = RenderSystem.getDevice();
            var encoder = device.createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorAttachment(), 0, target.getDepthAttachment(), 1);
            drawing = true;
            try { renderer.incrementFrame(); renderer.render(fog); }
            finally { drawing = false; }
            if (uniforms == null) uniforms = device.createBuffer(() -> "Maro Screen GUI pose", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 256);
            if (vertices == null) {
                ByteBuffer quad = BufferUtils.createByteBuffer(48);
                for (float[] xy : new float[][] {{-1,-1},{-1,1},{1,1},{1,-1}}) quad.putFloat(xy[0]).putFloat(xy[1]).putFloat(0);
                quad.flip();
                vertices = device.createBuffer(() -> "Maro Screen GUI quad", GpuBuffer.USAGE_VERTEX, quad);
                ByteBuffer index = BufferUtils.createByteBuffer(24);
                for (int i : new int[] {0,1,2,0,2,3}) index.putInt(i);
                index.flip();
                indices = device.createBuffer(() -> "Maro Screen GUI indices", GpuBuffer.USAGE_INDEX, index);
            }
            var m = pose.inverse();
            DATA.putFloat(0, m.m00()).putFloat(4, m.m10()).putFloat(8, m.m20()).putFloat(12, w);
            DATA.putFloat(16, m.m01()).putFloat(20, m.m11()).putFloat(24, m.m21()).putFloat(28, h);
            DATA.putFloat(32, m.m02()).putFloat(36, m.m12()).putFloat(40, m.m22()).putFloat(44, pose.active() ? 1 : 0);
            encoder.writeToBuffer(uniforms.slice(0, 48), DATA);
            var sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
            try (var pass = encoder.createRenderPass(() -> "Maro Screen GUI", main.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("ScreenGuiData", uniforms.slice(0, 48));
                pass.bindTexture("Menu", target.getColorAttachmentView(), sampler);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, VertexFormat.IndexType.INT);
                pass.drawIndexed(0, 0, 6, 1);
            }
            frames++;
        } catch (RuntimeException error) {
            Maro.LOGGER.error("Screen GUI could not draw its floating menu", error);
            ClientSettings.screenGui.set(false);
            release();
        }
    }

    public static void release() {
        pending = null;
        STATE.clear();
        rasterScale = 1;
        if (renderer != null) { renderer.close(); renderer = null; }
        if (target != null) { target.delete(); target = null; }
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (vertices != null) { vertices.close(); vertices = null; }
        if (indices != null) { indices.close(); indices = null; }
    }

    public static int drawnFrames() { return frames; }
    public static boolean hasResources() { return renderer != null || target != null || uniforms != null; }
    public static int textureWidth() { return target == null ? 0 : target.textureWidth; }
    public static int textureHeight() { return target == null ? 0 : target.textureHeight; }
}
