package dev.maro.gui.spatial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.maro.mixin.GameRendererSpatialAccessor;
import dev.maro.mixin.GuiRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.util.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * The menu drawn off screen: a GUI renderer of its own draws whatever the menu draws into a
 * window-sized texture with a clear background, which the Spatial menu then shows on a pane in
 * the world. The texture holds premultiplied colour, as the game's own GUI blending leaves it.
 */
public final class SpatialCanvas {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    /** The canvas whose renderer is drawing right now, so its draws go to its texture. */
    private static SpatialCanvas drawing;

    // One GUI renderer for every opening of the menu: it holds buffers on the graphics card.
    private static final GuiRenderState state = new GuiRenderState();
    private static GuiRenderer renderer;
    private SimpleFramebuffer target;
    private static float rasterScale = 1;

    public static float textScale(DrawContext ctx) {
        return ((dev.maro.mixin.DrawContextAccessor)ctx).maro$getState() == state ? rasterScale : 1;
    }
    public static boolean scissor(net.minecraft.client.gui.ScreenRect area, com.mojang.blaze3d.systems.RenderPass pass) {
        if (drawing == null || drawing.target == null) return false;
        var target = drawing.target;var window = mc.getWindow();
        double sx = (double)target.textureWidth / window.getFramebufferWidth() * window.getScaleFactor();
        double sy = (double)target.textureHeight / window.getFramebufferHeight() * window.getScaleFactor();
        int left=(int)Math.floor(area.getLeft()*sx),right=(int)Math.ceil(area.getRight()*sx);
        int top=(int)Math.floor(area.getTop()*sy),bottom=(int)Math.ceil(area.getBottom()*sy);
        pass.enableScissor(left,target.textureHeight-bottom,Math.max(0,right-left),Math.max(0,bottom-top));
        return true;
    }

    /** Where a GUI renderer draws: this canvas's texture while its own renderer runs, the screen otherwise. */
    public static Framebuffer target(GuiRenderer renderer, Framebuffer screen) {
        SpatialCanvas canvas = drawing;
        return canvas != null && SpatialCanvas.renderer == renderer && canvas.target != null ? canvas.target : screen;
    }

    /** The drawn menu, or null before the first draw. */
    public GpuTextureView view() {
        return target == null ? null : target.getColorAttachmentView();
    }

    /** Draws {@code painter}'s menu into the texture, replacing what was there. */
    public void draw(Consumer<DrawContext> painter, int mouseX, int mouseY) {
        Window window = mc.getWindow();
        int w = Math.max(1, window.getFramebufferWidth()), h = Math.max(1, window.getFramebufferHeight());
        rasterScale = dev.maro.gui.render.ScreenGuiSurface.qualityScale(w,h);
        w=(int)Math.ceil(w*rasterScale);h=(int)Math.ceil(h*rasterScale);
        if (target == null) target = new SimpleFramebuffer("Maro spatial menu", w, h, true);
        else if (target.textureWidth != w || target.textureHeight != h) target.resize(w, h);
        GameRendererSpatialAccessor game = (GameRendererSpatialAccessor) mc.gameRenderer;
        if (renderer == null) {
            renderer = new GuiRenderer(state, mc.getBufferBuilders().getEntityVertexConsumers(), game.maro$commandQueue(),
                    mc.gameRenderer.getEntityRenderDispatcher(),
                    List.copyOf(((GuiRendererAccessor) game.maro$guiRenderer()).maro$specialRenderers().values()));
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorAttachment(), 0, target.getDepthAttachment(), 1.0);
        state.clear();
        painter.accept(new DrawContext(mc, state, mouseX, mouseY));
        drawing = this;
        try {
            renderer.render(game.maro$fogRenderer().getFogBuffer(FogRenderer.FogType.NONE));
        } finally {
            drawing = null;
        }
    }

    /** Frees the texture; drawing again makes a new one. */
    public void close() {
        if (target != null) target.delete();
        target = null;
    }
}
