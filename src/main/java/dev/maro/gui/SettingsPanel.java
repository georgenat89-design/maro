package dev.maro.gui;

import dev.maro.config.ModuleFavorites;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GlyphNames;
import dev.maro.gui.render.Glyphs;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.Scroll;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Module;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The selected module's settings, in a panel that slides out on the right of the menu window:
 * a Main card (on/off, keybind, star), a live preview for modules that change how players look,
 * then a card per settings section.
 */
final class SettingsPanel {
    static final float WIDTH = 250f;
    private static final float ROW = 22f;

    private final ClickGuiScreen gui;
    private final SettingsList list = new SettingsList();
    private final Scroll scroll = new Scroll();
    private final Map<Module, List<SettingSection>> sections = new HashMap<>();
    private Module open, drawn;
    private String pendingSection;

    SettingsPanel(ClickGuiScreen gui) {
        this.gui = gui;
    }

    /** The module whose settings are open, or null. */
    Module module() {
        return open;
    }

    /** The module drawn while the panel slides away after closing. */
    Module drawn() {
        return drawn;
    }

    void open(Module m) {
        if (m != open) {
            scroll.reset();
            pendingSection = null;
        }
        open = m;
        drawn = m;
    }

    void close() {
        if (gui.listening != null && open != null && gui.listening == open.getBind()) gui.listening = null;
        open = null;
    }

    /** Brings the section called {@code title} to the top of the panel once it is drawn. */
    void scrollTo(String title) {
        pendingSection = title;
    }

    void onScroll(double amount) {
        scroll.scroll(amount);
    }

    static boolean hasPreview(Module m) {
        return VisualPreview.supports(m);
    }

    private List<SettingSection> visibleSections(Module m) {
        List<SettingSection> result = new ArrayList<>();
        for (SettingSection s : sections.computeIfAbsent(m, Module::getSettingSections)) {
            for (Setting<?> setting : s.getSettings()) {
                if (setting.isVisible()) {
                    result.add(s);
                    break;
                }
            }
        }
        return result;
    }

    void render(DrawContext ctx, float x, float y, float w, float h, float r) {
        Module m = drawn;
        if (m == null) return;
        gui.hit(x, y, w, h, (button, mx, my) -> {
        }); // clicks on the panel's empty space stay in it
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.windowBg());
        Render2D.roundOutline(ctx, x, y, w, h, r, Render2D.px(), Theme.BORDER);

        // header: icon, name and what it does, and a close button
        float on = Anims.of(m, "on", m.isEnabled());
        float ts = 30, tx = x + 14, ty = y + 14;
        Render2D.roundRect(ctx, tx, ty, ts, ts, 8f, ColorUtil.lerp(0x14FFFFFF, Theme.accent(0x33), on));
        Glyphs.draw(ctx, ModuleIcons.of(m), tx + ts / 2f, ty + ts / 2f, 15f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.accent(), on));
        float nx = tx + ts + 10, nameMax = x + w - 34 - nx;
        Fonts.draw(ctx, Fonts.trim(m.getDisplayName(), nameMax - 18, true, 0.98f), nx, ty + 2, Theme.TEXT, true, 0.98f);
        List<String> about = Fonts.wrap(m.getDescription(), x + w - 16 - nx, false, 0.64f);
        for (int i = 0; i < Math.min(2, about.size()); i++) {
            String line = about.get(i);
            if (i == 1 && about.size() > 2) line = Fonts.trim(line + " " + about.get(2), x + w - 16 - nx, false, 0.64f);
            Fonts.draw(ctx, line, nx, ty + 14 + i * 8.5f, Theme.TEXT_MUTED, false, 0.64f);
        }
        if (about.size() > 2 && gui.hovered(nx, ty + 12, x + w - 16 - nx, 18)) gui.tooltip(m.getDescription());
        float bx = x + w - 21, by = y + 21;
        boolean closeHover = gui.hovered(bx - 8, by - 8, 16, 16);
        float ch = Anims.of(this, "close", closeHover);
        if (ch > 0.01f) Render2D.circle(ctx, bx, by, 8, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x16 * ch)));
        Glyphs.draw(ctx, GlyphNames.X, bx, by, 10f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, ch));
        gui.hit(bx - 8, by - 8, 16, 16, (button, mx, my) -> {
            if (button == 0) {
                gui.closeSettings();
                Sounds.click();
            }
        });
        if (closeHover) gui.tooltip("Close (Esc)");
        gui.moduleConfigsButton(ctx, m, bx - 28, by - 8, 16);
        Render2D.rect(ctx, x + 1, y + 56, w - 2, Render2D.px(), ColorUtil.withAlpha(Theme.BORDER, 0x80));

        // the cards, scrolling under the header
        float top = y + 64, viewH = h - 64 - 8;
        float off = scroll.update();
        float cx = x + 12, cw = w - 24;
        gui.pushClip(x + 1, top, w - 2, viewH);
        float cy = top - off;
        cy += mainCard(ctx, m, cx, cy, cw) + SettingsList.CARD_GAP;
        if (hasPreview(m)) cy += previewCard(ctx, m, cx, cy, cw) + SettingsList.CARD_GAP;
        List<SettingSection> visible = visibleSections(m);
        float listTop = cy;
        if (visible.isEmpty()) {
            SettingsList.card(ctx, null, cx, cy, cw, 40);
            Fonts.drawCentered(ctx, "Nothing else to set up", cx + cw / 2f, cy + 14, Theme.TEXT_DIM, false, 0.78f);
            Fonts.drawCentered(ctx, "It just works while it is on", cx + cw / 2f, cy + 26, Theme.TEXT_MUTED, false, 0.66f);
            cy += 40;
        } else {
            cy += list.render(gui, ctx, cx, cy, cw, visible, true);
        }
        gui.popClip();
        scroll.setBounds(cy - (top - off) + 6, viewH);
        if (pendingSection != null) {
            for (SettingSection s : visible) {
                Float at = s.getTitle().equalsIgnoreCase(pendingSection) ? list.sectionTop(s) : null;
                if (at != null) scroll.scrollTo(listTop - (top - off) + at);
            }
            pendingSection = null;
        }
        scroll.drawBar(gui, ctx, x + w - 4, top, viewH);
    }

    // ---- main card ----------------------------------------------------------------------

    private float mainCard(DrawContext ctx, Module m, float x, float y, float w) {
        float head = SettingsList.headerHeight() + 3, h = head + ROW * 3 + 5;
        if (!gui.isVisible(y, h)) return h;
        SettingsList.card(ctx, "Main", x, y, w, h);
        float rx = x + 10, rw = w - 20, ry = y + head;

        boolean enabledHover = row(ctx, "Enabled", "Turn the module on or off (right-click its card)", rx, ry, rw);
        gui.hit(rx - 4, ry, rw + 8, ROW, (button, mx, my) -> {
            if (button != 0) return;
            m.toggle();
            Sounds.toggle(m.isEnabled());
        });
        Widgets.toggle(ctx, rx + rw - 20, ry + ROW / 2f - 5, 20, 10, Anims.of(m, "on", m.isEnabled()), enabledHover ? 1 : 0);
        ry += ROW;

        row(ctx, "Keybind", "Press it in-game to turn the module on or off", rx, ry, rw);
        float chipW = Widgets.bindChipWidth(gui, m.getBind());
        Widgets.bindChip(gui, ctx, m.getBind(), rx + rw - chipW, ry + ROW / 2f, 1f);
        ry += ROW;

        boolean starHover = row(ctx, "Starred", "Keep it under the Starred filter", rx, ry, rw);
        boolean starred = ModuleFavorites.contains(m);
        gui.hit(rx - 4, ry, rw + 8, ROW, (button, mx, my) -> {
            if (button != 0) return;
            ModuleFavorites.toggle(m);
            Sounds.click();
        });
        float st = Anims.of(m, "starred", starred);
        Glyphs.draw(ctx, GlyphNames.STAR, rx + rw - 6, ry + ROW / 2f, 11f,
                ColorUtil.lerp(starHover ? Theme.TEXT_DIM : Theme.TEXT_MUTED, Theme.YELLOW, st));
        return h;
    }

    /** A Main row's name, with its hover tint; returns whether it is hovered. */
    private boolean row(DrawContext ctx, String name, String about, float x, float y, float w) {
        boolean hovered = gui.hovered(x - 4, y, w + 8, ROW);
        float hv = Anims.of(this, name, hovered);
        if (hv > 0.01f) Render2D.roundRect(ctx, x - 5, y + 1, w + 10, ROW - 2, 4f, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x0A * hv)));
        Fonts.drawV(ctx, name, x, y + ROW / 2f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), false, 0.8f);
        if (hovered && gui.hovered(x, y + 4, Fonts.width(name, false, 0.8f), ROW - 8)) gui.tooltip(about);
        return hovered;
    }

    // ---- preview ------------------------------------------------------------------------

    private float previewCard(DrawContext ctx, Module m, float x, float y, float w) {
        // The live preview draws its own card: what the module changes, drawn as you edit it.
        float h = 176;
        // Not while the panel slides away: the preview's 3D picture cannot fade with it.
        if (gui.isVisible(y, h) && gui.settingsModule() == m) gui.visualPreview().draw(ctx, m, x, y, w, h);
        return h;
    }
}
