package dev.maro.gui.widget;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.ActionSetting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
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
 * Settings as tight one-line rows - a label on the left and its control on the right, sliders
 * under their label - for the small settings box the panels open next to a module.
 */
public final class CompactSettings {
    private static final float ROW = 15f, SLIDER = 23f, TEXT = 17f, PICKER = 50f, SECTION = 12f;
    private static final float LABEL = 0.74f;
    private final Set<ColorSetting> expanded = new HashSet<>();
    private final Map<TextSetting, TextField> textFields = new IdentityHashMap<>();

    /** @return the height used */
    public float render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, List<SettingSection> sections) {
        float start = y;
        int titled = 0;
        for (SettingSection section : sections) if (section.getSettings().stream().anyMatch(Setting::isVisible)) titled++;
        boolean first = true;
        for (SettingSection section : sections) {
            if (section.getSettings().stream().noneMatch(Setting::isVisible)) continue;
            // Section names only where there is more than one to tell apart.
            if (titled > 1) {
                if (!first) y += 3f;
                Fonts.drawV(ctx, section.getTitle(), x + 4f, y + SECTION / 2f, Theme.accent(0xC8), true, 0.6f);
                y += SECTION;
            }
            first = false;
            for (Setting<?> s : section.getSettings()) {
                float vis = Anims.of(s, "compactVisible", s.isVisible());
                if (vis < 0.01f) continue;
                float full = height(s), h = full * vis;
                if (gui.isVisible(y, h)) {
                    float prev = Render2D.getAlpha();
                    Render2D.setAlpha(prev * vis);
                    gui.pushClip(x, y, w, h);
                    row(gui, ctx, s, x, y, w, full);
                    gui.popClip();
                    Render2D.setAlpha(prev);
                }
                y += h;
            }
        }
        return y - start;
    }

    private float height(Setting<?> s) {
        if (s instanceof NumberSetting) return SLIDER;
        if (s instanceof TextSetting) return TEXT;
        if (s instanceof ColorSetting c) return ROW + PICKER * Anims.of(c, "compactExpand", expanded.contains(c));
        return ROW;
    }

    private void row(ClickGuiScreen gui, DrawContext ctx, Setting<?> s, float x, float y, float w, float h) {
        float top = Math.min(h, s instanceof NumberSetting ? SLIDER : s instanceof TextSetting ? TEXT : ROW);
        boolean hov = gui.hovered(x, y, w, top);
        float hv = Anims.of(s, "compactHover", hov);
        if (hv > 0.01f) Render2D.roundRect(ctx, x, y + 0.5f, w, top - 1f, 3f, ColorUtil.withAlpha(0xFF1E1E29, Math.round(0xFF * hv)));
        if (hov && !s.getDescription().isEmpty()) gui.tooltip(s.getDescription());
        float cy = y + (s instanceof NumberSetting ? 7.5f : top / 2f);

        // The label, as it is written.
        Fonts.beginRaw();
        float labelRoom = s instanceof BooleanSetting ? w - 34f : w * 0.5f;
        Fonts.drawV(ctx, Fonts.trim(s.getName(), labelRoom, false, LABEL), x + 5f, cy, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv), false, LABEL);
        Fonts.endRaw();

        float right = x + w - 5f;
        if (s instanceof BooleanSetting b) {
            Widgets.toggle(ctx, right - 17f, cy - 4.5f, 17f, 9f, Anims.of(b, "on", b.get()), hv);
            gui.hit(x, y, w, top, (button, mx, my) -> {
                if (button != 0) return;
                b.toggle();
                Sounds.toggle(b.get());
            });
        } else if (s instanceof NumberSetting n) {
            number(gui, ctx, n, x, y, w, right, cy);
        } else if (s instanceof ModeSetting m) {
            mode(gui, ctx, m, x, y, w, top, right, cy, hv);
        } else if (s instanceof TextSetting t) {
            TextField field = textFields.computeIfAbsent(t, k -> new TextField(k.getPlaceholder(), k.getMaxLength())
                    .filter(TextSetting::allowed).counter().onChange(k::set));
            if (gui.focused != field && !field.getText().equals(t.get())) field.setText(t.get());
            field.onEnter(() -> gui.focused = null);
            float fw = Math.min(110f, w * 0.52f);
            field.render(gui, ctx, right - fw, y + 2f, fw, 13f, null, null);
        } else if (s instanceof ColorSetting c) {
            color(gui, ctx, c, x, y, w, right, cy);
        } else if (s instanceof KeybindSetting k) {
            float cw = Fonts.width(gui.listening == k ? "Press a key" : k.getKeyName(), false, 0.72f) + 9;
            Widgets.bindChip(gui, ctx, k, right - cw, cy, 1f);
        } else if (s instanceof ActionSetting action) {
            small(gui, ctx, s, "Open", right, cy, action::run);
        } else if (s instanceof dev.maro.runtime.settings.SettingAdapters.ValueSetting value) {
            small(gui, ctx, s, "Edit", right, cy,
                    () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.ValueEditorScreen(gui, value)));
        } else if (s instanceof ButtonSetting b) {
            small(gui, ctx, s, b.getLabel(), right, cy, b::press);
        }
    }

    private static void small(ClickGuiScreen gui, DrawContext ctx, Object id, String label, float right, float cy, Runnable action) {
        float bw = Fonts.width(label, false, 0.85f) + 12f;
        Widgets.button(gui, ctx, id, right - bw, cy - 5.5f, bw, 11f, label, Widgets.Style.SECONDARY, null, action);
    }

    // ---- number ---------------------------------------------------------------------------

    private void number(ClickGuiScreen gui, DrawContext ctx, NumberSetting n, float x, float y, float w, float right, float cy) {
        String value = n.format();
        float vw = Fonts.width(value, false, 0.68f) + 8f, vx = right - vw;
        Render2D.roundRect(ctx, vx, cy - 5f, vw, 10f, 3f, 0xFF15141D);
        Render2D.roundOutline(ctx, vx, cy - 5f, vw, 10f, 3f, 1f, Theme.BORDER);
        Fonts.drawCentered(ctx, value, vx + vw / 2f, cy, Theme.accent(), false, 0.68f);
        gui.scrollHit(vx, cy - 6f, vw, 12f, amount -> n.set(n.get() + n.getStep() * Math.signum(amount)));

        float tx = x + 5f, tw = w - 10f, ty = y + 17.5f;
        boolean dragging = gui.isDragging(n);
        boolean hovered = gui.hovered(tx - 2, ty - 5, tw + 4, 10);
        float hv = Anims.of(n, "compactSlider", hovered || dragging);
        float pct = Anims.of(n, "pct", (float) n.getPercent(), 22f);
        float th = 2f + hv;
        Render2D.roundRect(ctx, tx, ty - th / 2, tw, th, th / 2, Theme.TOGGLE_OFF);
        if (pct > 0.001f) Render2D.roundGradientH(ctx, tx, ty - th / 2, tw * pct, th, th / 2, Theme.accent2(), Theme.accent());
        float kx = tx + tw * pct, kr = 2.8f + hv * 0.6f;
        if (Theme.glow()) Render2D.shadow(ctx, kx - kr, ty - kr, kr * 2, kr * 2, kr, 2.5f + hv * 2, Theme.accent(0x50));
        Render2D.circle(ctx, kx, ty, kr, 0xFFFFFFFF);
        Render2D.circle(ctx, kx, ty, kr * 0.45f, Theme.accent());
        gui.hit(tx - 3, ty - 6, tw + 6, 12, (button, mx, my) -> {
            if (button == 1) {
                n.reset();
                Sounds.click();
                return;
            }
            gui.startDrag(n, (dx, dy) -> n.setPercent((dx - tx) / tw));
        });
    }

    // ---- mode -----------------------------------------------------------------------------

    private void mode(ClickGuiScreen gui, DrawContext ctx, ModeSetting m, float x, float y, float w, float h, float right, float cy, float hv) {
        // The choice in the accent with a caret; a click opens every choice in a list beside the row.
        boolean open = Dropdown.isOpen(m);
        Dropdown.anchor(m, x, x + w, y, h);
        String value = m.get();
        float caret = open ? 1f : 0f;
        int arrows = open ? Theme.accent() : ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT_DIM, hv);
        Fonts.beginRaw();
        float vw = Fonts.width(value, false, 0.72f);
        float pw = vw + 16f, px = right - pw;
        Render2D.roundRect(ctx, px, cy - 5.5f, pw, 11f, 3f, open ? Theme.accent(0x30) : ColorUtil.lerp(0xFF15141D, 0xFF1F1E2A, hv));
        Render2D.roundOutline(ctx, px, cy - 5.5f, pw, 11f, 3f, 1f, open ? Theme.accent(0x90) : Theme.BORDER);
        Fonts.drawV(ctx, value, px + 4f, cy, ColorUtil.lerp(Theme.accent(), 0xFFFFFFFF, Math.max(hv * 0.3f, caret)), false, 0.72f);
        Fonts.endRaw();
        chevron(ctx, right - 5f, cy, 1, arrows);
        gui.hit(x, y, w, h, (button, mx, my) -> {
            if (button == 1) {
                m.cycle(-1);
                Sounds.click();
            } else {
                Dropdown.toggle(m);
            }
        });
        gui.scrollHit(x, y, w, h, amount -> m.cycle(amount > 0 ? -1 : 1));
    }

    // ---- colour ---------------------------------------------------------------------------

    private void color(ClickGuiScreen gui, DrawContext ctx, ColorSetting c, float x, float y, float w, float right, float cy) {
        float sw = 16f, sh = 9f, sx = right - sw, sy = cy - sh / 2f;
        if (c.allowsAlpha()) Render2D.checker(ctx, sx, sy, sw, sh, 3);
        Render2D.roundRect(ctx, sx, sy, sw, sh, 2.5f, c.get());
        Render2D.roundOutline(ctx, sx, sy, sw, sh, 2.5f, 1f, 0x30FFFFFF);
        Fonts.drawRight(ctx, ColorUtil.toHex(c.get(), c.allowsAlpha()), sx - 5f, cy, Theme.TEXT_MUTED, false, 0.62f);
        gui.hit(x, y, w, ROW, (button, mx, my) -> {
            if (button == 1) c.reset();
            else if (!expanded.remove(c)) expanded.add(c);
            Sounds.click();
        });

        float open = Anims.of(c, "compactExpand", expanded.contains(c));
        if (open < 0.02f) return;
        float py = y + ROW + 2f, ph = PICKER - 8f;
        float bars = c.allowsAlpha() ? 2 : 1;
        float bx = x + 5f, bw = w - 10f - bars * 10f;
        float hue = c.getHue();
        int hueColor = ColorUtil.hsv(hue, 1, 1);
        Render2D.roundRect(ctx, bx, py, bw, ph, 3, 0xFFFFFFFF, hueColor, hueColor, 0xFFFFFFFF);
        Render2D.roundRect(ctx, bx, py, bw, ph, 3, 0x00000000, 0x00000000, 0xFF000000, 0xFF000000);
        Render2D.ring(ctx, bx + c.getSaturation() * bw, py + (1 - c.getBrightness()) * ph, 3f, 1.1f, 0xFFFFFFFF);
        gui.hit(bx, py, bw, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                c.setHsv(c.getHue(), clamp((dx - bx) / bw), 1 - clamp((dy - py) / ph), c.getAlpha())));

        float hx = bx + bw + 4f, hw = 6f;
        int[] stops = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
        for (int i = 0; i < 6; i++) {
            float seg = ph / 6f;
            Render2D.rectGradient(ctx, hx, py + seg * i, hw, seg + 0.01f, stops[i], stops[i], stops[i + 1], stops[i + 1]);
        }
        Render2D.roundRect(ctx, hx - 1f, py + hue * ph - 1f, hw + 2, 2, 1f, 0xFFFFFFFF);
        gui.hit(hx - 2, py, hw + 4, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                c.setHsv(clamp((dy - py) / ph), c.getSaturation(), c.getBrightness(), c.getAlpha())));
        if (c.allowsAlpha()) {
            float ax = hx + hw + 4f;
            Render2D.checker(ctx, ax, py, hw, ph, 2);
            int opaque = c.get() | 0xFF000000;
            Render2D.rectGradient(ctx, ax, py, hw, ph, opaque, opaque, opaque & 0xFFFFFF, opaque & 0xFFFFFF);
            Render2D.roundRect(ctx, ax - 1f, py + (1 - c.getAlpha() / 255f) * ph - 1f, hw + 2, 2, 1f, 0xFFFFFFFF);
            gui.hit(ax - 2, py, hw + 4, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                    c.setHsv(c.getHue(), c.getSaturation(), c.getBrightness(), Math.round(255 * (1 - clamp((dy - py) / ph))))));
        }
    }

    /** A small arrow head pointing right ({@code dir} 1) or left (-1). */
    private static void chevron(DrawContext ctx, float x, float cy, int dir, int color) {
        Render2D.line(ctx, x - 1.6f * dir, cy - 2.4f, x + 0.8f * dir, cy, 1.1f, color);
        Render2D.line(ctx, x + 0.8f * dir, cy, x - 1.6f * dir, cy + 2.4f, 1.1f, color);
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }
}
