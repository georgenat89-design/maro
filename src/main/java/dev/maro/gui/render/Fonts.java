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
 * Text helpers. Uses the bundled Inter TTF (assets/maro/font) when "Custom Font" is enabled,
 * otherwise the vanilla font. Supports fractional positions, scaling and global alpha.
 */
public final class Fonts {
    /**
     * Inter SemiBold / ExtraBold, each defined once per oversample factor (1x..6x). The variant whose
     * oversample matches the real pixel size of the text is picked at draw time, so glyphs are
     * rasterised 1:1 with the screen instead of being resampled, which keeps them sharp.
     */
    private static final int MAX_OVERSAMPLE = 6;
    private static final Style[] REGULAR = new Style[MAX_OVERSAMPLE + 1];
    private static final Style[] BOLD = new Style[MAX_OVERSAMPLE + 1];
    private static final Style VANILLA_BOLD = Style.EMPTY.withBold(true);

    static {
        for (int i = 1; i <= MAX_OVERSAMPLE; i++) {
            REGULAR[i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(Maro.MOD_ID, "inter_semibold_x" + i)));
            BOLD[i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(Maro.MOD_ID, "inter_extrabold_x" + i)));
        }
    }

    private Fonts() {
    }

    private static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** Oversample whose glyph pixels map 1:1 to screen pixels for text drawn at {@code scale}. */
    private static int oversample(float scale) {
        double px = MinecraftClient.getInstance().getWindow().getScaleFactor() * scale;
        return (int) Math.max(1, Math.min(MAX_OVERSAMPLE, Math.ceil(px - 0.15)));
    }

    // ---- caps style -------------------------------------------------------------------
    // "Caps" draws every label in ExtraBold uppercase, slightly smaller, with a little letter
    // spacing. Text the user typed is drawn in raw mode so it keeps its real case.

    private static final float CAPS_SCALE = 0.82f;
    private static final float CAPS_TRACKING = 0.5f;
    private static int rawDepth;

    /** Draw/measure text exactly as given (no caps transform) until {@link #endRaw()}. */
    public static void beginRaw() {
        rawDepth++;
    }

    public static void endRaw() {
        rawDepth = Math.max(0, rawDepth - 1);
    }

    private static boolean caps() {
        return rawDepth == 0 && ClientSettings.capsText.get();
    }

    private static String transform(String s) {
        return caps() ? s.toUpperCase(Locale.ROOT) : s;
    }

    private static float eff(float scale) {
        return caps() ? Math.max(0.56f, scale * CAPS_SCALE) : scale;
    }

    private static boolean weight(boolean bold) {
        return bold || caps();
    }

    public static Text text(String s, boolean bold) {
        return styled(transform(s), weight(bold), eff(1f));
    }

    private static Text styled(String s, boolean bold, float scale) {
        if (ClientSettings.customFont.get()) {
            int o = oversample(scale);
            return Text.literal(s).setStyle(bold ? BOLD[o] : REGULAR[o]);
        }
        return bold ? Text.literal(s).setStyle(VANILLA_BOLD) : Text.literal(s);
    }

    private static float rawWidth(String s, boolean bold, float scale) {
        return tr().getTextHandler().getWidth(styled(s, bold, scale)) * scale;
    }

    public static float width(String s, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return 0;
        String t = transform(s);
        boolean b = weight(bold);
        float es = eff(scale);
        if (!caps()) return rawWidth(t, b, es);
        float w = 0;
        for (int i = 0; i < t.length(); i++) w += rawWidth(String.valueOf(t.charAt(i)), b, es);
        return w + CAPS_TRACKING * es * (t.length() - 1);
    }

    public static float width(String s) {
        return width(s, false, 1f);
    }

    /** Visual height of capital letters, used for vertical centring. */
    public static float height(float scale) {
        return 7f * eff(scale);
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return;
        int c = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        // the vanilla renderer treats alpha < 4 as opaque, so skip near-invisible text entirely
        if (ColorUtil.alpha(c) < 8) return;
        String t = transform(s);
        boolean b = weight(bold);
        float es = eff(scale);
        if (!caps()) {
            drawRun(ctx, t, x, y, c, b, es);
            return;
        }
        float cx = x;
        for (int i = 0; i < t.length(); i++) {
            String ch = String.valueOf(t.charAt(i));
            if (t.charAt(i) != ' ') drawRun(ctx, ch, cx, y, c, b, es);
            cx += rawWidth(ch, b, es) + CAPS_TRACKING * es;
        }
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
        draw(ctx, s, x, cy - height(scale) / 2f, color, bold, scale);
    }

    public static void drawCentered(DrawContext ctx, String s, float cx, float cy, int color, boolean bold, float scale) {
        draw(ctx, s, cx - width(s, bold, scale) / 2f, cy - height(scale) / 2f, color, bold, scale);
    }

    public static void drawRight(DrawContext ctx, String s, float right, float cy, int color, boolean bold, float scale) {
        draw(ctx, s, right - width(s, bold, scale), cy - height(scale) / 2f, color, bold, scale);
    }

    /** Cuts the string with an ellipsis so it fits in {@code maxWidth}. */
    public static String trim(String s, float maxWidth, boolean bold, float scale) {
        if (width(s, bold, scale) <= maxWidth) return s;
        String dots = "\u2026";
        float dw = width(dots, bold, scale);
        int end = s.length();
        while (end > 0 && width(s.substring(0, end), bold, scale) + dw > maxWidth) end--;
        return s.substring(0, end).trim() + dots;
    }

    /** Word-wraps text into lines no wider than {@code maxWidth}. */
    public static List<String> wrap(String s, float maxWidth, boolean bold, float scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (width(test, bold, scale) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
