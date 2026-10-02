package dev.maro.util;

public final class Easing {
    private Easing() {
    }

    public static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    public static float outCubic(float t) {
        t = clamp01(t);
        float f = 1 - t;
        return 1 - f * f * f;
    }

    public static float outQuint(float t) {
        t = clamp01(t);
        float f = 1 - t;
        return 1 - f * f * f * f * f;
    }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
