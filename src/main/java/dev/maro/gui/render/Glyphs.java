package dev.maro.gui.render;

import dev.maro.Maro;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Lucide line icons ({@link GlyphNames}), drawn through the game's font renderer like the menu's
 * text. Each icon is a square glyph; the copy whose oversample matches its real pixel size is used,
 * so icons stay sharp at every GUI scale and menu size.
 */
public final class Glyphs {
    private static final int MAX_OVERSAMPLE = 6;
    /** The glyph size in the font definitions (assets/maro/font/lucide_x*.json). */
    private static final float EM = 16f;
    /** Where the game puts a TTF baseline below the top of a line, in font pixels. */
    private static final float BASELINE = 7f;
    private static final Style[] STYLES = new Style[MAX_OVERSAMPLE + 1];

    static {
        for (int i = 1; i <= MAX_OVERSAMPLE; i++)
            STYLES[i] = Style.EMPTY.withFont(new StyleSpriteSource.Font(Identifier.of(Maro.MOD_ID, "lucide_x" + i)));
    }

    private Glyphs() {
    }

    /** Draws {@code glyph} {@code size} GUI units square, centred on {@code (cx, cy)}, faded by the global alpha. */
    public static void draw(DrawContext ctx, String glyph, float cx, float cy, float size, int color) {
        if (glyph == null || glyph.isEmpty()) return;
        int c = ColorUtil.mulAlpha(color, Render2D.getAlpha());
        if (ColorUtil.alpha(c) < 8) return;
        Matrix3x2fStack ms = ctx.getMatrices();
        float k = size / EM;
        double px = MinecraftClient.getInstance().getWindow().getScaleFactor() * k * Math.hypot(ms.m00(), ms.m01());
        int oversample = (int) Math.max(1, Math.min(MAX_OVERSAMPLE, Math.ceil(px - 0.15)));
        ms.pushMatrix();
        ms.translate(cx - size / 2f, cy - size / 2f);
        ms.scale(k, k);
        // Lucide's em box sits wholly above the baseline; start the line so the box fills 0..EM.
        ctx.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(glyph).setStyle(STYLES[oversample]),
                0, Math.round(EM - BASELINE), c, false);
        ms.popMatrix();
    }
}
