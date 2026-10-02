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
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.gui.widget.Anims;
import dev.maro.gui.widget.TextField;
import dev.maro.gui.widget.Widgets;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.setting.KeybindSetting;
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

    private record Entry(String label, Icons.Icon icon, Page page, Category category) {
    }

    private static final Object WINDOW = new Object();
    private static final Object INDICATOR = new Object();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final float SIDEBAR_W = 120f;
    private static final float HEADER_H = 26f;

    // remembered between openings
    private static int lastEntry = 0;
    private static float dragX, dragY;
    private static float logoSpin;

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

    private java.util.function.Supplier<SkinTextures> skinSupplier;
    private String tooltip, shownTooltip;
    private long tooltipSince;
    private int moduleEntries;

    public ClickGuiScreen() {
        super(Text.literal(Maro.NAME));
        for (Category c : Category.values()) entries.add(new Entry(c.getDisplayName(), c.getIcon(), new ModulesPage(this, c), c));
        moduleEntries = entries.size();
        entries.add(new Entry("Settings", Icons.SETTINGS, new SettingsPage(this), null));
        entries.add(new Entry("Configs", Icons.CONFIGS, new ConfigsPage(this), null));
        entries.add(new Entry("Theme", Icons.THEME, new ThemePage(this), null));
        entries.add(new Entry("Socials", Icons.SOCIALS, new SocialsPage(this), null));
        searchPage = new ModulesPage(this, null);
        search = new TextField("Search modules...", 32);
        selected = Math.max(0, Math.min(lastEntry, entries.size() - 1));
        entries.get(selected).page().onOpen();
    }

    // ---- immediate mode API used by pages and widgets -----------------------------------

    public boolean hovered(float x, float y, float w, float h) {
        if (!interactive || closingAt >= 0 || drag != null) return false;
        if (!new Rect(x, y, w, h).contains(mouseXd, mouseYd)) return false;
        Rect clip = clips.peek();
        return clip == null || clip.contains(mouseXd, mouseYd);
    }

    public void hit(float x, float y, float w, float h, ClickHandler handler) {
        if (!interactive) return;
        Rect r = new Rect(x, y, w, h);
        Rect clip = clips.peek();
        if (clip != null) r = r.intersect(clip);
        if (r.w <= 0 || r.h <= 0) return;
        hits.add(new Hit(r.x, r.y, r.w, r.h, handler));
    }

    /** Registers a region that consumes mouse wheel input before the page scrolls. */
    public void scrollHit(float x, float y, float w, float h, DoubleConsumer handler) {
        if (!interactive) return;
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
        if (index == selected && search.getText().isEmpty()) return;
        search.clear();
        if (focused == search) focused = null;
        selected = index;
        lastEntry = index;
        pageSwitchedAt = System.currentTimeMillis();
        entries.get(index).page().onOpen();
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
            Render2D.rectGradient(ctx, 0, 0, width, height, Theme.accent(0x22), 0x90000000, 0xB0000000, 0xA0000000);
        }

        // window layout
        float ww = Math.min(width - 10f, Math.max(400f, Math.min(620f, width - 50f)));
        float wh = Math.min(height - 10f, Math.max(282f, Math.min(370f, height - 50f)));
        float baseX = (width - ww) / 2f, baseY = (height - wh) / 2f;
        dragX = Math.max(-baseX + 2, Math.min(baseX - 2, dragX));
        dragY = Math.max(-baseY + 2, Math.min(baseY - 2, dragY));
        float wx = baseX + dragX;
        float wy = baseY + dragY + (1f - p) * 18f;
        float r = Theme.radius() + 2f;

        if (ClientSettings.shadow.get()) {
            Render2D.shadow(ctx, wx, wy + 2, ww, wh, r, 22f, 0x70000000);
            if (Theme.glow()) Render2D.shadow(ctx, wx, wy, ww, wh, r, 40f, Theme.accent(0x14));
        }
        Render2D.roundRect(ctx, wx, wy, ww, wh, r, Theme.windowBg());
        Render2D.roundOutline(ctx, wx, wy, ww, wh, r, 1f, 0xFF1A2130);

        renderSidebar(ctx, wx + 6, wy + 6, SIDEBAR_W, wh - 12);

        float hx = wx + 6 + SIDEBAR_W + 6, hy = wy + 6, hw = wx + ww - 6 - hx;
        renderHeader(ctx, hx, hy, hw, HEADER_H);

        // page
        boolean searching = !search.getText().isEmpty();
        if (searching != wasSearching) {
            wasSearching = searching;
            pageSwitchedAt = System.currentTimeMillis();
            if (searching) searchPage.onOpen();
            else entries.get(selected).page().onOpen();
        }
        float py = hy + HEADER_H + 6, ph = wy + wh - 6 - py;
        float t = Easing.outCubic((System.currentTimeMillis() - pageSwitchedAt) / (260f / ClientSettings.animationSpeed()));
        Render2D.setAlpha(p * t);
        pushClip(hx - 2, py, hw + 4, ph);
        currentPage().render(ctx, hx, py + (1 - t) * 8f, hw, ph);
        popClip();
        interactive = true;

        ctx.createNewRootLayer(); // overlays always above the menu
        renderTooltip(ctx, p);
        Render2D.setAlpha(1f);
        Notifications.render(ctx);
    }

    private void renderSidebar(DrawContext ctx, float x, float y, float w, float h) {
        float r = Theme.radius() + 1f;
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.panelBg());
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.BORDER);

        // logo
        boolean logoHover = hovered(x, y, w, 36);
        float lh = Anims.of(WINDOW, "logo", logoHover);
        logoSpin = (logoSpin + 1.5f + lh * 9f) % 360f;
        float lcx = x + 19, lcy = y + 19;
        if (Theme.glow()) Render2D.shadow(ctx, lcx - 9, lcy - 9, 18, 18, 9, 6 + lh * 4, Theme.accent(0x30));
        Render2D.arc(ctx, lcx, lcy, 9f, 3.2f, logoSpin, 290f, Theme.accent2(), Theme.accent());
        Render2D.circle(ctx, lcx, lcy, 1.8f + lh * 0.6f, Theme.accent());
        float tw = Fonts.width("maro", true, 1.3f);
        Fonts.draw(ctx, "maro", x + 34, y + 10.5f, Theme.TEXT, true, 1.3f);
        Fonts.draw(ctx, ".gg", x + 34 + tw, y + 10.5f, Theme.accent(), true, 1.3f);
        Fonts.draw(ctx, "v" + Maro.VERSION, x + 34.5f, y + 22.5f, Theme.TEXT_MUTED, false, 0.7f);
        Render2D.rectGradient(ctx, x + 8, y + 35, w - 16, Render2D.px(), 0x00253049, 0xFF253049, 0xFF253049, 0x00253049);

        // entries
        float itemH = 17f, stride = 18f;
        float[] ys = new float[entries.size()];
        float cy = y + 42;
        Widgets.sectionLabel(ctx, "Modules", x + 9, cy, w - 18);
        cy += 11;
        for (int i = 0; i < entries.size(); i++) {
            if (i == moduleEntries) {
                cy += 5;
                Widgets.sectionLabel(ctx, "General", x + 9, cy, w - 18);
                cy += 11;
            }
            ys[i] = cy;
            cy += stride;
        }

        boolean searching = !search.getText().isEmpty();
        float indY = Anims.of(INDICATOR, "y", ys[selected], 16f);
        float indA = Anims.of(INDICATOR, "a", searching ? 0.35f : 1f);
        float ix = x + 5, iw = w - 10;
        if (Theme.glow()) Render2D.shadow(ctx, ix, indY, iw, itemH, 5, 6, Theme.accent(Math.round(0x22 * indA)));
        Render2D.roundGradientH(ctx, ix, indY, iw, itemH, 5, Theme.accent(Math.round(0x40 * indA)), Theme.accent2(Math.round(0x0C * indA)));
        Render2D.roundOutline(ctx, ix, indY, iw, itemH, 5, 1f, Theme.accent(Math.round(0x45 * indA)), Theme.accent2(Math.round(0x10 * indA)),
                Theme.accent2(Math.round(0x10 * indA)), Theme.accent(Math.round(0x45 * indA)));
        Render2D.roundRect(ctx, ix - 1, indY + 4, 2.4f, itemH - 8, 1.2f, Theme.accent(Math.round(0xFF * indA)));

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float ey = ys[i];
            boolean hov = hovered(ix, ey, iw, itemH);
            float hv = Anims.of(e, "hover", hov);
            float sel = Anims.of(e, "sel", i == selected && !searching);
            int iconColor = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv), Theme.accent(), sel);
            e.icon().draw(ctx, x + 17, ey + itemH / 2f, 9f, iconColor, Math.max(hv, sel));
            int textColor = ColorUtil.lerp(ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), ColorUtil.shade(Theme.accent(), 0.3f), sel);
            Fonts.drawV(ctx, e.label(), x + 29 + hv * 1.5f, ey + itemH / 2f, textColor, false, 0.9f);

            if (e.category() != null) {
                long on = ModuleManager.enabledCount(e.category());
                float ba = Anims.of(e, "badge", on > 0);
                if (ba > 0.01f) {
                    String n = String.valueOf(on);
                    float bw = Math.max(11, Fonts.width(n, true, 0.62f) + 6);
                    float bx = ix + iw - bw - 4, by = ey + itemH / 2f - 4.5f;
                    float prev = Render2D.getAlpha();
                    Render2D.setAlpha(prev * ba);
                    Render2D.roundRect(ctx, bx, by, bw, 9, 4.5f, Theme.accent(0x40));
                    Fonts.drawCentered(ctx, n, bx + bw / 2f, by + 4.5f, ColorUtil.shade(Theme.accent(), 0.4f), true, 0.62f);
                    Render2D.setAlpha(prev);
                }
            }
            final int index = i;
            hit(ix, ey, iw, itemH, (button, mx, my) -> {
                if (index != selected || searching) Sounds.click();
                openPage(index);
            });
        }

        if (y + h - 33 > cy + 2) renderProfile(ctx, x + 5, y + h - 33, w - 10, 28);
    }

    private void renderProfile(DrawContext ctx, float x, float y, float w, float h) {
        Render2D.roundRect(ctx, x, y, w, h, Theme.radius(), 0xFF111725);
        Render2D.roundOutline(ctx, x, y, w, h, Theme.radius(), 1f, Theme.BORDER);
        String name = client != null ? client.getSession().getUsername() : "Player";
        SkinTextures skin = null;
        try {
            if (skinSupplier == null && client != null) skinSupplier = client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false);
            if (skinSupplier != null) skin = skinSupplier.get();
        } catch (Throwable ignored) {
        }
        float hs = 18;
        if (skin != null) Widgets.head(ctx, skin, x + 5, y + (h - hs) / 2f, (int) hs);
        else Widgets.avatar(ctx, name, x + 5, y + (h - hs) / 2f, hs);
        Render2D.circle(ctx, x + 5 + hs - 1, y + (h + hs) / 2f - 1, 2.6f, 0xFF111725);
        Render2D.circle(ctx, x + 5 + hs - 1, y + (h + hs) / 2f - 1, 1.8f, Theme.GREEN);

        String sub;
        ServerInfo server = client != null ? client.getCurrentServerEntry() : null;
        if (server != null) sub = server.address;
        else if (client != null && client.isInSingleplayer()) sub = "Singleplayer";
        else sub = "Offline";
        float tx = x + 5 + hs + 6, maxW = w - (tx - x) - 4;
        Fonts.draw(ctx, Fonts.trim(name, maxW, true, 0.82f), tx, y + 6.5f, Theme.TEXT, true, 0.82f);
        Fonts.draw(ctx, Fonts.trim(sub, maxW, false, 0.68f), tx, y + 16.5f, Theme.TEXT_MUTED, false, 0.68f);
    }

    private void renderHeader(DrawContext ctx, float x, float y, float w, float h) {
        float r = Theme.radius() + 1f;
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.panelBg());
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, Theme.BORDER);

        // drag the window by the header (registered first so the search box wins)
        hit(x, y, w, h, (button, mx, my) -> {
            if (button != 0) return;
            final float sx = dragX, sy = dragY;
            final double ox = mx, oy = my;
            startDrag(WINDOW, (nx, ny) -> {
                dragX = sx + (float) (nx - ox);
                dragY = sy + (float) (ny - oy);
            });
        });

        String name = client != null ? client.getSession().getUsername() : "Player";
        float cy = y + h / 2f;
        Fonts.drawV(ctx, "Hello, ", x + 10, cy, Theme.TEXT_DIM, false, 0.9f);
        float gw = Fonts.width("Hello, ", false, 0.9f);
        Fonts.drawV(ctx, name, x + 10 + gw, cy, Theme.TEXT, true, 0.9f);
        float greetEnd = x + 10 + gw + Fonts.width(name, true, 0.9f);

        float sw = Math.min(160f, w * 0.38f), sh = 17f;
        float sx = x + w - sw - 5;
        String time = LocalTime.now().format(CLOCK);
        float clockX = (greetEnd + sx) / 2f;
        if (Fonts.width(time, true, 0.9f) + 20 < sx - greetEnd) Fonts.drawCentered(ctx, time, clockX, cy, Theme.accent(), true, 0.9f);

        search.render(this, ctx, sx, y + (h - sh) / 2f, sw, sh, Icons.SEARCH, focused == search ? null : "Ctrl K");

        // accent line with a travelling shimmer
        float ly = y + h - 1.2f, lx = x + 12, lw = w - 24;
        Render2D.rectGradient(ctx, lx, ly, lw / 2, 1.2f, Theme.accent(0), Theme.accent(0xD0), Theme.accent(0xD0), Theme.accent(0));
        Render2D.rectGradient(ctx, lx + lw / 2, ly, lw / 2, 1.2f, Theme.accent2(0xD0), Theme.accent2(0), Theme.accent2(0), Theme.accent2(0xD0));
        float phase = (System.currentTimeMillis() % 3200L) / 3200f;
        float shX = lx + (lw + 60) * phase - 60;
        pushClip(lx, ly - 1, lw, 3);
        Render2D.rectGradient(ctx, shX, ly, 30, 1.2f, 0x00FFFFFF, 0xB0FFFFFF, 0xB0FFFFFF, 0x00FFFFFF);
        Render2D.rectGradient(ctx, shX + 30, ly, 30, 1.2f, 0xB0FFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0xB0FFFFFF);
        popClip();
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
        Render2D.roundRect(ctx, x, y, w, h, 4, 0xF5151C2A);
        Render2D.roundOutline(ctx, x, y, w, h, 4, 1f, 0xFF27324A);
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
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mouseX >= h.x && mouseX < h.x + h.w && mouseY >= h.y && mouseY < h.y + h.h) {
                h.handler.click(button, mouseX, mouseY);
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
        currentPage().onScroll(verticalAmount);
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
            if (!search.getText().isEmpty()) {
                search.clear();
                return true;
            }
            if (currentPage().onEscape()) return true;
            close();
            return true;
        }
        if (ClientSettings.guiBind.matches(keyCode)) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
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
        if (ClientSettings.typeToSearch.get() && currentPage().typeToSearch() && Character.isLetterOrDigit(chr)) {
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
