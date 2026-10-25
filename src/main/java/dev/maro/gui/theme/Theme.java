package dev.maro.gui.theme;

import dev.maro.config.ClientSettings;
import dev.maro.util.ColorUtil;

/** Central palette. The accent is animated so preset changes fade smoothly. */
public final class Theme {
    public static final int BG = 0xFF1B1F24;
    public static final int PANEL = 0xFF23272E;
    public static final int CARD = 0xFF252A31;
    public static final int CARD_HOVER = 0xFF2C323A;
    public static final int BORDER = 0xFF363C45;
    public static final int INPUT = 0xFF1B2026;
    public static final int TEXT = 0xFFE9EBF0;
    public static final int TEXT_DIM = 0xFFC5CBD5;
    public static final int TEXT_MUTED = 0xFF9DA7B6;
    public static final int TOGGLE_OFF = 0xFF1A2028;
    public static final int KNOB_OFF = 0xFF9AA6B6;
    public static final int DEFAULT_ACCENT = 0xFFB18AE8;
    public static final float MENU_HEADER_HEIGHT = 36f, MENU_NAV_HEIGHT = 36f;
    public static final float PANEL_RADIUS = 10f, CONTROL_RADIUS = 6f, ROW_RADIUS = 8f, SPACING = 12f;
    public static final float TEXT_BODY = .95f, TEXT_SMALL = .8f, TEXT_TITLE = 1.2f;
    public static final float MENU_TEXT_BASE = 1.3f;
    public static final int RED = 0xFFE5484D;
    public static final int GREEN = 0xFF30C77B;
    public static final int YELLOW = 0xFFF5A524;

    /** Accent presets shown on the Theme page. */
    public static final int[] PRESETS = {
            DEFAULT_ACCENT, // violet
            0xFF6366F1, // indigo
            0xFF3391FC, // Maro: the logo's blue
            0xFF06B6D4, // cyan
            0xFF14B8A6, // teal
            0xFF22C55E, // green
            0xFFF59E0B, // amber
            0xFFF97316, // orange
            0xFFF43F5E, // rose
            0xFFEC4899, // pink
    };
    public static final String[] PRESET_NAMES = {"Violet", "Indigo", "Maro", "Cyan", "Teal", "Green", "Amber", "Orange", "Rose", "Pink"};

    private static float r = -1, g, b;
    private static long last = System.nanoTime();

    private Theme() {
    }

    /** Advances the accent transition. Cheap; safe to call several times per frame. */
    public static void update() {
        int target = targetAccent();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        if (r < 0) {
            r = ColorUtil.red(target);
            g = ColorUtil.green(target);
            b = ColorUtil.blue(target);
            return;
        }
        float k = ClientSettings.rainbow.get() ? 1f : 1f - (float) Math.exp(-9f * dt);
        r += (ColorUtil.red(target) - r) * k;
        g += (ColorUtil.green(target) - g) * k;
        b += (ColorUtil.blue(target) - b) * k;
    }

    private static int targetAccent() {
        int base = ClientSettings.accent.get();
        if (!ClientSettings.rainbow.get()) return base;
        float period = ClientSettings.rainbowSpeed.getFloat() * 1000f;
        float hue = (System.currentTimeMillis() % (long) period) / period;
        float[] hsv = ColorUtil.toHsv(base);
        return ColorUtil.hsv(hue, Math.max(0.5f, hsv[1]), Math.max(0.6f, hsv[2]));
    }

    public static int accent() {
        if (r < 0) update();
        return ColorUtil.argb(255, Math.round(r), Math.round(g), Math.round(b));
    }

    public static int accent(int alpha) {
        return ColorUtil.withAlpha(accent(), alpha);
    }

    /** Category headings and icons follow the same live transition as the GUI controls. */
    public static int title(dev.maro.module.Category category) {
        return accent();
    }

    /** Second gradient stop. Equal to {@link #accent()} when gradients are off. */
    public static int accent2() {
        if (!ClientSettings.gradient.get()) return accent();
        return ColorUtil.shade(ColorUtil.hueShift(accent(), ClientSettings.gradientShift.getFloat()), -0.05f);
    }

    public static int accent2(int alpha) {
        return ColorUtil.withAlpha(accent2(), alpha);
    }

    public static int windowBg() {
        return ColorUtil.withAlpha(ClientSettings.background.get(), Math.round(255 * ClientSettings.opacity.getFloat() / 100f));
    }

    /** Slightly raised surface (top bar, inputs) derived from the window colour. */
    public static int panelBg() {
        int base = ColorUtil.shade(ClientSettings.background.get() | 0xFF000000, 0.045f);
        return ColorUtil.withAlpha(base, Math.round(255 * Math.min(1f, ClientSettings.opacity.getFloat() / 100f + 0.04f)));
    }

    public static int selected() { return ColorUtil.lerp(PANEL, accent(), .14f); }
    public static int controlHover() { return CARD_HOVER; }

    public static float radius() {
        return ClientSettings.radius.getFloat();
    }

    public static boolean glow() {
        return ClientSettings.glow.get();
    }
}
