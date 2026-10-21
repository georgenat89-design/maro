package dev.maro.render.emote;

import static dev.maro.render.character.skin.Human.*;

import dev.maro.render.character.skin.Human;
import dev.maro.render.character.skin.HumanClip;

/**
 * Every emote as a full-body clip on the shared skeleton: hips, spine, chest, neck and head,
 * shoulders, arms, forearms and hands, thighs, shins and feet, with the body's sway and lift. Each
 * is written as a function of time and sampled densely into keys, so the spline between them
 * follows the motion exactly. Angles are degrees in the {@link Human} conventions (an arm's pitch
 * of -90 points it forward and -180 straight up; a right arm's roll of +90 holds it out to the
 * side, the left arm's -90; a knee bends positive, an elbow negative; a positive trunk or head
 * pitch leans forward).
 */
final class EmoteClips {
    private EmoteClips() {
    }

    /** One moment of an emote. */
    interface Frame {
        void at(float t, K k);
    }

    /** A looping emote sampled {@code samples} times a round. */
    static HumanClip loop(String name, float seconds, int samples, Frame f) {
        HumanClip.Builder b = HumanClip.loop(name, seconds);
        K k = new K(b);
        for (int i = 0; i < samples; i++) {
            k.time = seconds * i / samples;
            f.at(k.time, k);
        }
        return k.finish();
    }

    /** A once-through emote, sampled from its start to its end. */
    static HumanClip once(String name, float seconds, int samples, Frame f) {
        HumanClip.Builder b = HumanClip.once(name, seconds);
        K k = new K(b);
        for (int i = 0; i <= samples; i++) {
            k.time = seconds * i / samples;
            f.at(k.time, k);
        }
        return k.finish();
    }

    /** Keys for the moment being sampled. */
    static final class K {
        final HumanClip.Builder b;
        float time;
        float spin;

        K(HumanClip.Builder b) {
            this.b = b;
        }

        K set(Human bone, float pitch, float yaw, float roll) {
            b.key(bone, time, pitch, yaw, roll);
            return this;
        }

        /** Both sides at once, the left mirrored (yaw and roll flipped). */
        K both(Human right, float pitch, float yaw, float roll) {
            b.key(right, time, pitch, yaw, roll);
            b.key(right.mirror(), time, pitch, -yaw, -roll);
            return this;
        }

        /** Sway left, lift and sway forward, in leg lengths. */
        K body(float left, float lift, float forward) {
            b.shift(time, left, lift, forward);
            return this;
        }

        K ground(float amount) {
            b.ground(time, amount);
            return this;
        }

        HumanClip finish() {
            if (spin != 0) b.spin(spin);
            return b.build();
        }
    }

    // ---- shapes over time

    static float sin(float t, float rate) {
        return (float) Math.sin(t * rate);
    }

    static float cos(float t, float rate) {
        return (float) Math.cos(t * rate);
    }

    static float pos(float x) {
        return Math.max(0, x);
    }

    static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** 0 at the start, 1 after {@code in} seconds; down again over the last {@code out} of {@code length}. */
    static float hold(float t, float length, float in, float out) {
        return Math.min(smooth(t / in), smooth((length - t) / out));
    }

    /** Rises to 1 and back to 0 over {@code length} seconds, holding at the top. */
    static float bump(float t, float length) {
        float x = Math.max(0, Math.min(1, t / length));
        return (float) Math.min(1, Math.sin(x * Math.PI) * 1.6);
    }

    /** A relaxed stance: knees soft, arms hanging a little out, the weight shared. */
    static void stand(K k, float knee) {
        k.both(RIGHT_THIGH, -knee * 0.5f, 0, 1).both(RIGHT_SHIN, knee, 0, 0).both(RIGHT_FOOT, -knee * 0.5f, 0, 0);
    }

    // ---- the emotes

    static HumanClip wave() {
        return loop("Wave", 2.6f, 32, (t, k) -> {
            float rate = (float) Math.PI * 2 * 4 / 2.6f, w = sin(t, rate), sway = sin(t, (float) Math.PI * 2 / 2.6f);
            k.set(RIGHT_SHOULDER, 0, 0, 8).set(RIGHT_UPPER_ARM, -158, -8, 24 + 22 * w).set(RIGHT_FOREARM, -24 + 8 * w, 0, 0)
                    .set(RIGHT_HAND, 0, 0, 14 * sin(t + 0.15f, rate));
            k.set(LEFT_UPPER_ARM, 4, 0, -6).set(LEFT_FOREARM, -12, 0, 0).set(LEFT_HAND, 0, 0, -3);
            k.set(SPINE, -2, 0, 3 + sway).set(CHEST, -2, 4, 2).set(NECK, -3, 2, 0).set(HEAD, -4, 6, 5 * sway);
            k.set(HIPS, 0, 0, -3);
            k.set(RIGHT_THIGH, -3, 0, 2).set(RIGHT_SHIN, 10, 0, 0).set(RIGHT_FOOT, -5, 0, 0);
            k.set(LEFT_THIGH, 0, 0, -2).set(LEFT_SHIN, 2, 0, 0).set(LEFT_FOOT, -1, 0, 0);
            k.body(0.03f, 0, 0);
        });
    }

    static HumanClip clap() {
        return loop("Clap", 2.4f, 48, (t, k) -> {
            float c = Math.abs(sin(t, (float) Math.PI * 5 / 2.4f));
            k.both(RIGHT_UPPER_ARM, -62, -(6 + 20 * c), 6).both(RIGHT_FOREARM, -48 - 8 * c, 0, 0).both(RIGHT_HAND, -6, 0, -10);
            k.set(SPINE, 5 + 2 * c, 0, 0).set(CHEST, 3, 0, 0).set(NECK, 3 + 2 * c, 0, 0).set(HEAD, 4 + 3 * c, 0, 0);
            stand(k, 6 + 8 * (1 - c));
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip dab() {
        return once("Dab", 1.8f, 36, (t, k) -> {
            float e = hold(t, 1.8f, 0.22f, 0.25f);
            k.set(RIGHT_SHOULDER, 0, 0, 10 * e).set(RIGHT_UPPER_ARM, -134 * e, -10 * e, 34 * e).set(RIGHT_FOREARM, -4 * e, 0, 0).set(RIGHT_HAND, 0, 0, 10 * e);
            k.set(LEFT_UPPER_ARM, -102 * e, 58 * e, 4 * e).set(LEFT_FOREARM, -112 * e, 0, 0).set(LEFT_HAND, 0, 0, -10 * e);
            k.set(SPINE, 12 * e, -10 * e, 8 * e).set(CHEST, 6 * e, -8 * e, 4 * e).set(NECK, 14 * e, -12 * e, 0).set(HEAD, 22 * e, -16 * e, 6 * e);
            k.set(HIPS, 0, 6 * e, -4 * e);
            k.set(RIGHT_THIGH, -8 * e, 0, 10 * e).set(RIGHT_SHIN, 18 * e, 0, 0).set(RIGHT_FOOT, -8 * e, 0, 0);
            k.set(LEFT_THIGH, -4 * e, 0, -4 * e).set(LEFT_SHIN, 8 * e, 0, 0).set(LEFT_FOOT, -4 * e, 0, 0);
            k.body(-0.04f * e, 0, 0);
        });
    }

    static HumanClip floss() {
        return loop("Floss", 2f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2), c = cos(t, (float) Math.PI * 4);
            // Both arms swing across the body to one side as the hips swing to the other; front and back trade each beat.
            k.set(RIGHT_UPPER_ARM, 26 * c, 0, 8 + 26 * s).set(RIGHT_FOREARM, -6, 0, 0).set(RIGHT_HAND, 0, 0, 8 * s);
            k.set(LEFT_UPPER_ARM, -26 * c, 0, -8 + 26 * s).set(LEFT_FOREARM, -6, 0, 0).set(LEFT_HAND, 0, 0, 8 * s);
            k.set(HIPS, 0, 10 * s, -9 * s).set(SPINE, 4, -6 * s, 6 * s).set(CHEST, 2, -4 * s, 4 * s).set(NECK, 0, 0, -2 * s).set(HEAD, 0, 9 * s, -3 * s);
            float knee = 12 + 6 * Math.abs(s);
            k.set(RIGHT_THIGH, -knee * 0.5f, 0, 6 * s).set(RIGHT_SHIN, knee, 0, 0).set(RIGHT_FOOT, -knee * 0.5f, 0, -4 * s);
            k.set(LEFT_THIGH, -knee * 0.5f, 0, 6 * s).set(LEFT_SHIN, knee, 0, 0).set(LEFT_FOOT, -knee * 0.5f, 0, -4 * s);
            k.body(0.07f * s, 0.01f * Math.abs(c), 0);
        });
    }

    static HumanClip cheer() {
        return loop("Cheer", 2f, 48, (t, k) -> {
            float hop = Math.abs(sin(t, (float) Math.PI * 3 / 2)), squat = 1 - hop, shake = sin(t, (float) Math.PI * 2 * 3 / 2);
            k.set(RIGHT_UPPER_ARM, -168, 0, 16 + 12 * shake).set(RIGHT_FOREARM, -8, 0, 0).set(RIGHT_HAND, -10, 0, 0);
            k.set(LEFT_UPPER_ARM, -168, 0, -16 - 12 * shake).set(LEFT_FOREARM, -8, 0, 0).set(LEFT_HAND, -10, 0, 0);
            k.set(SPINE, -6 + 8 * squat, 0, 0).set(CHEST, -4, 0, 0).set(NECK, -6, 0, 0).set(HEAD, -12, 0, 0);
            k.both(RIGHT_THIGH, -26 * squat, 0, 3).both(RIGHT_SHIN, 46 * squat, 0, 0).both(RIGHT_FOOT, -20 * squat + 22 * hop, 0, 0);
            k.set(HIPS, 4 * squat, 0, 0).body(0, 0.2f * hop * hop, 0);
        });
    }

    static HumanClip salute() {
        return once("Salute", 2.2f, 36, (t, k) -> {
            float e = hold(t, 2.2f, 0.3f, 0.3f);
            k.set(RIGHT_SHOULDER, 0, 0, 6 * e).set(RIGHT_UPPER_ARM, -96 * e, -40 * e, 58 * e).set(RIGHT_FOREARM, -128 * e, 0, 0).set(RIGHT_HAND, 6 * e, 0, -12 * e);
            k.set(LEFT_UPPER_ARM, 0, 0, -3).set(LEFT_FOREARM, -4, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(SPINE, -3 * e, 0, 0).set(CHEST, -4 * e, 0, 0).set(NECK, -2 * e, 0, 0).set(HEAD, -3 * e, 0, 0);
            k.both(RIGHT_THIGH, 0, 0, -1 * e).both(RIGHT_SHIN, 1, 0, 0).both(RIGHT_FOOT, 0, 0, 0);
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip facepalm() {
        return once("Facepalm", 2.4f, 40, (t, k) -> {
            float e = hold(t, 2.4f, 0.35f, 0.3f), shake = sin(t, 7) * e;
            k.set(RIGHT_UPPER_ARM, -118 * e, -34 * e, -4 * e).set(RIGHT_FOREARM, -122 * e, 0, 0).set(RIGHT_HAND, 18 * e, 0, 0);
            k.set(LEFT_UPPER_ARM, 12 * e, 0, -34 * e).set(LEFT_FOREARM, -92 * e, 0, 0).set(LEFT_HAND, 0, 0, 10 * e);
            k.set(SPINE, 10 * e, 0, 0).set(CHEST, 8 * e, 0, 0).set(NECK, 14 * e, 3 * shake, 0).set(HEAD, 20 * e, 6 * shake, 0);
            k.set(HIPS, 0, 0, 3 * e);
            k.set(RIGHT_THIGH, -2, 0, 0).set(RIGHT_SHIN, 4, 0, 0).set(RIGHT_FOOT, -2, 0, 0);
            k.set(LEFT_THIGH, -8 * e, 0, -2).set(LEFT_SHIN, 16 * e, 0, 0).set(LEFT_FOOT, -8 * e, 0, 0);
            k.body(-0.025f * e, 0, 0);
        });
    }

    static HumanClip point() {
        return once("Point", 2f, 32, (t, k) -> {
            float e = hold(t, 2f, 0.2f, 0.3f);
            k.set(RIGHT_SHOULDER, 0, -6 * e, 4 * e).set(RIGHT_UPPER_ARM, -90 * e, -8 * e, 2 * e).set(RIGHT_FOREARM, -4 * e, 0, 0).set(RIGHT_HAND, -4 * e, 0, 0);
            k.set(LEFT_UPPER_ARM, 6 * e, 0, -8 * e).set(LEFT_FOREARM, -18 * e, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(SPINE, 4 * e, -8 * e, 0).set(CHEST, 2 * e, -6 * e, 0).set(NECK, 0, -4 * e, 0).set(HEAD, -2 * e, -6 * e, 0);
            k.set(HIPS, 0, 4 * e, 0);
            k.set(RIGHT_THIGH, -16 * e, 0, 2).set(RIGHT_SHIN, 10 * e, 0, 0).set(RIGHT_FOOT, 4 * e, 0, 0);
            k.set(LEFT_THIGH, 8 * e, 0, -2).set(LEFT_SHIN, 6 * e, 0, 0).set(LEFT_FOOT, -12 * e, 0, 0);
            k.body(0, 0, 0.05f * e);
        });
    }

    static HumanClip shrug() {
        return once("Shrug", 1.6f, 32, (t, k) -> {
            float e = bump(t, 1.6f);
            k.both(RIGHT_SHOULDER, 0, 0, 14 * e).both(RIGHT_UPPER_ARM, -26 * e, 12 * e, 40 * e).both(RIGHT_FOREARM, -78 * e, 0, 0).both(RIGHT_HAND, 0, 0, 34 * e);
            k.set(SPINE, -2 * e, 0, 0).set(CHEST, -3 * e, 0, 0).set(NECK, 2 * e, 0, 8 * e).set(HEAD, 3 * e, 0, 12 * e);
            stand(k, 4 + 6 * e);
            k.set(HIPS, 0, 0, 0).body(0, 0.015f * e, 0);
        });
    }

    static HumanClip bow() {
        return once("Bow", 2.2f, 36, (t, k) -> {
            float b = bump(t, 2.2f);
            k.set(RIGHT_UPPER_ARM, -30 * b, -30 * b, 0).set(RIGHT_FOREARM, -92 * b, 0, 0).set(RIGHT_HAND, 0, 0, 0);
            k.set(LEFT_UPPER_ARM, 24 * b, 10 * b, -10 * b).set(LEFT_FOREARM, -64 * b, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(HIPS, 6 * b, 0, 0).set(SPINE, 24 * b, 0, 0).set(CHEST, 16 * b, 0, 0).set(NECK, 8 * b, 0, 0).set(HEAD, 14 * b, 0, 0);
            k.set(RIGHT_THIGH, -4 * b, 0, 0).set(RIGHT_SHIN, 6 * b, 0, 0).set(RIGHT_FOOT, -2 * b, 0, 0);
            k.set(LEFT_THIGH, 6 * b, 0, 0).set(LEFT_SHIN, 8 * b, 0, 0).set(LEFT_FOOT, -10 * b, 0, 0);
            k.body(0, 0, -0.06f * b);
        });
    }

    static HumanClip tPose() {
        return loop("T-Pose", 3f, 12, (t, k) -> {
            float breath = sin(t, (float) Math.PI * 2 / 3);
            k.both(RIGHT_UPPER_ARM, 0, 0, 90).both(RIGHT_FOREARM, 0, 0, 0).both(RIGHT_HAND, 0, 0, 0);
            k.set(SPINE, 0, 0, 0).set(CHEST, -1.5f * breath, 0, 0).set(NECK, 0, 0, 0).set(HEAD, 0, 0, 0);
            k.both(RIGHT_THIGH, 0, 0, 0).both(RIGHT_SHIN, 0, 0, 0).both(RIGHT_FOOT, 0, 0, 0);
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip zombie() {
        return loop("Zombie", 2f, 48, (t, k) -> {
            float step = sin(t, (float) Math.PI * 2), lurch = Math.abs(step);
            float reach = (float) Math.PI * 2;
            k.set(RIGHT_UPPER_ARM, -88 + 6 * sin(t, reach), 4, 4).set(RIGHT_FOREARM, -8, 0, 0).set(RIGHT_HAND, 12, 0, 0);
            k.set(LEFT_UPPER_ARM, -88 + 6 * cos(t, reach), -4, -4).set(LEFT_FOREARM, -8, 0, 0).set(LEFT_HAND, 12, 0, 0);
            k.set(SPINE, 12 + 3 * lurch, 4 * step, 0).set(CHEST, 4, 0, 4 * step).set(NECK, 6, 0, 0).set(HEAD, 4, 0, 10 * sin(t, (float) Math.PI));
            k.set(HIPS, 0, -5 * step, 4 * step);
            k.set(RIGHT_THIGH, -18 * pos(step) + 6 * pos(-step), 0, 3).set(RIGHT_SHIN, 10 + 26 * pos(step), 0, 0).set(RIGHT_FOOT, -6, 0, 0);
            k.set(LEFT_THIGH, -18 * pos(-step) + 6 * pos(step), 0, -3).set(LEFT_SHIN, 10 + 26 * pos(-step), 0, 0).set(LEFT_FOOT, -6, 0, 0);
            k.body(0.03f * step, 0, 0);
        });
    }

    static HumanClip spin() {
        return loop("Spin", 1.2f, 24, (t, k) -> {
            k.spin = 300;
            float lift = sin(t, (float) Math.PI * 2 / 1.2f);
            k.both(RIGHT_UPPER_ARM, -6, 0, 74 + 6 * lift).both(RIGHT_FOREARM, -10, 0, 0).both(RIGHT_HAND, 0, 0, 6);
            k.set(SPINE, -3, 0, 0).set(CHEST, -2, 0, 0).set(NECK, -4, 0, 0).set(HEAD, -6, 0, 0);
            k.both(RIGHT_THIGH, -3, 0, 2).both(RIGHT_SHIN, 6, 0, 0).both(RIGHT_FOOT, 8, 0, 0);
            k.set(HIPS, 0, 0, 0).body(0, 0.02f, 0);
        });
    }

    static HumanClip headbang() {
        return loop("Headbang", 1.4f, 42, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 3 / 1.4f);
            float down = 0.5f + 0.5f * s;
            k.set(RIGHT_UPPER_ARM, -160, 0, 14).set(RIGHT_FOREARM, -22, 0, 0).set(RIGHT_HAND, -10 * down, 0, 0);
            k.set(LEFT_UPPER_ARM, -26 + 12 * s, 20, -8).set(LEFT_FOREARM, -84, 0, 0).set(LEFT_HAND, 10 * s, 0, 0);
            k.set(SPINE, 8 + 10 * down, 0, 0).set(CHEST, 4 + 6 * down, 0, 0).set(NECK, 6 + 14 * down, 0, 0).set(HEAD, 6 + 22 * down, 0, 0);
            k.set(HIPS, 3 * down, 0, 0);
            stand(k, 14 + 12 * down);
            k.body(0, 0, -0.02f * down);
        });
    }

    static HumanClip flex() {
        return once("Flex", 2f, 40, (t, k) -> {
            float e = hold(t, 2f, 0.3f, 0.3f), p = 5 * sin(t, 12) * e;
            k.both(RIGHT_SHOULDER, 0, 0, 8 * e).both(RIGHT_UPPER_ARM, -8 * e, -30 * e, 96 * e + p).both(RIGHT_FOREARM, -112 * e - p, 0, 0).both(RIGHT_HAND, -20 * e, 0, 0);
            k.set(SPINE, -4 * e, 0, 0).set(CHEST, -6 * e, 0, 0).set(NECK, -4 * e, 0, 0).set(HEAD, -6 * e, 0, 0);
            k.both(RIGHT_THIGH, -5 * e, 0, 9 * e).both(RIGHT_SHIN, 10 * e, 0, 0).both(RIGHT_FOOT, -5 * e, 0, -8 * e);
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip think() {
        return once("Think", 2.6f, 40, (t, k) -> {
            float e = hold(t, 2.6f, 0.4f, 0.3f), tap = 3 * sin(t, 4) * e;
            k.set(RIGHT_UPPER_ARM, -66 * e, -38 * e, -2 * e).set(RIGHT_FOREARM, -136 * e, 0, 0).set(RIGHT_HAND, 14 * e + tap, 0, 0);
            k.set(LEFT_UPPER_ARM, -32 * e, 34 * e, 0).set(LEFT_FOREARM, -92 * e, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(SPINE, 3 * e, 0, -3 * e).set(CHEST, 2 * e, 0, 0).set(NECK, -4 * e, 4 * e, 4 * e).set(HEAD, -8 * e, 6 * e, 8 * e + tap);
            k.set(HIPS, 0, 0, -4 * e);
            k.set(RIGHT_THIGH, 0, 0, 1).set(RIGHT_SHIN, 2, 0, 0).set(RIGHT_FOOT, -1, 0, 0);
            k.set(LEFT_THIGH, -9 * e, 0, -1).set(LEFT_SHIN, 18 * e, 0, 0).set(LEFT_FOOT, -9 * e, 0, 0);
            k.body(0.035f * e, 0, 0);
        });
    }

    static HumanClip yes() {
        return once("Yes", 1.6f, 40, (t, k) -> {
            float e = hold(t, 1.6f, 0.15f, 0.25f), n = sin(t, 8) * e;
            k.set(NECK, 2 + 8 * n, 0, 0).set(HEAD, 3 + 14 * n, 0, 0).set(SPINE, 1 + 2 * n, 0, 0).set(CHEST, 1 * n, 0, 0);
            k.both(RIGHT_UPPER_ARM, 2, 0, 4).both(RIGHT_FOREARM, -10, 0, 0);
            stand(k, 4 + 3 * Math.abs(n));
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip no() {
        return once("No", 1.6f, 40, (t, k) -> {
            float e = hold(t, 1.6f, 0.15f, 0.25f), n = sin(t, 9) * e;
            k.set(NECK, 2, 10 * n, 0).set(HEAD, 2, 20 * n, 0).set(SPINE, 1, -3 * n, 0).set(CHEST, 0, -2 * n, 0);
            k.both(RIGHT_UPPER_ARM, 2, 0, 4).both(RIGHT_FOREARM, -10, 0, 0);
            stand(k, 4);
            k.set(HIPS, 0, 0, 0).body(0, 0, 0);
        });
    }

    static HumanClip dance() {
        return loop("Dance", 1.6f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.6f);
            k.set(RIGHT_UPPER_ARM, -66 - 50 * s, 0, 18).set(RIGHT_FOREARM, -44 + 20 * s, 0, 0).set(RIGHT_HAND, 0, 0, 8);
            k.set(LEFT_UPPER_ARM, -66 + 50 * s, 0, -18).set(LEFT_FOREARM, -44 - 20 * s, 0, 0).set(LEFT_HAND, 0, 0, -8);
            k.set(HIPS, 0, 12 * s, 4 * s).set(SPINE, 4, -6 * s, -4 * s).set(CHEST, 2, -4 * s, -2 * s).set(NECK, 0, 4 * s, 3 * s).set(HEAD, 0, 8 * s, 4 * s);
            k.set(RIGHT_THIGH, -8 - 34 * pos(s), 0, 3).set(RIGHT_SHIN, 14 + 52 * pos(s), 0, 0).set(RIGHT_FOOT, -6 + 10 * pos(s), 0, 0);
            k.set(LEFT_THIGH, -8 - 34 * pos(-s), 0, -3).set(LEFT_SHIN, 14 + 52 * pos(-s), 0, 0).set(LEFT_FOOT, -6 + 10 * pos(-s), 0, 0);
            k.body(-0.03f * s, 0.06f * Math.abs(s), 0);
        });
    }

    static HumanClip sit() {
        return loop("Sit", 4f, 24, (t, k) -> {
            float look = sin(t, (float) Math.PI * 2 / 4), breath = sin(t, (float) Math.PI);
            // Sitting on the ground, knees up, leaning back on the hands.
            k.both(RIGHT_THIGH, -112, 0, 8).both(RIGHT_SHIN, 104, 0, 0).both(RIGHT_FOOT, -6, 0, 0);
            k.both(RIGHT_UPPER_ARM, 34, 0, 16).both(RIGHT_FOREARM, -6, 0, 0).both(RIGHT_HAND, -50, 0, 0);
            k.set(HIPS, -8, 0, 0).set(SPINE, -4 + breath, 0, 0).set(CHEST, -2, 0, 0).set(NECK, 4, 4 * look, 0).set(HEAD, 6, 10 * look, 2 * look);
            k.ground(0).body(0, -0.9f, -0.05f);
        });
    }

    static HumanClip takeTheL() {
        return loop("Take the L", 2f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2);
            k.set(RIGHT_UPPER_ARM, -140, -24, -8).set(RIGHT_FOREARM, -98, 0, 0).set(RIGHT_HAND, 8, 0, 0);
            k.set(LEFT_UPPER_ARM, -24, 0, -14).set(LEFT_FOREARM, -36, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(SPINE, 2, 0, -5 * s).set(CHEST, -2, 0, -3 * s).set(NECK, -4, 6, 0).set(HEAD, -8, 8, -8);
            k.set(HIPS, 0, 0, 6 * s);
            k.set(RIGHT_THIGH, -6 * pos(s), 0, 34 * pos(s) + 2).set(RIGHT_SHIN, 10 + 8 * pos(s), 0, 0).set(RIGHT_FOOT, -4, 0, 0);
            k.set(LEFT_THIGH, -6 * pos(-s), 0, -34 * pos(-s) - 2).set(LEFT_SHIN, 10 + 8 * pos(-s), 0, 0).set(LEFT_FOOT, -4, 0, 0);
            k.body(0.03f * s, 0.1f * Math.abs(s), 0);
        });
    }

    static HumanClip defaultDance() {
        return loop("Default Dance", 2.4f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 4 / 2.4f), side = sin(t, (float) Math.PI * 2 * 2 / 2.4f);
            k.set(RIGHT_UPPER_ARM, -78 + 54 * s, 0, 22).set(RIGHT_FOREARM, -62 - 26 * s, 0, 0).set(RIGHT_HAND, 10, 0, 0);
            k.set(LEFT_UPPER_ARM, -78 - 54 * s, 0, -22).set(LEFT_FOREARM, -62 + 26 * s, 0, 0).set(LEFT_HAND, 10, 0, 0);
            k.set(HIPS, 0, 6 * side, 6 * side).set(SPINE, 4, -4 * side, -5 * side).set(CHEST, 2, 0, -3 * side).set(NECK, 2 + 3 * Math.abs(s), 0, 3 * side).set(HEAD, 4 + 5 * Math.abs(s), 0, 6 * side);
            k.set(RIGHT_THIGH, -10 - 26 * pos(s), 0, 6).set(RIGHT_SHIN, 18 + 44 * pos(s), 0, 0).set(RIGHT_FOOT, -8 + 14 * pos(s), 0, 0);
            k.set(LEFT_THIGH, -10 - 26 * pos(-s), 0, -6).set(LEFT_SHIN, 18 + 44 * pos(-s), 0, 0).set(LEFT_FOOT, -8 + 14 * pos(-s), 0, 0);
            k.body(0.05f * side, 0.05f * Math.abs(s), 0);
        });
    }

    static HumanClip orangeJustice() {
        return loop("Orange Justice", 2f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2);
            k.set(RIGHT_UPPER_ARM, -32, 50 * s, 30 + 22 * s).set(RIGHT_FOREARM, -24, 0, 0).set(RIGHT_HAND, 0, 0, 14 * s);
            k.set(LEFT_UPPER_ARM, -32, 50 * s, -30 + 22 * s).set(LEFT_FOREARM, -24, 0, 0).set(LEFT_HAND, 0, 0, 14 * s);
            k.set(HIPS, 4, 14 * s, -6 * s).set(SPINE, 10, -6 * s, 7 * s).set(CHEST, 4, -4 * s, 4 * s).set(NECK, 0, 4 * s, 0).set(HEAD, 2, 10 * s, -4 * s);
            k.set(RIGHT_THIGH, -12 - 30 * pos(s), 0, 4 + 14 * s).set(RIGHT_SHIN, 24 + 40 * pos(s), 0, 0).set(RIGHT_FOOT, -10, 0, 0);
            k.set(LEFT_THIGH, -12 - 30 * pos(-s), 0, -4 + 14 * s).set(LEFT_SHIN, 24 + 40 * pos(-s), 0, 0).set(LEFT_FOOT, -10, 0, 0);
            k.body(-0.06f * s, 0.03f * Math.abs(s), 0);
        });
    }

    static HumanClip griddy() {
        return loop("Griddy", 1.4f, 42, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.4f);
            k.set(RIGHT_UPPER_ARM, -40 - 42 * s, 0, 22).set(RIGHT_FOREARM, -72, 0, 0).set(RIGHT_HAND, 0, 0, 0);
            k.set(LEFT_UPPER_ARM, -40 + 42 * s, 0, -22).set(LEFT_FOREARM, -72, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(HIPS, 8, 6 * s, 0).set(SPINE, 16, -4 * s, 0).set(CHEST, 8, -3 * s, 0).set(NECK, 4, 0, 0).set(HEAD, 8, 0, 0);
            // Heel taps out in front, one foot then the other, knees soft and bouncing.
            k.set(RIGHT_THIGH, -16 - 30 * pos(s), 0, 4).set(RIGHT_SHIN, 30 - 16 * pos(s), 0, 0).set(RIGHT_FOOT, -14 - 26 * pos(s), 0, 0);
            k.set(LEFT_THIGH, -16 - 30 * pos(-s), 0, -4).set(LEFT_SHIN, 30 - 16 * pos(-s), 0, 0).set(LEFT_FOOT, -14 - 26 * pos(-s), 0, 0);
            k.body(0, 0.07f * Math.abs(s), 0.02f);
        });
    }

    static HumanClip laughItUp() {
        return loop("Laugh It Up", 2.8f, 56, (t, k) -> {
            float l = sin(t, (float) Math.PI * 2 * 8 / 2.8f);
            k.set(RIGHT_UPPER_ARM, -88, -10, 2).set(RIGHT_FOREARM, -6, 0, 0).set(RIGHT_HAND, -6, 0, 0);
            k.set(LEFT_UPPER_ARM, -36, 32, -6).set(LEFT_FOREARM, -96, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(HIPS, -3, 0, 0).set(SPINE, -6 + 2 * l, 0, 0).set(CHEST, -4 + 2 * l, 0, 0).set(NECK, -8 + 3 * l, 0, 0).set(HEAD, -14 + 5 * l, 0, 3 * l);
            stand(k, 8 + 4 * Math.abs(l));
            k.body(0, 0.015f * Math.abs(l), 0);
        });
    }

    static HumanClip hype() {
        return loop("Hype", 1.4f, 42, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.4f), hop = Math.abs(s), squat = 1 - hop;
            k.both(RIGHT_UPPER_ARM, -60 + 78 * s, 0, 12).both(RIGHT_FOREARM, -30 - 20 * squat, 0, 0).both(RIGHT_HAND, 0, 0, 0);
            k.set(HIPS, 6 * squat, 0, 0).set(SPINE, 4 + 10 * squat, 0, 0).set(CHEST, 2, 0, 0).set(NECK, 0, 0, 0).set(HEAD, 6 * s, 0, 0);
            k.both(RIGHT_THIGH, -28 * squat, 0, 4).both(RIGHT_SHIN, 52 * squat, 0, 0).both(RIGHT_FOOT, -24 * squat + 18 * hop, 0, 0);
            k.body(0, 0.16f * hop * hop, 0);
        });
    }

    static HumanClip electroShuffle() {
        return loop("Electro Shuffle", 1.6f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.6f), c = cos(t, (float) Math.PI * 2 * 2 / 1.6f);
            k.set(RIGHT_UPPER_ARM, -70 + 34 * c, 0, 16).set(RIGHT_FOREARM, -76, 0, 0).set(RIGHT_HAND, 0, 0, 0);
            k.set(LEFT_UPPER_ARM, -70 - 34 * c, 0, -16).set(LEFT_FOREARM, -76, 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(HIPS, 4, 16 * s, 0).set(SPINE, 8, -8 * s, 0).set(CHEST, 2, -4 * s, 0).set(NECK, 0, 4 * s, 0).set(HEAD, 0, 6 * s, 0);
            // Feet twisting in and out on the balls, knees bouncing.
            k.both(RIGHT_THIGH, -14, 22 * s, 6 + 10 * s).both(RIGHT_SHIN, 26 + 10 * Math.abs(c), 0, 0).both(RIGHT_FOOT, -12, 0, 0);
            k.body(-0.04f * s, 0.03f * Math.abs(c), 0);
        });
    }

    static HumanClip rideThePony() {
        return loop("Ride the Pony", 1.6f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.6f), hop = Math.abs(s);
            k.set(RIGHT_UPPER_ARM, -78, 22, 2).set(RIGHT_FOREARM, -44, 0, 0).set(RIGHT_HAND, 20, 0, 0);
            k.set(LEFT_UPPER_ARM, -78, -22, -2).set(LEFT_FOREARM, -44, 0, 0).set(LEFT_HAND, 20, 0, 0);
            k.set(HIPS, 6, 0, 4 * s).set(SPINE, 10, 0, -3 * s).set(CHEST, 4, 0, 0).set(NECK, 2 + 4 * hop, 0, 0).set(HEAD, 4 + 6 * hop, 0, 0);
            k.set(RIGHT_THIGH, -14 - 30 * pos(s), 0, 4).set(RIGHT_SHIN, 24 + 50 * pos(s), 0, 0).set(RIGHT_FOOT, -10 + 14 * pos(s), 0, 0);
            k.set(LEFT_THIGH, -14 - 30 * pos(-s), 0, -4).set(LEFT_SHIN, 24 + 50 * pos(-s), 0, 0).set(LEFT_FOOT, -10 + 14 * pos(-s), 0, 0);
            k.body(0.03f * s, 0.12f * hop, 0);
        });
    }

    static HumanClip billyBounce() {
        return loop("Billy Bounce", 1.6f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.6f), hop = Math.abs(s), squat = 1 - hop;
            k.set(RIGHT_UPPER_ARM, -90 - 72 * pos(s), 0, 18).set(RIGHT_FOREARM, -30 + 20 * pos(s), 0, 0).set(RIGHT_HAND, 0, 0, 0);
            k.set(LEFT_UPPER_ARM, -90 - 72 * pos(-s), 0, -18).set(LEFT_FOREARM, -30 + 20 * pos(-s), 0, 0).set(LEFT_HAND, 0, 0, 0);
            k.set(HIPS, 6 * squat, 10 * s, 0).set(SPINE, 6 * squat, -6 * s, 0).set(CHEST, 0, 0, 0).set(NECK, -2, 4 * s, 0).set(HEAD, -4, 8 * s, 0);
            k.both(RIGHT_THIGH, -32 * squat, 0, 4).both(RIGHT_SHIN, 58 * squat, 0, 0).both(RIGHT_FOOT, -26 * squat + 20 * hop, 0, 0);
            k.body(0, 0.24f * hop * hop, 0);
        });
    }

    static HumanClip teabag() {
        return loop("Teabag", 1.2f, 36, (t, k) -> {
            float d = Math.abs(sin(t, (float) Math.PI * 2 / 1.2f));
            d = smooth(d);
            k.both(RIGHT_THIGH, -66 * d, 0, 8 * d).both(RIGHT_SHIN, 118 * d, 0, 0).both(RIGHT_FOOT, -52 * d, 0, 0);
            k.both(RIGHT_UPPER_ARM, -24 * d, 0, 8).both(RIGHT_FOREARM, -30 * d - 6, 0, 0).both(RIGHT_HAND, 0, 0, 0);
            k.set(HIPS, 10 * d, 0, 0).set(SPINE, 18 * d, 0, 0).set(CHEST, 8 * d, 0, 0).set(NECK, -10 * d, 0, 0).set(HEAD, -6 * d, 0, 0);
            k.body(0, 0, -0.05f * d);
        });
    }

    static HumanClip crabwalk() {
        return loop("Crabwalk", 1.6f, 48, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 * 2 / 1.6f), snap = sin(t, (float) Math.PI * 2 * 5 / 1.6f);
            k.set(RIGHT_UPPER_ARM, -146, 16, 52 + 12 * s).set(RIGHT_FOREARM, -70, 0, 0).set(RIGHT_HAND, 18 * snap, 0, 0);
            k.set(LEFT_UPPER_ARM, -146, -16, -52 + 12 * s).set(LEFT_FOREARM, -70, 0, 0).set(LEFT_HAND, 18 * snap, 0, 0);
            k.set(HIPS, 6, 10 * s, 0).set(SPINE, 10, -6 * s, 4 * s).set(CHEST, 4, 0, 0).set(NECK, 0, 0, 0).set(HEAD, -4, 14 * s, 0);
            k.set(RIGHT_THIGH, -34, 0, 22 + 14 * s).set(RIGHT_SHIN, 56 + 10 * pos(s), 0, 0).set(RIGHT_FOOT, -20, 0, -10);
            k.set(LEFT_THIGH, -34, 0, -22 + 14 * s).set(LEFT_SHIN, 56 + 10 * pos(-s), 0, 0).set(LEFT_FOOT, -20, 0, 10);
            k.body(-0.1f * s, 0.03f * Math.abs(s), 0);
        });
    }

    // ---- full-body dances

    /** High kicks, right then left, arms out for balance. */
    static HumanClip highKick() {
        return loop("High Kick", 2f, 64, (t, k) -> {
            float p = t % 1f;
            boolean right = t < 1f;
            float kick = (float) Math.pow(Math.sin(Math.PI * smooth(p * 1.15f)), 2);
            float kr = right ? kick : 0, kl = right ? 0 : kick;
            k.set(RIGHT_THIGH, -98 * kr + 4 * kl, 0, 2).set(RIGHT_SHIN, 6 + 4 * (1 - kr) + 10 * kl, 0, 0).set(RIGHT_FOOT, 28 * kr - 4 * kl, 0, 0);
            k.set(LEFT_THIGH, -98 * kl + 4 * kr, 0, -2).set(LEFT_SHIN, 6 + 4 * (1 - kl) + 10 * kr, 0, 0).set(LEFT_FOOT, 28 * kl - 4 * kr, 0, 0);
            k.set(RIGHT_UPPER_ARM, -14 + 22 * kr, 0, 62 + 12 * kick).set(RIGHT_FOREARM, -14, 0, 0).set(RIGHT_HAND, 0, 0, 10);
            k.set(LEFT_UPPER_ARM, -14 + 22 * kl, 0, -62 - 12 * kick).set(LEFT_FOREARM, -14, 0, 0).set(LEFT_HAND, 0, 0, -10);
            k.set(HIPS, -8 * kick, 0, 4 * (kr - kl)).set(SPINE, -10 * kick, 0, -3 * (kr - kl)).set(CHEST, -4 * kick, 0, 0).set(NECK, 6 * kick, 0, 0).set(HEAD, 8 * kick, 0, 0);
            k.body(0.05f * (kl - kr) * kick, 0.02f * kick, -0.08f * kick);
        });
    }

    /** Deep side lunges, low to one side then the other, arms sweeping through. */
    static HumanClip lungeDance() {
        return loop("Lunge Dance", 2.4f, 64, (t, k) -> {
            float side = sin(t, (float) Math.PI * 2 / 2.4f);
            float r = pos(side), l = pos(-side), low = Math.abs(side), beat = Math.abs(sin(t, (float) Math.PI * 2 * 2 / 2.4f));
            // The bent leg takes the weight; the other reaches out straight to the side.
            k.set(RIGHT_THIGH, -48 * r - 6 * l, 0, 22 * r + 32 * l).set(RIGHT_SHIN, 92 * r + 4 * l, 0, 0).set(RIGHT_FOOT, -40 * r + 4 * l, 0, -14 * l);
            k.set(LEFT_THIGH, -48 * l - 6 * r, 0, -22 * l - 32 * r).set(LEFT_SHIN, 92 * l + 4 * r, 0, 0).set(LEFT_FOOT, -40 * l + 4 * r, 0, 14 * r);
            k.set(RIGHT_UPPER_ARM, -60 - 60 * l + 20 * r, -30 * r, 30 + 40 * r).set(RIGHT_FOREARM, -30 - 20 * beat, 0, 0).set(RIGHT_HAND, 0, 0, 10);
            k.set(LEFT_UPPER_ARM, -60 - 60 * r + 20 * l, 30 * l, -30 - 40 * l).set(LEFT_FOREARM, -30 - 20 * beat, 0, 0).set(LEFT_HAND, 0, 0, -10);
            k.set(HIPS, 10 * low, 8 * side, 8 * side).set(SPINE, 16 * low, -6 * side, -10 * side).set(CHEST, 4, -4 * side, -4 * side);
            k.set(NECK, -6 * low, 6 * side, 4 * side).set(HEAD, -8 * low, 10 * side, 6 * side);
            k.body(-0.32f * side, 0, 0);
        });
    }

    /** Rising on the toes to reach up and out, one hand then the other, stretching the whole body. */
    static HumanClip reachOut() {
        return loop("Reach Out", 2.6f, 52, (t, k) -> {
            float s = sin(t, (float) Math.PI * 2 / 2.6f), up = 0.5f + 0.5f * cos(t, (float) Math.PI * 4 / 2.6f);
            k.set(RIGHT_SHOULDER, 0, 0, 10 + 8 * pos(s)).set(RIGHT_UPPER_ARM, -142 - 22 * pos(s), -10, 18 - 8 * pos(s)).set(RIGHT_FOREARM, -12 + 8 * pos(s), 0, 0).set(RIGHT_HAND, -20, 0, 0);
            k.set(LEFT_SHOULDER, 0, 0, -10 - 8 * pos(-s)).set(LEFT_UPPER_ARM, -142 - 22 * pos(-s), 10, -18 + 8 * pos(-s)).set(LEFT_FOREARM, -12 + 8 * pos(-s), 0, 0).set(LEFT_HAND, -20, 0, 0);
            k.set(HIPS, -3 * up, 4 * s, -4 * s).set(SPINE, -8 * up, 6 * s, 6 * s).set(CHEST, -6 * up, 4 * s, 4 * s).set(NECK, -8 * up, 0, 0).set(HEAD, -14 * up, 6 * s, 0);
            // Up on the balls of the feet as the arms reach.
            k.both(RIGHT_THIGH, 2, 0, 1).both(RIGHT_SHIN, 4 * (1 - up), 0, 0).both(RIGHT_FOOT, 34 * up, 0, 0).both(RIGHT_TOE, -34 * up, 0, 0);
            k.body(0.03f * s, 0, 0);
        });
    }
}
