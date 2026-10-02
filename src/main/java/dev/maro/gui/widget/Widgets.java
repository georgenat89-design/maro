package dev.maro.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Icons;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.setting.KeybindSetting;
import dev.maro.util.ColorUtil;
import dev.maro.util.Sounds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.SkinTextures;

/** Reusable immediate-mode controls. */
public final class Widgets {
    public enum Style {PRIMARY, SECONDARY, DANGER, GHOST}

    private Widgets() {
    }

    /** iOS-style switch. {@code t} is the animated on-progress (0..1). */
    public static void toggle(DrawContext ctx, float x, float y, float w, float h, float t, float hover) {
        int offL = ColorUtil.lerp(Theme.TOGGLE_OFF, 0xFF2D3548, hover);
        if (t > 0.01f && Theme.glow()) Render2D.shadow(ctx, x, y, w, h, h / 2f, 4f, Theme.accent(Math.round(0x55 * t)));
        Render2D.roundGradientH(ctx, x, y, w, h, h / 2f, ColorUtil.lerp(offL, Theme.accent(), t), ColorUtil.lerp(offL, Theme.accent2(), t));
        float r = h / 2f - 1.6f + hover * 0.3f;
        float kx = x + h / 2f + (w - h) * t;
        Render2D.shadow(ctx, kx - r, y + h / 2f - r, r * 2, r * 2, r, 1.5f, 0x40000000);
        Render2D.circle(ctx, kx, y + h / 2f, r, ColorUtil.lerp(Theme.KNOB_OFF, 0xFFFFFFFF, t));
    }

    public static float buttonWidth(String label, Icons.Icon icon) {
        return Fonts.width(label, false, 0.85f) + (icon != null ? 26 : 16);
    }

    /** Draws a button and registers its click. Returns the button width. */
    public static float button(ClickGuiScreen gui, DrawContext ctx, Object id, float x, float y, float w, float h,
                               String label, Style style, Icons.Icon icon, Runnable action) {
        if (w <= 0) w = buttonWidth(label, icon);
        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(id, "btn", hovered);
        float press = Anims.of(id, "press", hovered && gui.isMouseDown() ? 1 : 0, 30f);
        float r = Math.min(h / 2f, Theme.radius());
        float shrink = press * 0.8f;
        float bx = x + shrink, by = y + shrink, bw = w - shrink * 2, bh = h - shrink * 2;
        int fg;
        switch (style) {
            case PRIMARY -> {
                if (Theme.glow()) Render2D.shadow(ctx, bx, by, bw, bh, r, 3f + hv * 3f, Theme.accent(0x30 + Math.round(0x30 * hv)));
                Render2D.roundGradientH(ctx, bx, by, bw, bh, r, ColorUtil.shade(Theme.accent(), hv * 0.12f), ColorUtil.shade(Theme.accent2(), hv * 0.12f));
                fg = 0xFFFFFFFF;
            }
            case DANGER -> {
                Render2D.roundRect(ctx, bx, by, bw, bh, r, ColorUtil.withAlpha(Theme.RED, 0x22 + Math.round(0x40 * hv)));
                Render2D.roundOutline(ctx, bx, by, bw, bh, r, 1f, ColorUtil.withAlpha(Theme.RED, 0x50 + Math.round(0x50 * hv)));
                fg = ColorUtil.lerp(0xFFFF8A8E, 0xFFFFFFFF, hv);
            }
            case GHOST -> {
                Render2D.roundRect(ctx, bx, by, bw, bh, r, ColorUtil.withAlpha(0xFF1A2232, Math.round(0xFF * hv)));
                fg = ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
            }
            default -> {
                Render2D.roundRect(ctx, bx, by, bw, bh, r, ColorUtil.lerp(0xFF161D2B, 0xFF1D2639, hv));
                Render2D.roundOutline(ctx, bx, by, bw, bh, r, 1f, ColorUtil.lerp(Theme.BORDER, 0xFF2C3750, hv));
                fg = ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv);
            }
        }
        float cy = y + h / 2f;
        if (label.isEmpty() && icon != null) {
            icon.draw(ctx, x + w / 2f, cy, Math.min(9f, h * 0.5f), fg, hv);
        } else {
            float tw = Fonts.width(label, false, 0.85f);
            float total = tw + (icon != null ? 11 : 0);
            float sx = x + (w - total) / 2f;
            if (icon != null) {
                icon.draw(ctx, sx + 3.5f, cy, 7.5f, fg, hv);
                sx += 11;
            }
            Fonts.drawV(ctx, label, sx, cy, fg, false, 0.85f);
        }
        gui.hit(x, y, w, h, (button, mx, my) -> {
            if (button != 0) return;
            Sounds.click();
            action.run();
        });
        return w;
    }

    /** Key chip ("RShift", "None", "...") that starts listening for a new bind when clicked. Returns width. */
    public static float bindChip(ClickGuiScreen gui, DrawContext ctx, KeybindSetting bind, float x, float cy, float scale) {
        boolean listening = gui.listening == bind;
        String label = listening ? "Press a key" : bind.getKeyName();
        float h = 11f * scale;
        float w = Fonts.width(label, false, 0.72f) + 9;
        float y = cy - h / 2f;
        boolean hovered = gui.hovered(x, y, w, h);
        float hv = Anims.of(bind, "chip", hovered);
        float ls = Anims.of(bind, "listen", listening);
        float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0) : 0f;

        Render2D.roundRect(ctx, x, y, w, h, 3f, ColorUtil.lerp(ColorUtil.lerp(0xFF0D121C, 0xFF1A2232, hv), Theme.accent(0x30), ls));
        Render2D.roundOutline(ctx, x, y, w, h, 3f, 1f, ColorUtil.lerp(ColorUtil.lerp(Theme.BORDER, 0xFF2C3750, hv), Theme.accent(0x80 + Math.round(0x7F * pulse)), ls));
        int fg = listening ? Theme.accent() : bind.isBound() ? ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv) : Theme.TEXT_MUTED;
        Fonts.drawCentered(ctx, label, x + w / 2f, cy, fg, false, 0.72f);

        gui.hit(x, y, w, h, (button, mx, my) -> {
            Sounds.click();
            if (button == 1) {
                bind.reset();
                gui.listening = null;
            } else {
                gui.listening = listening ? null : bind;
            }
        });
        if (hovered && !listening) gui.tooltip("Click to rebind • Right-click to reset • Del to unbind");
        return w;
    }

    /** Small "i" icon with a description tooltip. */
    public static void info(ClickGuiScreen gui, DrawContext ctx, Object id, float cx, float cy, String text) {
        boolean hovered = gui.hovered(cx - 5, cy - 5, 10, 10);
        float hv = Anims.of(id, "info", hovered);
        Icons.INFO.draw(ctx, cx, cy, 7.5f + hv * 0.6f, ColorUtil.lerp(Theme.TEXT_MUTED, Theme.TEXT, hv), hv);
        if (hovered && text != null && !text.isEmpty()) gui.tooltip(text);
        gui.hit(cx - 5, cy - 5, 10, 10, (button, mx, my) -> {
            // swallow clicks so the info icon doesn't trigger the card underneath
        });
    }

    public static void sectionLabel(DrawContext ctx, String text, float x, float y, float w) {
        Fonts.draw(ctx, text.toUpperCase(), x, y, Theme.TEXT_MUTED, true, 0.68f);
        float tw = Fonts.width(text.toUpperCase(), true, 0.68f);
        Render2D.rectGradient(ctx, x + tw + 6, y + 2.2f, Math.max(0, w - tw - 6), Render2D.px(),
                ColorUtil.withAlpha(Theme.BORDER, 0xFF), ColorUtil.withAlpha(Theme.BORDER, 0), ColorUtil.withAlpha(Theme.BORDER, 0), ColorUtil.withAlpha(Theme.BORDER, 0xFF));
    }

    /** Player face (with hat layer). Respects the global fade alpha. */
    public static void head(DrawContext ctx, SkinTextures skin, float x, float y, int size) {
        if (skin == null) return;
        ctx.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, Render2D.getAlpha());
        PlayerSkinDrawer.draw(ctx, skin, Math.round(x), Math.round(y), size);
        ctx.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Coloured circle with the first letter of a name, used when no skin is available. */
    public static void avatar(DrawContext ctx, String name, float x, float y, float size) {
        float hue = (name.toLowerCase().hashCode() & 0xFFFF) / 65535f;
        int c1 = ColorUtil.hsv(hue, 0.55f, 0.85f), c2 = ColorUtil.hsv(hue + 0.08f, 0.6f, 0.65f);
        Render2D.roundGradientV(ctx, x, y, size, size, size * 0.3f, c1, c2);
        Fonts.drawCentered(ctx, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), x + size / 2f, y + size / 2f, 0xFFFFFFFF, true, size / 14f);
    }
}
