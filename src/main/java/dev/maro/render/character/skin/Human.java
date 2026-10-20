package dev.maro.render.character.skin;

/**
 * The shared humanoid skeleton every clip is written for. Clips turn these bones; a {@link Rig}
 * maps each to a node of its own skeleton (or none).
 *
 * <p>Angles in clips use the player model's familiar rules, in degrees: a hanging arm or leg
 * swings forward with negative pitch and back with positive; a right limb's positive roll lifts
 * it out to the right (a left one's, negative roll to the left); positive pitch bends a knee back,
 * an elbow's negative pitch brings the forearm forward, and the spine, neck or head lean forward
 * with positive pitch. Yaw turns about the vertical, positive to the character's right, as a
 * player's head turns; the trunk's or head's positive roll tilts its top to the character's left.
 *
 * <p>The character frame is X to the character's left, Y up and Z forward. A bone's turn is about
 * its joint, relative to its parent's, along these axes as its parent has turned them.
 */
public enum Human {
    HIPS, SPINE, CHEST, NECK, HEAD,
    RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_FOREARM, RIGHT_HAND,
    LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_FOREARM, LEFT_HAND,
    RIGHT_THIGH, RIGHT_SHIN, RIGHT_FOOT, RIGHT_TOE,
    LEFT_THIGH, LEFT_SHIN, LEFT_FOOT, LEFT_TOE;

    public static final Human[] ALL = values();
    public static final int COUNT = ALL.length;

    public boolean right() {
        return name().startsWith("RIGHT");
    }

    public boolean left() {
        return name().startsWith("LEFT");
    }

    public boolean arm() {
        return this == RIGHT_SHOULDER || this == RIGHT_UPPER_ARM || this == RIGHT_FOREARM || this == RIGHT_HAND
                || this == LEFT_SHOULDER || this == LEFT_UPPER_ARM || this == LEFT_FOREARM || this == LEFT_HAND;
    }

    public boolean leg() {
        return this == RIGHT_THIGH || this == RIGHT_SHIN || this == RIGHT_FOOT || this == RIGHT_TOE
                || this == LEFT_THIGH || this == LEFT_SHIN || this == LEFT_FOOT || this == LEFT_TOE;
    }

    /** The trunk above the hips: what an upper-body emote moves along with the arms. */
    public boolean upper() {
        return this == SPINE || this == CHEST || this == NECK || this == HEAD || arm();
    }

    /** The same bone on the other side (itself for the trunk). */
    public Human mirror() {
        String n = name();
        if (n.startsWith("RIGHT_")) return valueOf("LEFT_" + n.substring(6));
        if (n.startsWith("LEFT_")) return valueOf("RIGHT_" + n.substring(5));
        return this;
    }

    /**
     * A rotation in clip terms (degrees, the player model's sense) as a quaternion in the
     * character frame: pitch about X first, then yaw about Y, then roll about Z.
     */
    public static void euler(float pitch, float yaw, float roll, float[] out, int o) {
        float d = (float) (Math.PI / 180);
        SkinMath.eulerZYX(pitch * d, -yaw * d, -roll * d, out, o);
    }

    /** The same as {@link #euler} for the player model's own angles in radians. */
    public static void vanilla(float pitch, float yaw, float roll, float[] out, int o) {
        SkinMath.eulerZYX(pitch, -yaw, -roll, out, o);
    }
}
