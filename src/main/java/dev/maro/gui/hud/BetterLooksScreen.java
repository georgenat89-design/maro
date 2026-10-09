package dev.maro.gui.hud;

import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BetterLooks;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.util.ColorUtil;
import dev.maro.util.Easing;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * The Better Looks panel: sections down the left (All Settings, World, Particles, Interface,
 * ViewModel, Color) with how many settings each holds, a search over every setting, and the
 * settings as cards of rows on the right: switches, choices, sliders with - and +, and colours.
 * Color is Color Correct's own settings, with its switch at the top.
 */
public final class BetterLooksScreen extends Screen {
    private static final float ROW = 20, ROW_GAP = 4, SLIDER_ROW = 32, PICKER = 46;

    private final Screen parent;
    private final BetterLooks module;

    /** A row: a setting, or a switch of its own (Color Correct on or off). */
    private record Row(String name, String description, Setting<?> setting, BooleanSupplier on, Runnable flip) {
        boolean visible() {
            return setting == null || setting.isVisible();
        }

        boolean matches(String query) {
            if (query.isEmpty()) return true;
            String q = query.toLowerCase(Locale.ROOT);
            return name.toLowerCase(Locale.ROOT).contains(q) || description.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    private record Card(String title, String subtitle, List<Row> rows) {
    }

    private final List<Card> cards = new ArrayList<>();
    /** The section shown: -1 for all of them. */
    private int section = -1;
    private String query = "";
    private boolean typing;

    private ModeSetting choosing;
    private float chooseX, chooseY, chooseW;
    private final Set<ColorSetting> picking = new HashSet<>();
    private NumberSetting dragging;
    private float dragX, dragW;
    private ColorSetting draggingColor;
    private int dragPart;
    private float pickX, pickY, pickW, pickH;

    private float scroll, scrollTarget;
    private final long openedAt = System.nanoTime();
    private long lastFrame = System.nanoTime();

    private interface Action {
        void run(double mx, double my, int button);
    }

    private record Hit(float x, float y, float w, float h, Action action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private float px, py, pw, ph, listTop, listBottom;

    public BetterLooksScreen(Screen parent, BetterLooks module) {
        super(Text.literal("Better Looks"));
        this.parent = parent;
        this.module = module;
        for (BetterLooks.Section s : module.sections()) {
            List<Row> rows = new ArrayList<>();
            for (Setting<?> setting : s.settings()) rows.add(new Row(setting.getName(), setting.getDescription(), setting, null, null));
            cards.add(new Card(s.title(), s.subtitle(), rows));
        }
        embed("Color Correct", "Color", "Your Color Correct: hue, saturation, light and tint");
        embed("Motion Blur", "Motion Blur", "Blur on fast movement, the same at any frame rate");
        embed("Bloom", "Bloom", "A soft glow round bright things");
    }

    /** Another module's settings as a card of their own, its on/off switch at the top. */
    private void embed(String moduleName, String title, String subtitle) {
        Module m = ModuleManager.getByName(moduleName);
        if (m == null) return;
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(moduleName, "Turn " + moduleName + " on or off", null, m::isEnabled, m::toggle));
        for (SettingSection s : m.getSettingSections()) {
            for (Setting<?> setting : s.getSettings()) {
                if (setting instanceof BooleanSetting || setting instanceof NumberSetting || setting instanceof ModeSetting || setting instanceof ColorSetting) {
                    rows.add(new Row(setting.getName(), setting.getDescription(), setting, null, null));
                }
            }
        }
        cards.add(new Card(title, subtitle, rows));
    }

    // ---- for tests -------------------------------------------------------------------------------

    public void showSection(int index) {
        section = index;
        scrollTarget = scroll = 0;
    }

    public void search(String words) {
        query = words;
        scrollTarget = scroll = 0;
    }

    public List<String> sectionTitles() {
        return cards.stream().map(Card::title).toList();
    }

    /** How many rows show for the search in a section (-1: all). */
    public int shownRows(int index) {
        int n = 0;
        for (int i = 0; i < cards.size(); i++) {
            if (index >= 0 && i != index) continue;
            for (Row r : cards.get(i).rows()) if (r.visible() && r.matches(query.trim())) n++;
        }
        return n;
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        hits.clear();
        Theme.update();

        pw = Math.min(660, width - 24);
        ph = Math.min(420, height - 20);
        px = Math.round((width - pw) / 2f);
        py = Math.round((height - ph) / 2f);

        float open = Easing.outCubic(Math.min(1f, (now - openedAt) / 1e9f / 0.18f));
        Render2D.setAlpha(open);
        Fonts.beginRaw();
        try {
            Render2D.rect(ctx, 0, 0, width, height, 0x90000000);
            Render2D.shadow(ctx, px, py + 4, pw, ph, 14, 20, 0x90000000);
            Render2D.roundRect(ctx, px, py, pw, ph, 12, 0xF5121520);
            Render2D.roundOutline(ctx, px, py, pw, ph, 12, 1, 0x1CFFFFFF);
            Render2D.roundRect(ctx, px, py, pw, 3, 1.5f, Theme.accent(0xC0));

            // Header: the name, the module's switch and Close.
            Fonts.drawV(ctx, "Better Looks", px + 16, py + 19, Theme.TEXT, true, 1.05f);
            Fonts.drawV(ctx, "Clean up how the game looks, all in one place", px + 16, py + 33, Theme.TEXT_MUTED, false, 0.6f);
            float bw = 66, bh = 20, by = py + 12, cx = px + pw - 16 - bw;
            button(ctx, "Close", cx, by, bw, bh, true, mx, my, this::close);
            boolean on = module.isEnabled();
            float tx = cx - 6 - bw;
            boolean hov = inside(mx, my, tx, by, bw, bh);
            Render2D.roundRect(ctx, tx, by, bw, bh, 6, on ? ColorUtil.lerp(0xFF1A1E29, Theme.GREEN, 0.25f) : hov ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, tx, by, bw, bh, 6, 1, on ? ColorUtil.withAlpha(Theme.GREEN, 0xA0) : 0x1EFFFFFF);
            Render2D.circle(ctx, tx + 11, by + bh / 2f, 2.6f, on ? Theme.GREEN : Theme.TEXT_MUTED);
            Fonts.drawV(ctx, on ? "Enabled" : "Disabled", tx + 18, by + bh / 2f, on ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.66f);
            hit(tx, by, bw, bh, (hx, hy, b) -> {
                module.toggle();
                Sounds.toggle(module.isEnabled());
            });

            // Search.
            float sy = py + 44, sh = 22;
            boolean sHover = inside(mx, my, px + 16, sy, pw - 32, sh);
            Render2D.roundRect(ctx, px + 16, sy, pw - 32, sh, 7, 0xFF0C0E14);
            Render2D.roundOutline(ctx, px + 16, sy, pw - 32, sh, 7, 1, typing ? Theme.accent(0xC8) : sHover ? 0x30FFFFFF : 0x18FFFFFF);
            if (query.isEmpty() && !typing) Fonts.drawV(ctx, "Search settings…", px + 26, sy + sh / 2f, 0xFF545A6C, false, 0.7f);
            else {
                Fonts.drawV(ctx, query, px + 26, sy + sh / 2f, Theme.TEXT, false, 0.7f);
                if (typing && (now / 500_000_000L) % 2 == 0) Render2D.rect(ctx, px + 27 + Fonts.width(query, false, 0.7f), sy + 6, 1, sh - 12, Theme.TEXT_DIM);
            }
            hit(px + 16, sy, pw - 32, sh, (hx, hy, b) -> typing = true);

            float top = sy + sh + 10, bottom = py + ph - 14;
            float sideW = Math.min(132, pw * 0.24f);
            sidebar(ctx, px + 16, top, sideW, bottom - top, mx, my);
            content(ctx, px + 16 + sideW + 10, top, pw - 32 - sideW - 10, bottom - top, mx, my, now, dt);
            if (choosing != null) choices(ctx, mx, my);
        } finally {
            Fonts.endRaw();
            Render2D.setAlpha(1f);
        }
    }

    private void sidebar(DrawContext ctx, float x, float y, float w, float h, int mx, int my) {
        Render2D.roundRect(ctx, x, y, w, h, 9, 0xFF0F1219);
        Render2D.roundOutline(ctx, x, y, w, h, 9, 1, 0x14FFFFFF);
        Fonts.drawV(ctx, "Sections", x + 10, y + 13, Theme.TEXT_MUTED, false, 0.62f);
        float iy = y + 24;
        String q = query.trim();
        for (int i = -1; i < cards.size(); i++) {
            String title = i < 0 ? "All Settings" : cards.get(i).title();
            int count = shownRows(i);
            boolean sel = section == i;
            boolean hov = inside(mx, my, x + 6, iy, w - 12, 20);
            Render2D.roundRect(ctx, x + 6, iy, w - 12, 20, 6, sel ? Theme.accent(0xD0) : hov ? 0xFF1F2430 : 0xFF161A23);
            Render2D.roundOutline(ctx, x + 6, iy, w - 12, 20, 6, 1, sel ? Theme.accent(0xFF) : 0x16FFFFFF);
            Fonts.drawV(ctx, title, x + 14, iy + 10, sel ? 0xFFFFFFFF : Theme.TEXT_DIM, false, 0.68f);
            String n = String.valueOf(count);
            float nw = Fonts.width(n, false, 0.56f) + 8;
            Render2D.roundRect(ctx, x + w - 12 - nw, iy + 4.5f, nw, 11, 3.5f, sel ? 0x40FFFFFF : 0xFF222736);
            Fonts.drawCentered(ctx, n, x + w - 12 - nw / 2f, iy + 10, sel ? 0xFFFFFFFF : (q.isEmpty() || count > 0 ? Theme.TEXT_DIM : Theme.TEXT_MUTED), false, 0.56f);
            final int index = i;
            hit(x + 6, iy, w - 12, 20, (hx, hy, b) -> {
                showSection(index);
                Sounds.click();
            });
            iy += 24;
        }
        // A tip at the bottom.
        float ty = y + h - 36;
        if (ty > iy + 4) {
            Fonts.drawV(ctx, "Tip", x + 10, ty, Theme.accent(), true, 0.6f);
            for (String line : Fonts.wrap("Right-click Better Looks in the GUI to open this panel directly.", w - 20, false, 0.56f)) {
                ty += 9;
                Fonts.drawV(ctx, line, x + 10, ty, Theme.TEXT_MUTED, false, 0.56f);
            }
        }
    }

    private float rowHeight(Row r) {
        if (r.setting() instanceof NumberSetting) return SLIDER_ROW;
        if (r.setting() instanceof ColorSetting c && picking.contains(c)) return ROW + PICKER;
        return ROW;
    }

    private void content(DrawContext ctx, float x, float y, float w, float h, int mx, int my, long now, float dt) {
        Render2D.roundRect(ctx, x, y, w, h, 9, 0xFF0F1219);
        Render2D.roundOutline(ctx, x, y, w, h, 9, 1, 0x14FFFFFF);
        String q = query.trim();

        // Lay out first, to know how far it scrolls.
        float total = 6;
        for (int i = 0; i < cards.size(); i++) {
            if (section >= 0 && i != section) continue;
            List<Row> rows = cards.get(i).rows().stream().filter(r -> r.visible() && r.matches(q)).toList();
            if (rows.isEmpty()) continue;
            total += 34;
            for (Row r : rows) total += rowHeight(r) + ROW_GAP;
            total += 10;
        }
        float viewH = h - 12;
        float maxScroll = Math.max(0, total - viewH);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 16f);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        listTop = y + 6;
        listBottom = y + h - 6;
        Render2D.clip(ctx, Math.round(x + 1), Math.round(listTop), Math.round(x + w - 1), Math.round(listBottom));
        float cy = listTop - scroll;
        float cardX = x + 8, cardW = w - 16 - (maxScroll > 0 ? 6 : 0);
        boolean any = false;
        for (int i = 0; i < cards.size(); i++) {
            if (section >= 0 && i != section) continue;
            Card card = cards.get(i);
            List<Row> rows = card.rows().stream().filter(r -> r.visible() && r.matches(q)).toList();
            if (rows.isEmpty()) continue;
            any = true;
            float cardH = 34;
            for (Row r : rows) cardH += rowHeight(r) + ROW_GAP;
            cardH += 4;
            if (cy + cardH >= listTop && cy <= listBottom) {
                Render2D.roundRect(ctx, cardX, cy, cardW, cardH, 8, 0xFF141822);
                Render2D.roundOutline(ctx, cardX, cy, cardW, cardH, 8, 1, 0x18FFFFFF);
                Fonts.drawV(ctx, card.title(), cardX + 12, cy + 12, Theme.TEXT, true, 0.9f);
                Fonts.drawV(ctx, card.subtitle(), cardX + 12, cy + 24, Theme.TEXT_MUTED, false, 0.56f);
                Fonts.drawRight(ctx, rows.size() + " settings", cardX + cardW - 12, cy + 12, Theme.accent(), false, 0.6f);
            }
            float ry = cy + 34;
            for (Row r : rows) {
                float rh = rowHeight(r);
                if (ry + rh >= listTop && ry <= listBottom) row(ctx, r, cardX + 8, ry, cardW - 16, rh, mx, my);
                ry += rh + ROW_GAP;
            }
            cy += cardH + 10;
        }
        if (!any) Fonts.drawCentered(ctx, "No settings match “" + q + "”", x + w / 2f, y + h / 2f, Theme.TEXT_MUTED, false, 0.7f);
        Render2D.unclip(ctx);
        if (maxScroll > 0) {
            float bar = Math.max(18, viewH * viewH / total);
            Render2D.roundRect(ctx, x + w - 8, listTop + (viewH - bar) * (scroll / maxScroll), 3, bar, 1.5f, Theme.accent(0x90));
        }
    }

    private void row(DrawContext ctx, Row r, float x, float y, float w, float h, int mx, int my) {
        boolean hov = inside(mx, my, x, y, w, h) && my >= listTop && my < listBottom;
        Setting<?> s = r.setting();
        boolean lit = s instanceof BooleanSetting b ? b.get() : r.on() != null && r.on().getAsBoolean();
        Render2D.roundRect(ctx, x, y, w, h, 6, hov ? 0xFF1C212C : 0xFF181C26);
        Render2D.roundOutline(ctx, x, y, w, h, 6, 1, lit ? Theme.accent(0x70) : hov ? 0x2AFFFFFF : 0x14FFFFFF);
        float cy = y + (s instanceof NumberSetting ? 10 : ROW / 2f);
        Fonts.drawV(ctx, r.name(), x + 10, cy, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hov || lit ? 1 : 0), false, 0.7f);
        float right = x + w - 10;

        if (s == null || s instanceof BooleanSetting) {
            boolean value = s == null ? r.on().getAsBoolean() : ((BooleanSetting) s).get();
            toggle(ctx, right - 24, cy - 6, value, hov);
            clipped(x, y, w, h, (hx, hy, b) -> {
                if (s == null) r.flip().run();
                else ((BooleanSetting) s).toggle();
                Sounds.toggle(s == null ? r.on().getAsBoolean() : ((BooleanSetting) s).get());
            });
        } else if (s instanceof ModeSetting m) {
            String value = m.get();
            float vw = Math.max(54, Fonts.width(value, false, 0.66f) + 22), vx = right - vw;
            boolean open = choosing == m;
            boolean over = inside(mx, my, vx, cy - 7, vw, 14);
            Render2D.roundRect(ctx, vx, cy - 7, vw, 14, 5, open || over ? Theme.accent() : Theme.accent(0xC8));
            Fonts.drawV(ctx, value, vx + 7, cy, 0xFFFFFFFF, false, 0.66f);
            caret(ctx, vx + vw - 8, cy, 0xFFFFFFFF);
            clipped(x, y, w, h, (hx, hy, b) -> {
                if (b == 1) {
                    m.cycle(-1);
                    Sounds.click();
                    return;
                }
                choosing = open ? null : m;
                chooseX = vx;
                chooseY = cy + 9;
                chooseW = vw;
                Sounds.click();
            });
        } else if (s instanceof NumberSetting n) {
            String range = trimNumber(n.getMin()) + " - " + trimNumber(n.getMax());
            Fonts.drawV(ctx, range, x + 10 + Fonts.width(r.name(), false, 0.7f) + 6, cy, Theme.TEXT_MUTED, false, 0.52f);
            // - value +
            String value = n.format();
            float vw = Fonts.width(value, false, 0.66f) + 12, boxW = vw + 28, bx = right - boxW;
            Render2D.roundRect(ctx, bx, cy - 6.5f, boxW, 13, 4, 0xFF0F1219);
            Render2D.roundOutline(ctx, bx, cy - 6.5f, boxW, 13, 4, 1, 0x1EFFFFFF);
            Fonts.drawCentered(ctx, "-", bx + 7, cy, Theme.TEXT_DIM, false, 0.72f);
            Fonts.drawCentered(ctx, value, bx + boxW / 2f, cy, Theme.accent(), false, 0.66f);
            Fonts.drawCentered(ctx, "+", bx + boxW - 7, cy, Theme.TEXT_DIM, false, 0.72f);
            clippedOnly(bx, cy - 6.5f, 14, 13, (hx, hy, b) -> {
                n.set(n.get() - n.getStep());
                Sounds.click();
            });
            clippedOnly(bx + boxW - 14, cy - 6.5f, 14, 13, (hx, hy, b) -> {
                n.set(n.get() + n.getStep());
                Sounds.click();
            });
            // The slider.
            float sx = x + 10, sw = w - 20, sy = y + h - 9;
            float pct = (float) n.getPercent();
            Render2D.roundRect(ctx, sx, sy - 2, sw, 4, 2, 0xFF2A2F3D);
            if (pct > 0) Render2D.roundGradientH(ctx, sx, sy - 2, sw * pct, 4, 2, Theme.accent2(), Theme.accent());
            Render2D.circle(ctx, sx + sw * pct, sy, 4.2f, 0xFFFFFFFF);
            clippedOnly(sx - 4, sy - 6, sw + 8, 12, (hx, hy, b) -> {
                if (b == 1) {
                    n.reset();
                    return;
                }
                dragging = n;
                dragX = sx;
                dragW = sw;
                n.setPercent((hx - sx) / sw);
            });
        } else if (s instanceof ColorSetting c) {
            float sw = 22, shh = 12, sx = right - sw;
            Render2D.roundRect(ctx, sx, cy - shh / 2f, sw, shh, 3.5f, c.get());
            Render2D.roundOutline(ctx, sx, cy - shh / 2f, sw, shh, 3.5f, 1, 0x40FFFFFF);
            Fonts.drawRight(ctx, ColorUtil.toHex(c.get(), c.allowsAlpha()), sx - 6, cy, Theme.TEXT_MUTED, false, 0.58f);
            clippedOnly(x, y, w, ROW, (hx, hy, b) -> {
                if (b == 1) c.reset();
                else if (!picking.remove(c)) picking.add(c);
                Sounds.click();
            });
            if (picking.contains(c)) picker(ctx, c, x + 10, y + ROW + 2, w - 20, PICKER - 8);
        }
    }

    /** A colour picker under its row: saturation and brightness on the left, hue on the right. */
    private void picker(DrawContext ctx, ColorSetting c, float x, float y, float w, float h) {
        float bw = w - 16;
        int hueColor = ColorUtil.hsv(c.getHue(), 1, 1);
        Render2D.roundRect(ctx, x, y, bw, h, 3, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
        Render2D.roundRect(ctx, x, y, bw, h, 3, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
        Render2D.ring(ctx, x + c.getSaturation() * bw, y + (1 - c.getBrightness()) * h, 3f, 1.1f, 0xFFFFFFFF);
        clippedOnly(x, y, bw, h, (hx, hy, b) -> {
            draggingColor = c;
            dragPart = 0;
            pickX = x;
            pickY = y;
            pickW = bw;
            pickH = h;
            dragColor(hx, hy);
        });
        float hx = x + bw + 6, hw = 8;
        int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        for (int i = 0; i < 6; i++) {
            float seg = h / 6f;
            Render2D.rectGradient(ctx, hx, y + seg * i, hw, seg + 0.01f, stops[i], stops[i], stops[i + 1], stops[i + 1]);
        }
        Render2D.roundRect(ctx, hx - 1, y + c.getHue() * h - 1, hw + 2, 2, 1, 0xFFFFFFFF);
        clippedOnly(hx - 2, y, hw + 4, h, (mx, my, b) -> {
            draggingColor = c;
            dragPart = 1;
            pickX = hx;
            pickY = y;
            pickW = hw;
            pickH = h;
            dragColor(mx, my);
        });
    }

    private void dragColor(double mx, double my) {
        ColorSetting c = draggingColor;
        float fx = clamp((float) ((mx - pickX) / pickW)), fy = clamp((float) ((my - pickY) / pickH));
        if (dragPart == 0) c.setHsv(c.getHue(), fx, 1 - fy, c.getAlpha());
        else c.setHsv(fy, c.getSaturation(), c.getBrightness(), c.getAlpha());
    }

    /** The choices of the mode being picked, in a list under its button. */
    private void choices(DrawContext ctx, int mx, int my) {
        ModeSetting m = choosing;
        List<String> modes = m.getModes();
        float w = chooseW;
        for (String mode : modes) w = Math.max(w, Fonts.width(mode, false, 0.66f) + 26);
        float x = Math.min(chooseX + chooseW - w, width - w - 6), h = modes.size() * 14 + 6;
        float y = chooseY + h > height - 6 ? chooseY - 18 - h : chooseY;
        hit(0, 0, width, height, (hx, hy, b) -> choosing = null);
        hit(x, y, w, h, (hx, hy, b) -> {
        });
        Render2D.shadow(ctx, x, y + 2, w, h, 6, 10, 0x90000000);
        Render2D.roundRect(ctx, x, y, w, h, 6, 0xFF161A23);
        Render2D.roundOutline(ctx, x, y, w, h, 6, 1, Theme.accent(0x70));
        float ry = y + 3;
        for (String mode : modes) {
            boolean sel = mode.equals(m.get());
            boolean hov = inside(mx, my, x + 3, ry, w - 6, 14);
            if (sel || hov) Render2D.roundRect(ctx, x + 3, ry, w - 6, 14, 4, sel ? Theme.accent(0x50) : 0xFF232838);
            Fonts.drawV(ctx, mode, x + 9, ry + 7, sel ? 0xFFFFFFFF : Theme.TEXT_DIM, sel, 0.66f);
            hit(x + 3, ry, w - 6, 14, (hx, hy, b) -> {
                m.set(mode);
                choosing = null;
                Sounds.click();
            });
            ry += 14;
        }
    }

    private void toggle(DrawContext ctx, float x, float y, boolean on, boolean hover) {
        float w = 24, h = 12;
        Render2D.roundRect(ctx, x, y, w, h, h / 2f, on ? Theme.accent() : hover ? 0xFF353B4C : 0xFF2A2F3D);
        Render2D.circle(ctx, on ? x + w - h / 2f : x + h / 2f, y + h / 2f, h / 2f - 2, 0xFFFFFFFF);
    }

    private static void caret(DrawContext ctx, float x, float cy, int color) {
        Render2D.line(ctx, x - 2.2f, cy - 1.1f, x, cy + 1.2f, 1.1f, color);
        Render2D.line(ctx, x, cy + 1.2f, x + 2.2f, cy - 1.1f, 1.1f, color);
    }

    private void button(DrawContext ctx, String label, float x, float y, float w, float h, boolean accent, int mx, int my, Runnable action) {
        boolean hover = inside(mx, my, x, y, w, h);
        if (accent) Render2D.roundRect(ctx, x, y, w, h, 6, hover ? Theme.accent() : Theme.accent(0xD8));
        else {
            Render2D.roundRect(ctx, x, y, w, h, 6, hover ? 0xFF262B3A : 0xFF1A1E29);
            Render2D.roundOutline(ctx, x, y, w, h, 6, 1, hover ? 0x40FFFFFF : 0x1EFFFFFF);
        }
        Fonts.drawCentered(ctx, label, x + w / 2f, y + h / 2f, 0xFFFFFFFF, false, 0.66f);
        hit(x, y, w, h, (hx, hy, b) -> {
            Sounds.click();
            action.run();
        });
    }

    private static String trimNumber(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    /** A hit kept inside the scrolling list. */
    private void clipped(float x, float y, float w, float h, Action action) {
        float top = Math.max(y, listTop), bottom = Math.min(y + h, listBottom);
        if (bottom > top) hit(x, top, w, bottom - top, action);
    }

    private void clippedOnly(float x, float y, float w, float h, Action action) {
        clipped(x, y, w, h, action);
    }

    private void hit(float x, float y, float w, float h, Action action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(click.x(), click.y())) {
                typing = false;
                hit.action().run(click.x(), click.y(), click.button());
                return true;
            }
        }
        typing = false;
        if (click.x() < px || click.x() > px + pw || click.y() < py || click.y() > py + ph) close();
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging != null) {
            dragging.setPercent((click.x() - dragX) / dragW);
            return true;
        }
        if (draggingColor != null) {
            dragColor(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        draggingColor = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scrollTarget -= (float) vertical * 30;
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (input.codepoint() > Character.MAX_VALUE) return true;
        char c = (char) input.codepoint();
        if (c < 32 || c == 127 || c == '§') return true;
        typing = true;
        if (query.length() < 40) search(query + c);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (choosing != null) choosing = null;
            else if (!query.isEmpty()) search("");
            else close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
            search(ctrl ? "" : query.substring(0, query.length() - 1));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
