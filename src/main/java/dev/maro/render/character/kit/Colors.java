// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Colors {
    public static final int WHITE = -1;
    public static final int BLACK = -16777216;
    public static final int CLEAR = 0;

    private Colors() {
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | Colors.clamp(r) << 16 | Colors.clamp(g) << 8 | Colors.clamp(b);
    }

    public static int argb(int a, int r, int g, int b) {
        return Colors.clamp(a) << 24 | Colors.clamp(r) << 16 | Colors.clamp(g) << 8 | Colors.clamp(b);
    }

    public static int hex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        long v = Long.parseLong(h, 16);
        return h.length() <= 6 ? (int)(0xFF000000L | v) : (int)v;
    }

    public static int hsv(float hueDegrees, float saturation, float value) {
        float b;
        float g;
        float r;
        float h = (hueDegrees % 360.0f + 360.0f) % 360.0f / 60.0f;
        float c = value * saturation;
        float x = c * (1.0f - Math.abs(h % 2.0f - 1.0f));
        if (h < 1.0f) {
            r = c;
            g = x;
            b = 0.0f;
        } else if (h < 2.0f) {
            r = x;
            g = c;
            b = 0.0f;
        } else if (h < 3.0f) {
            r = 0.0f;
            g = c;
            b = x;
        } else if (h < 4.0f) {
            r = 0.0f;
            g = x;
            b = c;
        } else if (h < 5.0f) {
            r = x;
            g = 0.0f;
            b = c;
        } else {
            r = c;
            g = 0.0f;
            b = x;
        }
        float m = value - c;
        return Colors.rgb(Math.round((r + m) * 255.0f), Math.round((g + m) * 255.0f), Math.round((b + m) * 255.0f));
    }

    public static int mix(int a, int b, float t) {
        float s = 1.0f - t;
        return Colors.argb(Math.round((float)Colors.alpha(a) * s + (float)Colors.alpha(b) * t), Math.round((float)Colors.red(a) * s + (float)Colors.red(b) * t), Math.round((float)Colors.green(a) * s + (float)Colors.green(b) * t), Math.round((float)Colors.blue(a) * s + (float)Colors.blue(b) * t));
    }

    public static int shade(int argb, float k) {
        if (k <= 1.0f) {
            return Colors.argb(Colors.alpha(argb), Math.round((float)Colors.red(argb) * k), Math.round((float)Colors.green(argb) * k), Math.round((float)Colors.blue(argb) * k));
        }
        float w = Math.min(1.0f, k - 1.0f);
        return Colors.argb(Colors.alpha(argb), Math.round((float)Colors.red(argb) + (float)(255 - Colors.red(argb)) * w), Math.round((float)Colors.green(argb) + (float)(255 - Colors.green(argb)) * w), Math.round((float)Colors.blue(argb) + (float)(255 - Colors.blue(argb)) * w));
    }

    public static int withAlpha(int argb, float alpha) {
        return Colors.clamp(Math.round(alpha * 255.0f)) << 24 | argb & 0xFFFFFF;
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    public static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
