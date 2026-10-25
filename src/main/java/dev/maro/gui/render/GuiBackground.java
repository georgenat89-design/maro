package dev.maro.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.util.ColorUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

/** A GUI-only backdrop; it never renders a world or owns a separate presentation surface. */
public final class GuiBackground {
    public record Placement(float x, float y, float width, float height, float u0, float v0, float u1, float v1) { }
    private static GuiBackgroundImage image;
    private static boolean initialized;
    private GuiBackground() { }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { if (image != null) image.close(); });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.of("maro", "gui-background"); }
            @Override public void reload(ResourceManager manager) { if (image != null) image.reload(); }
        });
    }
    public static GuiBackgroundImage image() { if (image == null) image = new GuiBackgroundImage(); return image; }
    public static boolean isDefault() { return ClientSettings.guiBackgroundMode.is("Default"); }
    public static void preset(String name) {
        int first, second;
        switch (name) {
            case "Maro Blue" -> { first = 0xFF0B1528; second = 0xFF203E6A; }
            case "Midnight" -> { first = 0xFF080C19; second = 0xFF242C48; }
            case "Ocean" -> { first = 0xFF061D30; second = 0xFF17606A; }
            case "Dusk" -> { first = 0xFF1A1434; second = 0xFF713B58; }
            case "Forest" -> { first = 0xFF0B201F; second = 0xFF345747; }
            default -> { return; }
        }
        ClientSettings.guiBackgroundColor.set(first); ClientSettings.guiBackgroundColor2.set(second);
    }
    public static void reset() {
        ClientSettings.GUI_BACKGROUND.getSettings().forEach(dev.maro.setting.Setting::reset);
        image().remove(); ConfigManager.saveClient();
    }

    public static void draw(DrawContext ctx, float x, float y, float w, float h) {
        if(dev.maro.render.capture.CapturePass.active())return;
        if (w <= 0 || h <= 0) return;
        if (isDefault()) {
            if (ClientSettings.backgroundDim.get()) {
                int dim = Math.round(255 * ClientSettings.dimAmount.getFloat() / 100f);
                Render2D.rectGradient(ctx, x, y, w, h, ColorUtil.withAlpha(0, Math.round(dim * .85f)), ColorUtil.withAlpha(0, Math.round(dim * .85f)), ColorUtil.withAlpha(0, dim), ColorUtil.withAlpha(0, dim));
            }
            return;
        }
        Render2D.rect(ctx, x, y, w, h, Math.round(ClientSettings.guiBackgroundDim.getFloat() * 2.55f) << 24);
        float opacity = ClientSettings.guiBackgroundOpacity.getFloat() / 100f;
        if (ClientSettings.guiBackgroundMode.is("Solid")) {
            Render2D.rect(ctx, x, y, w, h, ColorUtil.mulAlpha(ClientSettings.guiBackgroundColor.get(), opacity));
        } else if (ClientSettings.guiBackgroundMode.is("Gradient")) {
            gradient(ctx, x, y, w, h, opacity);
        } else if (ClientSettings.guiBackgroundMode.is("PNG Image")) {
            GuiBackgroundImage asset = image(); asset.ensureLoaded();
            if (!asset.loaded()) { gradient(ctx, x, y, w, h, opacity * .6f); return; }
            Placement p = placement(w, h, asset.width(), asset.height(), ClientSettings.guiBackgroundFit.get(),
                    ClientSettings.guiBackgroundScale.getFloat() / 100f,
                    ClientSettings.guiBackgroundAlignX.getFloat() / 100f, ClientSettings.guiBackgroundAlignY.getFloat() / 100f);
            int color = ColorUtil.mulAlpha(ClientSettings.guiBackgroundTint.get(), opacity * Render2D.getAlpha());
            var pose = new Matrix3x2f(ctx.getMatrices());
            float left = x + p.x(), top = y + p.y(), right = left + p.width(), bottom = top + p.height();
            float[] vertices = {left,top,p.u0(),p.v0(), left,bottom,p.u0(),p.v1(), right,bottom,p.u1(),p.v1(), right,top,p.u1(),p.v0()};
            var bounds = new ScreenRect((int)Math.floor(left), (int)Math.floor(top), Math.max(1, (int)Math.ceil(right) - (int)Math.floor(left)), Math.max(1, (int)Math.ceil(bottom) - (int)Math.floor(top))).transformEachVertex(pose);
            var sampler = ClientSettings.guiBackgroundFit.is("Tile")
                    ? RenderSystem.getSamplerCache().get(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false) : asset.texture().getSampler();
            ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, vertices, new int[]{color,color,color,color}, RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(asset.texture().getGlTextureView(), sampler), bounds, Render2D.getScissor()));
        }
    }
    private static void gradient(DrawContext ctx, float x, float y, float w, float h, float alpha) {
        double angle = Math.toRadians(ClientSettings.guiBackgroundDirection.getFloat()), dx = Math.cos(angle), dy = Math.sin(angle);
        double span = Math.abs(dx * w) + Math.abs(dy * h);
        int first = ColorUtil.mulAlpha(ClientSettings.guiBackgroundColor.get(), alpha), second = ColorUtil.mulAlpha(ClientSettings.guiBackgroundColor2.get(), alpha);
        float tl = (float)(.5 + (-w * dx - h * dy) / (2 * span)), tr = (float)(.5 + (w * dx - h * dy) / (2 * span));
        float bl = (float)(.5 + (-w * dx + h * dy) / (2 * span)), br = (float)(.5 + (w * dx + h * dy) / (2 * span));
        Render2D.rectGradient(ctx, x, y, w, h, ColorUtil.lerp(first,second,tl), ColorUtil.lerp(first,second,tr), ColorUtil.lerp(first,second,br), ColorUtil.lerp(first,second,bl));
    }

    /** Crops geometry and UVs to the viewport, preserving proportions in Fit and Fill. */
    public static Placement placement(float w, float h, int imageW, int imageH, String mode, float scale, float alignX, float alignY) {
        if (w <= 0 || h <= 0 || imageW <= 0 || imageH <= 0) throw new IllegalArgumentException("Background dimensions must be positive");
        scale = Math.max(.1f, Math.min(4, scale)); alignX = Math.clamp(alignX, 0, 1); alignY = Math.clamp(alignY, 0, 1);
        if (mode.equals("Stretch")) return new Placement(0,0,w,h,0,0,1,1);
        if (mode.equals("Tile")) {
            float tileW = imageW * scale, tileH = imageH * scale;
            float u = -(w / tileW - 1) * alignX, v = -(h / tileH - 1) * alignY;
            return new Placement(0,0,w,h,u,v,u + w / tileW,v + h / tileH);
        }
        float ratio = (mode.equals("Fit") ? Math.min(w / imageW, h / imageH) : Math.max(w / imageW, h / imageH)) * scale;
        float drawnW = imageW * ratio, drawnH = imageH * ratio, left = (w - drawnW) * alignX, top = (h - drawnH) * alignY;
        float x0 = Math.max(0,left), y0 = Math.max(0,top), x1 = Math.min(w,left + drawnW), y1 = Math.min(h,top + drawnH);
        return new Placement(x0,y0,x1-x0,y1-y0,(x0-left)/drawnW,(y0-top)/drawnH,(x1-left)/drawnW,(y1-top)/drawnH);
    }
}
