package dev.maro.gui.render;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import dev.maro.config.ClientSettings;
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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Grayscale antialiasing at final physical size, with bounded reusable GUI/HUD text textures. */
public final class SmoothHudText {
    private static final FontRenderContext METRICS = new FontRenderContext(new AffineTransform(), true, true);
    private static final Font[][] FONTS = new Font[5][2];
    private static final double[][] CAPS_RATIO = new double[5][2];
    private static final java.util.Map<String, Double> GLYPH_SIZE = new java.util.HashMap<>();
    private static final LinkedHashMap<Key, Line> CACHE = new LinkedHashMap<>(512, .75f, true);
    private static final LinkedHashMap<Key, Float> WIDTHS = new LinkedHashMap<>(1024, .75f, true);
    private static long cachedBytes;
    private static int sentenceDepth;
    private static int scopedFamily=-1;
    static int pushFamily(int family){int before=scopedFamily;scopedFamily=Math.clamp(family,0,3);return before;}
    static void restoreFamily(int previous){scopedFamily=previous;}
    /** Sentence-case product guidance is independent of the compact HUD caps preference. */
    public static void beginSentence(){sentenceDepth++;}
    public static void endSentence(){sentenceDepth=Math.max(0,sentenceDepth-1);}
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private record Key(String text, int pixels, int family, boolean bold) { }
    private record Line(Texture texture, int width, int height, float offsetX, float offsetY, int capHeight) { }
    record BrandMetrics(float left,float top,float width,float height,float capHeight) { }
    private SmoothHudText() { }
    /** Kept for callers that used to manage their own HUD-only cache. */
    public static void beginFrame() { }
    /** Evict only after GUI submission: another screen may still use a queued line this frame. */
    public static void endFrame() {
        var oldest = CACHE.values().iterator();
        while ((CACHE.size() > 768 || cachedBytes > MAX_BYTES) && oldest.hasNext()) {
            var line = oldest.next(); cachedBytes -= (long)line.width * line.height * 4;
            line.texture.close(); oldest.remove();
        }
        var widths = WIDTHS.values().iterator();
        while (WIDTHS.size() > 2048 && widths.hasNext()) { widths.next(); widths.remove(); }
    }
    public static String display(String text) {
        return sentenceDepth==0&&ClientSettings.capsText.get() ? text.toUpperCase(Locale.ROOT) : text;
    }
    private static int family() { return scopedFamily>=0?scopedFamily:Fonts.familyIndex(); }
    private static boolean weight(boolean bold) { return bold || sentenceDepth==0&&ClientSettings.capsText.get(); }
    /** Lower-profile Sora outlines retain their original advance and nominal text size. */
    public static float heightRatio() { return family() == 2 ? .86f : 1f; }
    private static Font font(int family, boolean bold) {
        int index = bold ? 1 : 0;
        if (FONTS[family][index] == null) {
            String path = "/assets/maro/font/" + (family == 4 ? "lucide.ttf" : Fonts.fontFile(family,bold));
            try (var input = SmoothHudText.class.getResourceAsStream(path)) {
                if (input == null) throw new IllegalStateException("Missing interface font: " + path);
                FONTS[family][index] = Font.createFont(Font.TRUETYPE_FONT, input);
                CAPS_RATIO[family][index] = FONTS[family][index].deriveFont(100f).createGlyphVector(METRICS, "H").getVisualBounds().getHeight() / 100;
                if (family == 2) CAPS_RATIO[family][index] /= .86;
            } catch (Exception error) { throw new IllegalStateException("Could not load interface font", error); }
        }
        return FONTS[family][index];
    }
    private static double unit(DrawContext ctx) {
        return MinecraftClient.getInstance().getWindow().getScaleFactor() * ScreenGuiSurface.textScale(ctx)
                * Math.hypot(ctx.getMatrices().m00(), ctx.getMatrices().m01());
    }
    private static Key key(String text, boolean bold, float size, double unit) {
        int family = family(); bold = weight(bold); font(family, bold);
        int pixels = Math.max(6, Math.min(256, (int)Math.round(size * 8 * unit / CAPS_RATIO[family][bold ? 1 : 0])));
        return new Key(display(dev.maro.module.impl.misc.NameProtect.mask(text)), pixels, family, bold);
    }
    private static TextLayout layout(Key key, float multiplier) {
        Font sized = font(key.family, key.bold).deriveFont(key.pixels * multiplier);
        if (sized.canDisplayUpTo(key.text) == -1) return new TextLayout(key.text, sized, METRICS);
        // Preserve shaping and use the platform Unicode fallback only for unsupported code points.
        var text = new AttributedString(key.text);
        text.addAttribute(TextAttribute.FONT, sized);
        Font fallback = new Font(Font.SANS_SERIF, key.bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(key.pixels * multiplier);
        for (int i = 0; i < key.text.length();) {
            int cp = key.text.codePointAt(i), end = i + Character.charCount(cp);
            if (!sized.canDisplay(cp)) text.addAttribute(TextAttribute.FONT, fallback, i, end);
            i = end;
        }
        return new TextLayout(text.getIterator(), METRICS);
    }
    private static float width(Key key) {
        if (key.text.isEmpty()) return 0;
        return WIDTHS.computeIfAbsent(key, k -> layout(k, 1).getAdvance());
    }
    public static float width(String text, boolean bold, float size, float matrixScale) {
        if (text == null || text.isEmpty()) return 0;
        double unit = MinecraftClient.getInstance().getWindow().getScaleFactor() * matrixScale;
        return (float)(width(key(text, bold, size, unit)) / unit);
    }
    public static float width(DrawContext ctx, String text, boolean bold, float size) {
        return text == null || text.isEmpty() ? 0 : (float)(width(key(text, bold, size, unit(ctx))) / unit(ctx));
    }
    public static String trim(DrawContext ctx, String text, float room, boolean bold, float size) {
        if (text == null || text.isEmpty()) return "";
        if (width(ctx, text, bold, size) <= room) return text;
        if (width(ctx, "\u2026", bold, size) > room) return "";
        int end = text.length();
        while (end > 0 && width(ctx, text.substring(0, end) + "\u2026", bold, size) > room)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + "\u2026";
    }
    private static Line raster(Key key) {
        // Rasterize once at the actual framebuffer size. Supersampling followed by
        // bicubic reduction softened vertical stems, especially in small bold labels.
        var layout = layout(key, 1);
        var bounds = layout.getPixelBounds(METRICS, 0, 0);
        var caps = font(key.family, key.bold).deriveFont((float)key.pixels).createGlyphVector(METRICS, "H").getPixelBounds(METRICS, 0, 0);
        int w = Math.max(1, Math.min(8192, bounds.width + 4)), h = Math.max(1, bounds.height + 4);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(java.awt.Color.WHITE);
            layout.draw(graphics, 2 - bounds.x, 2 - bounds.y);
        } finally { graphics.dispose(); }
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w); byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = (byte)255;
            // Preserve grayscale coverage: clipping or boosting it makes small text and
            // diagonal icon strokes look jagged even though the texture is antialiased.
            rgba[i * 4 + 3] = (byte)(argb[i] >>> 24);
        }
        Texture texture = new Texture(w, h, TextureFormat.RGBA8, FilterMode.LINEAR, FilterMode.LINEAR);
        texture.upload(rgba);
        cachedBytes += (long)w * h * 4;
        return new Line(texture, w, h, bounds.x - 2, bounds.y - caps.y - 2, -caps.y);
    }

    // Brand lettering is literal: user aliases/caps must never rename the wordmark.
    private static Key brandKey(String text,float size,double unit){
        int family=Fonts.familyIndex();font(family,true);
        int pixels=Math.clamp((int)Math.round(size*8*unit/CAPS_RATIO[family][1]),6,256);
        return new Key(text,pixels,family,true);
    }
    static BrandMetrics brandMetrics(DrawContext ctx,String text,float size){
        double unit=unit(ctx);
        Line line=CACHE.computeIfAbsent(brandKey(text,size,unit),SmoothHudText::raster);
        return new BrandMetrics((float)((line.offsetX+2)/unit),(float)((line.offsetY+2)/unit),
                (float)((line.width-4)/unit),(float)((line.height-4)/unit),(float)(line.capHeight/unit));
    }
    static void drawBrand(DrawContext ctx,String text,float x,float capTop,int color,float size){
        int ink=ColorUtil.mulAlpha(color,Render2D.getAlpha());if((ink>>>24)<8)return;
        double unit=unit(ctx);
        drawLine(ctx,CACHE.computeIfAbsent(brandKey(text,size,unit),SmoothHudText::raster),x,capTop,ink,unit);
    }
    public static void draw(DrawContext ctx, String text, float x, float y, int color, boolean bold, float size) {
        if (text == null || text.isBlank()) return;
        int ink = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        if ((ink >>> 24) < 8) return;
        double unit = unit(ctx);
        var key = key(text, bold, size, unit);
        Line line = CACHE.computeIfAbsent(key, SmoothHudText::raster);
        drawLine(ctx, line, x, y, ink, unit);
    }
    private static void drawLine(DrawContext ctx, Line line, float x, float y, int ink, double unit) {
        var pose = new Matrix3x2f(ctx.getMatrices());
        double gui = MinecraftClient.getInstance().getWindow().getScaleFactor() * ScreenGuiSurface.textScale(ctx);
        boolean flat = pose.m01() == 0 && pose.m10() == 0 && pose.m00() != 0 && pose.m11() != 0;
        if (flat) {
            x = (float)((Math.round((x * pose.m00() + pose.m20()) * gui) / gui - pose.m20()) / pose.m00());
            y = (float)((Math.round((y * pose.m11() + pose.m21()) * gui) / gui - pose.m21()) / pose.m11());
        }
        float left = x + (float)(line.offsetX / unit), top = y + (float)(line.offsetY / unit);
        float right = left + (float)(line.width / unit), bottom = top + (float)(line.height / unit);
        var bounds = new ScreenRect((int)Math.floor(left), (int)Math.floor(top),
            Math.max(1, (int)Math.ceil(right) - (int)Math.floor(left)), Math.max(1, (int)Math.ceil(bottom) - (int)Math.floor(top))).transformEachVertex(pose);
        ScreenRect scissor = ((TextScissor)ctx).maro$textScissor();
        if (scissor != null) { bounds = bounds.intersection(scissor); if (bounds == null) return; }
        ((DrawContextAccessor)ctx).maro$getState().addSimpleElement(new GuiMeshState(pose,
            new float[]{left,top,0,0, left,bottom,0,1, right,bottom,1,1, right,top,1,0}, new int[]{ink,ink,ink,ink},
            RenderPipelines.GUI_TEXTURED, TextureSetup.of(line.texture.getGlTextureView(), flat
                ? RenderSystem.getSamplerCache().get(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false)
                : line.texture.getSampler()), bounds, scissor));
    }
    public static void drawRight(DrawContext ctx, String text, float right, float cy, int color, boolean bold, float size) {
        if (text == null || text.isBlank()) return;
        double unit = unit(ctx);
        Line line = CACHE.computeIfAbsent(key(text, bold, size, unit), SmoothHudText::raster);
        draw(ctx, text, right - (float)((line.offsetX + line.width - 2) / unit),
                cy - (float)((line.offsetY + line.height / 2f) / unit), color, bold, size);
    }
    /** Vertically center the visible ink, rather than a nominal font line box. */
    public static void drawV(DrawContext ctx, String text, float x, float cy, int color, boolean bold, float size) {
        if (text == null || text.isBlank()) return;
        double unit = unit(ctx);
        Line line = CACHE.computeIfAbsent(key(text, bold, size, unit), SmoothHudText::raster);
        draw(ctx, text, x, cy - (float)((line.offsetY + line.height / 2f) / unit), color, bold, size);
    }
    /** Lucide icons use their actual outline bounds, independently of font bearings and baseline. */
    public static void drawGlyph(DrawContext ctx, String glyph, float cx, float cy, float size, int color) {
        if (glyph == null || glyph.isEmpty() || size <= 0) return;
        int ink = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        if ((ink >>> 24) < 8) return;
        double unit = unit(ctx);
        double ratio = GLYPH_SIZE.computeIfAbsent(glyph, g -> {
            var bounds = font(4, false).deriveFont(100f).createGlyphVector(METRICS, g).getVisualBounds();
            return Math.max(.01, Math.max(bounds.getWidth(), bounds.getHeight()) / 100);
        });
        int pixels = Math.clamp((int)Math.round(size * unit / ratio), 6, 512);
        Line line = CACHE.computeIfAbsent(new Key(glyph, pixels, 4, false), SmoothHudText::raster);
        drawLine(ctx, line, cx - (float)((line.offsetX + line.width / 2f) / unit),
                cy - (float)((line.offsetY + line.height / 2f) / unit), ink, unit);
    }
    /** Center the visible lettering, including its bearings and final raster height. */
    public static void drawCentered(DrawContext ctx, String text, float cx, float cy, int color, boolean bold, float size) {
        if (text == null || text.isBlank()) return;
        double unit = unit(ctx);
        var key = key(text, bold, size, unit);
        Line line = CACHE.computeIfAbsent(key, SmoothHudText::raster);
        draw(ctx, text, cx - (float)((line.offsetX + line.width / 2f) / unit),
                cy - (float)((line.offsetY + line.height / 2f) / unit), color, bold, size);
    }
}
