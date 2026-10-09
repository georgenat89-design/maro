package dev.maro.render.emote;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;

import java.util.Arrays;

/**
 * Where an emote puts each part of the player model at one moment, in radians. Anything left unset
 * keeps the game's own pose, so a wave leaves the legs walking as they were.
 *
 * <p>Angles follow the model's own rules: an arm's pitch of -PI/2 points it forward and -PI straight
 * up; a right arm's roll of +PI/2 holds it out sideways (the left arm's, -PI/2); a head's positive
 * pitch looks down.
 */
public final class Pose {
    private static final int HEAD = 0, BODY = 3, RIGHT_ARM = 6, LEFT_ARM = 9, RIGHT_LEG = 12, LEFT_LEG = 15, SPIN = 18, LIFT = 19;
    private final float[] v = new float[20];

    public Pose() {
        Arrays.fill(v, Float.NaN);
    }

    private Pose set(int at, float pitch, float yaw, float roll) {
        v[at] = pitch;
        v[at + 1] = yaw;
        v[at + 2] = roll;
        return this;
    }

    public Pose head(float pitch, float yaw, float roll) {
        return set(HEAD, pitch, yaw, roll);
    }

    public Pose body(float pitch, float yaw, float roll) {
        return set(BODY, pitch, yaw, roll);
    }

    public Pose rightArm(float pitch, float yaw, float roll) {
        return set(RIGHT_ARM, pitch, yaw, roll);
    }

    public Pose leftArm(float pitch, float yaw, float roll) {
        return set(LEFT_ARM, pitch, yaw, roll);
    }

    public Pose rightLeg(float pitch, float yaw, float roll) {
        return set(RIGHT_LEG, pitch, yaw, roll);
    }

    public Pose leftLeg(float pitch, float yaw, float roll) {
        return set(LEFT_LEG, pitch, yaw, roll);
    }

    /** Both legs straight and still. */
    public Pose stand() {
        return rightLeg(0, 0, 0).leftLeg(0, 0, 0);
    }

    /** Turns the whole model round, in radians. */
    public Pose spin(float radians) {
        v[SPIN] = radians;
        return this;
    }

    /** Raises the whole model, in model pixels (16 to a block); negative lowers it. */
    public Pose lift(float pixels) {
        v[LIFT] = pixels;
        return this;
    }

    /** Puts the model in this pose, {@code blend} of the way from the pose it already has (0 to 1). */
    public void apply(BipedEntityModel<?> model, float blend) {
        part(model.head, HEAD, blend);
        part(model.body, BODY, blend);
        part(model.rightArm, RIGHT_ARM, blend);
        part(model.leftArm, LEFT_ARM, blend);
        part(model.rightLeg, RIGHT_LEG, blend);
        part(model.leftLeg, LEFT_LEG, blend);
        ModelPart root = model.getRootPart();
        if (!Float.isNaN(v[SPIN])) root.yaw += v[SPIN] * blend;
        if (!Float.isNaN(v[LIFT])) root.originY -= v[LIFT] * blend;
    }

    private void part(ModelPart part, int at, float blend) {
        if (!Float.isNaN(v[at])) part.pitch += (v[at] - part.pitch) * blend;
        if (!Float.isNaN(v[at + 1])) part.yaw += (v[at + 1] - part.yaw) * blend;
        if (!Float.isNaN(v[at + 2])) part.roll += (v[at + 2] - part.roll) * blend;
    }
}
