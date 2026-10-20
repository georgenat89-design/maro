package dev.maro.render.character.skin;

import java.util.Locale;

/**
 * An animation that came inside a model's GLB, on its own nodes: glTF channels with STEP, LINEAR
 * or CUBICSPLINE keys for translation, rotation and scale. Sampling writes local transforms over
 * a pose, leaving nodes the clip does not move as they are.
 */
public final class NodeClip {
    public static final int TRANSLATION = 0, ROTATION = 1, SCALE = 2;
    public static final int STEP = 0, LINEAR = 1, CUBIC = 2;

    public final String name;
    /** The locomotion state this clip serves, from its name (an {@link Locomotion.State} ordinal), or -1. */
    public final int state;
    public final float duration;
    public final int[] node, path, interpolation;
    public final float[][] times, values;

    public NodeClip(String name, float duration, int[] node, int[] path, int[] interpolation, float[][] times, float[][] values) {
        this.name = name;
        this.duration = Math.max(1e-3f, duration);
        this.node = node;
        this.path = path;
        this.interpolation = interpolation;
        this.times = times;
        this.values = values;
        this.state = classify(name);
    }

    /** Which state a clip named this plays, or -1 when its name does not say. */
    public static int classify(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("crouch") || n.contains("sneak")) return n.contains("walk") ? Locomotion.State.CROUCH_WALK.ordinal() : Locomotion.State.CROUCH.ordinal();
        if (n.contains("sprint") || n.contains("run") || n.contains("jog")) return Locomotion.State.RUN.ordinal();
        if (n.contains("walk")) return Locomotion.State.WALK.ordinal();
        if (n.contains("idle") || n.contains("breath") || n.contains("stand")) return Locomotion.State.IDLE.ordinal();
        if (n.contains("jump")) return Locomotion.State.JUMP.ordinal();
        if (n.contains("fall")) return Locomotion.State.FALL.ordinal();
        if (n.contains("land")) return Locomotion.State.LAND.ordinal();
        if (n.contains("swim")) return Locomotion.State.SWIM.ordinal();
        return -1;
    }

    /**
     * Writes this clip at {@code time} seconds (wrapped) over a pose's local transforms; nodes it
     * does not animate keep their values.
     */
    public void sample(float time, float[] t, float[] r, float[] s) {
        float at = time % duration;
        if (at < 0) at += duration;
        for (int c = 0; c < node.length; c++) {
            int n = node[c];
            float[] k = times[c], v = values[c];
            int width = path[c] == ROTATION ? 4 : 3;
            float[] out = path[c] == TRANSLATION ? t : path[c] == ROTATION ? r : s;
            int o = n * width;
            int count = k.length;
            if (count == 0) continue;
            boolean cubic = interpolation[c] == CUBIC;
            int stride = cubic ? width * 3 : width, mid = cubic ? width : 0;
            if (at <= k[0] || count == 1) {
                System.arraycopy(v, mid, out, o, width);
                if (width == 4) SkinMath.normalizeQ(out, o);
                continue;
            }
            if (at >= k[count - 1]) {
                System.arraycopy(v, (count - 1) * stride + mid, out, o, width);
                if (width == 4) SkinMath.normalizeQ(out, o);
                continue;
            }
            int lo = 0, hi = count - 1;
            while (hi - lo > 1) {
                int m = (lo + hi) >>> 1;
                if (k[m] <= at) lo = m;
                else hi = m;
            }
            float span = k[hi] - k[lo], u = span > 0 ? (at - k[lo]) / span : 0;
            if (interpolation[c] == STEP) {
                System.arraycopy(v, lo * stride, out, o, width);
            } else if (!cubic) {
                if (width == 4) SkinMath.slerpQ(v, lo * 4, v, hi * 4, u, out, o);
                else for (int i = 0; i < 3; i++) out[o + i] = v[lo * 3 + i] + (v[hi * 3 + i] - v[lo * 3 + i]) * u;
            } else {
                // glTF cubic spline: in-tangent, value, out-tangent per key; tangents scaled by the span.
                float u2 = u * u, u3 = u2 * u;
                float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
                int a = lo * stride, b = hi * stride;
                for (int i = 0; i < width; i++) {
                    out[o + i] = h00 * v[a + width + i] + h10 * span * v[a + 2 * width + i] + h01 * v[b + width + i] + h11 * span * v[b + i];
                }
                if (width == 4) SkinMath.normalizeQ(out, o);
            }
        }
    }

    /** Whether this clip moves a node. */
    public boolean moves(int n) {
        for (int c : node) if (c == n) return true;
        return false;
    }
}
