package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders {@link SettingSection}s as cards: a titled header, then one quiet row per setting with
 * its name on the left and its control on the right; sliders run the full width under their name.
 * Descriptions show as tooltips. Used for module settings, the Settings page and the Theme page.
 */
public class SettingsList {
    public static final float CARD_GAP = 8f;
    private static final float HEADER = 22f, PAD = 10f, ROW = 22f, PICKER_H = 80f;
    private final Set<ColorSetting> expanded = new HashSet<>();
    private final Map<TextSetting, TextField> textFields = new IdentityHashMap<>();
    private final Map<NumberSetting, TextField> numericFields = new IdentityHashMap<>();
    private final Map<SettingSection, Float> tops = new IdentityHashMap<>();
    private final Map<Setting<?>, float[]> visiblePlaces = new IdentityHashMap<>();
    private final Scroll boxScroll = new Scroll();

    /** A card's background and title, the same for the cards the settings panel draws itself. */
    public static void card(DrawContext ctx, String title, float x, float y, float w, float h) {
        float r = cardRadius();
        Render2D.roundRect(ctx, x, y, w, h, r, Theme.CARD);
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.withAlpha(Theme.BORDER, 0x90));
        if (title == null) return;
        Fonts.drawV(ctx, title, x + PAD, y + HEADER / 2f, Theme.TEXT, true, 0.74f);
        Render2D.rect(ctx, x + 1, y + HEADER, w - 2, Render2D.px(), ColorUtil.withAlpha(Theme.BORDER, 0x70));
    }

    public static float cardRadius() {
        return 4f + Theme.radius() * 0.6f;
    }

    public static float headerHeight() {
        return HEADER;
    }

    /** Where each setting's row was drawn last frame, {x, y, w, h}; for tests. */
    public Map<Setting<?>, float[]> visiblePlaces() {
        return Map.copyOf(visiblePlaces);
    }

    public Map<NumberSetting, TextField> numericFields() { return Map.copyOf(numericFields); }

    /**
     * The cards in a box of their own, scrolling inside it, for pages that give the list a fixed
     * area (the Settings and Theme pages).
     */
    public void renderPaged(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, float h, List<SettingSection> sections, boolean labels) {
        float off = boxScroll.update(gui.hasPointerDrag());
        gui.scrollHit(x, y, w, h, boxScroll::scroll);
        gui.pushClip(x - 2, y, w + 4, h);
        float content = render(gui, ctx, x, y - off, w - 8, sections, labels || sections.size() > 1);
        gui.popClip();
        boxScroll.setBounds(content + 4, h);
        boxScroll.drawBar(gui, ctx, x + w - 2, y, h);
    }

    /** Back to the top of a {@link #renderPaged} box. */
    public void resetPage() {
        boxScroll.reset();
    }

    /** Where a section's card started in the last frame, relative to the list's top. */
    public Float sectionTop(SettingSection section) {
        return tops.get(section);
    }

    public float render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, List<SettingSection> sections) {
        return render(gui, ctx, x, y, w, sections, true);
    }

    /**
     * @param labels whether each card gets its section's title
     * @return total content height
     */
    public float render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, List<SettingSection> sections, boolean labels) {
        float start = y;
        tops.clear();
        visiblePlaces.clear();
        boolean first = true;
        for (SettingSection section : sections) {
            float rows = 0;
            for (Setting<?> s : section.getSettings()) rows += rowHeight(s) * Anims.of(s, "visible", s.isVisible());
            if (rows < 0.5f) continue;
            if (!first) y += CARD_GAP;
            first = false;
            tops.put(section, y - start);
            float head = labels ? HEADER + 3 : 4;
            float h = head + rows + 5;
            if (gui.isVisible(y, h)) card(ctx, labels ? section.getTitle() : null, x, y, w, h);
            float ry = y + head;
            for (Setting<?> s : section.getSettings()) {
                float vis = Anims.of(s, "visible", s.isVisible());
                if (vis < 0.01f) continue;
                float full = rowHeight(s);
                if (gui.isVisible(ry, full * vis)) {
                    float prevAlpha = Render2D.getAlpha();
                    Render2D.setAlpha(prevAlpha * vis);
                    gui.pushClip(x, ry, w, full * vis + 1);
                    renderRow(gui, ctx, s, x + PAD, ry, w - PAD * 2, full);
                    visiblePlaces.put(s, new float[] {x + PAD, ry, w - PAD * 2, full});
                    gui.popClip();
                    Render2D.setAlpha(prevAlpha);
                }
                ry += full * vis;
            }
            y += h;
        }
        return y - start;
    }

    private float rowHeight(Setting<?> s) {
        if (s instanceof NumberSetting) return 32f;
        if (s instanceof TextSetting) return 38f;
        if (s instanceof ColorSetting c) return ROW + PICKER_H * Anims.of(c, "expand", expanded.contains(c));
        return ROW;
    }

    private void renderRow(ClickGuiScreen gui, DrawContext ctx, Setting<?> s, float x, float y, float w, float h) {
        boolean hovered = gui.hovered(x - 4, y, w + 8, Math.min(h, s instanceof NumberSetting ? 32f : ROW));
        float hv = Anims.of(s, "hover", hovered);
        if (hv > 0.01f) Render2D.roundRect(ctx, x - 5, y + 1, w + 10, Math.min(h, s instanceof NumberSetting ? 32f : ROW) - 2, 4f,
                ColorUtil.withAlpha(0xFFFFFFFF, Math.round(0x0A * hv)));

        float labelW = s instanceof NumberSetting n ? w - numericWidth(n) - 10 : s instanceof TextSetting ? w * 0.7f : w * 0.52f;
        float nameY = s instanceof NumberSetting ? y + 10 : s instanceof TextSetting ? y + 9 : y + ROW / 2f;
        Fonts.drawV(ctx, Fonts.trim(s.getName(), labelW, false, 0.8f), x, nameY, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), false, 0.8f);
        if (hovered && !s.getDescription().isEmpty() && gui.hovered(x, nameY - 6, Fonts.width(s.getName(), false, 0.8f), 12))
            gui.tooltip(s.getDescription());

        if (s instanceof BooleanSetting b) bool(gui, ctx, b, x, y, w, hv);
        else if (s instanceof NumberSetting n) number(gui, ctx, n, x, y, w);
        else if (s instanceof ModeSetting m) mode(gui, ctx, m, x, y, w);
        else if (s instanceof TextSetting t) text(gui, ctx, t, x, y, w);
        else if (s instanceof ColorSetting c) color(gui, ctx, c, x, y, w);
        else if (s instanceof KeybindSetting k) {
            Widgets.bindChip(gui, ctx, k, x + w - Widgets.bindChipWidth(gui, k), y + ROW / 2f, 1f);
        } else if (s instanceof dev.maro.setting.ActionSetting action) {
            Widgets.button(gui, ctx, s, x + w - 46, y + 4, 46, 14, "Open", Widgets.Style.SECONDARY, null, action::run);
        } else if (s instanceof dev.maro.runtime.settings.SettingAdapters.ValueSetting value) {
            Widgets.button(gui, ctx, s, x + w - 46, y + 4, 46, 14, "Edit", Widgets.Style.SECONDARY, null,
                    () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.ValueEditorScreen(gui, value)));
        } else if (s instanceof ButtonSetting b) {
            float bw = Math.min(w * 0.46f, Fonts.width(b.getLabel(), false, 0.85f) + 16);
            Widgets.button(gui, ctx, b, x + w - bw, y + 4, bw, 14, Fonts.trim(b.getLabel(), bw - 8, false, 0.85f), Widgets.Style.SECONDARY, null, b::press);
        }
    }

    // ---- boolean ------------------------------------------------------------------------

    private void bool(ClickGuiScreen gui, DrawContext ctx, BooleanSetting b, float x, float y, float w, float hv) {
        gui.hit(x - 4, y, w + 8, ROW, (button, mx, my) -> {
            if (button != 0) return;
            b.toggle();
            Sounds.toggle(b.get());
        });
        Widgets.toggle(ctx, x + w - 20, y + ROW / 2f - 5, 20, 10, Anims.of(b, "on", b.get()), hv);
    }

    // ---- number -------------------------------------------------------------------------

    private void number(ClickGuiScreen gui, DrawContext ctx, NumberSetting n, float x, float y, float w) {
        float tx = x, tw = w, ty = y + 25f;
        // The track first: it is the row's control, and what a click on the row's slider finds.
        gui.hit(tx - 4, ty - 5, tw + 8, 10, (button, mx, my) -> {
            if (button == 1) {
                n.reset();
                Sounds.click();
                return;
            }
            if (button != 0) return;
            gui.focused = null;
            gui.startDrag(n, (dx, dy) -> n.setPercent((dx - tx) / tw));
        });
        TextField field = numericFields.computeIfAbsent(n, k -> new TextField("Value", 24)
                .suffix(k.getSuffix())
                .filter(c -> c >= '0' && c <= '9' || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')
                .onChange(value -> {
                    try {
                        double parsed = Double.parseDouble(value);
                        if (Double.isFinite(parsed)) k.set(parsed);
                    } catch (NumberFormatException ignored) { /* Incomplete input remains a draft. */ }
                }));
        if (gui.focused != field && !field.getText().equals(numericValue(n))) field.setTextSilently(numericValue(n));
        field.onEnter(() -> { gui.focused = null; field.setTextSilently(numericValue(n)); });
        float fw = numericWidth(n), fx = x + w - fw;
        field.render(gui, ctx, fx, y + 1, fw, 17, null, null);
        gui.scrollHit(fx, y + 1, fw, 17,
                amount -> n.set(n.get() + n.getStep() * Math.signum(amount)));

        boolean dragging = gui.isDragging(n);
        boolean hovered = gui.hovered(tx - 4, ty - 7, tw + 8, 14);
        float hv = Anims.of(n, "slider", hovered || dragging);
        float pct = Anims.of(n, "pct", (float) n.getPercent(), 22f);
        float th = 3f;
        Render2D.roundRect(ctx, tx, ty - th / 2, tw, th, th / 2, Theme.TOGGLE_OFF);
        if (pct > 0.001f) Render2D.roundGradientH(ctx, tx, ty - th / 2, tw * pct, th, th / 2, Theme.accent2(), Theme.accent());
        float kx = tx + tw * pct, kr = 3.6f + hv * 0.8f;
        if (Theme.glow()) Render2D.shadow(ctx, kx - kr, ty - kr, kr * 2, kr * 2, kr, 3f + hv * 2, Theme.accent(0x50));
        Render2D.circle(ctx, kx, ty, kr, 0xFFFFFFFF);
        if (hovered && !dragging) gui.tooltip("Drag to change • Type a value • Scroll to step • Right-click the slider to reset");
    }

    private static String numericValue(NumberSetting n) {
        return java.math.BigDecimal.valueOf(n.get()).stripTrailingZeros().toPlainString();
    }

    private static float numericWidth(NumberSetting n) {
        return Math.min(94, Math.max(42, Fonts.width(numericValue(n), false, .85f) + Fonts.width(n.getSuffix(), false, .75f) + 20));
    }

    // ---- text ---------------------------------------------------------------------------

    private void text(ClickGuiScreen gui, DrawContext ctx, TextSetting t, float x, float y, float w) {
        TextField field = textFields.computeIfAbsent(t, k -> new TextField(k.getPlaceholder(), k.getMaxLength())
                .filter(TextSetting::allowed).counter().onChange(k::set));
        // Follow the setting while it is not being typed in, after a reset or a config load.
        if (gui.focused != field && !field.getText().equals(t.get())) field.setText(t.get());
        field.onEnter(() -> gui.focused = null);
        field.render(gui, ctx, x, y + 18, w, 16, null, null);
    }

    // ---- mode ---------------------------------------------------------------------------

    private void mode(ClickGuiScreen gui, DrawContext ctx, ModeSetting m, float x, float y, float w) {
        List<String> modes = m.getModes();
        float scale = 0.74f, h = 15, cy = y + ROW / 2f;
        float total = 0;
        float[] widths = new float[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            widths[i] = Fonts.width(modes.get(i), false, scale) + 10;
            total += widths[i];
        }
        float r = Math.min(h / 2, 4f);
        if (modes.size() <= 4 && total + 4 <= w * 0.6f) {
            // segmented control
            float bx = x + w - total - 4;
            Render2D.roundRect(ctx, bx, cy - h / 2, total + 4, h, r, Theme.INPUT);
            Render2D.roundOutline(ctx, bx, cy - h / 2, total + 4, h, r, 1f, ColorUtil.withAlpha(Theme.BORDER, 0x90));
            float sx = bx + 2, selX = 0, selW = 0;
            for (int i = 0; i < modes.size(); i++) {
                if (i == m.index()) {
                    selX = sx;
                    selW = widths[i];
                }
                sx += widths[i];
            }
            float ax = Anims.of(m, "segX", selX - bx, 18f), aw = Anims.of(m, "segW", selW, 18f);
            Render2D.roundRect(ctx, bx + ax, cy - h / 2 + 2, aw, h - 4, Math.max(1f, r - 1), Theme.selected());
            sx = bx + 2;
            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                boolean sel = i == m.index();
                boolean hov = gui.hovered(sx, cy - h / 2, widths[i], h);
                float t = Anims.of(m, "seg" + i, sel ? 1 : hov ? 0.5f : 0);
                Fonts.drawCentered(ctx, mode, sx + widths[i] / 2f, cy, ColorUtil.lerp(Theme.TEXT_MUTED, 0xFFFFFFFF, t), false, scale);
                final String target = mode;
                gui.hit(sx, cy - h / 2, widths[i], h, (button, mx, my) -> {
                    m.set(target);
                    Sounds.click();
                });
                sx += widths[i];
            }
            return;
        }
        // A pill that opens every choice in a list beside it.
        String v = m.get();
        float pw = Math.min(w * 0.5f, Math.max(64, Fonts.width(v, false, scale) + 26));
        float px = x + w - pw;
        boolean hov = gui.hovered(px, cy - h / 2, pw, h);
        float hv = Anims.of(m, "pill", hov);
        boolean open = Dropdown.isOpen(m);
        Render2D.roundRect(ctx, px, cy - h / 2, pw, h, r, ColorUtil.lerp(Theme.INPUT, Theme.CARD_HOVER, hv));
        Render2D.roundOutline(ctx, px, cy - h / 2, pw, h, r, 1f, ColorUtil.lerp(ColorUtil.withAlpha(Theme.BORDER, 0x90), Theme.accent(0x90), open ? 1 : hv));
        Dropdown.anchor(m, px, px + pw, cy - h / 2, h);
        Fonts.drawV(ctx, Fonts.trim(v, pw - 22, false, scale), px + 7, cy, open ? Theme.accent() : Theme.TEXT, false, scale);
        Icons.CHEVRON_RIGHT.draw(ctx, px + pw - 8, cy, 5.5f, open ? Theme.accent() : Theme.TEXT_MUTED, open ? 1 : 0);
        gui.hit(px, cy - h / 2, pw, h, (button, mx, my) -> {
            if (button == 1) {
                m.cycle(-1);
                Sounds.click();
            } else {
                Dropdown.toggle(m);
            }
        });
        gui.scrollHit(px, cy - h / 2, pw, h, amount -> m.cycle(amount > 0 ? -1 : 1));
    }

    // ---- colour -------------------------------------------------------------------------

    private void color(ClickGuiScreen gui, DrawContext ctx, ColorSetting c, float x, float y, float w) {
        // A swatch and "Edit" chip; clicking the row opens the picker under it.
        float chipW = 44, chipH = 14, chipX = x + w - chipW, chipY = y + ROW / 2f - chipH / 2f;
        boolean open = expanded.contains(c);
        boolean chipHover = gui.hovered(chipX, chipY, chipW, chipH);
        Render2D.roundRect(ctx, chipX, chipY, chipW, chipH, 4f, ColorUtil.lerp(Theme.INPUT, Theme.CARD_HOVER, chipHover ? 1 : 0));
        Render2D.roundOutline(ctx, chipX, chipY, chipW, chipH, 4f, 1f, open ? Theme.accent(0x90) : ColorUtil.withAlpha(Theme.BORDER, 0x90));
        if (c.allowsAlpha()) Render2D.checker(ctx, chipX + 3, chipY + 3, 10, chipH - 6, 2);
        Render2D.roundRect(ctx, chipX + 3, chipY + 3, 10, chipH - 6, 2f, c.get());
        Fonts.drawV(ctx, "Edit", chipX + 17, chipY + chipH / 2f, open ? Theme.accent() : Theme.TEXT_DIM, false, 0.72f);

        gui.hit(x - 4, y, w + 8, ROW, (button, mx, my) -> {
            if (button == 1) c.reset();
            else if (!expanded.remove(c)) expanded.add(c);
            Sounds.click();
        });

        float shown = Anims.of(c, "expand", open);
        if (shown < 0.02f) return;

        float py = y + ROW + 2, ph = PICKER_H - 12;
        float bw = Math.max(60, w - 34 - (c.allowsAlpha() ? 16 : 0));
        float bx = x;
        float hue = c.getHue();
        int hueColor = ColorUtil.hsv(hue, 1, 1);

        // saturation / value square
        Render2D.roundRect(ctx, bx, py, bw, ph, 3, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
        Render2D.roundRect(ctx, bx, py, bw, ph, 3, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
        float kx = bx + c.getSaturation() * bw, ky = py + (1 - c.getBrightness()) * ph;
        Render2D.ring(ctx, kx, ky, 3.5f, 1.2f, 0xFFFFFFFF);
        gui.hit(bx, py, bw, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                c.setHsv(c.getHue(), clamp((dx - bx) / bw), 1 - clamp((dy - py) / ph), c.getAlpha())));

        // hue bar
        float hx = bx + bw + 8, hw = 8;
        int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        for (int i = 0; i < 6; i++) {
            float seg = ph / 6f;
            Render2D.rectGradient(ctx, hx, py + seg * i, hw, seg + 0.01f, stops[i], stops[i], stops[i + 1], stops[i + 1]);
        }
        Render2D.roundOutline(ctx, hx, py, hw, ph, 1, 1f, 0x30FFFFFF);
        float hy = py + hue * ph;
        Render2D.roundRect(ctx, hx - 1.5f, hy - 1.5f, hw + 3, 3, 1.5f, 0xFFFFFFFF);
        gui.hit(hx - 2, py, hw + 4, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                c.setHsv(clamp((dy - py) / ph), c.getSaturation(), c.getBrightness(), c.getAlpha())));

        if (c.allowsAlpha()) {
            float ax = hx + hw + 8;
            Render2D.checker(ctx, ax, py, hw, ph, 2);
            int opaque = c.get() | 0xFF000000;
            Render2D.rectGradient(ctx, ax, py, hw, ph, opaque, opaque, opaque & 0xFFFFFF, opaque & 0xFFFFFF);
            Render2D.roundOutline(ctx, ax, py, hw, ph, 1, 1f, 0x30FFFFFF);
            float ay = py + (1 - c.getAlpha() / 255f) * ph;
            Render2D.roundRect(ctx, ax - 1.5f, ay - 1.5f, hw + 3, 3, 1.5f, 0xFFFFFFFF);
            gui.hit(ax - 2, py, hw + 4, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                    c.setHsv(c.getHue(), c.getSaturation(), c.getBrightness(), Math.round(255 * (1 - clamp((dy - py) / ph))))));
        }

        // presets along the bottom, and the hex value
        float dot = 8, gap = 4, dy = py + ph + 4;
        for (int i = 0; i < Theme.PRESETS.length; i++) {
            int preset = Theme.PRESETS[i];
            float dx = bx + i * (dot + gap);
            if (dx + dot > x + w - 44) break;
            boolean hov = gui.hovered(dx, dy, dot, dot);
            boolean sel = (c.get() & 0xFFFFFF) == (preset & 0xFFFFFF);
            Render2D.circle(ctx, dx + dot / 2, dy + dot / 2, dot / 2 + (hov ? 0.5f : 0), preset);
            if (sel) Render2D.ring(ctx, dx + dot / 2, dy + dot / 2, dot / 2 + 1.6f, 1f, 0xFFFFFFFF);
            gui.hit(dx, dy, dot, dot, (button, mx, my) -> {
                c.set(ColorUtil.withAlpha(preset, c.getAlpha()));
                Sounds.click();
            });
            if (hov) gui.tooltip(Theme.PRESET_NAMES[i]);
        }
        Fonts.drawRight(ctx, ColorUtil.toHex(c.get(), c.allowsAlpha()), x + w, dy + dot / 2f, Theme.TEXT_MUTED, false, 0.68f);
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }
}
