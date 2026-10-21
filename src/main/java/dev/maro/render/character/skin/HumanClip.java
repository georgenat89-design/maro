package dev.maro.render.character.skin;

import java.util.ArrayList;
import java.util.List;

/**
 * A keyframed full-body clip on the shared skeleton. Each bone has its own keys (pitch, yaw, roll
 * in the {@link Human} conventions); the body has keys for sway and lift and for how grounded it
 * is. Between keys values follow a smooth spline: looping clips wrap round, one-shot clips ease
 * to a stop at their first and last keys.
 *
 * <p>Key times are seconds, or for a looping cycle (walk, run) fractions of one cycle.
 */
public final class HumanClip {
    public final String name;
    /** Seconds one play takes (one cycle for a loop). */
    public final float length;
    public final boolean loop;
    /** Key times and values per bone; null for a bone the clip leaves at rest. */
    private final float[][] times = new float[Human.COUNT][], values = new float[Human.COUNT][];
    private float[] shiftTimes, shiftValues, groundTimes, groundValues;
    /** A steady turn of the whole body, degrees a second to the right (a spin that never unwinds). */
    private float spin;
    /** Whether keys are fractions of a cycle (scaled by the play rate) rather than seconds. */
    public final boolean cyclic;

    private HumanClip(String name, float length, boolean loop, boolean cyclic) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.cyclic = cyclic;
    }

    /** Samples the clip at {@code t} seconds into {@code out}; the time wraps for a loop and clamps otherwise. */
    public void sample(float t, HumanPose out) {
        float at = cyclic ? t / length : t;
        float span = cyclic ? 1 : length;
        if (loop) {
            at %= span;
            if (at < 0) at += span;
        } else {
            at = Math.max(0, Math.min(span, at));
        }
        out.identity();
        float[] v = SCRATCH.get();
        for (int b = 0; b < Human.COUNT; b++) {
            if (times[b] == null) continue;
            spline(times[b], values[b], 3, at, span, v);
            Human.euler(v[0], v[1], v[2], out.rot, b * 4);
        }
        if (shiftTimes != null) {
            spline(shiftTimes, shiftValues, 3, at, span, v);
            out.shift[0] = v[0];
            out.shift[1] = v[1];
            out.shift[2] = v[2];
        }
        if (groundTimes != null) {
            spline(groundTimes, groundValues, 1, at, span, v);
            out.grounded = Math.max(0, Math.min(1, v[0]));
        }
        if (spin != 0) {
            float turn = (float) Math.toRadians(spin * (cyclic ? at * length : at)) % (float) (Math.PI * 2);
            float[] q = SPIN.get();
            SkinMath.axisAngleQ(0, 1, 0, -turn, q, 0);
            int o = Human.HIPS.ordinal() * 4;
            SkinMath.mulQ(q, 0, out.rot, o, out.rot, o);
        }
    }

    private static final ThreadLocal<float[]> SPIN = ThreadLocal.withInitial(() -> new float[4]);

    /**
     * The clip's raw angles at {@code t}: pitch, yaw and roll (degrees) per bone, NaN for a bone it
     * leaves alone; and the body's sway left, lift, sway forward (leg lengths), how grounded it is
     * and its spin so far (degrees). For drawing the clip on a model of six rigid parts.
     */
    public void angles(float t, float[] bones, float[] body) {
        float at = cyclic ? t / length : t;
        float span = cyclic ? 1 : length;
        if (loop) {
            at %= span;
            if (at < 0) at += span;
        } else {
            at = Math.max(0, Math.min(span, at));
        }
        float[] v = SCRATCH.get();
        for (int b = 0; b < Human.COUNT; b++) {
            if (times[b] == null) {
                bones[b * 3] = bones[b * 3 + 1] = bones[b * 3 + 2] = Float.NaN;
                continue;
            }
            spline(times[b], values[b], 3, at, span, v);
            System.arraycopy(v, 0, bones, b * 3, 3);
        }
        body[0] = body[1] = body[2] = 0;
        body[3] = 1;
        if (shiftTimes != null) {
            spline(shiftTimes, shiftValues, 3, at, span, v);
            body[0] = v[0];
            body[1] = v[1];
            body[2] = v[2];
        }
        if (groundTimes != null) {
            spline(groundTimes, groundValues, 1, at, span, v);
            body[3] = Math.max(0, Math.min(1, v[0]));
        }
        body[4] = spin == 0 ? 0 : spin * (cyclic ? at * length : at) % 360;
    }

    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[4]);

    /** Whether the clip moves a bone. */
    public boolean moves(Human bone) {
        return times[bone.ordinal()] != null;
    }

    /**
     * Cubic Hermite through the keys with Catmull-Rom slopes: wrapping round for loops, flat at
     * the ends otherwise so a one-shot clip settles on its first and last keys.
     */
    private void spline(float[] k, float[] v, int width, float at, float span, float[] out) {
        int n = k.length;
        if (n == 1 || (!loop && at <= k[0])) {
            System.arraycopy(v, 0, out, 0, width);
            return;
        }
        if (!loop && at >= k[n - 1]) {
            System.arraycopy(v, (n - 1) * width, out, 0, width);
            return;
        }
        int i1;
        float t1, t2;
        if (loop && (at < k[0] || at >= k[n - 1])) {
            i1 = n - 1;
            t1 = k[n - 1];
            t2 = k[0] + span;
            if (at < k[0]) at += span;
        } else {
            int lo = 0, hi = n - 1;
            while (hi - lo > 1) {
                int m = (lo + hi) >>> 1;
                if (k[m] <= at) lo = m;
                else hi = m;
            }
            i1 = lo;
            t1 = k[lo];
            t2 = k[hi];
        }
        int i2 = loop ? (i1 + 1) % n : Math.min(n - 1, i1 + 1);
        int i0 = loop ? (i1 - 1 + n) % n : Math.max(0, i1 - 1);
        int i3 = loop ? (i2 + 1) % n : Math.min(n - 1, i2 + 1);
        float t0 = time(k, i0, i1, t1, span, true), t3 = time(k, i3, i2, t2, span, false);
        float h = t2 - t1, u = h > 1e-6f ? (at - t1) / h : 0;
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 0; c < width; c++) {
            float p0 = v[i0 * width + c], p1 = v[i1 * width + c], p2 = v[i2 * width + c], p3 = v[i3 * width + c];
            float m1 = (i0 == i1) ? 0 : (p2 - p0) / Math.max(1e-6f, t2 - t0) * h;
            float m2 = (i3 == i2) ? 0 : (p3 - p1) / Math.max(1e-6f, t3 - t1) * h;
            out[c] = h00 * p1 + h10 * m1 + h01 * p2 + h11 * m2;
        }
    }

    /** A neighbour key's time, unwrapped round the loop so slopes across the seam are right. */
    private float time(float[] k, int index, int from, float fromTime, float span, boolean before) {
        float t = k[index];
        if (!loop) return t;
        if (before && t > fromTime) t -= span;
        if (!before && t < fromTime) t += span;
        if (before && index == from) t -= span;
        return t;
    }

    // ---- building -------------------------------------------------------------------------------

    /** A looping cycle with keys at fractions 0..1 of {@code seconds}. */
    public static Builder cycle(String name, float seconds) {
        return new Builder(new HumanClip(name, seconds, true, true));
    }

    /** A looping clip with keys in seconds. */
    public static Builder loop(String name, float seconds) {
        return new Builder(new HumanClip(name, seconds, true, false));
    }

    /** A clip that plays once over {@code seconds}, keys in seconds. */
    public static Builder once(String name, float seconds) {
        return new Builder(new HumanClip(name, seconds, false, false));
    }

    public static final class Builder {
        private final HumanClip clip;
        private final List<List<float[]>> bones = new ArrayList<>();
        private final List<float[]> shift = new ArrayList<>(), ground = new ArrayList<>();

        private Builder(HumanClip clip) {
            this.clip = clip;
            for (int i = 0; i < Human.COUNT; i++) bones.add(new ArrayList<>());
        }

        /** A key for a bone: at {@code time}, pitch, yaw and roll in degrees. */
        public Builder key(Human bone, float time, float pitch, float yaw, float roll) {
            bones.get(bone.ordinal()).add(new float[] {time, pitch, yaw, roll});
            return this;
        }

        /** Keys for a bone at evenly spaced times across [0, span) (a cycle) or [0, span] (once). */
        public Builder track(Human bone, float... pyr) {
            int n = pyr.length / 3;
            float span = clip.cyclic ? 1 : clip.length;
            for (int i = 0; i < n; i++) {
                float t = clip.loop ? span * i / n : (n == 1 ? 0 : span * i / (n - 1));
                key(bone, t, pyr[i * 3], pyr[i * 3 + 1], pyr[i * 3 + 2]);
            }
            return this;
        }

        /** Only a pitch track, evenly spaced: knees, elbows and other hinges. */
        public Builder pitch(Human bone, float... pitches) {
            float[] pyr = new float[pitches.length * 3];
            for (int i = 0; i < pitches.length; i++) pyr[i * 3] = pitches[i];
            return track(bone, pyr);
        }

        /** A held angle for the whole clip. */
        public Builder hold(Human bone, float pitch, float yaw, float roll) {
            return key(bone, 0, pitch, yaw, roll);
        }

        /** Body sway (left, forward) and lift, in leg lengths. */
        public Builder shift(float time, float left, float lift, float forward) {
            shift.add(new float[] {time, left, lift, forward});
            return this;
        }

        /** Evenly spaced (left, lift, forward) triples. */
        public Builder shifts(float... values) {
            int n = values.length / 3;
            float span = clip.cyclic ? 1 : clip.length;
            for (int i = 0; i < n; i++) {
                float t = clip.loop ? span * i / n : (n == 1 ? 0 : span * i / (n - 1));
                shift(t, values[i * 3], values[i * 3 + 1], values[i * 3 + 2]);
            }
            return this;
        }

        /** A steady turn of the whole body, degrees a second to the right. */
        public Builder spin(float degreesPerSecond) {
            clip.spin = degreesPerSecond;
            return this;
        }

        /** How grounded the body is at a time: 1 feet planted, 0 free (for a jump inside a dance). */
        public Builder ground(float time, float amount) {
            ground.add(new float[] {time, amount});
            return this;
        }

        /**
         * Copies one side's keys to the other, half a cycle (or {@code offset}) later, with yaw and
         * roll mirrored: one leg's step is the other's half a stride on.
         */
        public Builder mirror(Human from, float offset) {
            List<float[]> src = bones.get(from.ordinal()), dst = bones.get(from.mirror().ordinal());
            float span = clip.cyclic ? 1 : clip.length;
            for (float[] k : src) {
                float t = k[0] + offset;
                if (clip.loop) {
                    t %= span;
                    if (t < 0) t += span;
                }
                dst.add(new float[] {t, k[1], -k[2], -k[3]});
            }
            return this;
        }

        /** Mirrors every right-side bone onto the left. */
        public Builder mirrorAll(float offset) {
            for (Human h : Human.ALL) if (h.right() && !bones.get(h.ordinal()).isEmpty() && bones.get(h.mirror().ordinal()).isEmpty()) mirror(h, offset);
            return this;
        }

        public HumanClip build() {
            for (int b = 0; b < Human.COUNT; b++) {
                List<float[]> keys = bones.get(b);
                if (keys.isEmpty()) continue;
                keys.sort((x, y) -> Float.compare(x[0], y[0]));
                float[] t = new float[keys.size()], v = new float[keys.size() * 3];
                for (int i = 0; i < keys.size(); i++) {
                    t[i] = keys.get(i)[0];
                    System.arraycopy(keys.get(i), 1, v, i * 3, 3);
                }
                clip.times[b] = t;
                clip.values[b] = v;
            }
            if (!shift.isEmpty()) {
                shift.sort((x, y) -> Float.compare(x[0], y[0]));
                clip.shiftTimes = new float[shift.size()];
                clip.shiftValues = new float[shift.size() * 3];
                for (int i = 0; i < shift.size(); i++) {
                    clip.shiftTimes[i] = shift.get(i)[0];
                    System.arraycopy(shift.get(i), 1, clip.shiftValues, i * 3, 3);
                }
            }
            if (!ground.isEmpty()) {
                ground.sort((x, y) -> Float.compare(x[0], y[0]));
                clip.groundTimes = new float[ground.size()];
                clip.groundValues = new float[ground.size()];
                for (int i = 0; i < ground.size(); i++) {
                    clip.groundTimes[i] = ground.get(i)[0];
                    clip.groundValues[i] = ground.get(i)[1];
                }
            }
            return clip;
        }
    }
}
