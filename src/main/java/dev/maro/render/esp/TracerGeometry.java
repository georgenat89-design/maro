package dev.maro.render.esp;

import java.nio.ByteBuffer;

/** Subpixel tracer geometry shared by player, block and storage ESP. */
public final class TracerGeometry {
    private TracerGeometry() { }

    /**
     * Clip in homogeneous space, before dividing by depth. This keeps the direction continuous
     * across the camera plane and clips from the actual origin (including Bottom mode).
     * The endpoint stays outside the viewport so offscreen endpoint markers remain hidden.
     */
    public static boolean project(float x, float y, float w, boolean bottom, float[] out, int at) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w)) return false;
        float sy = bottom ? -1f : 0f;
        double depth = Math.max(w, 1e-5f);
        double dx = x, dy = y - sy * depth;
        if (w <= 0 && Math.abs(x) + Math.abs(y) < 1e-6) dy = -1;
        double t = 1.0 / depth;
        if (dx > 0) t = Math.min(t, 1.2 / dx);
        else if (dx < 0) t = Math.min(t, -1.2 / dx);
        if (dy > 0) t = Math.min(t, (1.2 - sy) / dy);
        else if (dy < 0) t = Math.min(t, (-1.2 - sy) / dy);
        out[at] = (float) (dx * t);
        out[at + 1] = (float) (sy + dy * t);
        return true;
    }

    /** Stable animation phase; list reordering must not recolor a target. */
    public static float phase(long identity) {
        long hash = identity;
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        hash ^= hash >>> 31;
        return (hash >>> 40) / 16777216f;
    }

    /** A padded capsule quad, including subpixel and zero-length segments, without allocations. */
    public static void writeQuad(ByteBuffer vertices, int quad, float[] line, float pad,
                                 int width, int height, float tag) {
        float dx = line[2] - line[0], dy = line[3] - line[1];
        float length = (float) Math.hypot(dx, dy);
        float ux = length > 1e-6f ? dx / length : 1f;
        float uy = length > 1e-6f ? dy / length : 0f;
        float nx = -uy * pad, ny = ux * pad, ex = ux * pad, ey = uy * pad;
        for (int v = 0; v < 4; v++) {
            boolean end = v >= 2;
            float side = v == 0 || v == 3 ? 1f : -1f;
            float x = (end ? line[2] + ex : line[0] - ex) + side * nx;
            float y = (end ? line[3] + ey : line[1] - ey) + side * ny;
            int at = (quad * 4 + v) * 12;
            vertices.putFloat(at, x / width * 2f - 1f);
            vertices.putFloat(at + 4, y / height * 2f - 1f);
            vertices.putFloat(at + 8, tag);
        }
    }
}
