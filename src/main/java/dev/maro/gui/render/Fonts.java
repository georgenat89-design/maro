package dev.maro.gui.render;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text helpers. Uses a bundled TTF (assets/maro/font: Sora, Rubik, or Inter) when "Custom Font" is
 * enabled, otherwise the vanilla font. Supports fractional positions, scaling and global alpha.
 */
public final class Fonts {
    /**
     * Each family's text and bold weights (Sora SemiBold / Bold, Rubik Medium / Bold,
     * Inter SemiBold / ExtraBold), each
     * defined once per oversample factor (1x..6x). The variant whose oversample matches the real
     * pixel size of the text is picked at draw time, so glyphs are rasterised 1:1 with the screen
     * instead of being resampled, which keeps them sharp.
     */
    private static final int MAX_OVERSAMPLE = 6;
    private static final String[][] FAMILIES = {{"rubik_medium", "rubik_bold"}, {"inter_semibold", "inter_extrabold"}, {"sora_semibold", "sora_bold"}};
    private static final Style[][] REGULAR = new Style[FAMILIES.length][MAX_OVERSAMPLE + 1];
    private static final Style[][] BOLD = new Style[FAMILIES.length][MAX_OVERSAMPLE + 1];
    private static final Style VANILLA_BOLD = Style.EMPTY.withBold(true);

    static {
        for (int f = 0; f < FAMILIES.length; f++) {
            for (int i = 1; i <= MAX_OVERSAMPLE; i++) {
                REGULAR[f][i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(Maro.MOD_ID, FAMILIES[f][0] + "_x" + i)));
                BOLD[f][i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(Maro.MOD_ID, FAMILIES[f][1] + "_x" + i)));
            }
        }
    }

    /** The chosen family: 0 Rubik, 1 Inter, 2 Sora. */
    private static int family() {
        return ClientSettings.fontFace.is("Sora") ? 2 : ClientSettings.fontFace.is("Inter") ? 1 : 0;
    }

    private Fonts() {
    }

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /**
     * Oversample whose glyph pixels map 1:1 to screen pixels for text drawn at {@code scale}: the
     * nearest one, or the next larger when the nearest would be stretched by more than 5%.
     */
    private static int oversample(float scale) {
        double px = MinecraftClient.getInstance().getWindow().getScaleFactor() * scale;
        long o = Math.round(px);
        if (o < px * 0.95) o++;
        return (int) Math.max(1, Math.min(MAX_OVERSAMPLE, o));
    }

    /**
     * The Minecraft font is a bitmap: at a fractional number of screen pixels per font pixel some
     * strokes come out a pixel wider than others. Round to whole screen pixels per font pixel (at
     * least one); text so small that this would blow it up keeps its size.
     */
    private static float pixelScale(float scale, double device) {
        double texel = scale * device;
        if (texel < 0.75 || !(device > 0)) return scale;
        return (float) (Math.max(1, Math.round(texel)) / device);
    }

    /** Screen pixels per local unit for text measured without a DrawContext: the enclosing menu's. */
    private static double measureDevice() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor() * measureScale;
    }

    private static double device(DrawContext ctx) {
        var m = ctx.getMatrices();
        return MinecraftClient.getInstance().getWindow().getScaleFactor() * Math.hypot(m.m00(), m.m01());
    }

    // ---- caps style -------------------------------------------------------------------
    // Caps changes presentation only. Stored names, commands, paths and editable values retain their original text.

    private static int rawDepth;
    private static int menuDepth;
    private static float measureScale = 1;

    public static void beginMenu() { menuDepth++; }
    public static void endMenu() { menuDepth = Math.max(0, menuDepth - 1); }

    /** Leaves the menu's text size for a HUD drawn inside the menu, as it is in game; returns what to resume. */
    public static int pauseMenu() { int depth = menuDepth; menuDepth = 0; return depth; }
    public static void resumeMenu(int depth) { menuDepth = depth; }

    /** Plain width helpers use the same glyph atlas as the enclosing menu transform. */
    public static void setMeasureScale(float scale) { measureScale = scale; }
    public static float measureScale() { return measureScale; }

    /** Legacy scope retained for existing callers; typography and casing now stay consistent across panels. */
    public static void beginRaw() {
        rawDepth++;
    }

    public static void endRaw() {
        rawDepth = Math.max(0, rawDepth - 1);
    }

    private static boolean caps() {
        return ClientSettings.capsText.get();
    }

    private static String transform(String s) {
        return caps() ? s.toUpperCase(Locale.ROOT) : s;
    }

    private static float eff(float scale) {
        return scale * (menuDepth > 0 ? ClientSettings.menuTextSize.getFloat() / 100f : 1f);
    }

    private static boolean weight(boolean bold) {
        return bold || caps();
    }

    public static Text text(String s, boolean bold) {
        return styled(transform(s), weight(bold), eff(1f));
    }

    /** The same glyph atlas as draw(), including the current HUD resize. */
    public static Text text(DrawContext ctx, String s, boolean bold, float scale) {
        var matrix = ctx.getMatrices();
        float atlasScale = eff(scale) * (float) Math.hypot(matrix.m00(), matrix.m01());
        return styled(transform(s), weight(bold), atlasScale);
    }

    private static Text styled(String s, boolean bold, float scale) {
        if (ClientSettings.customFont.get()) {
            int o = oversample(scale);
            return Text.literal(s).setStyle(bold ? BOLD[family()][o] : REGULAR[family()][o]);
        }
        return bold ? Text.literal(s).setStyle(VANILLA_BOLD) : Text.literal(s);
    }

    public static float width(String s, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return 0;
        if (ClientSettings.customFont.get()) return SmoothHudText.width(transform(s), weight(bold), eff(scale) * .875f, measureScale);
        return tr().getTextHandler().getWidth(styled(transform(s), weight(bold), eff(scale) * measureScale)) * pixelScale(eff(scale), measureDevice());
    }
    public static float width(String s) { return width(s, false, 1); }
    public static float width(DrawContext ctx, String s, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return 0;
        if (ClientSettings.customFont.get()) return SmoothHudText.width(ctx, transform(s), weight(bold), eff(scale) * .875f);
        float matrix = (float)Math.hypot(ctx.getMatrices().m00(), ctx.getMatrices().m01());
        return tr().getTextHandler().getWidth(styled(transform(s), weight(bold), eff(scale) * matrix)) * pixelScale(eff(scale), device(ctx));
    }

    /** Visual height of capital letters, used for vertical centring. */
    public static float height(float scale) {
        if (ClientSettings.customFont.get()) return 7f * eff(scale) * SmoothHudText.heightRatio();
        return 7f * pixelScale(eff(scale), measureDevice());
    }

    /** Visual height of capital letters as drawn under {@code ctx}'s matrix. */
    public static float height(DrawContext ctx, float scale) {
        if (ClientSettings.customFont.get()) return height(scale);
        return 7f * pixelScale(eff(scale), device(ctx));
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return;
        int c = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        // the vanilla renderer treats alpha < 4 as opaque, so skip near-invisible text entirely
        if (ColorUtil.alpha(c) < 8) return;
        if (ClientSettings.customFont.get()) {
            SmoothHudText.draw(ctx, transform(s), x, y, color, weight(bold), eff(scale) * .875f);
            return;
        }
        drawRun(ctx, transform(s), x, y, c, weight(bold), eff(scale));
    }

    private static void drawRun(DrawContext ctx, String s, float x, float y, int c, boolean bold, float scale) {
        float p = Render2D.px();
        Matrix3x2fStack ms = ctx.getMatrices();
        float matrixScale = (float) Math.hypot(ms.m00(), ms.m01());
        // HUDs apply an outer resize before calling Fonts. Pick a glyph atlas
        // for the final physical size, and snap after that outer transform.
        if (ms.m01() == 0 && ms.m10() == 0 && ms.m00() != 0 && ms.m11() != 0) {
            x = (Math.round((x * ms.m00() + ms.m20()) / p) * p - ms.m20()) / ms.m00();
            y = (Math.round((y * ms.m11() + ms.m21()) / p) * p - ms.m21()) / ms.m11();
        }
        scale = pixelScale(scale, device(ctx));
        ms.pushMatrix();
        ms.translate(x, y);
        if (scale != 1f) ms.scale(scale, scale);
        ctx.drawText(tr(), styled(s, bold, scale * matrixScale), 0, 0, c, false);
        ms.popMatrix();
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color) {
        draw(ctx, s, x, y, color, false, 1f);
    }

    /** Draws with the text vertically centred on {@code cy}. */
    public static void drawV(DrawContext ctx, String s, float x, float cy, int color, boolean bold, float scale) {
        draw(ctx, s, x, cy - height(ctx, scale) / 2f, color, bold, scale);
    }

    public static void drawCentered(DrawContext ctx, String s, float cx, float cy, int color, boolean bold, float scale) {
        draw(ctx, s, cx - width(ctx, s, bold, scale) / 2f, cy - height(ctx, scale) / 2f, color, bold, scale);
    }

    public static void drawRight(DrawContext ctx, String s, float right, float cy, int color, boolean bold, float scale) {
        draw(ctx, s, right - width(ctx, s, bold, scale), cy - height(ctx, scale) / 2f, color, bold, scale);
    }

    /** A centered top-aligned native-screen label, fitted using the same font as its drawing. */
    public static void centeredTop(DrawContext ctx, String s, float cx, float top, float room, int color) {
        float scale = .9f;
        String fitted = trim(ctx, s, room, false, scale);
        draw(ctx, fitted, cx - width(ctx, fitted, false, scale) / 2, top, color, false, scale);
    }
    public static String trim(DrawContext ctx, String s, float room, boolean bold, float scale) {
        if (ClientSettings.customFont.get()) return SmoothHudText.trim(ctx, s, room, weight(bold), eff(scale) * .875f);
        return trim(s, room, bold, scale);
    }
    /** Input clipping returns original text and never adds an ellipsis or changes stored characters. */
    public static String prefix(String s, float room, boolean reverse) {
        int index = reverse ? s.length() : 0;
        if (reverse) {
            while (index > 0) {
                int next = s.offsetByCodePoints(index, -1);
                if (width(s.substring(next), false, 1) > room) break;
                index = next;
            }
            return s.substring(index);
        }
        while (index < s.length()) {
            int next = s.offsetByCodePoints(index, 1);
            if (width(s.substring(0, next), false, 1) > room) break;
            index = next;
        }
        return s.substring(0, index);
    }
    /** Cuts the string with an ellipsis so it fits in {@code maxWidth}. */
    public static String trim(String s, float maxWidth, boolean bold, float scale) {
        if (width(s, bold, scale) <= maxWidth) return s;
        String dots = "\u2026";
        float dw = width(dots, bold, scale);
        if (dw > maxWidth) return "";
        int end = s.length();
        while (end > 0 && width(s.substring(0, end), bold, scale) + dw > maxWidth) end = s.offsetByCodePoints(end, -1);
        return s.substring(0, end).stripTrailing() + dots;
    }

    /** Word-wraps text into lines no wider than {@code maxWidth}. */
    public static List<String> wrap(String s, float maxWidth, boolean bold, float scale) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty() || maxWidth <= 0) return lines;
        for (String paragraph : s.split("\\R", -1)) {
            String line = "";
            for (String word : paragraph.split(" +")) {
                String joined = line.isEmpty() ? word : line + " " + word;
                if (width(joined, bold, scale) <= maxWidth) { line = joined; continue; }
                if (!line.isEmpty()) { lines.add(line); line = ""; }
                while (!word.isEmpty() && width(word, bold, scale) > maxWidth) {
                    int end = 0;
                    while (end < word.length()) {
                        int next = word.offsetByCodePoints(end, 1);
                        if (width(word.substring(0, next), bold, scale) > maxWidth) break;
                        end = next;
                    }
                    if (end == 0) end = word.offsetByCodePoints(0,1);
                    lines.add(word.substring(0,end)); word = word.substring(end);
                }
                line = word;
            }
            lines.add(line);
        }
        return lines;
    }
}
