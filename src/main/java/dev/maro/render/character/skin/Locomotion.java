package dev.maro.render.character.skin;

import static dev.maro.render.character.skin.Human.*;

/**
 * The shared movement clips every humanoid model moves with when its own GLB has none for a
 * state. They are keyed from ordinary human gait: a walk's hip, knee and ankle curves through a
 * stride, a run's longer flight and deeper knee, and so on. Cycles are keyed in fractions of a
 * stride, which starts as the right foot touches down.
 */
public final class Locomotion {
    private Locomotion() {
    }

    public enum State {
        IDLE, WALK, RUN, CROUCH, CROUCH_WALK, JUMP, FALL, LAND, SWIM, RIDE, SLEEP, GLIDE;
        /** The clip's steps follow the ground, so it plays as fast as the body moves. */
        public boolean strides() {
            return this == WALK || this == RUN || this == CROUCH_WALK || this == SWIM;
        }
    }

    public static final HumanClip IDLE = HumanClip.loop("Idle", 4.8f)
            // Breathing in the chest, the weight easing from foot to foot, the head looking round a little.
            .key(SPINE, 0, 1, 0, 0).key(SPINE, 2.4f, 2, 0, 0)
            .key(CHEST, 0, 0, 0, 0).key(CHEST, 1.2f, -1.5f, 0, 0.6f).key(CHEST, 2.4f, 0, 0, 0).key(CHEST, 3.6f, -1.5f, 0, -0.6f)
            .key(NECK, 0, 2, 0, 0).key(NECK, 2.4f, 3, 0, 0)
            .key(HEAD, 0, 0, 0, 0).key(HEAD, 1.6f, 1, 4, 1).key(HEAD, 3.2f, -1, -3, -1)
            .key(HIPS, 0, 0, 0, 1.2f).key(HIPS, 2.4f, 0, 0, -1.2f)
            .key(RIGHT_UPPER_ARM, 0, 2, 0, 2).key(RIGHT_UPPER_ARM, 2.4f, -1, 0, 3)
            .key(LEFT_UPPER_ARM, 0, -1, 0, -3).key(LEFT_UPPER_ARM, 2.4f, 2, 0, -2)
            .key(RIGHT_FOREARM, 0, -10, 0, 0).key(RIGHT_FOREARM, 2.4f, -13, 0, 0)
            .key(LEFT_FOREARM, 0, -13, 0, 0).key(LEFT_FOREARM, 2.4f, -10, 0, 0)
            .hold(RIGHT_HAND, 0, 0, 3).hold(LEFT_HAND, 0, 0, -3)
            // One knee eases, then the other, as the hips shift over the standing leg.
            .key(RIGHT_THIGH, 0, -2, 0, 1).key(RIGHT_THIGH, 2.4f, -5, 0, 2)
            .key(RIGHT_SHIN, 0, 2, 0, 0).key(RIGHT_SHIN, 2.4f, 9, 0, 0)
            .key(RIGHT_FOOT, 0, 0, 0, 0).key(RIGHT_FOOT, 2.4f, -4, 0, 0)
            .key(LEFT_THIGH, 0, -5, 0, -2).key(LEFT_THIGH, 2.4f, -2, 0, -1)
            .key(LEFT_SHIN, 0, 9, 0, 0).key(LEFT_SHIN, 2.4f, 2, 0, 0)
            .key(LEFT_FOOT, 0, -4, 0, 0).key(LEFT_FOOT, 2.4f, 0, 0, 0)
            .shift(0, -0.012f, 0, 0).shift(2.4f, 0.012f, 0, 0)
            .build();

    /**
     * One stride: the right heel strikes at 0, the left at 0.5. Minecraft walks at a brisk 4.3
     * blocks a second, so this is a long, quick walk: about 2.6 leg lengths a stride.
     */
    public static final HumanClip WALK = HumanClip.cycle("Walk", 1.0f)
            .key(RIGHT_THIGH, 0.00f, -40, 0, 1).key(RIGHT_THIGH, 0.12f, -31, 0, 1).key(RIGHT_THIGH, 0.30f, -6, 0, 1)
            .key(RIGHT_THIGH, 0.45f, 13, 0, 1).key(RIGHT_THIGH, 0.55f, 19, 0, 1).key(RIGHT_THIGH, 0.65f, 5, 0, 2)
            .key(RIGHT_THIGH, 0.75f, -24, 0, 2).key(RIGHT_THIGH, 0.87f, -42, 0, 1)
            .key(RIGHT_SHIN, 0.00f, 4, 0, 0).key(RIGHT_SHIN, 0.12f, 18, 0, 0).key(RIGHT_SHIN, 0.30f, 6, 0, 0)
            .key(RIGHT_SHIN, 0.45f, 7, 0, 0).key(RIGHT_SHIN, 0.55f, 30, 0, 0).key(RIGHT_SHIN, 0.65f, 62, 0, 0)
            .key(RIGHT_SHIN, 0.73f, 72, 0, 0).key(RIGHT_SHIN, 0.87f, 36, 0, 0).key(RIGHT_SHIN, 0.95f, 8, 0, 0)
            // The ankle keeps the foot flat while it bears weight (foot = -(thigh + knee)), then pushes off.
            .key(RIGHT_FOOT, 0.00f, 22, 0, 0).key(RIGHT_FOOT, 0.12f, 13, 0, 0).key(RIGHT_FOOT, 0.30f, 0, 0, 0)
            .key(RIGHT_FOOT, 0.45f, -18, 0, 0).key(RIGHT_FOOT, 0.55f, -14, 0, 0).key(RIGHT_FOOT, 0.62f, -4, 0, 0)
            .key(RIGHT_FOOT, 0.70f, -30, 0, 0).key(RIGHT_FOOT, 0.80f, -20, 0, 0).key(RIGHT_FOOT, 0.90f, 4, 0, 0).key(RIGHT_FOOT, 0.97f, 18, 0, 0)
            .key(RIGHT_TOE, 0.00f, 0, 0, 0).key(RIGHT_TOE, 0.50f, 0, 0, 0).key(RIGHT_TOE, 0.58f, -28, 0, 0).key(RIGHT_TOE, 0.64f, -10, 0, 0)
            .key(RIGHT_TOE, 0.72f, 0, 0, 0)
            // Arms swing against the legs, the elbow bending more as it comes forward.
            .key(RIGHT_UPPER_ARM, 0.00f, 24, 0, 4).key(RIGHT_UPPER_ARM, 0.25f, 3, 0, 5).key(RIGHT_UPPER_ARM, 0.50f, -26, 0, 5).key(RIGHT_UPPER_ARM, 0.75f, 0, 0, 4)
            .key(RIGHT_FOREARM, 0.00f, -12, 0, 0).key(RIGHT_FOREARM, 0.25f, -18, 0, 0).key(RIGHT_FOREARM, 0.50f, -36, 0, 0).key(RIGHT_FOREARM, 0.75f, -20, 0, 0)
            .key(RIGHT_HAND, 0.00f, 6, 0, 3).key(RIGHT_HAND, 0.50f, -6, 0, 3)
            .mirrorAll(0.5f)
            // The pelvis turns with the forward leg and drops over the swinging one, the chest turns against it.
            .key(HIPS, 0.00f, 0, -6, -2).key(HIPS, 0.25f, 0, 0, 2.5f).key(HIPS, 0.50f, 0, 6, 2).key(HIPS, 0.75f, 0, 0, -2.5f)
            .key(SPINE, 0.00f, 4, 4, 1).key(SPINE, 0.50f, 4, -4, -1)
            .key(CHEST, 0.00f, 1, 4, 0).key(CHEST, 0.50f, 1, -4, 0)
            .key(NECK, 0.00f, -2, -2, 0).key(NECK, 0.50f, -2, 2, 0)
            .key(HEAD, 0.00f, -2, -1, 0).key(HEAD, 0.25f, -1, 0, 0).key(HEAD, 0.50f, -2, 1, 0).key(HEAD, 0.75f, -1, 0, 0)
            // Weight over the standing foot.
            .shifts(0.014f, 0, 0, 0, 0, 0, -0.014f, 0, 0, 0, 0, 0)
            .build();

    /** A sprint: long strides with both feet off the ground between them, arms pumping. */
    public static final HumanClip RUN = HumanClip.cycle("Run", 0.68f)
            .key(RIGHT_THIGH, 0.00f, -44, 0, 1).key(RIGHT_THIGH, 0.14f, -16, 0, 1).key(RIGHT_THIGH, 0.30f, 18, 0, 1)
            .key(RIGHT_THIGH, 0.40f, 26, 0, 1).key(RIGHT_THIGH, 0.55f, 4, 0, 2).key(RIGHT_THIGH, 0.70f, -50, 0, 2)
            .key(RIGHT_THIGH, 0.85f, -64, 0, 1)
            .key(RIGHT_SHIN, 0.00f, 18, 0, 0).key(RIGHT_SHIN, 0.12f, 40, 0, 0).key(RIGHT_SHIN, 0.30f, 24, 0, 0)
            .key(RIGHT_SHIN, 0.40f, 34, 0, 0).key(RIGHT_SHIN, 0.55f, 96, 0, 0).key(RIGHT_SHIN, 0.66f, 120, 0, 0)
            .key(RIGHT_SHIN, 0.80f, 84, 0, 0).key(RIGHT_SHIN, 0.92f, 34, 0, 0)
            .key(RIGHT_FOOT, 0.00f, 16, 0, 0).key(RIGHT_FOOT, 0.12f, -22, 0, 0).key(RIGHT_FOOT, 0.30f, -30, 0, 0)
            .key(RIGHT_FOOT, 0.40f, 4, 0, 0).key(RIGHT_FOOT, 0.55f, -16, 0, 0).key(RIGHT_FOOT, 0.70f, -22, 0, 0)
            .key(RIGHT_FOOT, 0.85f, -8, 0, 0).key(RIGHT_FOOT, 0.95f, 8, 0, 0)
            .key(RIGHT_TOE, 0.00f, 0, 0, 0).key(RIGHT_TOE, 0.30f, -6, 0, 0).key(RIGHT_TOE, 0.36f, -28, 0, 0).key(RIGHT_TOE, 0.48f, -5, 0, 0)
            .key(RIGHT_TOE, 0.60f, 0, 0, 0)
            .key(RIGHT_UPPER_ARM, 0.00f, 44, 0, 6).key(RIGHT_UPPER_ARM, 0.25f, 6, 0, 8).key(RIGHT_UPPER_ARM, 0.50f, -48, 0, 6).key(RIGHT_UPPER_ARM, 0.75f, -6, 0, 7)
            .key(RIGHT_FOREARM, 0.00f, -62, 0, 0).key(RIGHT_FOREARM, 0.25f, -82, 0, 0).key(RIGHT_FOREARM, 0.50f, -100, 0, 0).key(RIGHT_FOREARM, 0.75f, -80, 0, 0)
            .key(RIGHT_HAND, 0.00f, 6, 0, 6).key(RIGHT_HAND, 0.50f, -8, 0, 6)
            .mirrorAll(0.5f)
            .key(HIPS, 0.00f, 3, -7, -2).key(HIPS, 0.25f, 3, 0, 2).key(HIPS, 0.50f, 3, 7, 2).key(HIPS, 0.75f, 3, 0, -2)
            .key(SPINE, 0.00f, 8, 6, 1).key(SPINE, 0.50f, 8, -6, -1)
            .key(CHEST, 0.00f, 3, 7, 0).key(CHEST, 0.50f, 3, -7, 0)
            .key(NECK, 0.00f, -7, -3, 0).key(NECK, 0.50f, -7, 3, 0)
            .key(HEAD, 0.00f, -5, -2, 0).key(HEAD, 0.50f, -5, 2, 0)
            // Up through the flight between steps (right toe-off to left touch-down and back).
            .shift(0.00f, 0.01f, 0, 0).shift(0.30f, 0.004f, 0, 0).shift(0.42f, -0.004f, 0.03f, 0)
            .shift(0.50f, -0.01f, 0, 0).shift(0.80f, -0.004f, 0, 0).shift(0.92f, 0.004f, 0.03f, 0)
            // Off the ground between steps: the feet are free there, not held to the floor.
            .ground(0.00f, 1).ground(0.30f, 1).ground(0.42f, 0.65f).ground(0.50f, 1).ground(0.80f, 1).ground(0.92f, 0.65f)
            .build();

    /** Crouched still: hips and knees bent, the trunk forward over the feet, eyes up. */
    public static final HumanClip CROUCH = HumanClip.loop("Crouch", 4f)
            .hold(HIPS, 4, 0, 0)
            .key(SPINE, 0, 20, 0, 0).key(SPINE, 2, 21.5f, 0, 0)
            .key(CHEST, 0, 8, 0, 0).key(CHEST, 2, 7, 0, 0)
            .hold(NECK, -12, 0, 0).hold(HEAD, -16, 0, 0)
            .hold(RIGHT_THIGH, -46, 0, 4).hold(LEFT_THIGH, -46, 0, -4)
            .hold(RIGHT_SHIN, 74, 0, 0).hold(LEFT_SHIN, 74, 0, 0)
            .hold(RIGHT_FOOT, -24, 0, 0).hold(LEFT_FOOT, -24, 0, 0)
            .key(RIGHT_UPPER_ARM, 0, -18, 0, 9).key(RIGHT_UPPER_ARM, 2, -20, 0, 9)
            .key(LEFT_UPPER_ARM, 0, -20, 0, -9).key(LEFT_UPPER_ARM, 2, -18, 0, -9)
            .hold(RIGHT_FOREARM, -32, 0, 0).hold(LEFT_FOREARM, -32, 0, 0)
            .build();

    /** Creeping: the crouch, with short steps. */
    public static final HumanClip CROUCH_WALK = HumanClip.cycle("Crouch Walk", 1.1f)
            .key(RIGHT_THIGH, 0.00f, -60, 0, 4).key(RIGHT_THIGH, 0.30f, -40, 0, 4).key(RIGHT_THIGH, 0.55f, -30, 0, 4)
            .key(RIGHT_THIGH, 0.75f, -55, 0, 5)
            .key(RIGHT_SHIN, 0.00f, 66, 0, 0).key(RIGHT_SHIN, 0.30f, 74, 0, 0).key(RIGHT_SHIN, 0.55f, 80, 0, 0)
            .key(RIGHT_SHIN, 0.70f, 100, 0, 0).key(RIGHT_SHIN, 0.85f, 80, 0, 0)
            .key(RIGHT_FOOT, 0.00f, -6, 0, 0).key(RIGHT_FOOT, 0.30f, -34, 0, 0).key(RIGHT_FOOT, 0.55f, -42, 0, 0)
            .key(RIGHT_FOOT, 0.70f, -20, 0, 0).key(RIGHT_FOOT, 0.85f, -14, 0, 0)
            .key(RIGHT_UPPER_ARM, 0.00f, -8, 0, 9).key(RIGHT_UPPER_ARM, 0.50f, -28, 0, 9)
            .key(RIGHT_FOREARM, 0.00f, -28, 0, 0).key(RIGHT_FOREARM, 0.50f, -38, 0, 0)
            .mirrorAll(0.5f)
            .hold(HIPS, 4, 0, 0)
            .key(SPINE, 0.00f, 21, 3, 0).key(SPINE, 0.50f, 21, -3, 0)
            .hold(CHEST, 7, 0, 0).hold(NECK, -12, 0, 0).hold(HEAD, -16, 0, 0)
            .build();

    /** Going up: knees drawn up, arms thrown up and forward. */
    public static final HumanClip JUMP = HumanClip.once("Jump", 0.45f)
            .key(SPINE, 0, -4, 0, 0).key(SPINE, 0.45f, 2, 0, 0)
            .key(CHEST, 0, -3, 0, 0).key(CHEST, 0.45f, 0, 0, 0)
            .hold(NECK, -3, 0, 0).hold(HEAD, -4, 0, 0)
            .key(RIGHT_UPPER_ARM, 0, -60, 0, 12).key(RIGHT_UPPER_ARM, 0.45f, -40, 0, 18)
            .key(LEFT_UPPER_ARM, 0, -48, 0, -14).key(LEFT_UPPER_ARM, 0.45f, -32, 0, -20)
            .key(RIGHT_FOREARM, 0, -30, 0, 0).key(RIGHT_FOREARM, 0.45f, -38, 0, 0)
            .key(LEFT_FOREARM, 0, -26, 0, 0).key(LEFT_FOREARM, 0.45f, -34, 0, 0)
            .key(RIGHT_THIGH, 0, -38, 0, 3).key(RIGHT_THIGH, 0.45f, -46, 0, 3)
            .key(RIGHT_SHIN, 0, 60, 0, 0).key(RIGHT_SHIN, 0.45f, 72, 0, 0)
            .key(RIGHT_FOOT, 0, 10, 0, 0).key(RIGHT_FOOT, 0.45f, 6, 0, 0)
            .key(LEFT_THIGH, 0, -8, 0, -3).key(LEFT_THIGH, 0.45f, -18, 0, -3)
            .key(LEFT_SHIN, 0, 30, 0, 0).key(LEFT_SHIN, 0.45f, 48, 0, 0)
            .key(LEFT_FOOT, 0, 22, 0, 0).key(LEFT_FOOT, 0.45f, 14, 0, 0)
            .ground(0, 0)
            .build();

    /** Coming down: arms out for balance and a little flailing, legs reaching for the ground. */
    public static final HumanClip FALL = HumanClip.loop("Fall", 1.6f)
            .key(SPINE, 0, 4, 0, 0).key(SPINE, 0.8f, 6, 0, 0)
            .hold(CHEST, 2, 0, 0).hold(NECK, -8, 0, 0).hold(HEAD, -10, 0, 0)
            .key(RIGHT_UPPER_ARM, 0, -30, 0, 48).key(RIGHT_UPPER_ARM, 0.8f, -40, 0, 60)
            .key(LEFT_UPPER_ARM, 0, -40, 0, -60).key(LEFT_UPPER_ARM, 0.8f, -30, 0, -48)
            .key(RIGHT_FOREARM, 0, -30, 0, 0).key(RIGHT_FOREARM, 0.8f, -22, 0, 0)
            .key(LEFT_FOREARM, 0, -22, 0, 0).key(LEFT_FOREARM, 0.8f, -30, 0, 0)
            .key(RIGHT_THIGH, 0, -22, 0, 6).key(RIGHT_THIGH, 0.8f, -12, 0, 6)
            .key(RIGHT_SHIN, 0, 30, 0, 0).key(RIGHT_SHIN, 0.8f, 18, 0, 0)
            .key(LEFT_THIGH, 0, -12, 0, -6).key(LEFT_THIGH, 0.8f, -22, 0, -6)
            .key(LEFT_SHIN, 0, 18, 0, 0).key(LEFT_SHIN, 0.8f, 30, 0, 0)
            .hold(RIGHT_FOOT, 12, 0, 0).hold(LEFT_FOOT, 12, 0, 0)
            .ground(0, 0)
            .build();

    /** Touching down: the knees take it, the trunk dips forward, then it all comes back up. */
    public static final HumanClip LAND = HumanClip.once("Land", 0.42f)
            .key(HIPS, 0, 0, 0, 0).key(HIPS, 0.12f, 6, 0, 0).key(HIPS, 0.42f, 0, 0, 0)
            .key(SPINE, 0, 6, 0, 0).key(SPINE, 0.12f, 18, 0, 0).key(SPINE, 0.42f, 2, 0, 0)
            .key(CHEST, 0, 2, 0, 0).key(CHEST, 0.12f, 8, 0, 0).key(CHEST, 0.42f, 0, 0, 0)
            .key(NECK, 0, -4, 0, 0).key(NECK, 0.12f, -12, 0, 0).key(NECK, 0.42f, 2, 0, 0)
            .key(RIGHT_THIGH, 0, -22, 0, 4).key(RIGHT_THIGH, 0.12f, -48, 0, 5).key(RIGHT_THIGH, 0.42f, -3, 0, 1)
            .key(RIGHT_SHIN, 0, 26, 0, 0).key(RIGHT_SHIN, 0.12f, 82, 0, 0).key(RIGHT_SHIN, 0.42f, 3, 0, 0)
            .key(RIGHT_FOOT, 0, -4, 0, 0).key(RIGHT_FOOT, 0.12f, -32, 0, 0).key(RIGHT_FOOT, 0.42f, 0, 0, 0)
            .key(RIGHT_UPPER_ARM, 0, -28, 0, 24).key(RIGHT_UPPER_ARM, 0.12f, -32, 0, 18).key(RIGHT_UPPER_ARM, 0.42f, 2, 0, 3)
            .key(RIGHT_FOREARM, 0, -26, 0, 0).key(RIGHT_FOREARM, 0.12f, -34, 0, 0).key(RIGHT_FOREARM, 0.42f, -10, 0, 0)
            .mirrorAll(0)
            .build();

    /** Front crawl, lying along the water (the game turns the body): arms reaching over the head in turn, legs kicking. */
    public static final HumanClip SWIM = HumanClip.cycle("Swim", 1.3f)
            .key(RIGHT_UPPER_ARM, 0.00f, -175, 0, 10).key(RIGHT_UPPER_ARM, 0.25f, -110, 0, 18).key(RIGHT_UPPER_ARM, 0.50f, -20, 0, 8)
            .key(RIGHT_UPPER_ARM, 0.65f, 10, 0, 14).key(RIGHT_UPPER_ARM, 0.82f, -90, 0, 70)
            .key(RIGHT_FOREARM, 0.00f, -8, 0, 0).key(RIGHT_FOREARM, 0.25f, -45, 0, 0).key(RIGHT_FOREARM, 0.50f, -30, 0, 0)
            .key(RIGHT_FOREARM, 0.65f, -12, 0, 0).key(RIGHT_FOREARM, 0.82f, -85, 0, 0)
            .key(RIGHT_THIGH, 0.00f, -12, 0, 3).key(RIGHT_THIGH, 0.25f, 10, 0, 3).key(RIGHT_THIGH, 0.50f, -12, 0, 3).key(RIGHT_THIGH, 0.75f, 10, 0, 3)
            .key(RIGHT_SHIN, 0.00f, 25, 0, 0).key(RIGHT_SHIN, 0.25f, 5, 0, 0).key(RIGHT_SHIN, 0.50f, 25, 0, 0).key(RIGHT_SHIN, 0.75f, 5, 0, 0)
            .hold(RIGHT_FOOT, 35, 0, 0)
            .mirrorAll(0.5f)
            .key(HIPS, 0.00f, 0, 0, 6).key(HIPS, 0.50f, 0, 0, -6)
            .key(CHEST, 0.00f, 0, 8, 10).key(CHEST, 0.50f, 0, -8, -10)
            .hold(NECK, -22, 0, 0).hold(HEAD, -18, 0, 0)
            .ground(0, 0)
            .build();

    /** Sitting on a mount or in a boat: thighs forward, shins down, hands on the reins. */
    public static final HumanClip RIDE = HumanClip.loop("Ride", 3.2f)
            .hold(RIGHT_THIGH, -82, 0, 12).hold(LEFT_THIGH, -82, 0, -12)
            .hold(RIGHT_SHIN, 78, 0, 0).hold(LEFT_SHIN, 78, 0, 0)
            .hold(RIGHT_FOOT, 4, 0, 0).hold(LEFT_FOOT, 4, 0, 0)
            .key(SPINE, 0, 4, 0, 0).key(SPINE, 1.6f, 6, 0, 0)
            .hold(CHEST, 2, 0, 0)
            .key(RIGHT_UPPER_ARM, 0, -30, 0, 6).key(RIGHT_UPPER_ARM, 1.6f, -33, 0, 6)
            .key(LEFT_UPPER_ARM, 0, -33, 0, -6).key(LEFT_UPPER_ARM, 1.6f, -30, 0, -6)
            .hold(RIGHT_FOREARM, -48, 0, 0).hold(LEFT_FOREARM, -48, 0, 0)
            .ground(0, 0)
            .build();

    /** Asleep in bed (the game lays the body down): relaxed, breathing slowly. */
    public static final HumanClip SLEEP = HumanClip.loop("Sleep", 5f)
            .key(CHEST, 0, 0, 0, 0).key(CHEST, 2.5f, -2, 0, 0)
            .hold(HEAD, 4, 12, 4)
            .hold(RIGHT_UPPER_ARM, 4, 0, 4).hold(LEFT_UPPER_ARM, -6, 0, -10)
            .hold(RIGHT_FOREARM, -20, 0, 0).hold(LEFT_FOREARM, -32, 0, 0)
            .hold(RIGHT_THIGH, -6, 0, 2).hold(LEFT_THIGH, -16, 0, -3)
            .hold(RIGHT_SHIN, 8, 0, 0).hold(LEFT_SHIN, 22, 0, 0)
            .ground(0, 0)
            .build();

    /** Flying with wings (the game tilts the body): arms swept back, legs together. */
    public static final HumanClip GLIDE = HumanClip.loop("Glide", 2f)
            .key(RIGHT_UPPER_ARM, 0, 18, 0, 22).key(RIGHT_UPPER_ARM, 1, 22, 0, 26)
            .key(LEFT_UPPER_ARM, 0, 22, 0, -26).key(LEFT_UPPER_ARM, 1, 18, 0, -22)
            .hold(RIGHT_FOREARM, -8, 0, 0).hold(LEFT_FOREARM, -8, 0, 0)
            .hold(RIGHT_THIGH, 4, 0, 2).hold(LEFT_THIGH, 4, 0, -2)
            .hold(RIGHT_SHIN, 8, 0, 0).hold(LEFT_SHIN, 8, 0, 0)
            .hold(RIGHT_FOOT, 30, 0, 0).hold(LEFT_FOOT, 30, 0, 0)
            .hold(SPINE, -6, 0, 0).hold(NECK, -18, 0, 0).hold(HEAD, -16, 0, 0)
            .ground(0, 0)
            .build();

    public static HumanClip clip(State state) {
        return switch (state) {
            case IDLE -> IDLE;
            case WALK -> WALK;
            case RUN -> RUN;
            case CROUCH -> CROUCH;
            case CROUCH_WALK -> CROUCH_WALK;
            case JUMP -> JUMP;
            case FALL -> FALL;
            case LAND -> LAND;
            case SWIM -> SWIM;
            case RIDE -> RIDE;
            case SLEEP -> SLEEP;
            case GLIDE -> GLIDE;
        };
    }
}
