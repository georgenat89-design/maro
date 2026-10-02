package dev.maro.gui.page;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;

/** Accent presets plus the theme settings. */
public class ThemePage extends Page {
    private final SettingsList list = new SettingsList();

    public ThemePage(ClickGuiScreen gui) {
        super(gui);
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        float off = scroll.update();
        float lw = w - 6;
        gui.pushClip(x - 6, y, w + 12, h);
        float cy = y - off;

        Widgets.sectionLabel(ctx, "Presets", x + 2, cy + 1, lw - 4);
        cy += 13;
        float tileW = 46, tileH = 38, gap = 5;
        int cols = Math.max(1, (int) ((lw + gap) / (tileW + gap)));
        tileW = (lw - gap * (cols - 1)) / cols;
        int rows = (Theme.PRESETS.length + cols - 1) / cols;
        for (int i = 0; i < Theme.PRESETS.length; i++) {
            int preset = Theme.PRESETS[i];
            float tx = x + (i % cols) * (tileW + gap), ty = cy + (i / cols) * (tileH + gap);
            float in = intro(i);
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * in);
            ty += (1 - in) * 8;
            boolean sel = !ClientSettings.rainbow.get() && (ClientSettings.accent.get() & 0xFFFFFF) == (preset & 0xFFFFFF);
            boolean hov = gui.hovered(tx, ty, tileW, tileH);
            float hv = Anims.of(Theme.PRESETS, "tile" + i, hov);
            float sv = Anims.of(Theme.PRESETS, "sel" + i, sel);
            float r = Theme.radius();
            if (sv > 0.01f && Theme.glow()) Render2D.shadow(ctx, tx, ty, tileW, tileH, r, 6, ColorUtil.withAlpha(preset, Math.round(0x40 * sv)));
            Render2D.roundRect(ctx, tx, ty, tileW, tileH, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
            Render2D.roundOutline(ctx, tx, ty, tileW, tileH, r, 1f, ColorUtil.lerp(Theme.BORDER, ColorUtil.withAlpha(preset, 0xC0), sv));
            float dcx = tx + tileW / 2f, dcy = ty + 14;
            float dr = 7 + hv * 1f;
            Render2D.circle(ctx, dcx, dcy, dr, ColorUtil.shade(ColorUtil.hueShift(preset, 0.08f), -0.05f));
            Render2D.arc(ctx, dcx, dcy, dr, dr, -90, 180, preset, preset);
            if (sv > 0.01f) Icons.CHECK.draw(ctx, dcx, dcy, 7 * sv, 0xFFFFFFFF, 0);
            Fonts.drawCentered(ctx, Theme.PRESET_NAMES[i], dcx, ty + 30, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(hv, sv)), false, 0.7f);
            Render2D.setAlpha(prev);
            gui.hit(tx, ty, tileW, tileH, (button, mx, my) -> {
                ClientSettings.accent.set(preset);
                ClientSettings.rainbow.set(false);
                Sounds.click();
            });
        }
        cy += rows * (tileH + gap) + 4;
        float header = cy - (y - off);

        float content = list.render(gui, ctx, x, cy, lw, ClientSettings.THEME_PAGE);
        gui.popClip();
        scroll.setBounds(header + content + 4, h);
        scroll.drawBar(gui, ctx, x + w - 2, y, h);
    }
}
