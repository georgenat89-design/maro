package dev.maro.gui.page;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.config.ModuleFavorites;
import dev.maro.gui.ModuleIcons;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.GlyphNames;
import dev.maro.gui.render.Glyphs;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * A category's modules (or search results when {@code category == null}) as a two-column grid of
 * cards: icon, name, star and switch. A left click opens the module's settings beside the window,
 * a right click turns it on or off, and a middle click sets its key.
 */
public class ModulesPage extends Page {
    private static final float CARD_H = 34f;
    private static final float GAP = 8f;
    private static final float FOOTER_H = 22f;

    private final Category category;

    public ModulesPage(ClickGuiScreen gui, Category category) {
        super(gui);
        this.category = category;
    }

    public Category category() {
        return category;
    }

    /** The modules shown, before the All / Enabled / Starred filter. */
    public List<Module> all() {
        List<Module> list = category == null ? ModuleManager.search(gui.getSearch().getText()) : ModuleManager.byCategory(category);
        return gui.antiCheatSectionUnlocked() ? list : list.stream().filter(m -> m.getCategory() != Category.ANTI_CHEAT_OFF).toList();
    }

    private List<Module> modules() {
        return switch (gui.filter()) {
            case ENABLED -> all().stream().filter(Module::isEnabled).toList();
            case STARRED -> all().stream().filter(ModuleFavorites::contains).toList();
            default -> all();
        };
    }

    /** Opens a module's settings: its own screen if it has one, otherwise the panel beside the window. */
    public void openSettings(Module m) {
        if (m instanceof dev.maro.module.impl.player.AutoBuilder builder) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.builder.BuilderControlScreen(gui, builder));
            return;
        }
        net.minecraft.client.gui.screen.Screen own = m.panel(gui);
        if (own != null) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(own);
            return;
        }
        openOptions(m);
    }

    /** Opens the settings panel for a module, even one with a screen of its own. */
    public void openOptions(Module m) {
        gui.showSettings(m);
        Sounds.click();
    }

    @Override
    public boolean typeToSearch() {
        return true;
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        if (category == Category.ANTI_CHEAT_OFF && !gui.antiCheatSectionUnlocked()) {
            gui.renderAntiCheatPrompt(ctx, x, y, w, h);
            return;
        }
        List<Module> mods = modules();
        float gridH = h - FOOTER_H;
        if (mods.isEmpty()) empty(ctx, x, y, w, gridH);
        else grid(ctx, mods, x, y, w, gridH);
        footer(ctx, x, y + h - FOOTER_H, w);
    }

    // ---- grid ---------------------------------------------------------------------------

    private void grid(DrawContext ctx, List<Module> mods, float x, float y, float w, float h) {
        int cols = 2;
        float cw = (w - GAP * (cols - 1)) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        scroll.setBounds(rows * (CARD_H + GAP) - GAP + 2, h);
        float off = scroll.update();

        gui.pushClip(x - 4, y, w + 8, h);
        for (int i = 0; i < mods.size(); i++) {
            float cx = x + (i % cols) * (cw + GAP);
            float cy = y + 1 + (i / cols) * (CARD_H + GAP) - off;
            if (!gui.isVisible(cy - 4, CARD_H + 8)) continue;
            card(ctx, mods.get(i), cx, cy, cw, CARD_H, intro(i));
        }
        gui.popClip();
        scroll.drawBar(gui, ctx, x + w + 6, y, h);
    }

    private void card(DrawContext ctx, Module m, float x, float y, float w, float h, float intro) {
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * intro);
        y += (1 - intro) * 8f;

        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(m, "hover", hovered);
        float on = Anims.of(m, "on", m.isEnabled());
        float sel = Anims.of(m, "selected", gui.settingsModule() == m);
        float r = SettingsList.cardRadius();
        float cy = y + h / 2f;

        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, Math.max(hv, sel * 0.6f)));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(ColorUtil.withAlpha(Theme.BORDER, 0x90), Theme.accent(0xB0), sel));
        if (sel > 0.01f) Render2D.roundRect(ctx, x + 1, y + 8, 2, h - 16, 1f, Theme.accent(Math.round(255 * sel)));

        // the card: left = settings, right = on/off, middle = key
        gui.hit(x, y, w, h, (button, mx, my) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                openSettings(m);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                m.toggle();
                Sounds.toggle(m.isEnabled());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                gui.listening = m.getBind();
                Sounds.click();
            }
        });
        gui.place(m, x + w * 0.4f, cy);

        // icon tile
        float ts = 22, tx = x + 7, ty = cy - ts / 2f;
        Render2D.roundRect(ctx, tx, ty, ts, ts, 6f, ColorUtil.lerp(ColorUtil.withAlpha(0xFFFFFFFF, 0x10 + Math.round(0x08 * hv)), Theme.accent(0x30), on));
        int iconColor = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), Theme.accent(), on);
        Glyphs.draw(ctx, ModuleIcons.of(m), tx + ts / 2f, ty + ts / 2f, 12f, iconColor);

        // switch, star, then the name in what is left
        float toggleX = x + w - 9 - 20, starX = toggleX - 12;
        float nx = tx + ts + 9, maxName = starX - 10 - nx;
        boolean listening = gui.listening == m.getBind();
        int nameColor = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, on);
        String name = listening ? "Press a key…" : Fonts.trim(m.getDisplayName(), maxName - (m.isExperimental() ? 11 : 0), false, 0.84f);
        Fonts.drawV(ctx, name, nx, cy, listening ? Theme.accent() : nameColor, false, 0.84f);
        if (m.isExperimental() && !listening) {
            float wx = nx + Fonts.width(name, false, 0.84f) + 6;
            Icons.WARNING.draw(ctx, wx, cy, 7f, Theme.RED, 0);
            if (gui.hovered(wx - 5, cy - 5, 10, 10)) gui.tooltip("Experimental - may be unstable or detectable");
        }

        boolean starred = ModuleFavorites.contains(m);
        boolean starHover = gui.hovered(starX - 7, cy - 7, 14, 14);
        float st = Anims.of(m, "starred", starred);
        int starIdle = ColorUtil.withAlpha(Theme.TEXT_MUTED, starHover ? 0xFF : Math.round(0x70 + 0x50 * hv));
        Glyphs.draw(ctx, GlyphNames.STAR, starX, cy, 10.5f, ColorUtil.lerp(starIdle, Theme.YELLOW, st));
        gui.hit(starX - 7, cy - 7, 14, 14, (button, mx, my) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            ModuleFavorites.toggle(m);
            Sounds.click();
        });

        boolean toggleHover = gui.hovered(toggleX - 3, cy - 8, 26, 16);
        Widgets.toggle(ctx, toggleX, cy - 5, 20, 10, on, toggleHover ? 1 : hv * 0.5f);
        gui.hit(toggleX - 3, cy - 8, 26, 16, (button, mx, my) -> {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            m.toggle();
            Sounds.toggle(m.isEnabled());
        });

        if (starHover) gui.tooltip(starred ? "Unstar" : "Star, to find it under Starred");
        else if (hovered && !toggleHover) {
            String key = m.getBind().isBound() ? "  •  Key: " + m.getBind().getKeyName() : "";
            gui.tooltip(m.getDescription() + key);
        }
        Render2D.setAlpha(prev);
    }

    private void footer(DrawContext ctx, float x, float y, float w) {
        List<Module> all = all();
        long enabled = all.stream().filter(Module::isEnabled).count();
        Render2D.rect(ctx, x, y + 2, w, Render2D.px(), ColorUtil.withAlpha(Theme.BORDER, 0x70));
        String count = all.size() + (all.size() == 1 ? " module" : " modules") + "  ·  " + enabled + " enabled";
        Fonts.drawV(ctx, count, x + 1, y + 13, Theme.TEXT_MUTED, false, 0.66f);
        Fonts.drawRight(ctx, "Left click: settings   ·   Right click: on / off", x + w, y + 13,
                ColorUtil.withAlpha(Theme.TEXT_MUTED, 0xA0), false, 0.6f);
    }

    private void empty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 12;
        String icon, title, hint;
        if (gui.filter() == ClickGuiScreen.Filter.STARRED) {
            icon = GlyphNames.STAR;
            title = "No starred modules";
            hint = "Click the star on a module to keep it here";
        } else if (gui.filter() == ClickGuiScreen.Filter.ENABLED) {
            icon = GlyphNames.POWER;
            title = "Nothing is on";
            hint = "Right-click a module to turn it on";
        } else if (category == null) {
            icon = GlyphNames.SEARCH;
            title = "No results";
            hint = "Try a different search";
        } else {
            icon = ModuleIcons.of(category);
            title = "No modules yet";
            hint = "Modules in " + category.getDisplayName() + " will show here";
        }
        Render2D.circle(ctx, cx, cy, 19, Theme.CARD);
        Render2D.ring(ctx, cx, cy, 19, 1f, ColorUtil.withAlpha(Theme.BORDER, 0x90));
        Glyphs.draw(ctx, icon, cx, cy, 16f, Theme.accent());
        Fonts.drawCentered(ctx, title, cx, cy + 31, Theme.TEXT, true, 0.9f);
        Fonts.drawCentered(ctx, hint, cx, cy + 43, Theme.TEXT_MUTED, false, 0.7f);
    }
}
