package dev.maro.gui.render;

import dev.maro.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/** Vector icons drawn with {@link Render2D}; crisp at every GUI scale, no textures needed. */
public final class Icons {
    private Icons() {
    }

    @FunctionalInterface
    public interface Icon {
        /**
         * @param size  nominal icon size in GUI units (roughly the bounding box)
         * @param hover 0..1 hover/selection progress, used for small micro-animations
         */
        void draw(DrawContext ctx, float cx, float cy, float size, int color, float hover);
    }

    private static float stroke(float size) {
        return Math.max(1f, size * 0.11f);
    }

    private static void rotated(DrawContext ctx, float cx, float cy, float degrees, Runnable draw) {
        Matrix3x2fStack ms = ctx.getMatrices();
        ms.pushMatrix();
        ms.translate(cx, cy);
        ms.rotate((float) Math.toRadians(degrees));
        ms.translate(-cx, -cy);
        draw.run();
        ms.popMatrix();
    }

    public static final Icon COMBAT = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, h * 45f, () -> {
        float t = stroke(s);
        Render2D.ring(ctx, cx, cy, s * 0.36f, t, c);
        float in = s * 0.16f, out = s * 0.52f;
        Render2D.line(ctx, cx, cy - in, cx, cy - out, t, c);
        Render2D.line(ctx, cx, cy + in, cx, cy + out, t, c);
        Render2D.line(ctx, cx - in, cy, cx - out, cy, t, c);
        Render2D.line(ctx, cx + in, cy, cx + out, cy, t, c);
        Render2D.circle(ctx, cx, cy, t * 0.7f, c);
    });

    public static final Icon MOVEMENT = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), o = h * s * 0.08f;
        float w = s * 0.22f, hh = s * 0.32f;
        for (int i = 0; i < 2; i++) {
            float x = cx - s * 0.22f + i * s * 0.34f + o;
            int col = i == 0 ? ColorUtil.mulAlpha(c, 0.55f) : c;
            Render2D.line(ctx, x - w / 2, cy - hh, x + w / 2, cy, t, col);
            Render2D.line(ctx, x + w / 2, cy, x - w / 2, cy + hh, t, col);
        }
    };

    public static final Icon PLAYER = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        Render2D.ring(ctx, cx, cy - s * 0.2f, s * 0.21f, t, c);
        Render2D.arc(ctx, cx, cy + s * 0.48f, s * 0.38f, t, 180, 180, c, c);
    };

    public static final Icon VISUALS = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        Render2D.roundOutline(ctx, cx - s * 0.5f, cy - s * 0.3f, s, s * 0.6f, s * 0.3f, t, c);
        Render2D.circle(ctx, cx, cy, s * (0.12f + 0.05f * h), c);
    };

    public static final Icon MISC = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), b = s * 0.4f, g = s * (0.08f + 0.04f * h);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? cx - g / 2 - b : cx + g / 2;
            float y = i < 2 ? cy - g / 2 - b : cy + g / 2;
            Render2D.roundOutline(ctx, x, y, b, b, b * 0.3f, t, c);
        }
    };

    public static final Icon SETTINGS = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, h * 60f, () -> {
        float t = stroke(s);
        Render2D.ring(ctx, cx, cy, s * 0.36f, t, c);
        Render2D.ring(ctx, cx, cy, s * 0.14f, t, c);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            Render2D.line(ctx, cx + cos * s * 0.38f, cy + sin * s * 0.38f, cx + cos * s * 0.5f, cy + sin * s * 0.5f, t * 1.5f, c);
        }
    });

    public static final Icon CONFIGS = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), w = s * 0.72f;
        Render2D.roundOutline(ctx, cx - w / 2, cy - s * 0.5f, w, s, s * 0.14f, t, c);
        for (int i = 0; i < 3; i++) {
            float y = cy - s * 0.2f + i * s * 0.2f;
            float len = i == 2 ? w * 0.3f : w * (0.42f + 0.1f * h);
            Render2D.line(ctx, cx - w * 0.24f, y, cx - w * 0.24f + len, y, t, c);
        }
    };

    public static final Icon THEME = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        Render2D.ring(ctx, cx, cy, s * 0.5f, t, c);
        float r = s * 0.1f;
        int[] dots = {0xFFFF5C7A, 0xFF3DDC97, 0xFF4C8DFF};
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(-150 + i * 60 + h * 120);
            int dc = ColorUtil.lerp(c, ColorUtil.withAlpha(dots[i], ColorUtil.alpha(c)), h);
            Render2D.circle(ctx, cx + (float) Math.cos(a) * s * 0.25f, cy + (float) Math.sin(a) * s * 0.25f, r, dc);
        }
        Render2D.circle(ctx, cx + s * 0.12f, cy + s * 0.22f, r * 0.9f, c);
    };

    public static final Icon SOCIALS = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        int back = ColorUtil.mulAlpha(c, 0.6f);
        float bx = cx + s * 0.24f + h * s * 0.05f;
        Render2D.ring(ctx, bx, cy - s * 0.22f, s * 0.15f, t, back);
        Render2D.arc(ctx, bx, cy + s * 0.42f, s * 0.28f, t, 200, 140, back, back);
        Render2D.ring(ctx, cx - s * 0.12f, cy - s * 0.18f, s * 0.19f, t, c);
        Render2D.arc(ctx, cx - s * 0.12f, cy + s * 0.5f, s * 0.36f, t, 180, 180, c, c);
    };

    public static final Icon SEARCH = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        float r = s * 0.32f, ox = cx - s * 0.1f, oy = cy - s * 0.1f;
        Render2D.ring(ctx, ox, oy, r, t, c);
        Render2D.line(ctx, ox + r * 0.8f, oy + r * 0.8f, cx + s * 0.45f, cy + s * 0.45f, t * 1.2f, c);
    };

    public static final Icon BACK = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s) * 1.2f, o = -h * s * 0.1f;
        Render2D.line(ctx, cx + s * 0.15f + o, cy - s * 0.35f, cx - s * 0.2f + o, cy, t, c);
        Render2D.line(ctx, cx - s * 0.2f + o, cy, cx + s * 0.15f + o, cy + s * 0.35f, t, c);
    };

    public static final Icon CHEVRON_RIGHT = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s) * 1.2f, o = h * s * 0.1f;
        Render2D.line(ctx, cx - s * 0.15f + o, cy - s * 0.35f, cx + s * 0.2f + o, cy, t, c);
        Render2D.line(ctx, cx + s * 0.2f + o, cy, cx - s * 0.15f + o, cy + s * 0.35f, t, c);
    };

    public static final Icon PLUS = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, h * 90f, () -> {
        float t = stroke(s) * 1.2f;
        Render2D.line(ctx, cx - s * 0.38f, cy, cx + s * 0.38f, cy, t, c);
        Render2D.line(ctx, cx, cy - s * 0.38f, cx, cy + s * 0.38f, t, c);
    });

    public static final Icon CLOSE = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, 45f + h * 90f, () -> {
        float t = stroke(s) * 1.2f;
        Render2D.line(ctx, cx - s * 0.4f, cy, cx + s * 0.4f, cy, t, c);
        Render2D.line(ctx, cx, cy - s * 0.4f, cx, cy + s * 0.4f, t, c);
    });

    public static final Icon CHECK = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s) * 1.3f;
        Render2D.line(ctx, cx - s * 0.38f, cy, cx - s * 0.1f, cy + s * 0.3f, t, c);
        Render2D.line(ctx, cx - s * 0.1f, cy + s * 0.3f, cx + s * 0.4f, cy - s * 0.3f, t, c);
    };

    public static final Icon DOTS = (ctx, cx, cy, s, c, h) -> {
        float r = Math.max(0.8f, s * 0.1f), g = s * (0.3f + 0.05f * h);
        for (int i = -1; i <= 1; i++) Render2D.circle(ctx, cx + i * g, cy, r, c);
    };

    public static final Icon FOLDER = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        float w = s, hh = s * 0.72f, x = cx - w / 2, y = cy - hh / 2 + s * 0.05f;
        Render2D.roundRect(ctx, x, y - s * 0.12f, w * 0.42f, s * 0.2f, s * 0.08f, c);
        Render2D.roundOutline(ctx, x, y, w, hh, s * 0.12f, t, c);
    };

    public static final Icon REFRESH = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, h * 180f, () -> {
        float t = stroke(s);
        Render2D.arc(ctx, cx, cy, s * 0.42f, t, -60, 300, c, c);
        float ax = cx + (float) Math.cos(Math.toRadians(-60)) * s * 0.36f, ay = cy + (float) Math.sin(Math.toRadians(-60)) * s * 0.36f;
        Render2D.line(ctx, ax, ay, ax + s * 0.02f, ay - s * 0.26f, t, c);
        Render2D.line(ctx, ax, ay, ax - s * 0.24f, ay - s * 0.06f, t, c);
    });

    public static final Icon INFO = (ctx, cx, cy, s, c, h) -> {
        Render2D.circle(ctx, cx, cy, s * 0.5f, c);
        int hole = 0xFF16151E;
        Render2D.roundRect(ctx, cx - s * 0.07f, cy - s * 0.08f, s * 0.14f, s * 0.36f, s * 0.07f, hole);
        Render2D.circle(ctx, cx, cy - s * 0.24f, s * 0.08f, hole);
    };

    // ---- setting category icons -----------------------------------------------------------

    /** A frame with corner marks: placement, layout, size. */
    public static final Icon LAYOUT = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), b = s * (0.46f + 0.04f * h), k = s * 0.22f;
        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -1 : 1, sy = i < 2 ? -1 : 1;
            float px = cx + sx * b, py = cy + sy * b;
            Render2D.line(ctx, px, py, px - sx * k, py, t, c);
            Render2D.line(ctx, px, py, px, py - sy * k, t, c);
        }
        Render2D.roundRect(ctx, cx - s * 0.16f, cy - s * 0.16f, s * 0.32f, s * 0.32f, s * 0.06f, c);
    };

    /** A play mark with motion lines: animation. */
    public static final Icon PLAY = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), o = h * s * 0.06f;
        float lx = cx - s * 0.12f + o, top = cy - s * 0.34f, bot = cy + s * 0.34f, tip = cx + s * 0.34f + o;
        Render2D.line(ctx, lx, top, lx, bot, t, c);
        Render2D.line(ctx, lx, top, tip, cy, t, c);
        Render2D.line(ctx, lx, bot, tip, cy, t, c);
        int faint = ColorUtil.mulAlpha(c, 0.55f);
        Render2D.line(ctx, cx - s * 0.5f, cy - s * 0.16f, cx - s * 0.3f, cy - s * 0.16f, t, faint);
        Render2D.line(ctx, cx - s * 0.5f, cy + s * 0.16f, cx - s * 0.3f, cy + s * 0.16f, t, faint);
    };

    /** A clock face: timing and delays. */
    public static final Icon CLOCK = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s);
        Render2D.ring(ctx, cx, cy, s * 0.46f, t, c);
        double a = Math.toRadians(-90 + h * 90);
        Render2D.line(ctx, cx, cy, cx + (float) Math.cos(a) * s * 0.28f, cy + (float) Math.sin(a) * s * 0.28f, t, c);
        Render2D.line(ctx, cx, cy, cx + s * 0.2f, cy, t, c);
    };

    /** A map pin: markers, regions, places. */
    public static final Icon PIN = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), o = -h * s * 0.06f;
        float r = s * 0.28f, py = cy - s * 0.12f + o;
        Render2D.arc(ctx, cx, py, r, t, 150, 240, c, c);
        Render2D.line(ctx, cx - r * 0.87f, py + r * 0.5f, cx, cy + s * 0.48f + o, t, c);
        Render2D.line(ctx, cx + r * 0.87f, py + r * 0.5f, cx, cy + s * 0.48f + o, t, c);
        Render2D.circle(ctx, cx, py, s * 0.09f, c);
    };

    /** A key cap: keybinds and controls. */
    public static final Icon KEY = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), w = s * 0.92f, hh = s * 0.68f, o = h * s * 0.05f;
        Render2D.roundOutline(ctx, cx - w / 2, cy - hh / 2 + o, w, hh, s * 0.14f, t, c);
        Render2D.line(ctx, cx - w * 0.22f, cy + s * 0.1f + o, cx + w * 0.22f, cy + s * 0.1f + o, t, c);
    };

    /** A shield: stops, safety and alarms. */
    public static final Icon SHIELD = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s), w = s * 0.4f, top = cy - s * 0.46f, mid = cy + s * 0.08f, bot = cy + s * 0.5f;
        Render2D.line(ctx, cx - w, top + s * 0.1f, cx, top, t, c);
        Render2D.line(ctx, cx, top, cx + w, top + s * 0.1f, t, c);
        Render2D.line(ctx, cx - w, top + s * 0.1f, cx - w, mid, t, c);
        Render2D.line(ctx, cx + w, top + s * 0.1f, cx + w, mid, t, c);
        Render2D.line(ctx, cx - w, mid, cx, bot, t, c);
        Render2D.line(ctx, cx + w, mid, cx, bot, t, c);
        if (h > 0.01f) Render2D.circle(ctx, cx, cy, s * 0.1f * h, c);
    };

    /** A lightning bolt: behaviour, actions and what it does. */
    public static final Icon BOLT = (ctx, cx, cy, s, c, h) -> {
        float t = stroke(s) * 1.1f, o = h * s * 0.04f;
        Render2D.line(ctx, cx + s * 0.12f + o, cy - s * 0.5f, cx - s * 0.22f, cy + s * 0.04f, t, c);
        Render2D.line(ctx, cx - s * 0.22f, cy + s * 0.04f, cx + s * 0.22f, cy - s * 0.04f, t, c);
        Render2D.line(ctx, cx + s * 0.22f, cy - s * 0.04f, cx - s * 0.12f - o, cy + s * 0.5f, t, c);
    };

    /** Small red diamond used to flag experimental modules. */
    public static final Icon WARNING = (ctx, cx, cy, s, c, h) -> rotated(ctx, cx, cy, 45f, () -> {
        float b = s * 0.66f;
        Render2D.roundRect(ctx, cx - b / 2, cy - b / 2, b, b, s * 0.1f, c);
        Render2D.circle(ctx, cx, cy, s * 0.11f, 0xFFFFFFFF);
    });
}
