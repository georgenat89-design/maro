package dev.maro.gui.render;

import dev.maro.config.ClientSettings;
import dev.maro.gui.theme.Theme;
import dev.maro.util.ColorUtil;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Maro's mark and contour lines follow the live GUI accent, spread faintly behind the menu
 * like a map. The M shares the resolution-independent outline used by startup and the title menu.
 */
public final class Logo {
    public static final Identifier MARK = Identifier.of("maro", "textures/gui/logo.png");
    public static final Identifier CONTOURS = Identifier.of("maro", "textures/gui/topo.png");
    private static final int MARK_SIZE = 128, CONTOURS_W = 1024, CONTOURS_H = 576;
    private Logo() {
    }

    /** The M, {@code size} GUI pixels across, centred on {@code (cx, cy)}, glowing by {@code glow} (0 to 1). */
    public static void mark(DrawContext ctx, float cx, float cy, float size, float glow) {
        Theme.update();
        if (Theme.glow()) {
            float g = size * 0.42f;
            Render2D.shadow(ctx, cx - g, cy - g, g * 2, g * 2, g, 3f + glow * 5f, Theme.accent(Math.round(0x28 + 0x38 * glow)));
        }
        BootMark.drawThemed(ctx,cx-size/2f,cy-size/2f,size,0xFFFFFFFF);
    }

    /** The contour lines across the whole screen, in the accent colour, {@code opacity} (0 to 1) strong. */
    public static void contours(DrawContext ctx, int width, int height, float opacity) {
        if (!ClientSettings.contourLines.get() || opacity <= 0.01f) return;
        int color = ColorUtil.withAlpha(Theme.accent(), Math.round(255 * Math.min(1f, opacity * Render2D.getAlpha())));
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, CONTOURS, 0, 0, 0, 0, width, height, CONTOURS_W, CONTOURS_H, CONTOURS_W, CONTOURS_H, color);
    }
}
