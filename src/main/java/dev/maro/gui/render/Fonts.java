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

    public static Text text(String s, boolean bold) {
        return text(s, bold, 1f);
    }

    public static Text text(String s, boolean bold, float scale) {
        if (ClientSettings.customFont.get()) {
            int o = oversample(scale);
            return Text.literal(s).setStyle(bold ? BOLD[o] : REGULAR[o]);
        }
        return bold ? Text.literal(s).setStyle(VANILLA_BOLD) : Text.literal(s);
    }

    public static float width(String s, boolean bold, float scale) {
        return tr().getWidth(text(s, bold, scale)) * scale;
    }

    public static float width(String s) {
        return width(s, false, 1f);
    }

    /** Visual height of capital letters, used for vertical centring. */
    public static float height(float scale) {
        return 7f * scale;
    }

    public static void draw(DrawContext ctx, String s, float x, float y, int color, boolean bold, float scale) {
        if (s == null || s.isEmpty()) return;
        int c = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        // the vanilla renderer treats alpha < 4 as opaque, so skip near-invisible text entirely
        if (ColorUtil.alpha(c) < 8) return;
        float p = Render2D.px();
        x = Math.round(x / p) * p;
        y = Math.round(y / p) * p;
        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.translate(x, y);
        if (scale != 1f) ms.scale(scale, scale);
        ctx.drawText(tr(), text(s, bold, scale), 0, 0, c, false);
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
        String dots = "…";
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
