package dev.maro.gui.theme;

import dev.maro.config.ClientSettings;
import dev.maro.util.ColorUtil;

/** Central palette. The accent is animated so preset changes fade smoothly. */
public final class Theme {
    public static final int BG = 0xFF0A0E16;
    public static final int PANEL = 0xFF0E131D;
    public static final int CARD = 0xFF121826;
    public static final int CARD_HOVER = 0xFF182033;
    public static final int BORDER = 0xFF1C2433;
    public static final int INPUT = 0xFF0B1019;
    public static final int TEXT = 0xFFE8ECF4;
    public static final int TEXT_DIM = 0xFF9AA3B5;
    public static final int TEXT_MUTED = 0xFF5D667A;
    public static final int TOGGLE_OFF = 0xFF252C3C;
    public static final int KNOB_OFF = 0xFF8C95A8;
    public static final int RED = 0xFFE5484D;
    public static final int GREEN = 0xFF30C77B;
    public static final int YELLOW = 0xFFF5A524;

    /** Accent presets shown on the Theme page. */
    public static final int[] PRESETS = {
            0xFF2F7BFF, // blue
            0xFF5B5CFF, // indigo
            0xFF9B5CFF, // purple
            0xFFE14BD0, // pink
            0xFFFF4D6D, // red
            0xFFFF7A2F, // orange
            0xFFF5B82E, // amber
            0xFF2FD07A, // green
            0xFF1FC8B0, // teal
            0xFF22B8F0, // cyan
    };
    public static final String[] PRESET_NAMES = {"Blue", "Indigo", "Purple", "Pink", "Red", "Orange", "Amber", "Green", "Teal", "Cyan"};

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
        return ColorUtil.withAlpha(BG, Math.round(255 * ClientSettings.opacity.getFloat() / 100f));
    }

    public static int panelBg() {
        return ColorUtil.withAlpha(PANEL, Math.round(255 * Math.min(1f, ClientSettings.opacity.getFloat() / 100f + 0.02f)));
    }

    public static float radius() {
        return ClientSettings.radius.getFloat();
    }

    public static boolean glow() {
        return ClientSettings.glow.get();
    }
}
