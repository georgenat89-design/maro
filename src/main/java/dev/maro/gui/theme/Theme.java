package dev.maro.gui.theme;

import dev.maro.config.ClientSettings;
import dev.maro.util.ColorUtil;

/** Central palette. The accent is animated so preset changes fade smoothly. */
public final class Theme {
    public static final int BG = 0xFF000000;
    public static final int PANEL = 0xFF0B0B0F;
    public static final int CARD = 0xFF0F0F14;
    public static final int CARD_HOVER = 0xFF16161D;
    public static final int BORDER = 0xFF1E1E27;
    public static final int INPUT = 0xFF09090C;
    public static final int TEXT = 0xFFEDEDF5;
    public static final int TEXT_DIM = 0xFFA3A3B8;
    public static final int TEXT_MUTED = 0xFF63637A;
    public static final int TOGGLE_OFF = 0xFF272737;
    public static final int KNOB_OFF = 0xFF8E8EA6;
    public static final int RED = 0xFFE5484D;
    public static final int GREEN = 0xFF30C77B;
    public static final int YELLOW = 0xFFF5A524;

    /** Accent presets shown on the Theme page. */
    public static final int[] PRESETS = {
            0xFF8B5CF6, // violet
            0xFF6366F1, // indigo
            0xFF3B82F6, // blue
            0xFF06B6D4, // cyan
            0xFF14B8A6, // teal
            0xFF22C55E, // green
            0xFFF59E0B, // amber
            0xFFF97316, // orange
            0xFFF43F5E, // rose
            0xFFEC4899, // pink
    };
    public static final String[] PRESET_NAMES = {"Violet", "Indigo", "Blue", "Cyan", "Teal", "Green", "Amber", "Orange", "Rose", "Pink"};

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

    public static float radius() {
        return ClientSettings.radius.getFloat();
    }

    public static boolean glow() {
        return ClientSettings.glow.get();
    }
}
