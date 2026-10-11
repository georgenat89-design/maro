package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ModeSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * The list a mode setting opens beside its row: every choice at once, the current one ticked.
 * Only one is open at a time. The row tells it where it is each frame with {@link #anchor}; a row
 * that is no longer drawn (its box closed, scrolled away) takes the list with it.
 */
public final class Dropdown {
    private Dropdown() {
    }

    private static final float ROW = 13f, PAD = 3f;

    private static ModeSetting open;
    private static long openedAt;
    /** The row's left and right edges and its top and height, from this frame. */
    private static float left, right, top, height;
    private static boolean anchored;
    private static float scroll;

    public static boolean isOpen(ModeSetting setting) {
        return open == setting;
    }

    /** Opens the list for a setting, or closes it if it is the one open. */
    public static void toggle(ModeSetting setting) {
        open = open == setting ? null : setting;
        openedAt = System.currentTimeMillis();
        scroll = 0;
        Sounds.click();
    }

    public static void close() {
        open = null;
    }

    public static boolean anyOpen() {
        return open != null;
    }

    /** Where the open setting's row is this frame; call it while drawing the row. */
    public static void anchor(ModeSetting setting, float rowLeft, float rowRight, float rowTop, float rowHeight) {
        if (open != setting) return;
        left = rowLeft;
        right = rowRight;
        top = rowTop;
        height = rowHeight;
        anchored = true;
    }

    /** Draws the open list over everything; call it once a frame, after all the rows. */
    public static void render(ClickGuiScreen gui, DrawContext ctx, float screenW, float screenH) {
        ModeSetting m = open;
        if (m == null) return;
        if (!anchored) {
            open = null;
            return;
        }
        anchored = false;

        List<String> modes = m.getModes();
        Fonts.beginRaw();
        try {
            float w = 70f;
            for (String mode : modes) w = Math.max(w, Fonts.width(mode, false, 0.74f) + 30f);
            float full = modes.size() * ROW + PAD * 2;
            float h = Math.min(full, screenH - 8f);
            // Beside the row, on whichever side has room; level with it, nudged on screen.
            float x = right + 4f;
            if (x + w > screenW - 4f) x = left - w - 4f;
            x = Math.max(4f, Math.min(screenW - w - 4f, x));
            float y = Math.max(4f, Math.min(screenH - h - 4f, top + height / 2f - ROW / 2f - PAD - m.index() * ROW));

            float t = Easing.outCubic(Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f));
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * t);
            float r = Math.min(5f, Theme.radius() + 1f);

            // A click anywhere else closes it; the list itself takes its own clicks and wheel.
            gui.hit(0, 0, screenW, screenH, (button, mx, my) -> close());
            gui.hit(x, y, w, h, (button, mx, my) -> {
            });
            float maxScroll = Math.max(0, full - h);
            gui.scrollHit(x, y, w, h, amount -> scroll = Math.max(0, Math.min(maxScroll, scroll - (float) amount * ROW * 2)));

            Render2D.shadow(ctx, x, y + 2, w, h, r, 12f, 0x90000000);
            if (Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 10f, Theme.accent(0x18));
            Render2D.roundRect(ctx, x, y, w, h, r, Theme.windowBg());
            Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.accent(0x60), Theme.accent2(0x30), 0xFF1A1A22, 0xFF1A1A22);

            gui.pushClip(x + 1, y + 1, w - 2, h - 2);
            float ry = y + PAD - scroll;
            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                boolean selected = i == m.index();
                boolean hov = gui.hovered(x + 2, ry, w - 4, ROW);
                float hv = Anims.of(m, "drop" + i, hov);
                if (selected) Render2D.roundRect(ctx, x + 2, ry + 0.5f, w - 4, ROW - 1, 3f, Theme.accent(0x38));
                else if (hv > 0.01f) Render2D.roundRect(ctx, x + 2, ry + 0.5f, w - 4, ROW - 1, 3f, ColorUtil.withAlpha(0xFF262633, Math.round(0xFF * hv)));
                if (selected) Render2D.roundRect(ctx, x + 3.5f, ry + 3f, 1.6f, ROW - 6, 0.8f, Theme.accent());
                int color = selected ? 0xFFFFFFFF : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
                Fonts.drawV(ctx, mode, x + 9f, ry + ROW / 2f, color, selected, 0.74f);
                if (selected) Icons.CHECK.draw(ctx, x + w - 10f, ry + ROW / 2f, 6.5f, Theme.accent(), 0f);
                final String choice = mode;
                gui.hit(x + 2, ry, w - 4, ROW, (button, mx, my) -> {
                    m.set(choice);
                    close();
                    Sounds.click();
                });
                ry += ROW;
            }
            gui.popClip();
            if (maxScroll > 0) {
                float bar = Math.max(12, h * h / full);
                Render2D.roundRect(ctx, x + w - 4, y + (h - bar) * (scroll / maxScroll), 2, bar, 1f, 0x50FFFFFF);
            }
            Render2D.setAlpha(prev);
        } finally {
            Fonts.endRaw();
        }
    }
}
