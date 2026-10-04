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
import dev.maro.setting.ActionSetting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
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

    // the open module's settings: a card per category, or one category's settings
    private final Animation sectionView = new Animation(15f, 0f);
    private final Scroll homeScroll = new Scroll();
    private SettingSection section;
    private SettingSection shownSection;
    private long homeSince;
    private boolean snapTabs;
    private SettingSection resetArmed;
    private long resetArmedAt;

    public ModulesPage(ClickGuiScreen gui, Category category) {
        super(gui);
        this.category = category;
    }

    private List<Module> modules() {
        return category == null ? ModuleManager.search(gui.getSearch().getText()) : ModuleManager.byCategory(category);
    }

    public void openSettings(Module m) {
        open = m;
        section = null;
        shownSection = null;
        sectionView.snap(0f);
        settingsScroll.reset();
        homeScroll.reset();
        homeSince = System.currentTimeMillis();
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
        back();
        return true;
    }

    @Override
    public void onScroll(double amount) {
        if (open == null) scroll.scroll(amount);
        else if (section == null && usesCards(visibleSections(open))) homeScroll.scroll(amount);
        else settingsScroll.scroll(amount);
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
    //
    // A module with a few settings shows them straight away. A bigger one opens on a card per
    // category (Look, Colors, Placement, ...) saying what is inside; clicking a card opens just
    // that category, with tabs along the top to hop between categories and a back arrow home.

    private static final float HEADER_H = 34f;
    private static final float SECTION_CARD_H = 48f;
    private static final float TAB_H = 18f;
    /** Modules with more settings than this, spread over two or more categories, get cards. */
    private static final int CARD_THRESHOLD = 6;

    private void back() {
        if (section != null) {
            section = null;
            homeSince = System.currentTimeMillis();
            Sounds.click();
        } else {
            open = null;
        }
    }

    private void openSection(SettingSection s) {
        if (section == null) snapTabs = true;
        section = s;
        settingsScroll.reset();
        resetArmed = null;
        Sounds.click();
    }

    /** Opens the open module's category called {@code title}, if it has one. */
    public void openSection(String title) {
        if (open == null) return;
        for (SettingSection s : visibleSections(open)) {
            if (s.getTitle().equalsIgnoreCase(title)) {
                openSection(s);
                return;
            }
        }
    }

    private List<SettingSection> visibleSections(Module m) {
        List<SettingSection> result = new ArrayList<>();
        for (SettingSection s : sections.computeIfAbsent(m, Module::getSettingSections)) {
            if (visibleCount(s) > 0) result.add(s);
        }
        return result;
    }

    private static int visibleCount(SettingSection s) {
        int n = 0;
        for (Setting<?> setting : s.getSettings()) if (setting.isVisible()) n++;
        return n;
    }

    private static boolean usesCards(List<SettingSection> visible) {
        if (visible.size() < 2) return false;
        int total = 0;
        for (SettingSection s : visible) total += visibleCount(s);
        return total > CARD_THRESHOLD;
    }

    private void renderSettings(DrawContext ctx, Module m, float x, float y, float w, float h) {
        boolean base = open != null;
        List<SettingSection> visible = visibleSections(m);
        boolean cards = usesCards(visible);
        if (section != null && (!cards || !visible.contains(section))) section = null;
        if (!cards) {
            sectionView.snap(0f);
            shownSection = null;
        }

        header(ctx, m, x, y, w);

        float ly = y + HEADER_H + 7, lh = h - HEADER_H - 7;
        if (visible.isEmpty()) {
            Fonts.drawCentered(ctx, "Nothing to set up", x + w / 2f, ly + 30, Theme.TEXT_DIM, false, 0.9f);
            Fonts.drawCentered(ctx, "This module just works when it is on", x + w / 2f, ly + 42, Theme.TEXT_MUTED, false, 0.72f);
            return;
        }
        if (!cards) {
            settingsList(ctx, visible, visible.size() > 1, x, ly, w, lh);
            return;
        }

        float v = sectionView.update(section != null ? 1f : 0f);
        if (section != null) shownSection = section;
        float prev = Render2D.getAlpha();
        if (v < 0.999f) {
            Render2D.setAlpha(prev * (1 - v));
            gui.setInteractive(base && section == null);
            renderHome(ctx, visible, x - v * 18f, ly, w, lh);
        }
        if (v > 0.001f && shownSection != null) {
            Render2D.setAlpha(prev * v);
            gui.setInteractive(base && section != null);
            renderSection(ctx, visible, shownSection, x + (1 - v) * 18f, ly, w, lh);
        }
        if (v == 0f) shownSection = null;
        Render2D.setAlpha(prev);
        gui.setInteractive(base);
    }

    private void header(DrawContext ctx, Module m, float x, float y, float w) {
        float r = Theme.radius();
        float hh = HEADER_H;
        Render2D.roundRect(ctx, x, y + 1, w - 6, hh, r, Theme.CARD);
        Render2D.roundOutline(ctx, x, y + 1, w - 6, hh, r, 1f, Theme.BORDER);
        Widgets.button(gui, ctx, this, x + 6, y + 7, 22, 22, "", Widgets.Style.SECONDARY, Icons.BACK, this::back);
        if (gui.hovered(x + 6, y + 7, 22, 22)) gui.tooltip(section != null ? "Back to categories (Esc)" : "Back to modules (Esc)");

        // big toggle, then the keybind chip to its left
        float on = Anims.of(m, "on", m.isEnabled());
        float tx = x + w - 6 - 38, ty = y + 1 + hh / 2f - 6.5f;
        boolean th = gui.hovered(tx - 4, ty - 4, 34, 21);
        Widgets.toggle(ctx, tx, ty, 26, 13, on, Anims.of(m, "bigToggle", th));
        gui.hit(tx - 4, ty - 4, 34, 21, (button, mx, my) -> {
            m.toggle();
            Sounds.toggle(m.isEnabled());
        });
        String bindLabel = gui.listening == m.getBind() ? "Press a key" : m.getBind().getKeyName();
        float chipW = Fonts.width(bindLabel, false, 0.72f) + 9;
        float chipX = tx - 10 - chipW;
        Widgets.bindChip(gui, ctx, m.getBind(), chipX, y + 1 + hh / 2f, 1f);
        Fonts.drawRight(ctx, "KEY", chipX - 5, y + 1 + hh / 2f, Theme.TEXT_MUTED, true, 0.6f);

        // name, with the open category after it, and the description below
        float nx = x + 36, maxW = chipX - 26 - nx;
        float nameW = Fonts.width(m.getName(), true, 1.05f);
        Fonts.draw(ctx, Fonts.trim(m.getName(), maxW, true, 1.05f), nx, y + 8.5f, Theme.TEXT, true, 1.05f);
        float sv = sectionView.get();
        if (shownSection != null && sv > 0.01f && nameW + 30 < maxW) {
            float prev = Render2D.getAlpha();
            Render2D.setAlpha(prev * sv);
            float cx = nx + nameW + 7;
            Icons.CHEVRON_RIGHT.draw(ctx, cx, y + 12.5f, 6f, Theme.TEXT_MUTED, 0);
            Fonts.draw(ctx, Fonts.trim(shownSection.getTitle(), maxW - nameW - 18, false, 0.92f), cx + 7, y + 9f, Theme.accent(), false, 0.92f);
            Render2D.setAlpha(prev);
        }
        Fonts.draw(ctx, Fonts.trim(m.getDescription(), maxW, false, 0.72f), nx, y + 21f, Theme.TEXT_MUTED, false, 0.72f);
    }

    private void settingsList(DrawContext ctx, List<SettingSection> list, boolean labels, float x, float y, float w, float h) {
        float off = settingsScroll.update();
        gui.pushClip(x - 6, y, w + 12, h);
        float content = settingsList.render(gui, ctx, x, y - off, w - 6, list, labels);
        gui.popClip();
        settingsScroll.setBounds(content + 2, h);
        settingsScroll.drawBar(gui, ctx, x + w - 2, y, h);
    }

    // ---- category cards -----------------------------------------------------------------

    private void renderHome(DrawContext ctx, List<SettingSection> visible, float x, float y, float w, float h) {
        int cols = w >= 330 ? 2 : 1;
        float cw = (w - 6 - GAP * (cols - 1)) / cols;
        int rows = (visible.size() + cols - 1) / cols;
        float hintH = 16;
        homeScroll.setBounds(rows * (SECTION_CARD_H + GAP) - GAP + 2 + hintH, h);
        float off = homeScroll.update();

        gui.pushClip(x - 6, y, w + 12, h);
        for (int i = 0; i < visible.size(); i++) {
            float cx = x + (i % cols) * (cw + GAP);
            float cy = y + 1 + (i / cols) * (SECTION_CARD_H + GAP) - off;
            if (!gui.isVisible(cy - 6, SECTION_CARD_H + 12)) continue;
            sectionCard(ctx, visible.get(i), i, cx, cy, cw, SECTION_CARD_H);
        }
        float hy = y + 1 + rows * (SECTION_CARD_H + GAP) - off + 3;
        Fonts.drawCentered(ctx, "Pick a category to change its settings", x + (w - 6) / 2f, hy + 4, Theme.TEXT_MUTED, false, 0.66f);
        gui.popClip();
        homeScroll.drawBar(gui, ctx, x + w - 2, y, h);
    }

    private void sectionCard(DrawContext ctx, SettingSection s, int index, float x, float y, float w, float h) {
        float t = (System.currentTimeMillis() - homeSince) / 1000f - Math.min(index, 12) * 0.035f;
        float intro = dev.maro.util.Easing.outCubic(Math.max(0f, Math.min(1f, t / 0.28f)));
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * intro);
        y += (1 - intro) * 8f;

        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(s, "card", hovered);
        float r = Theme.radius();
        if (hv > 0.01f && Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 6f, Theme.accent(Math.round(0x22 * hv)));
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        if (hv > 0.01f) Render2D.roundRect(ctx, x, y, w, h, r, Theme.accent(Math.round(0x16 * hv)), 0x00000000, 0x00000000, Theme.accent2(Math.round(0x08 * hv)));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(0x70), hv));

        // icon tile
        float ts = 26, tx = x + 10, ty = y + (h - ts) / 2f;
        Render2D.roundRect(ctx, tx, ty, ts, ts, Math.min(7f, r + 1), Theme.accent(0x1C + Math.round(0x22 * hv)));
        Render2D.roundOutline(ctx, tx, ty, ts, ts, Math.min(7f, r + 1), 1f, Theme.accent(0x38 + Math.round(0x40 * hv)));
        iconFor(s.getTitle()).draw(ctx, tx + ts / 2f, ty + ts / 2f, 12f, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, hv * 0.6f), hv);

        // title, what is inside, and a count
        float nx = tx + ts + 10, maxW = x + w - 22 - nx;
        Fonts.draw(ctx, Fonts.trim(s.getTitle(), maxW, false, 0.95f), nx, y + 8.5f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.6f + hv * 0.4f), false, 0.95f);
        StringBuilder names = new StringBuilder();
        int count = 0, toggles = 0, toggled = 0;
        List<Integer> colors = new ArrayList<>();
        for (Setting<?> setting : s.getSettings()) {
            if (!setting.isVisible()) continue;
            if (count++ > 0) names.append(", ");
            names.append(setting.getName());
            if (setting instanceof BooleanSetting b) {
                toggles++;
                if (b.get()) toggled++;
            } else if (setting instanceof ColorSetting c && colors.size() < 5) colors.add(c.get());
        }
        String inside = names.toString();
        String shownInside = Fonts.trim(inside, maxW, false, 0.68f);
        Fonts.draw(ctx, shownInside, nx, y + 20.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), false, 0.68f);

        String meta = count + (count == 1 ? " setting" : " settings") + (toggles > 0 ? "  •  " + toggled + "/" + toggles + " on" : "");
        Fonts.draw(ctx, meta, nx, y + 32f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), hv * 0.7f), false, 0.62f);
        float sx = nx + Fonts.width(meta, false, 0.62f) + 8;
        for (int c : colors) {
            if (sx + 7 > x + w - 22) break;
            Render2D.circle(ctx, sx + 3, y + 35f, 3.2f, c | 0xFF000000);
            Render2D.ring(ctx, sx + 3, y + 35f, 3.2f, 0.8f, 0x40FFFFFF);
            sx += 9;
        }

        Icons.CHEVRON_RIGHT.draw(ctx, x + w - 13, y + h / 2f, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), hv), hv);
        gui.hit(x, y, w, h, (button, mx, my) -> openSection(s));
        if (hovered && !shownInside.equals(inside) && gui.hovered(nx, y + 18, maxW, 10)) gui.tooltip(inside);
        Render2D.setAlpha(prev);
    }

    /** An icon that fits a category, by the words in its title. */
    private static Icons.Icon iconFor(String title) {
        String t = title.toLowerCase(java.util.Locale.ROOT);
        if (has(t, "color", "colour", "palette", "tint", "theme")) return Icons.THEME;
        if (has(t, "anim", "motion", "swing", "transition", "inspect")) return Icons.PLAY;
        if (has(t, "placement", "layout", "position", "fit", "size", "hand", "preset", "shape", "outline", "image", "png")) return Icons.LAYOUT;
        if (has(t, "time", "timing", "delay", "break", "speed", "pace", "lyrics")) return Icons.CLOCK;
        if (has(t, "marker", "region", "rtp", "map", "route", "chunk", "spot", "where")) return Icons.PIN;
        if (has(t, "key", "bind", "control", "input")) return Icons.KEY;
        if (has(t, "stop", "safety", "pause", "alarm", "response", "found", "alert", "protect", "equipment")) return Icons.SHIELD;
        if (has(t, "target", "aim", "entit", "player", "highlight", "who", "companion", "follow")) return Icons.COMBAT;
        if (has(t, "look", "appearance", "style", "visual", "render", "overlay", "hud", "world", "display", "accessor", "camera", "view", "light", "correction")) return Icons.VISUALS;
        if (has(t, "action", "behav", "mining", "tunnel", "tool", "scan", "what", "block", "ore", "food", "eat", "sound", "audio")) return Icons.BOLT;
        if (has(t, "advanced", "extra", "more")) return Icons.MISC;
        return Icons.SETTINGS;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    // ---- one category -------------------------------------------------------------------

    private void renderSection(DrawContext ctx, List<SettingSection> visible, SettingSection s, float x, float y, float w, float h) {
        float rowW = w - 6;

        // reset, with a second click to confirm
        boolean armed = resetArmed == s && System.currentTimeMillis() - resetArmedAt < 2500;
        String resetLabel = armed ? "Sure?" : "Reset";
        float rw = Widgets.buttonWidth(resetLabel, Icons.REFRESH);
        Widgets.button(gui, ctx, s, x + rowW - rw, y, rw, TAB_H, resetLabel, armed ? Widgets.Style.DANGER : Widgets.Style.GHOST, Icons.REFRESH, () -> {
            if (resetArmed == s && System.currentTimeMillis() - resetArmedAt < 2500) {
                for (Setting<?> setting : s.getSettings()) {
                    if (!setting.isVisible() || setting instanceof ButtonSetting || setting instanceof ActionSetting) continue;
                    setting.reset();
                }
                resetArmed = null;
            } else {
                resetArmed = s;
                resetArmedAt = System.currentTimeMillis();
            }
        });
        if (gui.hovered(x + rowW - rw, y, rw, TAB_H)) gui.tooltip(armed ? "Click again to reset " + s.getTitle() : "Put this category back to its defaults");

        tabs(ctx, visible, s, x, y, rowW - rw - 8);

        float ly = y + TAB_H + 8;
        settingsList(ctx, List.of(s), false, x, ly, w, h - TAB_H - 8);
    }

    /** One chip per category, the open one lit, or a pager when they do not fit. */
    private void tabs(DrawContext ctx, List<SettingSection> visible, SettingSection current, float x, float y, float maxW) {
        float scale = 0.74f, pad = 12, gap = 3;
        float[] widths = new float[visible.size()];
        float total = -gap;
        for (int i = 0; i < visible.size(); i++) {
            widths[i] = Fonts.width(visible.get(i).getTitle(), false, scale) + pad;
            total += widths[i] + gap;
        }
        int index = Math.max(0, visible.indexOf(current));
        float cy = y + TAB_H / 2f;
        float r = Math.min(TAB_H / 2f, Theme.radius());

        if (total > maxW) {
            // pager: < Title 2/5 >
            float bw = TAB_H;
            Widgets.button(gui, ctx, "tabPrev", x, y, bw, TAB_H, "", Widgets.Style.SECONDARY, Icons.BACK,
                    () -> openSection(visible.get((index - 1 + visible.size()) % visible.size())));
            float px = x + bw + 4, pw = Math.min(maxW - bw * 2 - 8, 170);
            Render2D.roundGradientH(ctx, px, y, pw, TAB_H, r, Theme.accent(), Theme.accent2());
            Fonts.drawCentered(ctx, Fonts.trim(current.getTitle(), pw - 30, false, scale), px + pw / 2f - 8, cy, 0xFFFFFFFF, false, scale);
            Fonts.drawRight(ctx, (index + 1) + "/" + visible.size(), px + pw - 6, cy, 0xD0FFFFFF, false, 0.62f);
            Widgets.button(gui, ctx, "tabNext", px + pw + 4, y, bw, TAB_H, "", Widgets.Style.SECONDARY, Icons.CHEVRON_RIGHT,
                    () -> openSection(visible.get((index + 1) % visible.size())));
            return;
        }

        Render2D.roundRect(ctx, x, y, total + 4, TAB_H, r, 0xFF15141D);
        Render2D.roundOutline(ctx, x, y, total + 4, TAB_H, r, 1f, Theme.BORDER);
        float sx = x + 2, selX = 0;
        for (int i = 0; i < index; i++) sx += widths[i] + gap;
        selX = sx - x;
        Object owner = shown != null ? shown : this;
        if (snapTabs) {
            Anims.snap(owner, "tabX", selX);
            Anims.snap(owner, "tabW", widths[index]);
            snapTabs = false;
        }
        float ax = Anims.of(owner, "tabX", selX, 18f), aw = Anims.of(owner, "tabW", widths[index], 18f);
        Render2D.roundGradientH(ctx, x + ax, y + 2, aw, TAB_H - 4, Math.max(1f, r - 1), Theme.accent(), Theme.accent2());

        sx = x + 2;
        for (int i = 0; i < visible.size(); i++) {
            SettingSection tab = visible.get(i);
            boolean sel = i == index;
            boolean hov = gui.hovered(sx, y, widths[i], TAB_H);
            float t = Anims.of(tab, "tab", sel ? 1f : hov ? 0.5f : 0f);
            Fonts.drawCentered(ctx, tab.getTitle(), sx + widths[i] / 2f, cy, ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t), false, scale);
            if (!sel) gui.hit(sx, y, widths[i], TAB_H, (button, mx, my) -> openSection(tab));
            sx += widths[i] + gap;
        }
    }
}
