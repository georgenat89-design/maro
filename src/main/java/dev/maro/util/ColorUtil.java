package dev.maro.util;

/**
 * Small ARGB colour helpers. All colours are packed {@code 0xAARRGGBB} ints.
 */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static int alpha(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int c, int a) {
        return (clamp(a) << 24) | (c & 0xFFFFFF);
    }

    /** Multiplies the existing alpha channel by {@code f}. */
    public static int mulAlpha(int c, float f) {
        return withAlpha(c, Math.round(alpha(c) * Math.max(0f, Math.min(1f, f))));
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
                Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
                Math.round(red(a) + (red(b) - red(a)) * t),
                Math.round(green(a) + (green(b) - green(a)) * t),
                Math.round(blue(a) + (blue(b) - blue(a)) * t));
    }

    /** Moves the colour towards white ({@code amount > 0}) or black ({@code amount < 0}). */
    public static int shade(int c, float amount) {
        return amount >= 0 ? withAlpha(lerp(c, 0xFFFFFFFF, amount), alpha(c)) : withAlpha(lerp(c, 0xFF000000, -amount), alpha(c));
    }

    /** h, s, v in [0, 1]; returns an opaque colour. */
    public static int hsv(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float r, g, b;
        int i = (int) (h * 6f);
        float f = h * 6f - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return argb(255, Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    /** @return {h, s, v} in [0, 1] */
    public static float[] toHsv(int c) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h = 0f;
        if (d != 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Rotates the hue of a colour by {@code amount} (0..1). */
    public static int hueShift(int c, float amount) {
        float[] hsv = toHsv(c);
        return withAlpha(hsv(hsv[0] + amount, hsv[1], hsv[2]), alpha(c));
    }

    public static String toHex(int c, boolean withAlpha) {
        return withAlpha ? String.format("#%08X", c) : String.format("#%06X", c & 0xFFFFFF);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
