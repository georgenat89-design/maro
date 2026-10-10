package dev.maro.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.config.ClientSettings;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Logo;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.CompactSettings;
import dev.maro.gui.widget.Scroll;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The menu as panels: one per category, side by side over the game, every module a row you can
 * see at once. Left click turns a module on or off, right click opens a small box of its settings
 * beside it, middle click sets its key. Panels can be dragged by their header and folded away with
 * a right click on it; where they are is remembered. A dock at the top holds the search, the
 * client Settings and Theme (in the same kind of box) and the Configs and Socials pages.
 */
public final class PanelsView {
    /** A page the dock opens: in a settings box when it has {@code sections}, else in the menu window. */
    record PageLink(String label, Icons.Icon icon, int index, List<SettingSection> sections) {
    }

    /** The open settings box: a module's, or a dock page's next to its button. */
    private record Popover(Object key, String title, Module module, List<SettingSection> sections, float anchorX, float anchorY) {
    }

    private static final float POP_W = 172f, POP_HEAD = 20f, POP_FOOT = 18f;

    /** Where a panel is and whether it is folded; kept between openings and saved with the client settings. */
    private static final class Panel {
        final Category category;
        final Scroll scroll = new Scroll();
        float x, y;
        /** Moved by hand; otherwise it sits in the automatic row. */
        boolean placed;
        boolean folded;

        Panel(Category category) {
            this.category = category;
        }
    }

    private static final float DOCK_H = 22f, HEADER = 21f, ROW = 13f, GAP = 6f, MARGIN = 8f;
    private static final float MIN_W = 84f, MAX_W = 120f;
    private static final float NAME_SCALE = 0.78f;
    private static final Object DOCK = new Object();

    /** Front-most last. */
    private static final List<Panel> PANELS = new ArrayList<>();

    static {
        for (Category c : Category.values()) PANELS.add(new Panel(c));
    }

    private final ClickGuiScreen gui;
    private final List<PageLink> pages;
    private final long openedAt = System.currentTimeMillis();
    /** Each row and header's place on screen in the last frame, for tests. */
    private final Map<Object, float[]> places = new HashMap<>();
    /** Each panel's {x, y, width} for the rows drawn in it last frame, so a settings box can sit beside it. */
    private final Map<Module, float[]> rowPanels = new HashMap<>();
    /** Where under each dock button its box opens, from last frame. */
    private final Map<PageLink, float[]> dockAnchors = new HashMap<>();
    /** Each panel's {x, y, width} last frame. */
    private final Map<Category, float[]> panelRects = new HashMap<>();
    private Popover popover;
    private long popoverSince;
    /** A dock page with no settings (Configs, Socials), in a box under its button so the panels stay the view. */
    private PageLink pageBox;
    private long pageBoxSince;
    private final Scroll popoverScroll = new Scroll();
    private final CompactSettings compact = new CompactSettings();
    private float popoverContent = 40f;

    PanelsView(ClickGuiScreen gui, List<PageLink> pages) {
        this.gui = gui;
        this.pages = pages;
    }

    float[] placeOf(Object rowOrCategory) {
        return places.get(rowOrCategory);
    }

    /** The module whose settings box is open, if any. */
    Module popoverModule() {
        return popover != null ? popover.module() : null;
    }

    /** Closes the settings box or page box; false if none was open. */
    boolean closePopover() {
        if (popover == null && pageBox == null) return false;
        popover = null;
        pageBox = null;
        gui.focused = null;
        gui.listening = null;
        Sounds.click();
        return true;
    }

    private void openPopover(Popover next) {
        boolean same = popover != null && popover.key().equals(next.key());
        popover = same ? null : next;
        pageBox = null;
        popoverSince = System.currentTimeMillis();
        popoverScroll.reset();
        gui.focused = null;
        Sounds.click();
    }

    /** Opens a dock page by its label, as its button does; for tests. */
    void openDockPage(String label) {
        for (PageLink page : pages) {
            if (!page.label().equals(label)) continue;
            float[] at = dockAnchors.getOrDefault(page, new float[] {gui.width / 2f, MARGIN + DOCK_H + 6f});
            if (page.sections() != null) openPopover(new Popover(page, page.label(), null, page.sections(), at[0], at[1]));
            else openPageBox(page);
        }
    }

    /** Opens a page in its box under the dock, or closes it if it is the one open. */
    private void openPageBox(PageLink page) {
        boolean same = pageBox == page;
        popover = null;
        gui.focused = null;
        pageBox = same ? null : page;
        pageBoxSince = System.currentTimeMillis();
        if (!same) gui.pageAt(page.index()).onOpen();
        Sounds.click();
    }

    /** The dock page open in its box, if any; for tests. */
    String pageBoxLabel() {
        return pageBox != null ? pageBox.label() : null;
    }

    /** A module's settings box beside its row, or the Auto Builder's own screen. */
    private void openSettings(Module m) {
        if (m instanceof dev.maro.module.impl.player.AutoBuilder builder) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.builder.BuilderControlScreen(gui, builder));
            return;
        }
        net.minecraft.client.gui.screen.Screen own = m.panel(gui);
        if (own != null) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(own);
            return;
        }
        openPopover(new Popover(m, m.getName(), m, m.getSettingSections(), 0, 0));
    }

    // ---- layout -----------------------------------------------------------------------------

    /** Categories with modules in them, in their usual order; an empty one gets no panel. */
    private static List<Category> shownCategories() {
        List<Category> shown = new ArrayList<>();
        for (Category c : Category.values()) if (!ModuleManager.byCategory(c).isEmpty()) shown.add(c);
        return shown;
    }

    private static float panelWidth(float width, int n) {
        float fit = (width - MARGIN * 2 - GAP * (n - 1)) / n;
        return Math.max(MIN_W, Math.min(MAX_W, fit));
    }

    /** Where a panel sits until it is moved: in one centred row, wrapping onto more if the screen is narrow. */
    private static float[] autoPlace(int i, int n, float width, float pw, float top) {
        // The small allowance keeps rounding from pushing the last panel onto a row of its own.
        int perRow = Math.max(1, (int) ((width - MARGIN * 2 + GAP + 0.5f) / (pw + GAP)));
        int row = i / perRow, col = i % perRow;
        int inRow = Math.min(perRow, n - row * perRow);
        float rowW = inRow * pw + (inRow - 1) * GAP;
        return new float[] {(width - rowW) / 2f + col * (pw + GAP), top + row * (HEADER + 12f)};
    }

    static void resetLayout() {
        for (Panel panel : PANELS) {
            panel.placed = false;
            panel.folded = false;
            panel.scroll.reset();
        }
        PANELS.sort((a, b) -> a.category.ordinal() - b.category.ordinal());
    }

    private static void toFront(Panel panel) {
        PANELS.remove(panel);
        PANELS.add(panel);
    }

    // ---- drawing ----------------------------------------------------------------------------

    /**
     * @param p    the menu's open/close progress
     * @param shown 1 while the panels are the view, falling to 0 as the menu window covers them
     */
    void render(DrawContext ctx, float width, float height, float p, float shown) {
        places.clear();
        rowPanels.clear();
        panelRects.clear();
        // A click on nothing closes the settings box.
        if (popover != null || pageBox != null) gui.hit(0, 0, width, height, (button, mx, my) -> closePopover());
        float base = Render2D.getAlpha();
        float top = MARGIN + DOCK_H + 10f;
        List<Category> visible = shownCategories();
        float pw = panelWidth(width, Math.max(1, visible.size()));
        String query = gui.getSearch().getText().trim();
        Set<Module> matches = query.isEmpty() ? null : new HashSet<>(ModuleManager.search(query));

        boolean first = true;
        for (Panel panel : List.copyOf(PANELS)) {
            int index = visible.indexOf(panel.category);
            if (index < 0) continue;
            if (!panel.placed) {
                float[] at = autoPlace(index, visible.size(), width, pw, top);
                panel.x = at[0];
                panel.y = at[1];
            }
            // Each panel on a layer of its own, so one in front hides the text of one behind it.
            if (!first) ctx.createNewRootLayer();
            first = false;
            panel.x = Math.max(0, Math.min(width - pw, panel.x));
            panel.y = Math.max(0, Math.min(height - HEADER, panel.y));
            // Each panel drops in a moment after the one before it.
            float since = (System.currentTimeMillis() - openedAt) / (1000f / ClientSettings.animationSpeed());
            float intro = Easing.outCubic((since - index * 0.045f) / 0.32f);
            Render2D.setAlpha(base * intro);
            renderPanel(ctx, panel, panel.x, panel.y + (1f - intro) * -10f - (1f - p) * 6f, pw, height, matches);
        }
        Render2D.setAlpha(base);
        ctx.createNewRootLayer();
        renderDock(ctx, width, shown);
        if (popover != null) {
            ctx.createNewRootLayer();
            Render2D.setAlpha(base * shown);
            renderPopover(ctx, width, height);
        }
        if (pageBox != null) {
            ctx.createNewRootLayer();
            Render2D.setAlpha(base * shown);
            renderPageBox(ctx, width, height);
        }
        // How to use it, along the bottom.
        Render2D.setAlpha(base * shown);
        Fonts.drawCentered(ctx, "Click to toggle  •  right click for settings  •  middle click for a key  •  drag a header to move it",
                width / 2f, height - 7f, ColorUtil.withAlpha(Theme.TEXT_MUTED, 0xB0), false, 0.6f);
        Render2D.setAlpha(base);
    }

    private void renderPanel(DrawContext ctx, Panel panel, float x, float y, float w, float screenH, Set<Module> matches) {
        if (panel.category == Category.ANTI_CHEAT_OFF && !gui.antiCheatSectionUnlocked()) {
            places.put(panel.category, new float[] {x + w / 2, y + HEADER / 2});
            Render2D.roundRect(ctx, x, y, w, HEADER + 23, Theme.radius() + 2, Theme.windowBg());
            Render2D.roundOutline(ctx, x, y, w, HEADER + 23, Theme.radius() + 2, 1, 0xFF7D5B34);
            Icons.LOCK.draw(ctx, x + 11, y + HEADER / 2, 9, 0xFFFFC36A, 0);
            Fonts.drawV(ctx, "Anti-Cheat Off", x + 20, y + HEADER / 2, Theme.TEXT, true, 0.7f);
            Fonts.drawCentered(ctx, "Enter Section", x + w / 2, y + HEADER + 10, 0xFFFFC36A, false, 0.7f);
            gui.hit(x, y, w, HEADER + 23, (button, mx, my) -> { if (button == 0) gui.openPage(Category.ANTI_CHEAT_OFF.ordinal()); });
            return;
        }
        List<Module> rows = new ArrayList<>();
        for (Module m : ModuleManager.byCategory(panel.category)) if (matches == null || matches.contains(m)) rows.add(m);
        boolean searching = matches != null;
        long on = ModuleManager.enabledCount(panel.category);
        float open = Anims.of(panel, "open", !panel.folded && !rows.isEmpty());
        float dim = Anims.of(panel, "dim", searching && rows.isEmpty());

        float content = rows.size() * ROW + 6f;
        float room = Math.max(ROW * 3, screenH - y - HEADER - MARGIN - 8f);
        float listH = Math.min(content, room);
        float h = HEADER + listH * open;
        float r = Theme.radius() + 2f;
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * (1f - dim * 0.6f));

        panelRects.put(panel.category, new float[] {x, y, w});
        // The panel takes every click on it, so nothing behind it gets them; any click brings it forward.
        gui.hit(x, y, w, h, (button, mx, my) -> toFront(panel));

        float lit = Anims.of(panel, "lit", on > 0);
        if (ClientSettings.shadow.get()) {
            Render2D.shadow(ctx, x, y + 2, w, h, r, 14f, 0x70000000);
            if (Theme.glow() && lit > 0.01f) Render2D.shadow(ctx, x, y, w, h, r, 16f, Theme.accent(Math.round(0x14 * lit)));
        }
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.windowBg());
        Render2D.roundRect(ctx, x, y, w, Math.min(46f, h), r, Theme.accent(0x16), Theme.accent2(0x16), 0x00000000, 0x00000000);
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, 0xFF26262F, 0xFF26262F, 0xFF15151B, 0xFF15151B);

        renderHeader(ctx, panel, x, y, w, searching ? rows.size() : -1, on, open, lit);

        if (open > 0.01f) {
            float ly = y + HEADER, lh = listH * open;
            panel.scroll.setBounds(content, listH);
            float off = panel.scroll.update();
            gui.scrollHit(x, ly, w, lh, panel.scroll::scroll);
            gui.pushClip(x, ly, w, lh);
            float ry = ly + 3f - off;
            for (Module m : rows) {
                if (gui.isVisible(ry, ROW)) renderRow(ctx, panel, m, x + 4f, ry, w - 8f, ROW - 1f);
                ry += ROW;
            }
            gui.popClip();
            panel.scroll.drawBar(gui, ctx, x + w - 2.5f, ly + 2f, lh - 4f);
        }
        Render2D.setAlpha(prev);
    }

    /** @param found how many modules match the search, or -1 when not searching */
    private void renderHeader(DrawContext ctx, Panel panel, float x, float y, float w, int found, long on, float open, float lit) {
        boolean hov = gui.hovered(x, y, w, HEADER);
        float hv = Anims.of(panel, "headHover", hov);
        float cy = y + HEADER / 2f;
        places.put(panel.category, new float[] {x + w / 2f, cy});

        // Drag by the header; a right click folds the panel away or opens it again.
        gui.hit(x, y, w, HEADER, (button, mx, my) -> {
            toFront(panel);
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                panel.folded = !panel.folded;
                Sounds.click();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                final float sx = panel.x, sy = panel.y;
                final double ox = mx, oy = my;
                gui.startDrag(panel, (nx, ny) -> {
                    if (Math.abs(nx - ox) + Math.abs(ny - oy) < 2 && !panel.placed) return;
                    panel.placed = true;
                    panel.x = sx + (float) (nx - ox);
                    panel.y = sy + (float) (ny - oy);
                });
            }
        });
        if (hov) gui.tooltip("Drag to move  •  right click to fold");

        // The category's icon on a small tile, lit in the accent while any of its modules are on.
        float ix = x + 12f;
        if (Theme.glow() && lit > 0.01f) Render2D.shadow(ctx, ix - 6, cy - 6, 12, 12, 4, 4, Theme.accent(Math.round(0x40 * lit)));
        Render2D.roundRect(ctx, ix - 6.5f, cy - 6.5f, 13f, 13f, 3.5f, Theme.accent(Math.round(0x1C + 0x24 * Math.max(hv, lit))),
                Theme.accent2(Math.round(0x10 + 0x18 * Math.max(hv, lit))), Theme.accent2(Math.round(0x08 + 0x10 * lit)), Theme.accent(Math.round(0x10 + 0x18 * lit)));
        panel.category.getIcon().draw(ctx, ix, cy, 8.5f, ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, lit), hv);
        float caretX = x + w - 9f;
        // A badge: how many match the search, or how many are on; nothing when none are.
        float titleRight = caretX - 6f;
        String badge = found >= 0 ? String.valueOf(found) : on > 0 ? String.valueOf(on) : null;
        if (badge != null) {
            Fonts.beginRaw();
            float bw = Math.max(9f, Fonts.width(badge, true, 0.6f) + 6f), bh = 9f, bx = caretX - 6f - bw;
            boolean accent = found < 0 || found > 0;
            Render2D.roundRect(ctx, bx, cy - bh / 2f, bw, bh, bh / 2f, accent ? Theme.accent(0x48) : 0xFF22222C);
            Fonts.drawCentered(ctx, badge, bx + bw / 2f, cy, accent ? 0xFFFFFFFF : Theme.TEXT_MUTED, true, 0.6f);
            Fonts.endRaw();
            titleRight = bx - 4f;
        }
        // The name, a little smaller rather than cut short when the panel is narrow.
        String title = panel.category.getDisplayName();
        float tx = ix + 10.5f, room = titleRight - tx, scale = 0.86f;
        float full = Fonts.width(title, true, scale);
        if (full > room) scale = Math.max(0.68f, scale * room / full);
        Fonts.drawV(ctx, Fonts.trim(title, room, true, scale), tx, cy, Theme.TEXT, true, scale);

        // A caret that turns as the panel folds.
        double turn = Math.toRadians(-90f * (1f - open));
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        float[][] caret = {{-2.3f, -1.1f}, {0f, 1.2f}, {2.3f, -1.1f}};
        int caretColor = ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv);
        for (int i = 0; i < 2; i++) {
            float ax = caretX + caret[i][0] * cos - caret[i][1] * sin, ay = cy + caret[i][0] * sin + caret[i][1] * cos;
            float bx = caretX + caret[i + 1][0] * cos - caret[i + 1][1] * sin, by = cy + caret[i + 1][0] * sin + caret[i + 1][1] * cos;
            Render2D.line(ctx, ax, ay, bx, by, 1.2f, caretColor);
        }

        // An accent line under the header, fading out at both ends.
        if (open > 0.01f) {
            float lw = w - 16f, lx = x + 8f, half = lw / 2f;
            int a = Math.round(0xB0 * open);
            Render2D.rectGradient(ctx, lx, y + HEADER - 1f, half, 1f, Theme.accent(0), Theme.accent(a), Theme.accent(a), Theme.accent(0));
            Render2D.rectGradient(ctx, lx + half, y + HEADER - 1f, half, 1f, Theme.accent2(a), Theme.accent2(0), Theme.accent2(0), Theme.accent2(a));
        }
    }

    private void renderRow(DrawContext ctx, Panel panel, Module m, float x, float y, float w, float h) {
        boolean hov = gui.hovered(x, y, w, h);
        float hv = Anims.of(m, "panelHover", hov);
        float en = Anims.of(m, "panelOn", m.isEnabled());
        boolean listening = gui.listening == m.getBind();
        float cy = y + h / 2f, r = Math.min(4f, Theme.radius());
        places.put(m, new float[] {x + w / 2f, cy});
        rowPanels.put(m, new float[] {x - 4f, y, w + 8f});

        gui.hit(x, y, w, h, (button, mx, my) -> {
            toFront(panel);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                m.toggle();
                Sounds.toggle(m.isEnabled());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                openSettings(m);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                gui.listening = m.getBind();
            }
        });
        if (hov) gui.tooltip(m.getDescription());

        // Its settings box is open: an accent outline.
        float sel = Anims.of(m, "panelSelected", popover != null && popover.module() == m);
        if (sel > 0.01f) Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.accent(Math.round(0xC0 * sel)));
        // On: an accent gradient pill with a soft glow and a light bar at its left edge.
        if (hv > 0.01f) Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.withAlpha(0xFF1C1C26, Math.round(0xFF * hv * (1f - en * 0.6f))));
        if (en > 0.01f) {
            if (Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 5f, Theme.accent(Math.round(0x22 * en)));
            Render2D.roundGradientH(ctx, x, y, w, h, r, Theme.accent(Math.round((0x58 + 0x18 * hv) * en)), Theme.accent2(Math.round((0x20 + 0x10 * hv) * en)));
            Render2D.roundOutline(ctx, x, y, w, h, r, 0.8f, Theme.accent(Math.round(0x70 * en)), Theme.accent2(Math.round(0x28 * en)),
                    Theme.accent2(Math.round(0x28 * en)), Theme.accent(Math.round(0x70 * en)));
            float bh = (h - 5f) * en;
            Render2D.roundRect(ctx, x + 2f, cy - bh / 2f, 1.6f, bh, 0.8f, Theme.accent());
        }

        // Its key, or a hint that it has settings, on the right.
        String key = listening ? "Press a key" : m.getBind().isBound() ? m.getBind().getKeyName() : null;
        float right = x + w - 4f;
        if (key != null) {
            float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;
            int keyColor = listening ? ColorUtil.lerp(Theme.TEXT_MUTED, Theme.accent(), pulse) : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, en);
            Fonts.beginRaw();
            Fonts.drawRight(ctx, key, right, cy, keyColor, false, 0.6f);
            right -= Fonts.width(key, false, 0.6f) + 4f;
            Fonts.endRaw();
        } else if (hv > 0.01f && !m.getSettings().isEmpty()) {
            Icons.DOTS.draw(ctx, right - 3f, cy, 7f, ColorUtil.withAlpha(Theme.TEXT_MUTED, Math.round(0xFF * hv)), hv);
            right -= 10f;
        }

        // Its name, as it is written, nudged right while it is on.
        float nx = x + 6f + en * 2f;
        int color = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), 0xFFFFFFFF, en);
        Fonts.beginRaw();
        Fonts.drawV(ctx, Fonts.trim(m.getName(), right - nx - 2f, en > 0.5f, NAME_SCALE), nx, cy, color, en > 0.5f, NAME_SCALE);
        Fonts.endRaw();
    }

    // ---- the settings box -------------------------------------------------------------------

    /**
     * A small box of settings: beside the module's panel (on whichever side has room) level with its
     * row, or under the dock button that opened it. Tight rows, a header with the name, the module's
     * own switch and a close button, and the module's key at the bottom.
     */
    private void renderPopover(DrawContext ctx, float width, float height) {
        Popover pop = popover;
        Module m = pop.module();
        float w = POP_W, x, y;
        if (m != null) {
            float[] at = rowPanels.get(m);
            if (at == null) {
                // Its row is scrolled out of sight or filtered out by the search: beside its panel's top.
                float[] panelAt = panelRects.get(m.getCategory());
                if (panelAt == null) {
                    popover = null;
                    return;
                }
                at = new float[] {panelAt[0], panelAt[1] + HEADER, panelAt[2]};
            }
            x = at[0] + at[2] + 5f;
            if (x + w > width - 4f) x = at[0] - w - 5f;
            y = at[1] - 4f;
        } else {
            x = pop.anchorX() - w / 2f;
            y = pop.anchorY();
        }
        float foot = m != null ? POP_FOOT : 0f;
        float extra = pop.sections() == ClientSettings.THEME_PAGE ? 20f : 0f;
        // A box from the dock stays under its button and scrolls rather than climbing over the dock.
        float room = m != null ? height - 8f : height - pop.anchorY() - 6f;
        float maxBody = Math.max(40f, room - POP_HEAD - foot - extra - 4f);
        float body = Math.min(popoverContent, maxBody);
        float h = Anims.of(pop.key(), "popHeight", POP_HEAD + extra + body + foot + 4f, 22f);
        x = Math.max(4f, Math.min(width - w - 4f, x));
        y = Math.max(4f, Math.min(height - h - 4f, y));
        float t = Easing.outCubic((System.currentTimeMillis() - popoverSince) / (180f / ClientSettings.animationSpeed()));
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * t);
        y += (1f - t) * 4f;
        float r = Theme.radius() + 1f;

        gui.hit(x, y, w, h, (button, mx, my) -> {
        });
        if (ClientSettings.shadow.get()) Render2D.shadow(ctx, x, y + 2, w, h, r, 14f, 0x80000000);
        if (Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 14f, Theme.accent(0x16));
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.windowBg());
        Render2D.roundRect(ctx, x, y, w, Math.min(40f, h), r, Theme.accent(0x18), Theme.accent2(0x18), 0x00000000, 0x00000000);
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.accent(0x50), Theme.accent2(0x30), 0xFF15151B, 0xFF15151B);

        // Header: the name, the module's switch and a close button.
        float hy = y + POP_HEAD / 2f, right = x + w - 6f;
        boolean closeHov = gui.hovered(right - 9f, hy - 5f, 10f, 10f);
        float ch = Anims.of(pop.key(), "popClose", closeHov);
        Icons.CLOSE.draw(ctx, right - 4f, hy, 6.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, ch), ch);
        gui.hit(right - 10f, hy - 6f, 12f, 12f, (button, mx, my) -> closePopover());
        right -= 15f;
        if (m != null) {
            boolean th = gui.hovered(right - 18f, hy - 5f, 18f, 10f);
            Widgets.toggle(ctx, right - 17f, hy - 4.5f, 17f, 9f, Anims.of(m, "popOn", m.isEnabled()), Anims.of(m, "popToggleHover", th));
            gui.hit(right - 19f, hy - 6f, 20f, 12f, (button, mx, my) -> {
                m.toggle();
                Sounds.toggle(m.isEnabled());
            });
            right -= 24f;
        }
        Render2D.roundGradientV(ctx, x + 6f, hy - 4.5f, 2f, 9f, 1f, Theme.accent(), Theme.accent2());
        Fonts.beginRaw();
        Fonts.drawV(ctx, Fonts.trim(pop.title(), right - x - 16f, true, 0.84f), x + 12f, hy, Theme.TEXT, true, 0.84f);
        Fonts.endRaw();
        Render2D.rectGradient(ctx, x + 6f, y + POP_HEAD, w - 12f, 1f, Theme.accent(0x60), Theme.accent2(0x10), Theme.accent2(0x10), Theme.accent(0x60));

        float by = y + POP_HEAD + 2f;
        if (extra > 0) {
            presets(ctx, x + 6f, by + 3f, w - 12f);
            by += extra;
        }

        // The settings, scrolling when there are more than fit.
        popoverScroll.setBounds(popoverContent, body);
        float off = popoverScroll.update();
        gui.scrollHit(x, by, w, body, popoverScroll::scroll);
        gui.pushClip(x + 2f, by, w - 4f, body);
        if (pop.sections().stream().allMatch(section -> section.getSettings().stream().noneMatch(dev.maro.setting.Setting::isVisible))) {
            Fonts.drawV(ctx, "No settings", x + 10f, by + 8f, Theme.TEXT_MUTED, false, 0.7f);
            popoverContent = 16f;
        } else {
            popoverContent = compact.render(gui, ctx, x + 4f, by - off, w - 8f, pop.sections()) + 2f;
        }
        gui.popClip();
        popoverScroll.drawBar(gui, ctx, x + w - 2.5f, by + 2f, body - 4f);

        // Footer: the module's key, and a button to clear it.
        if (m != null) {
            float fy = by + body + 2f + POP_FOOT / 2f;
            Render2D.rect(ctx, x + 6f, fy - POP_FOOT / 2f, w - 12f, Render2D.px(), Theme.BORDER);
            Fonts.beginRaw();
            Fonts.drawV(ctx, "Keybind", x + 9f, fy, Theme.TEXT_DIM, false, 0.74f);
            Fonts.endRaw();
            float ux = x + w - 9f;
            if (m.getBind().isBound()) {
                float uw = Fonts.width("Unbind", false, 0.85f) + 12f;
                ux -= uw;
                Widgets.button(gui, ctx, m.getBind(), ux, fy - 5.5f, uw, 11f, "Unbind", Widgets.Style.GHOST, null, () -> {
                    m.getBind().set(dev.maro.util.KeyUtil.NONE);
                    gui.listening = null;
                });
                ux -= 4f;
            }
            String key = gui.listening == m.getBind() ? "Press a key" : m.getBind().getKeyName();
            Widgets.bindChip(gui, ctx, m.getBind(), ux - (Fonts.width(key, false, 0.72f) + 9f), fy, 1f);
        }
        Render2D.setAlpha(prev);
    }

    /** A dock page (Socials, Configs) in a box of its own under its button, over the panels. */
    private void renderPageBox(DrawContext ctx, float width, float height) {
        PageLink page = pageBox;
        float[] at = dockAnchors.getOrDefault(page, new float[] {width / 2f, MARGIN + DOCK_H + 6f});
        float w = Math.min(320f, width - 12f), h = Math.min(290f, height - at[1] - 8f);
        float x = Math.max(6f, Math.min(width - w - 6f, at[0] - w / 2f)), y = at[1];
        float t = Easing.outCubic((System.currentTimeMillis() - pageBoxSince) / (180f / ClientSettings.animationSpeed()));
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * t);
        y += (1f - t) * 4f;
        float r = Theme.radius() + 1f;

        gui.hit(x, y, w, h, (button, mx, my) -> {
        });
        if (ClientSettings.shadow.get()) Render2D.shadow(ctx, x, y + 2, w, h, r, 14f, 0x80000000);
        if (Theme.glow()) Render2D.shadow(ctx, x, y, w, h, r, 14f, Theme.accent(0x16));
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.windowBg());
        Render2D.roundRect(ctx, x, y, w, Math.min(40f, h), r, Theme.accent(0x18), Theme.accent2(0x18), 0x00000000, 0x00000000);
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.accent(0x50), Theme.accent2(0x30), 0xFF15151B, 0xFF15151B);

        float hy = y + POP_HEAD / 2f, right = x + w - 6f;
        boolean closeHov = gui.hovered(right - 9f, hy - 5f, 10f, 10f);
        float ch = Anims.of(page, "pageBoxClose", closeHov);
        Icons.CLOSE.draw(ctx, right - 4f, hy, 6.5f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, ch), ch);
        gui.hit(right - 10f, hy - 6f, 12f, 12f, (button, mx, my) -> closePopover());
        page.icon().draw(ctx, x + 11f, hy, 8f, Theme.accent(), 0f);
        Fonts.beginRaw();
        Fonts.drawV(ctx, page.label(), x + 20f, hy, Theme.TEXT, true, 0.84f);
        Fonts.endRaw();
        Render2D.rectGradient(ctx, x + 6f, y + POP_HEAD, w - 12f, 1f, Theme.accent(0x60), Theme.accent2(0x10), Theme.accent2(0x10), Theme.accent(0x60));

        float bx = x + 9f, by = y + POP_HEAD + 6f, bw = w - 18f, bh = h - POP_HEAD - 12f;
        dev.maro.gui.page.Page shownPage = gui.pageAt(page.index());
        gui.scrollHit(x, by, w, bh, shownPage::onScroll);
        gui.pushClip(x + 2f, by - 2f, w - 4f, bh + 4f);
        shownPage.render(ctx, bx, by, bw, bh);
        gui.popClip();
        Render2D.setAlpha(prev);
    }

    /** The Theme box's accent presets as a row of dots. */
    private void presets(DrawContext ctx, float x, float y, float w) {
        int n = Theme.PRESETS.length;
        float dot = 10f, gap = (w - n * dot) / Math.max(1, n - 1);
        for (int i = 0; i < n; i++) {
            int preset = Theme.PRESETS[i];
            float dx = x + i * (dot + gap), cy = y + dot / 2f;
            boolean hov = gui.hovered(dx, y, dot, dot);
            float hv = Anims.of(Theme.PRESETS, "popPreset" + i, hov);
            boolean sel = !ClientSettings.rainbow.get() && (ClientSettings.accent.get() & 0xFFFFFF) == (preset & 0xFFFFFF);
            Render2D.circle(ctx, dx + dot / 2f, cy, dot / 2f - 0.5f + hv * 0.6f, preset);
            if (sel) Render2D.ring(ctx, dx + dot / 2f, cy, dot / 2f + 1.5f, 1f, 0xFFFFFFFF);
            final int index = i;
            gui.hit(dx - 1f, y - 1f, dot + 2f, dot + 2f, (button, mx, my) -> {
                ClientSettings.accent.set(preset);
                ClientSettings.rainbow.set(false);
                Sounds.click();
            });
            if (hov) gui.tooltip(Theme.PRESET_NAMES[index]);
        }
    }

    // ---- the dock ---------------------------------------------------------------------------

    /** Logo, search and the general pages, in a pill at the top of the screen. */
    private void renderDock(DrawContext ctx, float width, float shown) {
        float bs = DOCK_H - 6f, searchW = Math.min(150f, width * 0.3f);
        float logoW = 62f, buttonsW = (pages.size() + 1) * (bs + 3f) + 4f;
        float w = 6f + logoW + searchW + 8f + buttonsW, x = (width - w) / 2f, y = MARGIN;
        float prev = Render2D.getAlpha();
        Render2D.setAlpha(prev * shown);
        gui.hit(x, y, w, DOCK_H, (button, mx, my) -> {
        });
        if (ClientSettings.shadow.get()) Render2D.shadow(ctx, x, y + 2, w, DOCK_H, DOCK_H / 2f, 12f, 0x70000000);
        Render2D.roundRect(ctx, x, y, w, DOCK_H, DOCK_H / 2f, Theme.panelBg());
        Render2D.roundOutline(ctx, x, y, w, DOCK_H, DOCK_H / 2f, 1f, Theme.BORDER);
        float cy = y + DOCK_H / 2f;

        // The M and maro.gg, as on the window's bar.
        boolean logoHover = gui.hovered(x, y, logoW, DOCK_H);
        float lh = Anims.of(DOCK, "logo", logoHover);
        float lcx = x + DOCK_H / 2f + 1f;
        Logo.mark(ctx, lcx, cy, 15f + lh * 1.5f, lh);
        Fonts.beginRaw();
        float tx = lcx + 10.5f, tw = Fonts.width("maro", true, 0.92f);
        Fonts.drawV(ctx, "maro", tx, cy, Theme.TEXT, true, 0.92f);
        Fonts.drawV(ctx, ".gg", tx + tw, cy, Theme.accent(), true, 0.92f);
        Fonts.endRaw();

        float sx = x + 6f + logoW;
        gui.getSearch().render(gui, ctx, sx, y + 3f, searchW, DOCK_H - 6f, Icons.SEARCH, gui.focused == gui.getSearch() ? null : "Ctrl K");

        float bx = sx + searchW + 8f, by = y + 3f;
        Render2D.rect(ctx, bx - 4f, y + 6f, Render2D.px(), DOCK_H - 12f, Theme.BORDER);
        for (PageLink page : pages) {
            final float ax = bx + bs / 2f, ay = by + bs + 6f;
            dockAnchors.put(page, new float[] {ax, ay});
            dockButton(ctx, page, page.label(), page.icon(), bx, by, bs, () -> {
                if (page.sections() != null) openPopover(new Popover(page, page.label(), null, page.sections(), ax, ay));
                else openPageBox(page);
            });
            bx += bs + 3f;
        }
        dockButton(ctx, DOCK, "Put the panels back in a row", Icons.LAYOUT, bx, by, bs, () -> {
            resetLayout();
            popover = null;
        });
        Render2D.setAlpha(prev);
    }

    private void dockButton(DrawContext ctx, Object key, String label, Icons.Icon icon, float x, float y, float s, Runnable action) {
        boolean hov = gui.hovered(x, y, s, s);
        float hv = Math.max(Anims.of(key, "dockHover", hov), Anims.of(key, "dockOpen", popover != null && popover.key() == key || pageBox == key));
        if (Theme.glow() && hv > 0.01f) Render2D.shadow(ctx, x, y, s, s, s / 2f, 5, Theme.accent(Math.round(0x30 * hv)));
        Render2D.roundRect(ctx, x, y, s, s, s / 2f, ColorUtil.lerp(0x00000000, Theme.accent(0x38), hv));
        icon.draw(ctx, x + s / 2f, y + s / 2f, 8f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), hv);
        gui.hit(x, y, s, s, (button, mx, my) -> {
            Sounds.click();
            action.run();
        });
        if (hov) gui.tooltip(label);
    }

    // ---- saved with the client settings -------------------------------------------------------

    public static JsonObject save() {
        JsonObject out = new JsonObject();
        for (Panel panel : PANELS) {
            JsonObject o = new JsonObject();
            o.addProperty("x", panel.x);
            o.addProperty("y", panel.y);
            o.addProperty("placed", panel.placed);
            o.addProperty("folded", panel.folded);
            out.add(panel.category.name(), o);
        }
        return out;
    }

    public static void load(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject all = json.getAsJsonObject();
        for (Panel panel : PANELS) {
            JsonElement e = all.get(panel.category.name());
            if (e == null || !e.isJsonObject()) continue;
            try {
                JsonObject o = e.getAsJsonObject();
                panel.placed = o.has("placed") && o.get("placed").getAsBoolean();
                panel.folded = o.has("folded") && o.get("folded").getAsBoolean();
                if (o.has("x")) panel.x = o.get("x").getAsFloat();
                if (o.has("y")) panel.y = o.get("y").getAsFloat();
            } catch (RuntimeException ignored) {
                // a hand-edited file: that panel goes back to its automatic place
                panel.placed = false;
            }
        }
    }
}
