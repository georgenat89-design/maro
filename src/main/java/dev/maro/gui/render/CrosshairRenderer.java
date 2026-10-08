package dev.maro.gui.render;

import net.minecraft.client.gui.DrawContext;

/** Small vector presets: no texture scaling or font symbols. */
public final class CrosshairRenderer {
    public static final java.util.List<String> PRESETS = java.util.List.of(
        "Dot", "Square Dot", "Plus", "Cross", "Cross + Dot", "T Cross", "X", "X + Dot",
        "Circle", "Circle + Dot", "Ring Cross", "Double Ring", "Square", "Square + Dot",
        "Diamond", "Diamond + Dot", "Chevron", "Double Chevron", "Triangle", "Brackets",
        "Corner Brackets", "Star", "Star of David", "Reticle", "Four Dots");

    private CrosshairRenderer() { }

    public static void draw(DrawContext ctx, String style, float size, float gap, float thickness,
                            float dot, boolean centerDot, int color, float outline, int outlineColor) {
        if (outline > 0) shape(ctx, style, size, gap, thickness, dot, centerDot, outline, outlineColor);
        shape(ctx, style, size, gap, thickness, dot, centerDot, 0, color);
    }

    private static void shape(DrawContext ctx, String style, float s, float g, float t, float dot,
                              boolean centerDot, float edge, int color) {
        float r = g + s;
        switch (style) {
            case "Dot" -> Render2D.circle(ctx, 0, 0, dot + edge, color);
            case "Square Dot" -> Render2D.roundRect(ctx, -dot - edge, -dot - edge,
                (dot + edge) * 2, (dot + edge) * 2, 0.25f + edge, color);
            case "Plus" -> {
                line(ctx, -s, 0, s, 0, t, edge, color);
                line(ctx, 0, -s, 0, s, t, edge, color);
            }
            case "Cross", "Cross + Dot", "T Cross" -> {
                line(ctx, -r, 0, -g, 0, t, edge, color);
                line(ctx, g, 0, r, 0, t, edge, color);
                line(ctx, 0, g, 0, r, t, edge, color);
                if (!style.equals("T Cross")) line(ctx, 0, -r, 0, -g, t, edge, color);
            }
            case "X", "X + Dot", "Star" -> {
                int arms = style.equals("Star") ? 8 : 4;
                for (int i = 0; i < arms; i++) {
                    double angle = Math.PI / 4 + i * Math.PI * 2 / arms;
                    float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                    line(ctx, x * g, y * g, x * r, y * r, t, edge, color);
                }
            }
            case "Circle", "Circle + Dot", "Double Ring", "Ring Cross", "Reticle" -> {
                ring(ctx, s, t, edge, color);
                if (style.equals("Double Ring")) ring(ctx, Math.max(dot + t + 1, s * 0.55f), t, edge, color);
                if (style.equals("Ring Cross")) {
                    line(ctx, -s, 0, s, 0, t, edge, color);
                    line(ctx, 0, -s, 0, s, t, edge, color);
                }
                if (style.equals("Reticle")) {
                    for (int i = 0; i < 4; i++) {
                        double angle = i * Math.PI / 2;
                        float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                        line(ctx, x * (s + g), y * (s + g), x * (s * 1.6f + g), y * (s * 1.6f + g), t, edge, color);
                    }
                }
            }
            case "Square", "Square + Dot" -> {
                line(ctx, -s, -s, s, -s, t, edge, color);
                line(ctx, s, -s, s, s, t, edge, color);
                line(ctx, s, s, -s, s, t, edge, color);
                line(ctx, -s, s, -s, -s, t, edge, color);
            }
            case "Diamond", "Diamond + Dot" -> {
                line(ctx, 0, -s, s, 0, t, edge, color);
                line(ctx, s, 0, 0, s, t, edge, color);
                line(ctx, 0, s, -s, 0, t, edge, color);
                line(ctx, -s, 0, 0, -s, t, edge, color);
            }
            case "Chevron", "Double Chevron" -> {
                chevron(ctx, s, style.equals("Double Chevron") ? -s * 0.4f : 0, t, edge, color);
                if (style.equals("Double Chevron")) chevron(ctx, s, s * 0.4f, t, edge, color);
            }
            case "Triangle" -> {
                line(ctx, 0, -s, s * 0.866f, s * 0.5f, t, edge, color);
                line(ctx, s * 0.866f, s * 0.5f, -s * 0.866f, s * 0.5f, t, edge, color);
                line(ctx, -s * 0.866f, s * 0.5f, 0, -s, t, edge, color);
            }
            case "Star of David" -> {
                // Two equilateral triangles, one pointing up and one down, each the size of Triangle.
                for (int flip : new int[]{1, -1}) {
                    float tip = -s * flip, base = s * 0.5f * flip;
                    line(ctx, 0, tip, s * 0.866f, base, t, edge, color);
                    line(ctx, s * 0.866f, base, -s * 0.866f, base, t, edge, color);
                    line(ctx, -s * 0.866f, base, 0, tip, t, edge, color);
                }
            }
            case "Brackets" -> {
                for (int side : new int[]{-1, 1}) {
                    line(ctx, side * s, -s * 0.7f, side * s, s * 0.7f, t, edge, color);
                    line(ctx, side * s, -s * 0.7f, side * s * 0.55f, -s * 0.7f, t, edge, color);
                    line(ctx, side * s, s * 0.7f, side * s * 0.55f, s * 0.7f, t, edge, color);
                }
            }
            case "Corner Brackets" -> {
                for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                    line(ctx, x * s, y * s, x * s * 0.5f, y * s, t, edge, color);
                    line(ctx, x * s, y * s, x * s, y * s * 0.5f, t, edge, color);
                }
            }
            case "Four Dots" -> {
                float distance = r * 0.75f;
                Render2D.circle(ctx, -distance, 0, dot + edge, color);
                Render2D.circle(ctx, distance, 0, dot + edge, color);
                Render2D.circle(ctx, 0, -distance, dot + edge, color);
                Render2D.circle(ctx, 0, distance, dot + edge, color);
            }
            default -> throw new IllegalArgumentException("Unknown crosshair preset: " + style);
        }
        if ((centerDot || style.endsWith("+ Dot")) && !style.equals("Dot") && !style.equals("Square Dot"))
            Render2D.circle(ctx, 0, 0, dot + edge, color);
    }

    private static void line(DrawContext ctx, float x1, float y1, float x2, float y2, float t, float edge, int color) {
        Render2D.line(ctx, x1, y1, x2, y2, t + edge * 2, color);
    }
    private static void ring(DrawContext ctx, float r, float t, float edge, int color) {
        Render2D.ring(ctx, 0, 0, r + edge, t + edge * 2, color);
    }
    private static void chevron(DrawContext ctx, float s, float offset, float t, float edge, int color) {
        line(ctx, -s, offset + s * 0.45f, 0, offset - s * 0.45f, t, edge, color);
        line(ctx, 0, offset - s * 0.45f, s, offset + s * 0.45f, t, edge, color);
    }
}
