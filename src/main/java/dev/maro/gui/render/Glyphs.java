package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;

/**
 * Lucide outlines rasterized at framebuffer size and centered on their visible bounds.
 */
public final class Glyphs {
    private Glyphs() {
    }

    /** Draws {@code glyph} {@code size} GUI units square, centred on {@code (cx, cy)}, faded by the global alpha. */
    public static void draw(DrawContext ctx, String glyph, float cx, float cy, float size, int color) {
        SmoothHudText.drawGlyph(ctx, glyph, cx, cy, size, color);
    }
}
