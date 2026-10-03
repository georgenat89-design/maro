package dev.maro.gui.hider;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.maro.Maro;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.setting.RegionsSetting;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Draws Screen Hider areas. The rendered world is copied out of the main framebuffer and the
 * areas are drawn as quads sampling that copy through a blur/pixelate shader, so only the
 * chosen rectangles are affected.
 */
public final class HiderRenderer {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.of(Maro.MOD_ID, "pipeline/screen_hider"))
            .withVertexShader(Identifier.ofVanilla("core/position_tex_color"))
            .withFragmentShader(Identifier.of(Maro.MOD_ID, "core/screen_hider"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .build();

    private static GpuTexture copy;
    private static GpuTextureView copyView;
    private static TextureSetup textureSetup;

    private HiderRenderer() {
    }

    /** Copies the current main framebuffer (the rendered world) into our texture. */
    private static boolean capture() {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        GpuTexture src = fb.getColorAttachment();
        if (src == null) return false;
        int w = src.getWidth(0), h = src.getHeight(0);
        GpuDevice device = RenderSystem.getDevice();
        if (copy == null || copy.getWidth(0) != w || copy.getHeight(0) != h) {
            if (copyView != null) copyView.close();
            if (copy != null) copy.close();
            copy = device.createTexture(() -> "maro screen hider", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, w, h, 1, 1);
            copyView = device.createTextureView(copy);
            textureSetup = TextureSetup.of(copyView,
                    device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty()));
        }
        device.createCommandEncoder().copyTextureToTexture(src, copy, 0, 0, 0, 0, 0, w, h);
        return true;
    }

    /**
     * @param style    "Blur", "Pixelate" or "Solid"
     * @param strength 0..1
     */
    public static void draw(DrawContext ctx, List<RegionsSetting.Region> regions, String style, float strength, int solidColor) {
        if (regions.isEmpty()) return;
        float sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        if (style.equals("Solid")) {
            for (RegionsSetting.Region r : regions) Render2D.rect(ctx, r.x() * sw, r.y() * sh, r.w() * sw, r.h() * sh, solidColor);
            return;
        }
        if (!capture()) return;
        TextureSetup texture = textureSetup;
        int color = ColorUtil.argb(255, Math.round(Math.max(0f, Math.min(1f, strength)) * 255), style.equals("Pixelate") ? 255 : 0, 0);
        Matrix3x2f pose = new Matrix3x2f(ctx.getMatrices());
        for (RegionsSetting.Region r : regions) {
            float x1 = r.x() * sw, y1 = r.y() * sh, x2 = (r.x() + r.w()) * sw, y2 = (r.y() + r.h()) * sh;
            // framebuffer textures are stored bottom-up
            float u1 = x1 / sw, u2 = x2 / sw, v1 = 1f - y1 / sh, v2 = 1f - y2 / sh;
            int bx = (int) Math.floor(x1), by = (int) Math.floor(y1);
            ScreenRect bounds = new ScreenRect(bx, by, Math.max(1, (int) Math.ceil(x2) - bx), Math.max(1, (int) Math.ceil(y2) - by)).transformEachVertex(pose);
            ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new TexturedQuadState(PIPELINE, texture, pose,
                    x1, y1, x2, y2, u1, v1, u2, v2, color, null, bounds));
        }
    }
}
