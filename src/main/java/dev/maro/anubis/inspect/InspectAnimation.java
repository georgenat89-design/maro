// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

@Environment(value=EnvType.CLIENT)
final class InspectAnimation {
    static final String CLASSIC = "Classic";
    static final String TWIRL = "Twirl";
    static final int X = 0;
    static final int Y = 1;
    static final int Z = 2;
    static final int YAW = 3;
    static final int PITCH = 4;
    static final int ROLL = 5;
    static final int FLIP = 6;
    static final int SPIN = 7;
    static final int PART_A = 8;
    static final int PART_B = 9;
    static final int SHINE = 10;
    static final int SLIDE = 11;
    static final int DRAW = 12;
    static final int SHEATH = 13;
    static final int OFF_HAND = 14;
    static final int OFF_AT = 15;
    static final int CHANNELS = 16;
    static final float GRIP_X = 0.07f;
    static final float GRIP_Y = -0.05f;
    static final float GRIP_Z = -0.02f;
    static final float CENTER_X = 0.07f;
    static final float CENTER_Y = 0.2f;
    static final float CENTER_Z = 0.07f;
    static final float BLADE_Y = 0.94f;
    static final float BLADE_Z = 0.342f;
    static final float SECONDS = 3.2f;
    private static final float[][] CLASSIC_KEYS = new float[][]{{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, {0.14f, -0.24f, 0.17f, 0.1f, 60.0f, -10.0f, 50.0f, 0.0f, 0.0f}, {0.3f, -0.25f, 0.18f, 0.11f, 70.0f, -5.0f, 46.0f, 0.0f, 0.0f}, {0.4f, -0.24f, 0.21f, 0.11f, 66.0f, -6.0f, 48.0f, 180.0f, 0.0f}, {0.46f, -0.24f, 0.2f, 0.11f, 64.0f, -7.0f, 49.0f, 184.0f, 0.0f}, {0.56f, -0.25f, 0.19f, 0.11f, 70.0f, -5.0f, 46.0f, 360.0f, 0.0f}, {0.62f, -0.25f, 0.18f, 0.11f, 70.0f, -5.0f, 46.0f, 360.0f, 0.0f}, {0.8f, -0.23f, 0.19f, 0.1f, 56.0f, -10.0f, 52.0f, 366.0f, 0.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 360.0f, 0.0f}};
    private static final float[][] TWIRL_KEYS = new float[][]{{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f}, {0.12f, -0.2f, 0.15f, 0.08f, 75.0f, -6.0f, 20.0f, 0.0f, -20.0f}, {0.22f, -0.21f, 0.2f, 0.09f, 78.0f, -6.0f, 22.0f, 0.0f, 0.0f}, {0.34f, -0.22f, 0.3f, 0.1f, 80.0f, -4.0f, 24.0f, 0.0f, 180.0f}, {0.46f, -0.21f, 0.2f, 0.09f, 78.0f, -6.0f, 22.0f, 0.0f, 360.0f}, {0.7f, -0.24f, 0.17f, 0.1f, 68.0f, -8.0f, 42.0f, 0.0f, 360.0f}, {1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 360.0f}};
    private static final Clip CLASSIC_CLIP = new Clip(CLASSIC_KEYS, 3.2f, 0.3f, 0.62f);
    private static final Clip TWIRL_CLIP = new Clip(TWIRL_KEYS, 3.2f, 0.22f, 0.46f);

    private InspectAnimation() {
    }

    static Clip clip(String style) {
        return TWIRL.equals(style) ? TWIRL_CLIP : CLASSIC_CLIP;
    }

    static void place(MatrixStack pose, float[] at, float weight, int side, float centerX, float centerY, float centerZ) {
        float turn = (float)Math.PI / 180 * weight;
        float yaw = MathHelper.wrapDegrees((float)at[3]) * turn * (float)side;
        float pitch = MathHelper.wrapDegrees((float)at[4]) * turn;
        float roll = MathHelper.wrapDegrees((float)at[5]) * turn * (float)side;
        float flip = MathHelper.wrapDegrees((float)at[6]) * turn * (float)side;
        float spin = MathHelper.wrapDegrees((float)at[7]) * turn;
        pose.translate(at[0] * weight * (float)side, at[1] * weight, at[2] * weight);
        float gripX = 0.07f * (float)side;
        pose.multiply((Quaternionfc)new Quaternionf().rotateX(pitch).rotateZ(roll).rotateY(yaw), gripX, -0.05f, -0.02f);
        pose.multiply((Quaternionfc)new Quaternionf().rotateX(spin), centerX * (float)side, centerY, centerZ);
        pose.multiply((Quaternionfc)new Quaternionf().rotateAxis(flip, 0.0f, 0.94f, 0.342f), gripX, -0.05f, -0.02f);
    }

    static float[] sample(float[][] keys, float t, float[] out) {
        int i;
        t = MathHelper.clamp((float)t, (float)0.0f, (float)1.0f);
        for (i = 0; i < keys.length - 2 && t > keys[i + 1][0]; ++i) {
        }
        float[] to = keys[i + 1];
        float[] from = keys[i];
        float span = to[0] - from[0];
        float u = span <= 0.0f ? 1.0f : (t - from[0]) / span;
        for (int c = 0; c < 16; ++c) {
            out[c] = InspectAnimation.hermite(InspectAnimation.value(from, c), InspectAnimation.value(to, c), InspectAnimation.slope(keys, i, c) * span, InspectAnimation.slope(keys, i + 1, c) * span, u);
        }
        return out;
    }

    static float[] sampleLoop(float[][] keys, float t, float[] out) {
        int i;
        t -= (float)Math.floor(t);
        int n = keys.length;
        for (i = 0; i < n - 2 && t > keys[i + 1][0]; ++i) {
        }
        float[] to = keys[i + 1];
        float[] from = keys[i];
        float span = to[0] - from[0];
        float u = span <= 0.0f ? 1.0f : (t - from[0]) / span;
        for (int c = 0; c < 16; ++c) {
            out[c] = InspectAnimation.hermite(InspectAnimation.value(from, c), InspectAnimation.value(to, c), InspectAnimation.loopSlope(keys, i, c) * span, InspectAnimation.loopSlope(keys, i + 1, c) * span, u);
        }
        return out;
    }

    private static float loopSlope(float[][] keys, int i, int c) {
        float nextV;
        float nextT;
        float prevV;
        float prevT;
        int n = keys.length;
        float drift = InspectAnimation.value(keys[n - 1], c) - InspectAnimation.value(keys[0], c);
        if (i == 0) {
            prevT = keys[n - 2][0] - 1.0f;
            prevV = InspectAnimation.value(keys[n - 2], c) - drift;
        } else {
            prevT = keys[i - 1][0];
            prevV = InspectAnimation.value(keys[i - 1], c);
        }
        if (i == n - 1) {
            nextT = keys[1][0] + 1.0f;
            nextV = InspectAnimation.value(keys[1], c) + drift;
        } else {
            nextT = keys[i + 1][0];
            nextV = InspectAnimation.value(keys[i + 1], c);
        }
        return (nextV - prevV) / (nextT - prevT);
    }

    private static float value(float[] key, int channel) {
        return channel + 1 < key.length ? key[channel + 1] : 0.0f;
    }

    private static float slope(float[][] keys, int i, int c) {
        if (i == 0 || i == keys.length - 1) {
            return 0.0f;
        }
        float[] prev = keys[i - 1];
        float[] next = keys[i + 1];
        return (InspectAnimation.value(next, c) - InspectAnimation.value(prev, c)) / (next[0] - prev[0]);
    }

    private static float hermite(float p0, float p1, float m0, float m1, float u) {
        float u2 = u * u;
        float u3 = u2 * u;
        return (2.0f * u3 - 3.0f * u2 + 1.0f) * p0 + (u3 - 2.0f * u2 + u) * m0 + (-2.0f * u3 + 3.0f * u2) * p1 + (u3 - u2) * m1;
    }

    @Environment(value=EnvType.CLIENT)
    static final class Clip {
        final float[][] keys;
        final float seconds;
        final float loopEnter;
        final float loopExit;
        private final float[][] loop;

        Clip(float[][] keys, float seconds) {
            this(keys, seconds, 0.0f, 0.0f);
        }

        Clip(float[][] keys, float seconds, float loopEnter, float loopExit) {
            this.keys = keys;
            this.seconds = seconds;
            this.loopEnter = loopEnter;
            this.loopExit = loopExit;
            this.loop = loopExit > loopEnter ? Clip.cut(keys, loopEnter, loopExit) : null;
        }

        boolean loops() {
            return this.loop != null;
        }

        float[] sample(float t, float[] out) {
            if (this.loop != null && t > this.loopEnter && t < this.loopExit) {
                return InspectAnimation.sampleLoop(this.loop, (t - this.loopEnter) / (this.loopExit - this.loopEnter), out);
            }
            return InspectAnimation.sample(this.keys, t, out);
        }

        private static float[][] cut(float[][] keys, float enter, float exit) {
            int from = -1;
            int to = -1;
            for (int i = 0; i < keys.length; ++i) {
                if (Math.abs(keys[i][0] - enter) < 1.0E-4f) {
                    from = i;
                }
                if (!(Math.abs(keys[i][0] - exit) < 1.0E-4f)) continue;
                to = i;
            }
            if (from < 0 || to <= from) {
                throw new IllegalArgumentException("a loop starts and ends on keys");
            }
            float[][] loop = new float[to - from + 1][];
            for (int i = from; i <= to; ++i) {
                loop[i - from] = (float[])keys[i].clone();
                loop[i - from][0] = (keys[i][0] - enter) / (exit - enter);
            }
            return loop;
        }
    }
}
