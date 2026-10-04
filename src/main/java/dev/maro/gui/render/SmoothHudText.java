package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import dev.maro.runtime.renderer.Texture;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;

/** Cached Inter text with grayscale antialiasing, drawn through the normal GUI layers. */
public final class SmoothHudText {
    private static final FontRenderContext METRICS = new FontRenderContext(new AffineTransform(), true, false);
    private static final Font[] FONTS = new Font[2];
    private static final double[] CAPS_RATIO = new double[2];
    private static final LinkedHashMap<Key, Line> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static final LinkedHashMap<Key, Float> WIDTHS = new LinkedHashMap<>(256, .75f, true);
    private record Key(String text, int pixels, boolean bold) { }
    private record Line(Texture texture, int width, int height, float offsetX, float offsetY) { }
    private SmoothHudText() { }

    /** Evict before submitting this frame, so queued GUI elements retain their textures. */
    public static void beginFrame() {
        var oldest = CACHE.values().iterator();
        while (CACHE.size() > 128 && oldest.hasNext()) { oldest.next().texture.close(); oldest.remove(); }
        var widths = WIDTHS.values().iterator();
        while (WIDTHS.size() > 512 && widths.hasNext()) { widths.next(); widths.remove(); }
    }
    private static Font font(boolean bold) {
        int index = bold ? 1 : 0;
        if (FONTS[index] == null) {
            String path = "/assets/maro/font/inter_" + (bold ? "extrabold" : "semibold") + ".ttf";
            try (var input = SmoothHudText.class.getResourceAsStream(path)) {
                if (input == null) throw new IllegalStateException("Missing HUD font");
                FONTS[index] = Font.createFont(Font.TRUETYPE_FONT, input);
                CAPS_RATIO[index] = FONTS[index].deriveFont(100f).createGlyphVector(METRICS, "H").getVisualBounds().getHeight() / 100;
            } catch (Exception error) { throw new IllegalStateException("Could not load HUD font", error); }
        }
        return FONTS[index];
    }
    private static double unit(DrawContext ctx) {
        return MinecraftClient.getInstance().getWindow().getScaleFactor() * Math.hypot(ctx.getMatrices().m00(), ctx.getMatrices().m01());
    }
    private static int pixels(DrawContext ctx, boolean bold, float size) {
        font(bold);
        double ratio = CAPS_RATIO[bold ? 1 : 0];
        return Math.max(6, Math.min(160, (int)Math.round(size * 8 * unit(ctx) / ratio)));
    }
    private static float width(String text, int pixels, boolean bold) {
        return WIDTHS.computeIfAbsent(new Key(text, pixels, bold), key ->
            (float)font(key.bold).deriveFont(key.pixels * 2f).createGlyphVector(METRICS, key.text).getVisualBounds().getWidth() / 2);
    }
    public static String trim(DrawContext ctx, String text, float room, boolean bold, float size) {
        int pixels = pixels(ctx, bold, size); double limit = room * unit(ctx);
        if (width(text, pixels, bold) <= limit) return text;
        int end = text.length();
        while (end > 0 && width(text.substring(0, end) + "…", pixels, bold) > limit) end--;
        return text.substring(0, end) + "…";
    }
    private static Line raster(Key key) {
        Font sized = font(key.bold).deriveFont(key.pixels * 2f);
        var glyphs = sized.createGlyphVector(METRICS, key.text);
        var bounds = glyphs.getPixelBounds(METRICS, 0, 0);
        var caps = sized.createGlyphVector(METRICS, "H").getPixelBounds(METRICS, 0, 0);
        int w = Math.max(1, (bounds.width + 1) / 2 + 4), h = Math.max(1, (bounds.height + 1) / 2 + 4);
        BufferedImage large = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = large.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setColor(java.awt.Color.WHITE);
            graphics.drawGlyphVector(glyphs, 4 - bounds.x, 4 - bounds.y);
        } finally { graphics.dispose(); }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(large, 0, 0, w, h, null);
        } finally { graphics.dispose(); }
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w); byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = (byte)255;
            rgba[i * 4 + 3] = (byte)(argb[i] >>> 24);
        }
        Texture texture = new Texture(w, h, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);
        texture.upload(rgba);
        return new Line(texture, w, h, bounds.x / 2f - 2, (bounds.y - caps.y) / 2f - 2);
    }
    public static void draw(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        if (text.isEmpty()) return;
        int ink = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        if ((ink >>> 24) < 8) return;
        var key = new Key(text, pixels(ctx, bold, size), bold);
        Line line = CACHE.computeIfAbsent(key, SmoothHudText::raster);
        double gui = MinecraftClient.getInstance().getWindow().getScaleFactor();
        var origin = ctx.getMatrices().transformPosition(x, y, new Vector2f());
        // One final texel maps to one physical pixel. The GUI only positions it;
        // it never stretches the lettering or interpolates the edge a second time.
        float left = (float)(Math.round(origin.x * gui + line.offsetX) / gui);
        float top = (float)(Math.round(origin.y * gui + line.offsetY) / gui);
        float right = left + (float)(line.width / gui), bottom = top + (float)(line.height / gui);
        var bounds = new ScreenRect((int)Math.floor(left), (int)Math.floor(top),
            Math.max(1, (int)Math.ceil(right) - (int)Math.floor(left)), Math.max(1, (int)Math.ceil(bottom) - (int)Math.floor(top)));
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(new Matrix3x2f(),
            new float[]{left,top,0,0, left,bottom,0,1, right,bottom,1,1, right,top,1,0}, new int[]{ink,ink,ink,ink},
            RenderPipelines.GUI_TEXTURED, TextureSetup.of(line.texture.getGlTextureView(), line.texture.getSampler()), bounds));
    }
    public static void drawRight(DrawContext ctx, String text, float right, float cy, int color, boolean bold, float size) {
        float w = (float)(width(text, pixels(ctx, bold, size), bold) / unit(ctx));
        draw(ctx, text, right - w, cy - size * 4, color, bold, size);
    }
}
