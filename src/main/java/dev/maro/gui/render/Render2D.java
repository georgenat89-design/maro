package dev.maro.gui.render;

import dev.maro.mixin.DrawContextAccessor;
import dev.maro.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;

/**
 * Vector shape renderer. Everything is built from triangles with a one-physical-pixel
 * alpha fringe, which gives smooth anti-aliased edges at any GUI scale without shaders.
 * <p>
 * Coordinates are regular (scaled) GUI units and may be fractional. A global alpha
 * multiplier ({@link #setAlpha(float)}) lets whole screens fade in and out.
 * <p>
 * Each shape is submitted to the vanilla GUI render state as one {@link ShapeRenderState},
 * so it batches and layers correctly with vanilla text and textures.
 */
public final class Render2D {
    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final int MAX_SEG = 32;
    private static final float[] OUTER = new float[4 * 4 * (MAX_SEG + 1)];
    private static final float[] INNER = new float[4 * 4 * (MAX_SEG + 1)];

    private static float alpha = 1f;
    private static ScreenRect scissor;

    private Render2D() {
    }

    public static void setAlpha(float a) {
        alpha = Math.max(0f, Math.min(1f, a));
    }

    public static float getAlpha() {
        return alpha;
    }

    /** Scissor applied to shapes submitted from now on (null = none). Kept in sync with DrawContext's scissor by the GUI. */
    public static void setScissor(ScreenRect rect) {
        scissor = rect;
    }

    /** Size of one physical pixel in GUI units. */
    public static float px() {
        return (float) (1.0 / MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    private static int col(int c) {
        return alpha >= 1f ? c : ColorUtil.mulAlpha(c, alpha);
    }

    // ---- low level ----------------------------------------------------------------------

    private static ShapeRenderState.Builder begin(DrawContext ctx) {
        return new ShapeRenderState.Builder(ctx);
    }

    private static void end(ShapeRenderState.Builder b) {
        ShapeRenderState state = b.build(scissor);
        if (state != null) ((DrawContextAccessor) b.context()).maro$getState().addSimpleElement(state);
    }

    private static void tri(ShapeRenderState.Builder b, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        b.quad(x1, y1, c1, x2, y2, c2, x3, y3, c3, x3, y3, c3);
    }

    private static void quad(ShapeRenderState.Builder b,
                             float x1, float y1, int c1, float x2, float y2, int c2,
                             float x3, float y3, int c3, float x4, float y4, int c4) {
        b.quad(x1, y1, c1, x2, y2, c2, x3, y3, c3, x4, y4, c4);
    }

    private static int clear(int c) {
        return c & 0x00FFFFFF;
    }

    private static int segments(float r) {
        return Math.max(3, Math.min(MAX_SEG, (int) Math.ceil(Math.sqrt(r / px()) * 3.4)));
    }

    /** Writes a clockwise rounded-rect outline into {@code out} as (x, y, nx, ny) tuples. */
    private static int roundPath(float[] out, float x, float y, float w, float h, float r, int seg) {
        int i = 0;
        float[] cx = {x + r, x + w - r, x + w - r, x + r};
        float[] cy = {y + r, y + r, y + h - r, y + h - r};
        for (int c = 0; c < 4; c++) {
            float base = (float) Math.PI + c * HALF_PI;
            for (int s = 0; s <= seg; s++) {
                float a = base + HALF_PI * s / seg;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                out[i++] = cx[c] + cos * r;
                out[i++] = cy[c] + sin * r;
                out[i++] = cos;
                out[i++] = sin;
            }
        }
        return i / 4;
    }

    private static int bilerp(float px, float py, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        float u = w <= 0 ? 0 : (px - x) / w, v = h <= 0 ? 0 : (py - y) / h;
        return ColorUtil.lerp(ColorUtil.lerp(tl, tr, u), ColorUtil.lerp(bl, br, u), v);
    }

    // ---- rectangles ---------------------------------------------------------------------

    /** Plain hard-edged rectangle. */
    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        rectGradient(ctx, x, y, w, h, color, color, color, color);
    }

    /** Hard-edged rectangle with per-corner colours (top-left, top-right, bottom-right, bottom-left). */
    public static void rectGradient(DrawContext ctx, float x, float y, float w, float h, int tl, int tr, int br, int bl) {
        if (w <= 0 || h <= 0) return;
        ShapeRenderState.Builder b = begin(ctx);
        quad(b, x, y, col(tl), x + w, y, col(tr), x + w, y + h, col(br), x, y + h, col(bl));
        end(b);
    }

    public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        roundRect(ctx, x, y, w, h, r, color, color, color, color);
    }

    public static void roundGradientH(DrawContext ctx, float x, float y, float w, float h, float r, int left, int right) {
        roundRect(ctx, x, y, w, h, r, left, right, right, left);
    }

    public static void roundGradientV(DrawContext ctx, float x, float y, float w, float h, float r, int top, int bottom) {
        roundRect(ctx, x, y, w, h, r, top, top, bottom, bottom);
    }

    /** Anti-aliased rounded rectangle with per-corner colours (tl, tr, br, bl). */
    public static void roundRect(DrawContext ctx, float x, float y, float w, float h, float r, int tl, int tr, int br, int bl) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        boolean uniform = tl == tr && tr == br && br == bl;
        int n = roundPath(OUTER, x, y, w, h, r, segments(r));
        float f = px();
        float cx = x + w / 2f, cy = y + h / 2f;
        int cc = col(uniform ? tl : bilerp(cx, cy, x, y, w, h, tl, tr, br, bl));

        ShapeRenderState.Builder b = begin(ctx);
        for (int i = 0; i < n; i++) {
            int a = i * 4, o = ((i + 1) % n) * 4;
            float xi = OUTER[a], yi = OUTER[a + 1], xj = OUTER[o], yj = OUTER[o + 1];
            int ci = col(uniform ? tl : bilerp(xi, yi, x, y, w, h, tl, tr, br, bl));
            int cj = col(uniform ? tl : bilerp(xj, yj, x, y, w, h, tl, tr, br, bl));
            tri(b, cx, cy, cc, xi, yi, ci, xj, yj, cj);
            quad(b, xi, yi, ci, xj, yj, cj,
                    xj + OUTER[o + 2] * f, yj + OUTER[o + 3] * f, clear(cj),
                    xi + OUTER[a + 2] * f, yi + OUTER[a + 3] * f, clear(ci));
        }
        end(b);
    }

    public static void roundOutline(DrawContext ctx, float x, float y, float w, float h, float r, float thickness, int color) {
        roundOutline(ctx, x, y, w, h, r, thickness, color, color, color, color);
    }

    /** Anti-aliased rounded border drawn inside the given bounds. */
    public static void roundOutline(DrawContext ctx, float x, float y, float w, float h, float r, float t,
                                    int tl, int tr, int br, int bl) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        t = Math.min(t, Math.min(w, h) / 2f);
        int seg = segments(r);
        int n = roundPath(OUTER, x, y, w, h, r, seg);
        roundPath(INNER, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0f, r - t), seg);
        boolean uniform = tl == tr && tr == br && br == bl;
        float f = px();

        ShapeRenderState.Builder b = begin(ctx);
        for (int i = 0; i < n; i++) {
            int a = i * 4, o = ((i + 1) % n) * 4;
            float oxi = OUTER[a], oyi = OUTER[a + 1], oxj = OUTER[o], oyj = OUTER[o + 1];
            float ixi = INNER[a], iyi = INNER[a + 1], ixj = INNER[o], iyj = INNER[o + 1];
            int ci = col(uniform ? tl : bilerp(oxi, oyi, x, y, w, h, tl, tr, br, bl));
            int cj = col(uniform ? tl : bilerp(oxj, oyj, x, y, w, h, tl, tr, br, bl));
            float nxi = OUTER[a + 2], nyi = OUTER[a + 3], nxj = OUTER[o + 2], nyj = OUTER[o + 3];
            quad(b, oxi, oyi, ci, oxj, oyj, cj, ixj, iyj, cj, ixi, iyi, ci);
            quad(b, oxi, oyi, ci, oxj, oyj, cj, oxj + nxj * f, oyj + nyj * f, clear(cj), oxi + nxi * f, oyi + nyi * f, clear(ci));
            quad(b, ixi, iyi, ci, ixj, iyj, cj, ixj - nxj * f, iyj - nyj * f, clear(cj), ixi - nxi * f, iyi - nyi * f, clear(ci));
        }
        end(b);
    }

    /** Soft drop shadow / glow around a rounded rectangle. Draws only outside the shape. */
    public static void shadow(DrawContext ctx, float x, float y, float w, float h, float r, float size, int color) {
        if (w <= 0 || h <= 0 || size <= 0 || ColorUtil.alpha(color) == 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        int n = roundPath(OUTER, x, y, w, h, r, Math.max(segments(r + size), 6));
        int c0 = col(color);
        int c1 = ColorUtil.mulAlpha(c0, 0.35f);
        float mid = size * 0.35f;

        ShapeRenderState.Builder b = begin(ctx);
        for (int i = 0; i < n; i++) {
            int a = i * 4, o = ((i + 1) % n) * 4;
            float xi = OUTER[a], yi = OUTER[a + 1], xj = OUTER[o], yj = OUTER[o + 1];
            float nxi = OUTER[a + 2], nyi = OUTER[a + 3], nxj = OUTER[o + 2], nyj = OUTER[o + 3];
            quad(b, xi, yi, c0, xj, yj, c0, xj + nxj * mid, yj + nyj * mid, c1, xi + nxi * mid, yi + nyi * mid, c1);
            quad(b, xi + nxi * mid, yi + nyi * mid, c1, xj + nxj * mid, yj + nyj * mid, c1,
                    xj + nxj * size, yj + nyj * size, clear(c1), xi + nxi * size, yi + nyi * size, clear(c1));
        }
        end(b);
    }

    // ---- circles, arcs, lines -----------------------------------------------------------

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        roundRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    public static void ring(DrawContext ctx, float cx, float cy, float radius, float thickness, int color) {
        arc(ctx, cx, cy, radius, thickness, 0, 360, color, color);
    }

    /**
     * Anti-aliased arc band. Angles are degrees, 0 = right, increasing clockwise (screen space).
     * The colour fades from {@code c1} at the start to {@code c2} at the end.
     */
    public static void arc(DrawContext ctx, float cx, float cy, float radius, float thickness, float startDeg, float sweepDeg, int c1, int c2) {
        if (radius <= 0 || thickness <= 0 || sweepDeg == 0) return;
        float rOut = radius, rIn = Math.max(0f, radius - thickness);
        float f = px();
        int seg = Math.max(12, (int) Math.ceil(Math.abs(sweepDeg) / 360f * Math.max(32, radius / f * 2f)));
        seg = Math.min(seg, 256);
        float start = (float) Math.toRadians(startDeg), sweep = (float) Math.toRadians(sweepDeg);

        ShapeRenderState.Builder b = begin(ctx);
        for (int s = 0; s < seg; s++) {
            float t0 = (float) s / seg, t1 = (float) (s + 1) / seg;
            float a0 = start + sweep * t0, a1 = start + sweep * t1;
            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);
            int k0 = col(ColorUtil.lerp(c1, c2, t0)), k1 = col(ColorUtil.lerp(c1, c2, t1));
            quad(b, cx + cos0 * rIn, cy + sin0 * rIn, k0, cx + cos0 * rOut, cy + sin0 * rOut, k0,
                    cx + cos1 * rOut, cy + sin1 * rOut, k1, cx + cos1 * rIn, cy + sin1 * rIn, k1);
            quad(b, cx + cos0 * rOut, cy + sin0 * rOut, k0, cx + cos1 * rOut, cy + sin1 * rOut, k1,
                    cx + cos1 * (rOut + f), cy + sin1 * (rOut + f), clear(k1), cx + cos0 * (rOut + f), cy + sin0 * (rOut + f), clear(k0));
            if (rIn > 0) {
                float ri = Math.max(0f, rIn - f);
                quad(b, cx + cos0 * rIn, cy + sin0 * rIn, k0, cx + cos1 * rIn, cy + sin1 * rIn, k1,
                        cx + cos1 * ri, cy + sin1 * ri, clear(k1), cx + cos0 * ri, cy + sin0 * ri, clear(k0));
            }
        }
        end(b);
    }

    /** Anti-aliased line with round caps. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f || thickness <= 0) return;
        float nx = -dy / len, ny = dx / len, h = thickness / 2f, f = px();
        int c = col(color), z = clear(c);

        ShapeRenderState.Builder b = begin(ctx);
        quad(b, x1 + nx * h, y1 + ny * h, c, x2 + nx * h, y2 + ny * h, c, x2 - nx * h, y2 - ny * h, c, x1 - nx * h, y1 - ny * h, c);
        quad(b, x1 + nx * h, y1 + ny * h, c, x2 + nx * h, y2 + ny * h, c, x2 + nx * (h + f), y2 + ny * (h + f), z, x1 + nx * (h + f), y1 + ny * (h + f), z);
        quad(b, x1 - nx * h, y1 - ny * h, c, x2 - nx * h, y2 - ny * h, c, x2 - nx * (h + f), y2 - ny * (h + f), z, x1 - nx * (h + f), y1 - ny * (h + f), z);
        float base = (float) Math.atan2(ny, nx);
        cap(b, x1, y1, h, f, base, c, z);
        cap(b, x2, y2, h, f, base + (float) Math.PI, c, z);
        end(b);
    }

    private static void cap(ShapeRenderState.Builder b, float x, float y, float r, float f, float start, int c, int z) {
        int seg = 8;
        for (int s = 0; s < seg; s++) {
            float a0 = start + (float) Math.PI * s / seg, a1 = start + (float) Math.PI * (s + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            tri(b, x, y, c, x + c0 * r, y + s0 * r, c, x + c1 * r, y + s1 * r, c);
            quad(b, x + c0 * r, y + s0 * r, c, x + c1 * r, y + s1 * r, c, x + c1 * (r + f), y + s1 * (r + f), z, x + c0 * (r + f), y + s0 * (r + f), z);
        }
    }

    /** Checkerboard used behind translucent colour previews. */
    public static void checker(DrawContext ctx, float x, float y, float w, float h, float cell) {
        rect(ctx, x, y, w, h, 0xFFCFCFCF);
        int cols = (int) Math.ceil(w / cell), rows = (int) Math.ceil(h / cell);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                if (((i + j) & 1) == 0) continue;
                float cx = x + i * cell, cy = y + j * cell;
                rect(ctx, cx, cy, Math.min(cell, x + w - cx), Math.min(cell, y + h - cy), 0xFF9A9A9A);
            }
        }
    }
}
