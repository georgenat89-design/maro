package dev.maro.gui;

import dev.maro.Maro;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.page.ConfigsPage;
import dev.maro.gui.page.ModulesPage;
import dev.maro.gui.page.Page;
import dev.maro.gui.page.SettingsPage;
import dev.maro.gui.page.SocialsPage;
import dev.maro.gui.page.ThemePage;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Logo;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.TextField;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.setting.KeybindSetting;
import dev.maro.util.Animation;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.KeyUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * The menu. Uses an immediate-mode approach: everything (including click regions) is
 * rebuilt every frame during {@link #render}, so pages only need to describe what they draw.
 *
 * <p>With the Panels menu style (the default) it opens on {@link PanelsView}, a panel per
 * category; pages and module settings then open in the window over them, and Escape or a click
 * outside the window goes back to the panels. With the Window style it is the window alone.
 */
public class ClickGuiScreen extends Screen {
    @FunctionalInterface
    public interface ClickHandler {
        void click(int button, double mouseX, double mouseY);
    }

    @FunctionalInterface
    public interface DragHandler {
        void drag(double mouseX, double mouseY);
    }

    private record Hit(float x, float y, float w, float h, ClickHandler handler) {
    }

    private record ScrollHit(float x, float y, float w, float h, DoubleConsumer handler) {
    }

    private record Rect(float x, float y, float w, float h) {
        Rect intersect(Rect o) {
            float nx = Math.max(x, o.x), ny = Math.max(y, o.y);
            float nr = Math.min(x + w, o.x + o.w), nb = Math.min(y + h, o.y + o.h);
            return new Rect(nx, ny, Math.max(0, nr - nx), Math.max(0, nb - ny));
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private record Entry(String label, Icons.Icon icon, Page page, Category category, String subtitle) {
    }

    private static final Object WINDOW = new Object();
    private static final Object INDICATOR = new Object();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final float BAR_H = 26f;
    private static final float HEADER_H = 24f;

    // remembered between openings
    private static int lastEntry = 0;
    private static float dragX, dragY;

    public TextField focused;
    public KeybindSetting listening;

    private final List<Hit> hits = new ArrayList<>();
    private final List<ScrollHit> scrollHits = new ArrayList<>();
    private final Deque<Rect> clips = new ArrayDeque<>();
    private final List<Entry> entries = new ArrayList<>();
    private final TextField search;
    private final ModulesPage searchPage;
    private int selected;
    private long pageSwitchedAt = System.currentTimeMillis();
    private boolean wasSearching;

    private Object dragOwner;
    private DragHandler drag;
    private double mouseXd, mouseYd;
    private boolean mouseDown;
    private boolean interactive = true;

    private final long openedAt = System.currentTimeMillis();
    private long closingAt = -1;
    private float lastProgress;

    private final PanelsView panels;
    /** With the Panels style: whether the window is showing over the panels. */
    private boolean windowOpen;
    private final Animation windowShown = new Animation(14f, 0f);
    /** Set while the window is still fading out over the panels: it is drawn but takes no input. */
    private boolean ghost;

    private java.util.function.Supplier<SkinTextures> skinSupplier;
    private String tooltip, shownTooltip;
    private long tooltipSince;
    private int moduleEntries;

    public ClickGuiScreen() {
        super(Text.literal(Maro.NAME));
        for (Category c : Category.values()) entries.add(new Entry(c.getDisplayName(), c.getIcon(), new ModulesPage(this, c), c, null));
        moduleEntries = entries.size();
        entries.add(new Entry("Settings", Icons.SETTINGS, new SettingsPage(this), null, "Menu, input and behaviour options"));
        entries.add(new Entry("Configs", Icons.CONFIGS, new ConfigsPage(this), null, "Save and switch module setups"));
        entries.add(new Entry("Theme", Icons.THEME, new ThemePage(this), null, "Accent, colours and window style"));
        entries.add(new Entry("Socials", Icons.SOCIALS, new SocialsPage(this), null, "Friends and online players"));
        searchPage = new ModulesPage(this, null);
        search = new TextField("Search modules...", 32);
        List<PanelsView.PageLink> pages = new ArrayList<>();
        for (int i = moduleEntries; i < entries.size(); i++) {
            Page page = entries.get(i).page();
            // The client settings and theme open in a settings box over the panels; configs and socials in the window.
            List<dev.maro.setting.SettingSection> sections = page instanceof SettingsPage ? ClientSettings.GENERAL_PAGE
                    : page instanceof ThemePage ? ClientSettings.THEME_PAGE : null;
            pages.add(new PanelsView.PageLink(entries.get(i).label(), entries.get(i).icon(), i, sections));
        }
        panels = new PanelsView(this, pages);
        selected = Math.max(0, Math.min(lastEntry, entries.size() - 1));
        entries.get(selected).page().onOpen();
    }

    // ---- immediate mode API used by pages and widgets -----------------------------------

    public boolean hovered(float x, float y, float w, float h) {
        if (!interactive || ghost || closingAt >= 0 || drag != null) return false;
        if (!new Rect(x, y, w, h).contains(mouseXd, mouseYd)) return false;
        Rect clip = clips.peek();
        return clip == null || clip.contains(mouseXd, mouseYd);
    }

    public void hit(float x, float y, float w, float h, ClickHandler handler) {
        if (!interactive || ghost) return;
        Rect r = new Rect(x, y, w, h);
        Rect clip = clips.peek();
        if (clip != null) r = r.intersect(clip);
        if (r.w <= 0 || r.h <= 0) return;
        hits.add(new Hit(r.x, r.y, r.w, r.h, handler));
    }

    /** Registers a region that consumes mouse wheel input before the page scrolls. */
    public void scrollHit(float x, float y, float w, float h, DoubleConsumer handler) {
        if (!interactive || ghost) return;
        Rect r = new Rect(x, y, w, h);
        Rect clip = clips.peek();
        if (clip != null) r = r.intersect(clip);
        if (r.w <= 0 || r.h <= 0) return;
        scrollHits.add(new ScrollHit(r.x, r.y, r.w, r.h, handler));
    }

    public void pushClip(float x, float y, float w, float h) {
        Rect r = new Rect(x, y, w, h);
        Rect parent = clips.peek();
        if (parent != null) r = r.intersect(parent);
        clips.push(r);
        applyClip(r);
    }

    public void popClip() {
        clips.pop();
        Rect parent = clips.peek();
        if (parent != null) applyClip(parent);
        else if (scissorActive) {
            currentContext().disableScissor();
            scissorActive = false;
            Render2D.setScissor(null);
        }
    }

    private DrawContext currentCtx;

    private DrawContext currentContext() {
        return currentCtx;
    }

    private void applyClip(Rect r) {
        DrawContext ctx = currentContext();
        // DrawContext scissors stack; we manage our own stack so always replace the top one
        if (scissorActive) ctx.disableScissor();
        int x1 = (int) Math.floor(r.x), y1 = (int) Math.floor(r.y);
        int x2 = (int) Math.ceil(r.x + r.w), y2 = (int) Math.ceil(r.y + r.h);
        ctx.enableScissor(x1, y1, x2, y2);
        Render2D.setScissor(new ScreenRect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1)));
        scissorActive = true;
    }

    private boolean scissorActive;

    /** True if a vertical span intersects the current clip, used to skip off-screen rows. */
    public boolean isVisible(float y, float h) {
        Rect clip = clips.peek();
        return clip == null || (y + h >= clip.y && y <= clip.y + clip.h);
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void tooltip(String text) {
        if (ClientSettings.tooltips.get()) tooltip = text;
    }

    public void startDrag(Object owner, DragHandler handler) {
        dragOwner = owner;
        drag = handler;
        handler.drag(mouseXd, mouseYd);
    }

    public boolean isDragging(Object owner) {
        return drag != null && dragOwner == owner;
    }

    public boolean isMouseDown() {
        return mouseDown;
    }

    public TextField getSearch() {
        return search;
    }

    public void openPage(int index) {
        windowOpen = true;
        if (index == selected && search.getText().isEmpty()) return;
        search.clear();
        if (focused == search) focused = null;
        selected = index;
        lastEntry = index;
        pageSwitchedAt = System.currentTimeMillis();
        entries.get(index).page().onOpen();
    }

    /** Jumps to a module's category and opens its settings view. */
    public void openModuleSettings(dev.maro.module.Module module) {
        int index = module.getCategory().ordinal();
        openPage(index);
        if (entries.get(index).page() instanceof ModulesPage page) page.openSettings(module);
    }

    /** Opens a module's settings straight at one of its categories, by title. */
    public void openModuleOptions(dev.maro.module.Module module) {
        int index=module.getCategory().ordinal();openPage(index);
        if(entries.get(index).page() instanceof ModulesPage page)page.openOptions(module);
    }

    /** Opens a module's settings straight at one of its categories, by title. */
    public void openModuleSettings(dev.maro.module.Module module, String category) {
        openModuleOptions(module);
        if (entries.get(module.getCategory().ordinal()).page() instanceof ModulesPage page) page.openSection(category);
    }

    private static boolean panelsStyle() {
        return ClientSettings.menuStyle.is("Panels");
    }

    /** Whether the panels are what is showing (the Panels style, with no page open over them). */
    public boolean showingPanels() {
        return panelsStyle() && !windowOpen;
    }

    /** Back from the window to the panels. */
    public void showPanels() {
        if (!panelsStyle() || !windowOpen) return;
        windowOpen = false;
        listening = null;
        focused = null;
        Sounds.click();
    }

    /** Opens a page from the panels' dock by its label, as its button does. */
    public void openPanelPage(String label) {
        panels.openDockPage(label);
    }

    /** The module whose settings box is open over the panels, if any; for tests. */
    public dev.maro.module.Module panelSettingsModule() {
        return panels.popoverModule();
    }

    /** Where a module's row or a category's header was drawn in the panels last frame, as {x, y}; for tests. */
    public float[] panelPlace(Object moduleOrCategory) {
        return panels.placeOf(moduleOrCategory);
    }

    private Page currentPage() {
        return search.getText().isEmpty() ? entries.get(selected).page() : searchPage;
    }

    // ---- rendering ----------------------------------------------------------------------

    private float progress() {
        long now = System.currentTimeMillis();
        float speed = ClientSettings.animationSpeed();
        if (closingAt >= 0) {
            float t = (now - closingAt) / (200f / speed);
            return lastProgress * (1f - Easing.outCubic(t));
        }
        return Easing.outQuint((now - openedAt) / (420f / speed));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // drawn manually in render() so it can fade with the menu
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        currentCtx = ctx;
        hits.clear();
        scrollHits.clear();
        clips.clear();
        scissorActive = false;
        Render2D.setScissor(null);
        tooltip = null;
        interactive = true;
        Theme.update();
        if (drag != null) drag.drag(mouseXd, mouseYd);

        float p = progress();
        if (closingAt >= 0 && p <= 0.001f) return; // tick() removes the screen
        if (closingAt < 0) lastProgress = p;

        // background
        if (ClientSettings.backgroundBlur.get() && client != null && client.world != null && p > 0.2f) {
            try {
                ctx.applyBlur();
            } catch (IllegalStateException ignored) {
                // something else already blurred this frame
            }
        }
        Render2D.setAlpha(p);
        if (ClientSettings.backgroundDim.get()) {
            int dim = Math.round(255 * ClientSettings.dimAmount.getFloat() / 100f);
            Render2D.rectGradient(ctx, 0, 0, width, height, ColorUtil.withAlpha(0, Math.round(dim * 0.85f)), ColorUtil.withAlpha(0, Math.round(dim * 0.85f)),
                    ColorUtil.withAlpha(0, dim), ColorUtil.withAlpha(0, dim));
        }
        // The contour lines of the logo, faint, behind everything.
        Logo.contours(ctx, width, height, 0.16f);

        // The panels, fading back while the window is over them; a click outside the window returns to them.
        float win = 1f;
        if (panelsStyle()) {
            win = windowShown.update(windowOpen ? 1f : 0f);
            Render2D.setAlpha(p * (1f - win * 0.8f));
            interactive = !windowOpen;
            panels.render(ctx, width, height, p, 1f - win);
            interactive = true;
            if (windowOpen) {
                hit(0, 0, width, height, (button, mx, my) -> showPanels());
                Render2D.setAlpha(p * win);
                Fonts.drawCentered(ctx, "Escape or click outside to go back to the panels", width / 2f, height - 7f,
                        ColorUtil.withAlpha(Theme.TEXT_MUTED, 0xB0), false, 0.6f);
            }
        }
        if (win > 0.01f) {
            if (panelsStyle()) ctx.createNewRootLayer(); // the window wholly over the panels
            ghost = panelsStyle() && !windowOpen;
            renderWindow(ctx, p * win);
            ghost = false;
        }
        interactive = true;

        ctx.createNewRootLayer(); // overlays always above the menu
        renderTooltip(ctx, p);
        Render2D.setAlpha(1f);
        Notifications.render(ctx);
    }

    /** The window: tabs, header and the open page. */
    private void renderWindow(DrawContext ctx, float p) {
        Render2D.setAlpha(p);

        // window layout
        float ww = Math.min(width - 10f, Math.max(420f, Math.min(660f, width - 40f)));
        float wh = Math.min(height - 10f, Math.max(250f, Math.min(380f, height - 40f)));
        float baseX = (width - ww) / 2f, baseY = (height - wh) / 2f;
        dragX = Math.max(-baseX + 2, Math.min(baseX - 2, dragX));
        dragY = Math.max(-baseY + 2, Math.min(baseY - 2, dragY));
        float wx = baseX + dragX;
        float wy = baseY + dragY + (1f - p) * 18f;
        float r = Theme.radius() + 3f;

        if (ClientSettings.shadow.get()) {
            Render2D.shadow(ctx, wx, wy + 3, ww, wh, r, 24f, 0x80000000);
            if (Theme.glow()) Render2D.shadow(ctx, wx, wy, ww, wh, r, 36f, Theme.accent(0x12));
        }
        hit(wx, wy, ww, wh, (button, mx, my) -> {
        }); // the window takes clicks that land on it but on nothing in it
        Render2D.roundRect(ctx, wx, wy, ww, wh, r, Theme.windowBg());
        // soft accent light falling from the top edge
        Render2D.roundRect(ctx, wx, wy, ww, Math.min(110f, wh), r, Theme.accent(0x18), Theme.accent2(0x18), 0x00000000, 0x00000000);
        Render2D.roundOutline(ctx, wx, wy, ww, wh, r, 1f, 0xFF24242E, 0xFF24242E, 0xFF15151B, 0xFF15151B);
        float hl = ww * 0.5f, hlx = wx + (ww - hl) / 2f;
        Render2D.rectGradient(ctx, hlx, wy + 0.5f, hl / 2, 1f, Theme.accent(0), Theme.accent(0xE0), Theme.accent(0xE0), Theme.accent(0));
        Render2D.rectGradient(ctx, hlx + hl / 2, wy + 0.5f, hl / 2, 1f, Theme.accent2(0xE0), Theme.accent2(0), Theme.accent2(0), Theme.accent2(0xE0));

        float pad = 10f;
        float hx = wx + pad, hw = ww - pad * 2;
        float barY = wy + 9;
        renderTopBar(ctx, hx, barY, hw, BAR_H);
        float hy = barY + BAR_H + 10;
        renderHeader(ctx, hx, hy, hw, HEADER_H);

        // page
        boolean searching = !search.getText().isEmpty();
        if (searching != wasSearching) {
            wasSearching = searching;
            pageSwitchedAt = System.currentTimeMillis();
            if (searching) searchPage.onOpen();
            else entries.get(selected).page().onOpen();
        }
        float py = hy + HEADER_H + 9, ph = wy + wh - 9 - py;
        float t = Easing.outCubic((System.currentTimeMillis() - pageSwitchedAt) / (260f / ClientSettings.animationSpeed()));
        Render2D.setAlpha(p * t);
        pushClip(hx - 2, py, hw + 4, ph);
        currentPage().render(ctx, hx, py + (1 - t) * 8f, hw, ph);
        popClip();
        interactive = true;
    }

    private void renderTopBar(DrawContext ctx, float x, float y, float w, float h) {
        // drag the window by the bar (registered first so every control on it wins)
        hit(x, y, w, h, (button, mx, my) -> {
            if (button != 0) return;
            final float sx = dragX, sy = dragY;
            final double ox = mx, oy = my;
            startDrag(WINDOW, (nx, ny) -> {
                dragX = sx + (float) (nx - ox);
                dragY = sy + (float) (ny - oy);
            });
        });

        Render2D.roundRect(ctx, x, y, w, h, h / 2f, Theme.panelBg());
        Render2D.roundOutline(ctx, x, y, w, h, h / 2f, 1f, Theme.BORDER);
        float cy = y + h / 2f;

        // logo
        boolean logoHover = hovered(x, y, 70, h);
        float lh = Anims.of(WINDOW, "logo", logoHover);
        float lcx = x + h / 2f + 1;
        Logo.mark(ctx, lcx, cy, 17f + lh * 1.5f, lh);
        float tx = lcx + 12;
        Fonts.beginRaw(); // the wordmark keeps its lowercase look
        float tw = Fonts.width("maro", true, 1.05f);
        Fonts.drawV(ctx, "maro", tx, cy, Theme.TEXT, true, 1.05f);
        Fonts.drawV(ctx, ".gg", tx + tw, cy, Theme.accent(), true, 1.05f);
        float logoEnd = tx + tw + Fonts.width(".gg", true, 1.05f) + 12;
        Fonts.endRaw();

        // right side: profile + general pages
        float bs = h - 6;
        float ax = x + w - 3 - bs, ay = y + 3;
        renderAvatar(ctx, ax, ay, bs);
        boolean searching = !search.getText().isEmpty();
        float gx = ax - 6;
        for (int i = entries.size() - 1; i >= moduleEntries; i--) {
            gx -= bs;
            Entry e = entries.get(i);
            boolean hov = hovered(gx, ay, bs, bs);
            float hv = Anims.of(e, "hover", hov);
            float sel = Anims.of(e, "sel", i == selected && !searching);
            if (sel > 0.01f && Theme.glow()) Render2D.shadow(ctx, gx, ay, bs, bs, bs / 2f, 5, Theme.accent(Math.round(0x40 * sel)));
            Render2D.roundRect(ctx, gx, ay, bs, bs, bs / 2f, ColorUtil.lerp(ColorUtil.withAlpha(0xFF1E1E28, Math.round(0xFF * hv)), Theme.accent(0x40), sel));
            if (sel > 0.01f) Render2D.roundOutline(ctx, gx, ay, bs, bs, bs / 2f, 1f, Theme.accent(Math.round(0x90 * sel)));
            e.icon().draw(ctx, gx + bs / 2f, ay + bs / 2f, 8.5f,
                    ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), ColorUtil.shade(Theme.accent(), 0.45f), sel), Math.max(hv, sel));
            final int index = i;
            hit(gx, ay, bs, bs, (button, mx, my) -> {
                if (index != selected || searching) Sounds.click();
                openPage(index);
            });
            if (hov) tooltip(e.label());
            gx -= 3;
        }
        float tabsEnd = gx - 6;
        Render2D.rect(ctx, tabsEnd + 2, y + 7, Render2D.px(), h - 14, Theme.BORDER);

        // category tabs (labels collapse to icons when space is tight)
        float labelScale = 0.82f, tabH = h - 6, tabPad = 8;
        float[] widths = new float[moduleEntries];
        float total = 0;
        for (int i = 0; i < moduleEntries; i++) {
            widths[i] = tabPad * 2 + 9 + 5 + Fonts.width(entries.get(i).label(), false, labelScale);
            total += widths[i] + 2;
        }
        boolean labels = total <= tabsEnd - logoEnd;
        if (!labels) for (int i = 0; i < moduleEntries; i++) widths[i] = tabH + 4;
        float sx = logoEnd;
        float selX = sx, selW = widths[0];
        float[] xs = new float[moduleEntries];
        for (int i = 0; i < moduleEntries; i++) {
            xs[i] = sx;
            if (i == selected) {
                selX = sx;
                selW = widths[i];
            }
            sx += widths[i] + 2;
        }
        boolean tabSelected = selected < moduleEntries && !searching;
        float ix = Anims.of(INDICATOR, "x", selX, 16f), iw = Anims.of(INDICATOR, "w", selW, 16f);
        float ia = Anims.of(INDICATOR, "a", tabSelected ? 1f : 0f);
        if (ia > 0.01f) {
            if (Theme.glow()) Render2D.shadow(ctx, ix, ay, iw, tabH, tabH / 2f, 6, Theme.accent(Math.round(0x38 * ia)));
            Render2D.roundGradientH(ctx, ix, ay, iw, tabH, tabH / 2f, Theme.accent(Math.round(0x50 * ia)), Theme.accent2(Math.round(0x30 * ia)));
            Render2D.roundOutline(ctx, ix, ay, iw, tabH, tabH / 2f, 1f, Theme.accent(Math.round(0x80 * ia)), Theme.accent2(Math.round(0x40 * ia)),
                    Theme.accent2(Math.round(0x40 * ia)), Theme.accent(Math.round(0x80 * ia)));
        }
        for (int i = 0; i < moduleEntries; i++) {
            Entry e = entries.get(i);
            float ex = xs[i], ew = widths[i];
            boolean hov = hovered(ex, ay, ew, tabH);
            float hv = Anims.of(e, "hover", hov);
            float sel = Anims.of(e, "sel", i == selected && !searching);
            if (hv > 0.01f && sel < 0.99f) Render2D.roundRect(ctx, ex, ay, ew, tabH, tabH / 2f, ColorUtil.withAlpha(0xFF1A1A23, Math.round(0xFF * hv * (1 - sel))));
            int fg = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), 0xFFFFFFFF, sel);
            if (labels) {
                e.icon().draw(ctx, ex + tabPad + 4.5f, cy, 8.5f, ColorUtil.lerp(fg, ColorUtil.shade(Theme.accent(), 0.5f), sel), Math.max(hv, sel));
                Fonts.drawV(ctx, e.label(), ex + tabPad + 14, cy, fg, sel > 0.5f, labelScale);
            } else {
                e.icon().draw(ctx, ex + ew / 2f, cy, 8.5f, fg, Math.max(hv, sel));
                if (hov) tooltip(e.label());
            }
            long on = ModuleManager.enabledCount(e.category());
            float ba = Anims.of(e, "badge", on > 0);
            if (ba > 0.01f) {
                float prev = Render2D.getAlpha();
                Render2D.setAlpha(prev * ba);
                Render2D.circle(ctx, ex + ew - 5, ay + 4, 2.4f, Theme.panelBg());
                Render2D.circle(ctx, ex + ew - 5, ay + 4, 1.7f, Theme.GREEN);
                Render2D.setAlpha(prev);
            }
            final int index = i;
            hit(ex, ay, ew, tabH, (button, mx, my) -> {
                if (index != selected || searching) Sounds.click();
                openPage(index);
            });
        }
    }

    private void renderAvatar(DrawContext ctx, float x, float y, float s) {
        String name = client != null ? client.getSession().getUsername() : "Player";
        SkinTextures skin = null;
        try {
            if (skinSupplier == null && client != null) skinSupplier = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
            if (skinSupplier != null) skin = skinSupplier.get();
        } catch (Throwable ignored) {
        }
        boolean hov = hovered(x, y, s, s);
        float hv = Anims.of(WINDOW, "avatar", hov);
        Render2D.ring(ctx, x + s / 2f, y + s / 2f, s / 2f, 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(), hv));
        float hs = s - 6;
        if (skin != null) Widgets.head(ctx, skin, x + 3, y + 3, (int) hs);
        else Widgets.avatar(ctx, name, x + 3, y + 3, hs);
        Render2D.circle(ctx, x + s - 3, y + s - 3, 2.6f, Theme.panelBg());
        Render2D.circle(ctx, x + s - 3, y + s - 3, 1.8f, Theme.GREEN);
        if (hov) {
            ServerInfo server = client != null ? client.getCurrentServerEntry() : null;
            String where = server != null ? server.address : client != null && client.isInSingleplayer() ? "Singleplayer" : "Offline";
            tooltip(name + " \u2022 " + where);
        }
        hit(x, y, s, s, (button, mx, my) -> {
        });
    }

    private void renderHeader(DrawContext ctx, float x, float y, float w, float h) {
        boolean searching = !search.getText().isEmpty();
        Entry e = entries.get(selected);
        String title = searching ? "Search" : e.label();
        String subtitle;
        if (searching) {
            int n = ModuleManager.search(search.getText()).size();
            subtitle = n + (n == 1 ? " match" : " matches") + " across all categories";
        } else if (e.category() != null) {
            int total = ModuleManager.byCategory(e.category()).size();
            subtitle = total + (total == 1 ? " module" : " modules") + "  \u2022  " + ModuleManager.enabledCount(e.category()) + " enabled";
        } else {
            subtitle = e.subtitle();
        }
        float sw = Math.min(160f, w * 0.34f), sh = 18f;
        float sx = x + w - sw;
        String time = LocalTime.now().format(CLOCK);
        float cw = Fonts.width(time, false, 0.8f) + 16;
        float cx = sx - cw - 5;
        float titleMax = cx - x - 8;

        // accent tick + title
        Render2D.roundGradientV(ctx, x + 1, y + 3, 2.4f, h - 6, 1.2f, Theme.accent(), Theme.accent2());
        Fonts.draw(ctx, Fonts.trim(title, titleMax, true, 1.25f), x + 9, y + 2.5f, Theme.TEXT, true, 1.25f);
        Fonts.draw(ctx, Fonts.trim(subtitle, titleMax, false, 0.7f), x + 9.5f, y + 15.5f, Theme.TEXT_MUTED, false, 0.7f);

        float cyy = y + (h - sh) / 2f;
        Render2D.roundRect(ctx, cx, cyy, cw, sh, sh / 2f, Theme.INPUT);
        Render2D.roundOutline(ctx, cx, cyy, cw, sh, sh / 2f, 1f, Theme.BORDER);
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 500.0);
        Render2D.circle(ctx, cx + 6.5f, cyy + sh / 2f, 1.6f, Theme.accent(0x80 + Math.round(0x7F * pulse)));
        Fonts.drawV(ctx, time, cx + 10.5f, cyy + sh / 2f, Theme.TEXT_DIM, false, 0.8f);

        search.render(this, ctx, sx, cyy, sw, sh, Icons.SEARCH, focused == search ? null : "Ctrl K");
    }

    private void renderTooltip(DrawContext ctx, float p) {
        long now = System.currentTimeMillis();
        if (tooltip == null || !tooltip.equals(shownTooltip)) {
            shownTooltip = tooltip;
            tooltipSince = now;
        }
        if (shownTooltip == null) return;
        float a = Easing.outCubic((now - tooltipSince - 280) / 140f);
        if (a <= 0) return;
        List<String> lines = Fonts.wrap(shownTooltip, 170, false, 0.75f);
        float lw = 0;
        for (String l : lines) lw = Math.max(lw, Fonts.width(l, false, 0.75f));
        float w = lw + 12, h = lines.size() * 8.5f + 8;
        float x = (float) mouseXd + 8, y = (float) mouseYd + 10 + (1 - a) * 4;
        if (x + w > width - 4) x = (float) mouseXd - w - 6;
        if (y + h > height - 4) y = (float) mouseYd - h - 6;
        Render2D.setAlpha(p * a);
        Render2D.shadow(ctx, x, y, w, h, 4, 6, 0x60000000);
        Render2D.roundRect(ctx, x, y, w, h, 4, 0xF5211F2B);
        Render2D.roundOutline(ctx, x, y, w, h, 4, 1f, 0xFF3B384B);
        for (int i = 0; i < lines.size(); i++) Fonts.draw(ctx, lines.get(i), x + 6, y + 5 + i * 8.5f, Theme.TEXT_DIM, false, 0.75f);
    }

    // ---- input --------------------------------------------------------------------------

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        mouseXd = mouseX;
        mouseYd = mouseY;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        mouseXd = mouseX;
        mouseYd = mouseY;
        if (closingAt >= 0) return true;
        mouseDown = true;
        if (listening != null) {
            if (button >= 2) {
                listening.set(KeyUtil.mouse(button));
                listening = null;
                Sounds.click();
                return true;
            }
            listening = null;
        }
        focused = null;
        return clickAt(mouseX, mouseY, button);
    }

    /** Clicks whatever was drawn at {@code x, y} in the last frame, front-most first. */
    public boolean clickAt(double x, double y, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x < h.x + h.w && y >= h.y && y < h.y + h.h) {
                h.handler.click(button, x, y);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        mouseDown = false;
        drag = null;
        dragOwner = null;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        mouseXd = click.x();
        mouseYd = click.y();
        if (drag != null) drag.drag(mouseXd, mouseYd);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (int i = scrollHits.size() - 1; i >= 0; i--) {
            ScrollHit h = scrollHits.get(i);
            if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                h.handler.accept(verticalAmount);
                return true;
            }
        }
        if (!showingPanels()) currentPage().onScroll(verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), modifiers = input.modifiers();
        if (closingAt >= 0 || justOpened()) return true;
        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) listening.set(KeyUtil.NONE);
            else if (keyCode != GLFW.GLFW_KEY_ESCAPE) listening.set(keyCode);
            listening = null;
            Sounds.click();
            return true;
        }
        boolean ctrl = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (ctrl && (keyCode == GLFW.GLFW_KEY_K || keyCode == GLFW.GLFW_KEY_F)) {
            focused = search;
            return true;
        }
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (focused == search && !search.getText().isEmpty()) search.clear();
                else focused = null;
                return true;
            }
            focused.keyPressed(keyCode, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showingPanels() && panels.closePopover()) return true;
            if (!search.getText().isEmpty()) {
                search.clear();
                return true;
            }
            if (showingPanels()) {
                close();
                return true;
            }
            if (currentPage().onEscape()) return true;
            if (panelsStyle()) showPanels();
            else close();
            return true;
        }
        if (ClientSettings.guiBind.matches(keyCode)) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && !showingPanels()) {
            int dir = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            openPage(Math.floorMod(selected + dir, entries.size()));
            Sounds.click();
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char chr = (char) input.codepoint();
        if (closingAt >= 0 || listening != null || justOpened()) return true;
        if (focused != null) return focused.charTyped(chr);
        if (ClientSettings.typeToSearch.get() && (showingPanels() || currentPage().typeToSearch()) && Character.isLetterOrDigit(chr)) {
            focused = search;
            return search.charTyped(chr);
        }
        return false;
    }

    // ---- lifecycle ----------------------------------------------------------------------

    @Override
    public void close() {
        if (closingAt >= 0) return;
        closingAt = System.currentTimeMillis();
        listening = null;
        focused = null;
        drag = null;
    }

    @Override
    public void tick() {
        if (closingAt >= 0 && progress() <= 0.001f && client != null) client.setScreen(null);
    }

    /** The key/char that opened the menu is also delivered to it; ignore input for a moment. */
    private boolean justOpened() {
        return System.currentTimeMillis() - openedAt < 150;
    }

    @Override
    public void removed() {
        Render2D.setAlpha(1f);
        Render2D.setScissor(null);
        ConfigManager.saveClient();
        if (ClientSettings.autoSave.get()) ConfigManager.save(ConfigManager.getCurrent());
    }

    @Override
    public boolean shouldPause() {
        return ClientSettings.pauseGame.get();
    }

    public static void resetPosition() {
        dragX = 0;
        dragY = 0;
    }
}
