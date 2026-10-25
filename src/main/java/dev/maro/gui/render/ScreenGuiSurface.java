package dev.maro.gui.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
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
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalInt;

/** Render the whole menu once, then project it. Text, textures and scissors share one plane. */
public final class ScreenGuiSurface {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/screen_gui"))
            .withVertexShader(Identifier.of(Maro.MOD_ID, "core/player_esp"))
            .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/screen_gui"))
            .withSampler("Menu").withSampler("WorldDepth").withUniform("ScreenGuiData", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).withCull(false).build();
    private static final GuiRenderState STATE = new GuiRenderState();
    private static final int DATA_BYTES = 64;
    private static final ByteBuffer DATA = BufferUtils.createByteBuffer(DATA_BYTES);
    private static GuiRenderer renderer;
    private static SimpleFramebuffer target;
    private static GpuTexture worldDepth;
    private static GpuTextureView worldDepthView;
    private static GpuBuffer uniforms, vertices, indices;
    private static ScreenGuiPose pending;
    private static boolean drawing, captureDepth, depthReady, reloadListener;
    private static float rasterScale = 1;
    private static int frames;
    private static long depthFrames;

    private ScreenGuiSurface() { }

    /** Each canvas owns its native special renderers and their picture-in-picture targets. */
    private static GuiRenderer renderer() {
        if (renderer == null) {
            var client = MinecraftClient.getInstance();
            var buffers = client.getBufferBuilders().getEntityVertexConsumers();
            var sprites = client.getAtlasManager();
            renderer = new GuiRenderer(STATE, buffers, client.gameRenderer.getEntityRenderCommandQueue(),
                    client.gameRenderer.getEntityRenderDispatcher(), List.of(
                    new net.minecraft.client.gui.render.EntityGuiElementRenderer(buffers, client.getEntityRenderDispatcher()),
                    new net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer(buffers),
                    new net.minecraft.client.gui.render.BookModelGuiElementRenderer(buffers),
                    new net.minecraft.client.gui.render.BannerResultGuiElementRenderer(buffers, sprites),
                    new net.minecraft.client.gui.render.SignGuiElementRenderer(buffers, sprites),
                    new net.minecraft.client.gui.render.ProfilerChartGuiElementRenderer(buffers)));
        }
        return renderer;
    }

    /** Register dynamic special subjects on the renderer that actually consumes this context. */
    public static GuiRenderer rendererFor(DrawContext context) {
        if (((dev.maro.mixin.DrawContextAccessor) context).maro$getState() == STATE) return renderer();
        return ((dev.maro.mixin.GameRendererSpatialAccessor) MinecraftClient.getInstance().gameRenderer).maro$guiRenderer();
    }

    /** Read-only lifecycle access; never creates a canvas while closing a preview. */
    public static GuiRenderer existingRenderer() { return renderer; }

    public static DrawContext begin(MinecraftClient client, int mouseX, int mouseY, ScreenGuiPose pose) {
        if (!reloadListener) {
            reloadListener = true;
            net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                    .registerReloader(Identifier.of(Maro.MOD_ID, "screen_gui_surface"),
                            (net.minecraft.resource.SynchronousResourceReloader) manager ->
                                    MinecraftClient.getInstance().execute(ScreenGuiSurface::release));
        }
        STATE.clear();
        var main = client.getFramebuffer();
        // Bound GPU memory at high resolutions while retaining 2x detail at 1080p.
        rasterScale = qualityScale(main.textureWidth, main.textureHeight);
        pending = pose;
        return new DrawContext(client, STATE, mouseX, mouseY);
    }

    /** Observe the actual terrain projection, including FOV, stretch and view transforms. */
    public static void worldBegin(net.minecraft.client.render.Camera camera, Matrix4fc view, Matrix4fc projection) {
        depthReady = false;
        var client = MinecraftClient.getInstance();
        captureDepth = client.currentScreen instanceof dev.maro.gui.ClickGuiScreen gui && gui.worldControls();
        if (captureDepth) {
            var pose = ((dev.maro.gui.ClickGuiScreen) client.currentScreen).screenPose();
            pose.worldProjection(camera, view, projection);
            captureDepth = pose.visible();
        }
    }

    /** WorldRenderer has completed; the hand's subsequent depth clear has not happened yet. */
    public static void worldEnd() {
        if (!captureDepth) return;
        captureDepth = false;
        var client = MinecraftClient.getInstance();
        var source = client.getFramebuffer().getDepthAttachment();
        if (source == null || source.isClosed()) return;
        int w = source.getWidth(0), h = source.getHeight(0);
        if (w < 1 || h < 1) return;
        try {
            var device = RenderSystem.getDevice();
            if (worldDepth == null || worldDepth.getWidth(0) != w || worldDepth.getHeight(0) != h
                    || worldDepth.getFormat() != source.getFormat()) {
                releaseDepth();
                worldDepth = device.createTexture(() -> "Maro Screen GUI terrain depth",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        source.getFormat(), w, h, 1, 1);
                worldDepthView = device.createTextureView(worldDepth);
            }
            device.createCommandEncoder().copyTextureToTexture(source, worldDepth, 0, 0, 0, 0, 0, w, h);
            depthReady = true;
            depthFrames++;
        } catch (RuntimeException error) {
            Maro.LOGGER.error("Screen GUI could not preserve terrain depth", error);
            ClientSettings.screenGui.set(false);
            release();
        }
    }

    private static void releaseDepth() {
        depthReady = false;
        if (worldDepthView != null) { worldDepthView.close(); worldDepthView = null; }
        if (worldDepth != null) { worldDepth.close(); worldDepth = null; }
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
        if (drawing || pending == null || dev.maro.render.capture.CapturePass.active()) return;
        ScreenGuiPose pose = pending;
        pending = null;
        MinecraftClient client = MinecraftClient.getInstance();
        var main = client.getFramebuffer();
        // No stale plane or through-wall fallback while resources/depth are unavailable.
        if (!pose.active() || !pose.visible() || !depthReady || worldDepth == null
                || client.world == null || worldDepth.getWidth(0) != main.textureWidth
                || worldDepth.getHeight(0) != main.textureHeight) { STATE.clear(); return; }
        try {
            renderer();
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
            var depth = pose.clipDepthRow();
            DATA.putFloat(48, depth.x()).putFloat(52, depth.y()).putFloat(56, depth.z()).putFloat(60, 0);
            encoder.writeToBuffer(uniforms.slice(0, DATA_BYTES), DATA);
            var sampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, false);
            var depthSampler = RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
            try (var pass = encoder.createRenderPass(() -> "Maro Screen GUI", main.getColorAttachmentView(), OptionalInt.empty())) {
                pass.setPipeline(PIPELINE);
                pass.setUniform("ScreenGuiData", uniforms.slice(0, DATA_BYTES));
                pass.bindTexture("Menu", target.getColorAttachmentView(), sampler);
                pass.bindTexture("WorldDepth", worldDepthView, depthSampler);
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
        captureDepth = false;
        STATE.clear();
        rasterScale = 1;
        if (renderer != null) { renderer.close(); renderer = null; }
        if (target != null) { target.delete(); target = null; }
        releaseDepth();
        if (uniforms != null) { uniforms.close(); uniforms = null; }
        if (vertices != null) { vertices.close(); vertices = null; }
        if (indices != null) { indices.close(); indices = null; }
    }

    public static int drawnFrames() { return frames; }
    public static boolean hasResources() { return renderer != null || target != null || uniforms != null || worldDepth != null; }
    public static long worldDepthFrames() { return depthFrames; }
    public static boolean depthReady() { return depthReady; }
    public static int textureWidth() { return target == null ? 0 : target.textureWidth; }
    public static int textureHeight() { return target == null ? 0 : target.textureHeight; }
}
