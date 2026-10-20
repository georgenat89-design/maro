package dev.maro.render.character.skin;

/** What a player is doing this frame, as the animation needs it. Filled from the game's render state. */
public final class MotionInput {
    /** Seconds on the player's own clock (game time, so a paused game holds still). */
    public float time;
    /** Ground speed and vertical speed, blocks per second. */
    public float speed, vertical;
    /**
     * Ground velocity along the body's facing and to its left, blocks per second (both 0: all of
     * {@link #speed} forward), and the body's yaw in degrees, so planted feet stay put as it turns.
     */
    public float forward, strafe, bodyYaw;
    public boolean grounded = true, sneaking, sprinting, swimming, riding, sleeping, gliding, inWater, crawling;
    /** Head turn and tilt relative to the body, degrees (the player model's head yaw and pitch). */
    public float headYaw, headPitch;
    /**
     * The game's own arm angles (radians, as the player model holds them) and how much of each to
     * use: swinging, aiming a bow, blocking, eating. 0 leaves the clip's arms.
     */
    public final float[] rightArm = new float[3], leftArm = new float[3];
    public float rightArmWeight, leftArmWeight, rightElbow, leftElbow;
    /** The body's twist into a swing (radians). */
    public float bodyTwist;
    /** An emote on top: its clip, seconds into it, how far it is blended in (0-1). */
    public HumanClip emote;
    public float emoteTime, emoteBlend;
    /** Blocks per character unit at the size drawn, for matching steps to ground speed. */
    public float unit = 1 / 16f;

    public void reset() {
        time = speed = vertical = forward = strafe = bodyYaw = 0;
        grounded = true;
        sneaking = sprinting = swimming = riding = sleeping = gliding = inWater = crawling = false;
        headYaw = headPitch = 0;
        for (int i = 0; i < 3; i++) rightArm[i] = leftArm[i] = 0;
        rightArmWeight = leftArmWeight = rightElbow = leftElbow = bodyTwist = 0;
        emote = null;
        emoteTime = emoteBlend = 0;
        unit = 1 / 16f;
    }
}
