package dev.maro.gui.spotify;

import dev.maro.gui.render.Render2D;
import dev.maro.mixin.DrawContextAccessor;
import dev.maro.runtime.renderer.GuiMeshState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

import java.util.ArrayList;

/** Small, antialiased phone glyphs. Geometry and tint arrays are built once, not every frame. */
final class PhoneIcons {
    private static final int WHITE = 0xFFF5F7F6, DISABLED = 0xFF68716D;
    private static final Mesh SPOTIFY = spotify();
    private static final Mesh VOLUME_LOW = speaker(1, WHITE), VOLUME_HIGH = speaker(2, WHITE);
    private static final Mesh MUTED = speaker(0, WHITE), UNAVAILABLE = speaker(0, DISABLED);

    private PhoneIcons() { }

    static void spotify(DrawContext ctx, float x, float y) {
        Render2D.circle(ctx, x, y, 10, 0xFF1ED760);
        SPOTIFY.draw(ctx, x, y);
    }

    static void volume(DrawContext ctx, float x, float y, boolean available, boolean muted, double level) {
        (!available ? UNAVAILABLE : muted || level <= 0 ? MUTED : level <= .5 ? VOLUME_LOW : VOLUME_HIGH).draw(ctx, x, y);
    }

    private static Mesh spotify() {
        var b = new Builder(0xFF0B2013);
        b.curve(-5.9f, -2.8f, -2.2f, -4.5f, 2.6f, -4.1f, 6.1f, -1.9f, 2.05f);
        b.curve(-5.1f, .3f, -1.9f, -1.2f, 2.2f, -.8f, 5.1f, 1.0f, 1.75f);
        b.curve(-4.3f, 3.2f, -1.6f, 2.1f, 1.6f, 2.4f, 4.2f, 3.9f, 1.45f);
        return b.build();
    }

    private static Mesh speaker(int waves, int color) {
        var b = new Builder(color);
        // One continuous speaker silhouette, with no gap between the neck and cone.
        b.quad(-7, -2.4f, -4, -2.4f, -4, 2.4f, -7, 2.4f, color, color, color, color);
        b.quad(-4, -2.4f, -.7f, -5.5f, -.7f, 5.5f, -4, 2.4f, color, color, color, color);
        b.fringe(new float[]{-7, -2.4f, -4, -2.4f, -.7f, -5.5f, -.7f, 5.5f, -4, 2.4f, -7, 2.4f});
        if (waves == 0) {
            b.stroke(new float[]{2, -2.4f, 6, 2.4f}, 1.3f);
            b.stroke(new float[]{2, 2.4f, 6, -2.4f}, 1.3f);
        } else {
            b.arc(-1.4f, 0, 4, -48, 96, 1.25f);
            if (waves == 2) b.arc(-1.4f, 0, 7, -48, 96, 1.25f);
        }
        return b.build();
    }

    private record Mesh(float[] vertices, int[] colors) {
        void draw(DrawContext ctx, float x, float y) {
            var pose = new Matrix3x2f(ctx.getMatrices()).translate(x, y);
            var bounds = new ScreenRect(-11, -11, 22, 22).transformEachVertex(pose);
            ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new GuiMeshState(pose, vertices, colors,
                RenderPipelines.GUI, TextureSetup.empty(), bounds));
        }
    }

    private static final class Builder {
        private static final float AA = .55f;
        private final int color, clear;
        private final ArrayList<Float> vertices = new ArrayList<>();
        private final ArrayList<Integer> colors = new ArrayList<>();
        Builder(int color) { this.color = color; clear = color & 0xFFFFFF; }

        private void vertex(float x, float y, int tint) {
            vertices.add(x); vertices.add(y); vertices.add(0f); vertices.add(0f); colors.add(tint);
        }

        void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
                  int a, int b, int c, int d) {
            float area = ax * by - bx * ay + bx * cy - cx * by + cx * dy - dx * cy + dx * ay - ax * dy;
            vertex(ax, ay, a);
            if (area > 0) { vertex(dx, dy, d); vertex(cx, cy, c); vertex(bx, by, b); }
            else { vertex(bx, by, b); vertex(cx, cy, c); vertex(dx, dy, d); }
        }

        void curve(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float width) {
            var points = new float[34];
            for (int i = 0; i <= 16; i++) {
                float t = i / 16f, s = 1 - t;
                points[i * 2] = s * s * s * ax + 3 * s * s * t * bx + 3 * s * t * t * cx + t * t * t * dx;
                points[i * 2 + 1] = s * s * s * ay + 3 * s * s * t * by + 3 * s * t * t * cy + t * t * t * dy;
            }
            stroke(points, width);
        }

        void arc(float x, float y, float r, float start, float sweep, float width) {
            var points = new float[26];
            for (int i = 0; i <= 12; i++) {
                double angle = Math.toRadians(start + sweep * i / 12f);
                points[i * 2] = x + r * (float) Math.cos(angle);
                points[i * 2 + 1] = y + r * (float) Math.sin(angle);
            }
            stroke(points, width);
        }

        // Mitered normals keep adjoining segments continuous, including the two neck corners.
        private static float[] normals(float[] points, boolean closed) {
            int n = points.length / 2;
            var normals = new float[points.length];
            for (int i = 0; i < n; i++) {
                int prev = i == 0 ? (closed ? n - 1 : 0) : i - 1;
                int next = i == n - 1 ? (closed ? 0 : n - 1) : i + 1;
                float ax = points[i * 2] - points[prev * 2], ay = points[i * 2 + 1] - points[prev * 2 + 1];
                float bx = points[next * 2] - points[i * 2], by = points[next * 2 + 1] - points[i * 2 + 1];
                if (prev == i) { ax = bx; ay = by; }
                if (next == i) { bx = ax; by = ay; }
                float al = (float) Math.hypot(ax, ay), bl = (float) Math.hypot(bx, by);
                ax /= al; ay /= al; bx /= bl; by /= bl;
                float denominator = 1 + ax * bx + ay * by;
                normals[i * 2] = (ay + by) / denominator;
                normals[i * 2 + 1] = -(ax + bx) / denominator;
            }
            return normals;
        }

        void fringe(float[] points) { band(points, normals(points, true), 0, AA, color, clear, true); }

        private void band(float[] p, float[] normals, float inner, float outer, int c1, int c2, boolean closed) {
            int n = p.length / 2;
            for (int i = 0; i < (closed ? n : n - 1); i++) {
                int j = (i + 1) % n;
                float x = p[i * 2], y = p[i * 2 + 1], nx = normals[i * 2], ny = normals[i * 2 + 1];
                float xx = p[j * 2], yy = p[j * 2 + 1], nnx = normals[j * 2], nny = normals[j * 2 + 1];
                quad(x + nx * inner, y + ny * inner, xx + nnx * inner, yy + nny * inner,
                    xx + nnx * outer, yy + nny * outer, x + nx * outer, y + ny * outer, c1, c1, c2, c2);
            }
        }

        void stroke(float[] points, float width) {
            var normals = normals(points, false);
            float r = width / 2;
            band(points, normals, -r, r, color, color, false);
            band(points, normals, r, r + AA, color, clear, false);
            band(points, normals, -r, -r - AA, color, clear, false);
            for (int end : new int[]{0, points.length - 2}) {
                double start = Math.atan2(normals[end + 1], normals[end]) + (end == 0 ? Math.PI : 0);
                float x = points[end], y = points[end + 1];
                for (int i = 0; i < 8; i++) {
                    double a = start + i * Math.PI / 8, b = start + (i + 1) * Math.PI / 8;
                    float ax = (float) Math.cos(a), ay = (float) Math.sin(a), bx = (float) Math.cos(b), by = (float) Math.sin(b);
                    quad(x, y, x + ax * r, y + ay * r, x + bx * r, y + by * r, x, y, color, color, color, color);
                    quad(x + ax * r, y + ay * r, x + bx * r, y + by * r,
                        x + bx * (r + AA), y + by * (r + AA), x + ax * (r + AA), y + ay * (r + AA), color, color, clear, clear);
                }
            }
        }

        Mesh build() {
            var xyuv = new float[vertices.size()];
            var rgba = new int[colors.size()];
            for (int i = 0; i < xyuv.length; i++) xyuv[i] = vertices.get(i);
            for (int i = 0; i < rgba.length; i++) rgba[i] = colors.get(i);
            return new Mesh(xyuv, rgba);
        }
    }
}
