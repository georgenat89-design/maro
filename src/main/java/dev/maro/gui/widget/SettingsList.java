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
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a list of {@link SettingSection}s as rows of controls. Used for module settings,
 * the Settings page and the Theme page.
 */
public class SettingsList {
    private static final float GAP = 4f;
    private static final float PICKER_H = 78f;
    private final Set<ColorSetting> expanded = new HashSet<>();

    /**
     * @return total content height
     */
    public float render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, List<SettingSection> sections) {
        return render(gui, ctx, x, y, w, sections, true);
    }

    /**
     * @param labels whether each section gets its title above its rows
     * @return total content height
     */
    public float render(ClickGuiScreen gui, DrawContext ctx, float x, float y, float w, List<SettingSection> sections, boolean labels) {
        float start = y;
        boolean firstSection = true;
        for (SettingSection section : sections) {
            boolean any = false;
            for (Setting<?> s : section.getSettings()) any |= s.isVisible();
            if (!any) continue;
            if (labels) {
                if (!firstSection) y += 6;
                Widgets.sectionLabel(ctx, section.getTitle(), x + 2, y, w - 4);
                y += 12;
            }
            firstSection = false;
            for (Setting<?> s : section.getSettings()) {
                float vis = Anims.of(s, "visible", s.isVisible());
                if (vis < 0.01f) continue;
                float full = rowHeight(s);
                float h = full * vis;
                if (gui.isVisible(y, h)) {
                    float prevAlpha = Render2D.getAlpha();
                    Render2D.setAlpha(prevAlpha * vis);
                    gui.pushClip(x - 8, y, w + 16, h + 6);
                    renderRow(gui, ctx, s, x, y, w, full);
                    gui.popClip();
                    Render2D.setAlpha(prevAlpha);
                }
                y += (full + GAP) * vis;
            }
        }
        return y - start;
    }

    private float rowHeight(Setting<?> s) {
        if (s instanceof NumberSetting) return 38f;
        if (s instanceof ColorSetting c) return 30f + PICKER_H * Anims.of(c, "expand", expanded.contains(c));
        return 30f;
    }

    private void renderRow(ClickGuiScreen gui, DrawContext ctx, Setting<?> s, float x, float y, float w, float h) {
        boolean hovered = gui.hovered(x, y, w, Math.min(h, 30f));
        float hv = Anims.of(s, "hover", hovered);
        float r = Theme.radius();
        Render2D.roundRect(ctx, x, y, w, h, r, ColorUtil.lerp(Theme.CARD, Theme.CARD_HOVER, hv));
        Render2D.roundOutline(ctx, x, y, w, h, r, 1f, ColorUtil.withAlpha(Theme.BORDER, 0xC0));

        boolean hasDesc = !s.getDescription().isEmpty();
        float nameY = hasDesc ? y + 6.5f : y + 11.5f;
        float rightLimit = x + w * 0.5f;
        Fonts.draw(ctx, Fonts.trim(s.getName(), rightLimit - x - 10, false, 0.92f), x + 10, nameY, Theme.TEXT, false, 0.92f);
        if (hasDesc) Fonts.draw(ctx, Fonts.trim(s.getDescription(), w * 0.55f, false, 0.7f), x + 10, y + 18f, Theme.TEXT_MUTED, false, 0.7f);

        if (s instanceof BooleanSetting b) bool(gui, ctx, b, x, y, w, hv);
        else if (s instanceof NumberSetting n) number(gui, ctx, n, x, y, w);
        else if (s instanceof ModeSetting m) mode(gui, ctx, m, x, y, w);
        else if (s instanceof ColorSetting c) color(gui, ctx, c, x, y, w, h);
        else if (s instanceof KeybindSetting k) {
            float cw = Fonts.width(gui.listening == k ? "Press a key" : k.getKeyName(), false, 0.72f) + 9;
            Widgets.bindChip(gui, ctx, k, x + w - 10 - cw, y + 15, 1f);
        } else if (s instanceof dev.maro.setting.ActionSetting action) {
            Widgets.button(gui,ctx,s,x+w-62,y+6,52,18,"Open",Widgets.Style.SECONDARY,null,action::run);
        } else if (s instanceof dev.maro.runtime.settings.SettingAdapters.ValueSetting value) {
            Widgets.button(gui,ctx,s,x+w-62,y+6,52,18,"Edit",Widgets.Style.SECONDARY,null,
                ()->net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.maro.gui.ValueEditorScreen(gui,value)));
        } else if (s instanceof ButtonSetting b) {
            float bw = Widgets.buttonWidth(b.getLabel(), Icons.CHEVRON_RIGHT);
            Widgets.button(gui, ctx, b, x + w - 10 - bw, y + 7, bw, 16, b.getLabel(), Widgets.Style.PRIMARY, Icons.CHEVRON_RIGHT, b::press);
        }
    }

    // ---- boolean ------------------------------------------------------------------------

    private void bool(ClickGuiScreen gui, DrawContext ctx, BooleanSetting b, float x, float y, float w, float hv) {
        float t = Anims.of(b, "on", b.get());
        Widgets.toggle(ctx, x + w - 32, y + 9.5f, 22, 11, t, hv);
        gui.hit(x, y, w, 30, (button, mx, my) -> {
            if (button != 0) return;
            b.toggle();
            Sounds.toggle(b.get());
        });
    }

    // ---- number -------------------------------------------------------------------------

    private void number(ClickGuiScreen gui, DrawContext ctx, NumberSetting n, float x, float y, float w) {
        String value = n.format();
        float vw = Fonts.width(value, false, 0.8f) + 8;
        float vx = x + w - 10 - vw;
        Render2D.roundRect(ctx, vx, y + 6, vw, 11, 3, 0xFF15141D);
        Fonts.drawCentered(ctx, value, vx + vw / 2f, y + 11.5f, Theme.accent(), false, 0.8f);

        float tx = x + 10, tw = w - 20, ty = y + 28.5f;
        boolean dragging = gui.isDragging(n);
        boolean hovered = gui.hovered(tx - 2, ty - 6, tw + 4, 12);
        float hv = Anims.of(n, "slider", hovered || dragging);
        float pct = Anims.of(n, "pct", (float) n.getPercent(), 22f);
        float th = 3f + hv;
        Render2D.roundRect(ctx, tx, ty - th / 2, tw, th, th / 2, Theme.TOGGLE_OFF);
        if (pct > 0.001f) Render2D.roundGradientH(ctx, tx, ty - th / 2, tw * pct, th, th / 2, Theme.accent2(), Theme.accent());
        float kx = tx + tw * pct;
        float kr = 3.2f + hv * 0.8f;
        if (Theme.glow()) Render2D.shadow(ctx, kx - kr, ty - kr, kr * 2, kr * 2, kr, 3f + hv * 2, Theme.accent(0x50));
        Render2D.circle(ctx, kx, ty, kr, 0xFFFFFFFF);
        Render2D.circle(ctx, kx, ty, kr * 0.45f, Theme.accent());

        gui.hit(tx - 4, ty - 7, tw + 8, 14, (button, mx, my) -> {
            if (button == 1) {
                n.reset();
                Sounds.click();
                return;
            }
            gui.startDrag(n, (dx, dy) -> n.setPercent((dx - tx) / tw));
        });
        if (hovered && !dragging) gui.tooltip("Drag to change • Scroll on value • Right-click to reset");
        gui.scrollHit(vx, y + 4, vw, 15, amount -> n.set(n.get() + n.getStep() * Math.signum(amount)));
    }

    // ---- mode ---------------------------------------------------------------------------

    private void mode(ClickGuiScreen gui, DrawContext ctx, ModeSetting m, float x, float y, float w) {
        List<String> modes = m.getModes();
        float scale = 0.78f;
        float total = 0;
        float[] widths = new float[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            widths[i] = Fonts.width(modes.get(i), false, scale) + 12;
            total += widths[i];
        }
        float h = 15, cy = y + 15;
        if (total + 4 <= w * 0.5f) {
            // segmented control
            float bx = x + w - 10 - total - 4;
            Render2D.roundRect(ctx, bx, cy - h / 2, total + 4, h, Math.min(h / 2, Theme.radius()), 0xFF15141D);
            Render2D.roundOutline(ctx, bx, cy - h / 2, total + 4, h, Math.min(h / 2, Theme.radius()), 1f, Theme.BORDER);
            float sx = bx + 2;
            float selX = 0, selW = 0;
            for (int i = 0; i < modes.size(); i++) {
                if (i == m.index()) {
                    selX = sx;
                    selW = widths[i];
                }
                sx += widths[i];
            }
            float ax = Anims.of(m, "segX", selX - bx, 18f), aw = Anims.of(m, "segW", selW, 18f);
            Render2D.roundGradientH(ctx, bx + ax, cy - h / 2 + 2, aw, h - 4, Math.min((h - 4) / 2, Theme.radius() - 1), Theme.accent(), Theme.accent2());
            sx = bx + 2;
            for (int i = 0; i < modes.size(); i++) {
                String mode = modes.get(i);
                boolean sel = i == m.index();
                boolean hov = gui.hovered(sx, cy - h / 2, widths[i], h);
                float t = Anims.of(m, "seg" + i, sel ? 1 : hov ? 0.5f : 0);
                Fonts.drawCentered(ctx, mode, sx + widths[i] / 2f, cy, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, t), false, scale);
                final String target = mode;
                gui.hit(sx, cy - h / 2, widths[i], h, (button, mx, my) -> {
                    m.set(target);
                    Sounds.click();
                });
                sx += widths[i];
            }
        } else {
            // cycling pill
            String v = m.get();
            float pw = Math.max(70, Fonts.width(v, false, scale) + 34);
            float px = x + w - 10 - pw;
            boolean hov = gui.hovered(px, cy - h / 2, pw, h);
            float hv = Anims.of(m, "pill", hov);
            Render2D.roundRect(ctx, px, cy - h / 2, pw, h, Math.min(h / 2, Theme.radius()), ColorUtil.lerp(0xFF15141D, 0xFF22202C, hv));
            Render2D.roundOutline(ctx, px, cy - h / 2, pw, h, Math.min(h / 2, Theme.radius()), 1f, ColorUtil.lerp(Theme.BORDER, Theme.accent(0x90), hv));
            Icons.BACK.draw(ctx, px + 8, cy, 6, Theme.TEXT_MUTED, 0);
            Icons.CHEVRON_RIGHT.draw(ctx, px + pw - 8, cy, 6, Theme.TEXT_MUTED, 0);
            Fonts.drawCentered(ctx, v, px + pw / 2f, cy, Theme.TEXT, false, scale);
            Fonts.drawRight(ctx, (m.index() + 1) + "/" + modes.size(), px - 6, cy, Theme.TEXT_MUTED, false, 0.65f);
            gui.hit(px, cy - h / 2, pw, h, (button, mx, my) -> {
                m.cycle(button == 1 || mx < px + pw / 3f ? -1 : 1);
                Sounds.click();
            });
            gui.scrollHit(px, cy - h / 2, pw, h, amount -> m.cycle(amount > 0 ? -1 : 1));
        }
    }

    // ---- colour -------------------------------------------------------------------------

    private void color(ClickGuiScreen gui, DrawContext ctx, ColorSetting c, float x, float y, float w, float h) {
        float sw = 22, sh = 12, sx = x + w - 10 - sw, sy = y + 9;
        float r = 3f;
        if (c.allowsAlpha()) Render2D.checker(ctx, sx, sy, sw, sh, 3);
        Render2D.roundRect(ctx, sx, sy, sw, sh, r, c.get());
        Render2D.roundOutline(ctx, sx, sy, sw, sh, r, 1f, 0x30FFFFFF);
        Fonts.drawRight(ctx, ColorUtil.toHex(c.get(), c.allowsAlpha()), sx - 6, sy + sh / 2f, Theme.TEXT_DIM, false, 0.72f);

        gui.hit(x, y, w, 30, (button, mx, my) -> {
            if (button == 1) c.reset();
            else if (!expanded.remove(c)) expanded.add(c);
            Sounds.click();
        });

        float open = Anims.of(c, "expand", expanded.contains(c));
        if (open < 0.02f) return;

        float py = y + 32, ph = PICKER_H - 12;
        float bw = Math.min(150, w - 150);
        float bx = x + 10;
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

        float nextX = hx + hw + 8;
        if (c.allowsAlpha()) {
            float ax = nextX;
            Render2D.checker(ctx, ax, py, hw, ph, 2);
            int opaque = c.get() | 0xFF000000;
            Render2D.rectGradient(ctx, ax, py, hw, ph, opaque, opaque, opaque & 0xFFFFFF, opaque & 0xFFFFFF);
            Render2D.roundOutline(ctx, ax, py, hw, ph, 1, 1f, 0x30FFFFFF);
            float ay = py + (1 - c.getAlpha() / 255f) * ph;
            Render2D.roundRect(ctx, ax - 1.5f, ay - 1.5f, hw + 3, 3, 1.5f, 0xFFFFFFFF);
            gui.hit(ax - 2, py, hw + 4, ph, (button, mx, my) -> gui.startDrag(c, (dx, dy) ->
                    c.setHsv(c.getHue(), c.getSaturation(), c.getBrightness(), Math.round(255 * (1 - clamp((dy - py) / ph))))));
            nextX += hw + 8;
        }

        // presets
        float gx = nextX + 4;
        Fonts.draw(ctx, "PRESETS", gx, py, Theme.TEXT_MUTED, true, 0.62f);
        float dot = 9, gap = 4;
        int perRow = Math.max(1, (int) ((x + w - 10 - gx + gap) / (dot + gap)));
        for (int i = 0; i < Theme.PRESETS.length; i++) {
            int preset = Theme.PRESETS[i];
            float dx = gx + (i % perRow) * (dot + gap), dy = py + 9 + (i / perRow) * (dot + gap);
            boolean hov = gui.hovered(dx, dy, dot, dot);
            float hv = Anims.of(c, "preset" + i, hov);
            boolean sel = (c.get() & 0xFFFFFF) == (preset & 0xFFFFFF);
            float grow = hv * 1f;
            Render2D.circle(ctx, dx + dot / 2, dy + dot / 2, dot / 2 + grow * 0.5f, preset);
            if (sel) Render2D.ring(ctx, dx + dot / 2, dy + dot / 2, dot / 2 + 2f, 1f, 0xFFFFFFFF);
            gui.hit(dx, dy, dot, dot, (button, mx, my) -> {
                c.set(ColorUtil.withAlpha(preset, c.getAlpha()));
                Sounds.click();
            });
            if (hov) gui.tooltip(Theme.PRESET_NAMES[i]);
        }
        Fonts.draw(ctx, "Click to collapse • Right-click header to reset", gx, py + ph - 5, Theme.TEXT_MUTED, false, 0.6f);
    }

    private static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }
}
