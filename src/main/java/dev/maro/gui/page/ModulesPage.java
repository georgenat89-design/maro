package dev.maro.gui.page;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.Scroll;
import dev.maro.gui.widget.SettingsList;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.SettingSection;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Module grid for a category (or search results when {@code category == null}) plus a per-module settings view. */
public class ModulesPage extends Page {
    private static final float CARD_H = 40f;
    private static final float GAP = 5f;

    private final Category category;
    private final Animation view = new Animation(13f, 0f);
    private final Scroll settingsScroll = new Scroll();
    private final SettingsList settingsList = new SettingsList();
    private final Map<Module, List<SettingSection>> sections = new HashMap<>();
    private Module open;
    private Module shown;

    public ModulesPage(ClickGuiScreen gui, Category category) {
        super(gui);
        this.category = category;
    }

    private List<Module> modules() {
        return category == null ? ModuleManager.search(gui.getSearch().getText()) : ModuleManager.byCategory(category);
    }

    public void openSettings(Module m) {
        open = m;
        settingsScroll.reset();
        Sounds.click();
    }

    @Override
    public void onOpen() {
        super.onOpen();
        if (category == null) open = null;
    }

    @Override
    public boolean onEscape() {
        if (open == null) return false;
        open = null;
        return true;
    }

    @Override
    public void onScroll(double amount) {
        if (open != null) settingsScroll.scroll(amount);
        else scroll.scroll(amount);
    }

    @Override
    public boolean typeToSearch() {
        return open == null;
    }

    @Override
    public void render(DrawContext ctx, float x, float y, float w, float h) {
        float v = view.update(open != null ? 1f : 0f);
        if (open != null) shown = open;
        float prev = Render2D.getAlpha();
        if (v < 0.999f) {
            Render2D.setAlpha(prev * (1 - v));
            gui.setInteractive(open == null);
            renderGrid(ctx, x - v * 24f, y, w, h);
        }
        if (v > 0.001f && shown != null) {
            Render2D.setAlpha(prev * v);
            gui.setInteractive(open != null);
            renderSettings(ctx, shown, x + (1 - v) * 24f, y, w, h);
        }
        if (v == 0f) shown = null;
        Render2D.setAlpha(prev);
        gui.setInteractive(true);
    }

    // ---- grid ---------------------------------------------------------------------------

    private void renderGrid(DrawContext ctx, float x, float y, float w, float h) {
        List<Module> mods = modules();
        float top = y;
        if (category == null) {
            String q = gui.getSearch().getText();
            String label = mods.size() + (mods.size() == 1 ? " result" : " results") + " for ";
            Fonts.draw(ctx, label, x + 2, y + 2, Theme.TEXT_MUTED, false, 0.78f);
            Fonts.draw(ctx, "\"" + q + "\"", x + 2 + Fonts.width(label, false, 0.78f), y + 2, Theme.accent(), false, 0.78f);
            top += 14;
        }
        if (mods.isEmpty()) {
            empty(ctx, x, top, w, h - (top - y));
            return;
        }

        int cols = w >= 330 ? 2 : 1;
        float cw = (w - 6 - GAP * (cols - 1)) / cols;
        int rows = (mods.size() + cols - 1) / cols;
        float viewH = h - (top - y);
        scroll.setBounds(rows * (CARD_H + GAP) - GAP + 2, viewH);
        float off = scroll.update();

        gui.pushClip(x - 6, top, w + 12, viewH);
        for (int i = 0; i < mods.size(); i++) {
            float cx = x + (i % cols) * (cw + GAP);
            float cy = top + 1 + (i / cols) * (CARD_H + GAP) - off;
            if (!gui.isVisible(cy - 6, CARD_H + 12)) continue;
            card(ctx, mods.get(i), cx, cy, cw, CARD_H, intro(i));
        }
        gui.popClip();
        scroll.drawBar(gui, ctx, x + w - 2, top, viewH);
    }

    private void card(DrawContext ctx, Module m, float x, float y, float w, float h, float intro) {
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * intro);
        y += (1 - intro) * 10f;

        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(m, "hover", hovered);
        float on = Anims.of(m, "on", m.isEnabled());
        float r = Theme.radius();

        if (on > 0.01f && Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 7f, Theme.accent(Math.round(0x26 * on)));
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        if (on > 0.01f) {
            Render2D.roundRect(ctx, x, y, w, h, r, Theme.accent(Math.round(0x34 * on)), Theme.accent2(Math.round(0x0A * on)), 0x00000000, Theme.accent(Math.round(0x10 * on)));
        }
        int border = ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, 0xFF3A374A, hv), Theme.accent(0x70), on);
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, border, ColorUtil.lerp(border, Theme.BORDER, on * 0.7f),
                ColorUtil.lerp(border, Theme.BORDER, on * 0.7f), border);

        float nx = x + 11;
        float toggleX = x + w - 32;
        boolean hasSettings = !m.getSettings().isEmpty();
        float dotsX = toggleX - 13;

        // whole card: left = toggle, right = settings, middle = bind
        gui.hit(x, y, w, h, (button, mx, my) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                m.toggle();
                Sounds.toggle(m.isEnabled());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openSettings(m);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                gui.listening = m.getBind();
            }
        });

        // keybind chip, right-aligned next to the controls
        String bindLabel = gui.listening == m.getBind() ? "Press a key" : m.getBind().getKeyName();
        float chipW = Fonts.width(bindLabel, false, 0.72f) + 9;
        float chipRight = hasSettings ? dotsX - 9 : toggleX - 7;
        float chipX = chipRight - chipW;
        Widgets.bindChip(gui, ctx, m.getBind(), chipX, y + h / 2f, 1f);

        // name + description
        float textMax = chipX - nx - 8;
        String name = Fonts.trim(m.getName(), textMax - (m.isExperimental() ? 12 : 0), false, 0.95f);
        Fonts.draw(ctx, name, nx, y + 9f, ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, on), false, 0.95f);
        if (m.isExperimental()) {
            float wx = nx + Fonts.width(name, false, 0.95f) + 7;
            Icons.WARNING.draw(ctx, wx, y + 12.5f, 7.5f, Theme.RED, 0);
            if (gui.hovered(wx - 5, y + 7.5f, 10, 10)) gui.tooltip("Experimental - may be unstable or detectable");
        }
        String desc = m.getDescription();
        String shown = Fonts.trim(desc, textMax, false, 0.7f);
        Fonts.draw(ctx, shown, nx, y + 22.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv * 0.6f), false, 0.7f);
        if (!shown.equals(desc) && gui.hovered(nx, y + 20, textMax, 10)) gui.tooltip(desc);

        // settings button
        if (hasSettings) {
            float dy = y + h / 2f;
            boolean dh = gui.hovered(dotsX - 7, dy - 7, 14, 14);
            float dhv = Anims.of(m, "dots", dh);
            if (dhv > 0.01f) Render2D.roundRect(ctx, dotsX - 7, dy - 7, 14, 14, 4, ColorUtil.withAlpha(0xFF2A2938, Math.round(0xFF * dhv)));
            Icons.DOTS.draw(ctx, dotsX, dy, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, Math.max(dhv, hv * 0.5f)), dhv);
            gui.hit(dotsX - 7, dy - 7, 14, 14, (button, mx, my) -> openSettings(m));
            if (dh) gui.tooltip("Settings (or right-click the card)");
        }

        Widgets.toggle(ctx, toggleX, y + h / 2f - 5.5f, 22, 11, on, hv);
        Render2D.setAlpha(prev);
    }

    private void empty(DrawContext ctx, float x, float y, float w, float h) {
        float cx = x + w / 2f, cy = y + h / 2f - 14;
        float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 1.5f;
        Render2D.circle(ctx, cx, cy + bob, 20, Theme.CARD);
        Render2D.ring(ctx, cx, cy + bob, 20, 1f, Theme.BORDER);
        if (Theme.glow()) Render2D.shadow(ctx, cx - 20, cy - 20 + bob, 40, 40, 20, 10, Theme.accent(0x18));
        Icons.Icon icon = category == null ? Icons.SEARCH : category.getIcon();
        icon.draw(ctx, cx, cy + bob, 15, Theme.accent(), 0);
        boolean search = category == null;
        Fonts.drawCentered(ctx, search ? "No results" : "No modules yet", cx, cy + 32, Theme.TEXT, true, 0.95f);
        Fonts.drawCentered(ctx, search ? "Try a different search term" : "Register modules in ModuleManager#init()", cx, cy + 43, Theme.TEXT_MUTED, false, 0.75f);
    }

    // ---- settings view ------------------------------------------------------------------

    private void renderSettings(DrawContext ctx, Module m, float x, float y, float w, float h) {
        float r = Theme.radius();
        float hh = 34;
        Render2D.roundRect(ctx, x, y + 1, w - 6, hh, r, Theme.CARD);
        Render2D.roundOutline(ctx, x, y + 1, w - 6, hh, r, 1f, Theme.BORDER);
        Widgets.button(gui, ctx, this, x + 6, y + 7, 22, 22, "", Widgets.Style.SECONDARY, Icons.BACK, () -> open = null);
        Fonts.draw(ctx, m.getName(), x + 36, y + 8.5f, Theme.TEXT, true, 1.05f);
        Fonts.draw(ctx, Fonts.trim(m.getDescription(), w - 120, false, 0.72f), x + 36, y + 21f, Theme.TEXT_MUTED, false, 0.72f);

        float on = Anims.of(m, "on", m.isEnabled());
        float tx = x + w - 6 - 38, ty = y + 1 + hh / 2f - 6.5f;
        boolean th = gui.hovered(tx - 4, ty - 4, 34, 21);
        Widgets.toggle(ctx, tx, ty, 26, 13, on, Anims.of(m, "bigToggle", th));
        gui.hit(tx - 4, ty - 4, 34, 21, (button, mx, my) -> {
            m.toggle();
            Sounds.toggle(m.isEnabled());
        });

        List<SettingSection> secs = sections.computeIfAbsent(m, mod -> {
            java.util.ArrayList<SettingSection> result = new java.util.ArrayList<>(mod.getSettingSections());
            SettingSection bind = new SettingSection("Keybind");
            bind.add(mod.getBind());
            result.add(bind);
            return result;
        });

        float ly = y + hh + 7, lh = h - hh - 7;
        float off = settingsScroll.update();
        gui.pushClip(x - 6, ly, w + 12, lh);
        float content = settingsList.render(gui, ctx, x, ly - off, w - 6, secs);
        gui.popClip();
        settingsScroll.setBounds(content + 2, lh);
        settingsScroll.drawBar(gui, ctx, x + w - 2, ly, lh);
    }
}
