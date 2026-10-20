package dev.maro.render.character.skin;

/**
 * A pose of the shared skeleton: each {@link Human} bone's turn from canonical rest (a quaternion
 * in the character frame), and the body's shift: sideways and forward sway and lift off the
 * ground, in leg lengths.
 */
public final class HumanPose {
    public final float[] rot = new float[Human.COUNT * 4];
    /** Sway left (+x) and forward (+z), and lift above the planted feet (+y), in leg lengths. */
    public final float[] shift = new float[3];
    /** How much the feet are kept on the ground: 1 standing or walking, 0 in the air. */
    public float grounded = 1;

    public HumanPose() {
        identity();
    }

    public HumanPose identity() {
        for (int i = 0; i < Human.COUNT; i++) SkinMath.identityQ(rot, i * 4);
        shift[0] = shift[1] = shift[2] = 0;
        grounded = 1;
        return this;
    }

    public void set(HumanPose other) {
        System.arraycopy(other.rot, 0, rot, 0, rot.length);
        System.arraycopy(other.shift, 0, shift, 0, 3);
        grounded = other.grounded;
    }

    /** Moves {@code t} of the way to {@code other}. */
    public void blend(HumanPose other, float t) {
        if (t <= 0) return;
        if (t >= 1) {
            set(other);
            return;
        }
        for (int i = 0; i < Human.COUNT; i++) SkinMath.nlerpQ(rot, i * 4, other.rot, i * 4, t, rot, i * 4);
        for (int i = 0; i < 3; i++) shift[i] += (other.shift[i] - shift[i]) * t;
        grounded += (other.grounded - grounded) * t;
    }

    /** Sets one bone's turn from clip angles (degrees). */
    public void set(Human bone, float pitch, float yaw, float roll) {
        Human.euler(pitch, yaw, roll, rot, bone.ordinal() * 4);
    }

    /** Adds a turn after a bone's own (in its parent's frame): {@code rot = extra * rot}. */
    public void add(Human bone, float pitch, float yaw, float roll, float[] scratch) {
        Human.euler(pitch, yaw, roll, scratch, 0);
        int o = bone.ordinal() * 4;
        SkinMath.mulQ(scratch, 0, rot, o, rot, o);
    }

    public boolean finite() {
        return SkinMath.finite(rot) && SkinMath.finite(shift) && Float.isFinite(grounded);
    }
}
