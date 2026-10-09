package dev.maro.render.emote;

import java.util.function.Function;

/**
 * The emotes: each a pose over time. A looping one plays until stopped; the others play once and
 * ease back. Time is in seconds from the start.
 */
public enum Emote {
    WAVE("Wave", 2.6f, true, t -> new Pose().head(0.05f, 0, 0.08f * sin(t, 2))
            .rightArm(-2.7f, 0, 0.3f + 0.45f * sin(t, 9)).leftArm(0.05f, 0, -0.08f)),
    CLAP("Clap", 2.4f, true, t -> {
        float c = Math.abs(sin(t, 7));
        return new Pose().head(0.12f, 0, 0).rightArm(-1.3f, -(0.08f + 0.42f * c), 0).leftArm(-1.3f, 0.08f + 0.42f * c, 0);
    }),
    DAB("Dab", 1.8f, false, t -> new Pose().head(0.6f, -0.45f, 0.1f)
            .rightArm(-2.3f, -0.2f, 0.55f).leftArm(-1.75f, 1.05f, 0.1f)),
    FLOSS("Floss", 2f, true, t -> {
        float s = sin(t, 8), c = cos(t, 8);
        return new Pose().head(0, 0.15f * s, 0).rightArm(0.4f * c, 0, 0.42f * s + 0.12f).leftArm(-0.4f * c, 0, 0.42f * s - 0.12f)
                .rightLeg(0, 0, 0.06f * -s).leftLeg(0, 0, 0.06f * -s).spin(0.18f * s);
    }),
    CHEER("Cheer", 2f, true, t -> new Pose().head(-0.3f, 0, 0).rightArm(-2.95f, 0, 0.25f + 0.2f * sin(t, 10))
            .leftArm(-2.95f, 0, -0.25f - 0.2f * sin(t, 10)).stand().lift(2.5f * Math.abs(sin(t, 5)))),
    SALUTE("Salute", 2.2f, false, t -> new Pose().head(-0.05f, 0, 0).rightArm(-2.45f, -0.55f, 0.95f).leftArm(0, 0, -0.05f).stand()),
    FACEPALM("Facepalm", 2.4f, false, t -> new Pose().head(0.55f, 0.08f * sin(t, 6), 0).rightArm(-2.25f, -0.5f, -0.15f).leftArm(0.1f, 0, -0.1f)),
    POINT("Point", 2f, false, t -> new Pose().head(0, -0.1f, 0).rightArm(-1.55f, -0.1f, 0).leftArm(0.05f, 0, -0.08f)),
    SHRUG("Shrug", 1.6f, false, t -> new Pose().head(0.05f, 0, 0.28f).rightArm(-0.55f, 0.2f, 0.75f).leftArm(-0.55f, -0.2f, -0.75f).lift(0.6f)),
    BOW("Bow", 2.2f, false, t -> {
        float b = bump(t, 2.2f);
        return new Pose().head(0.9f * b, 0, 0).body(0.35f * b, 0, 0).rightArm(-0.55f * b, -0.6f * b, 0).leftArm(0.45f * b, 0, 0).stand();
    }),
    T_POSE("T-Pose", 3f, true, t -> new Pose().head(0, 0, 0).rightArm(0, 0, (float) (Math.PI / 2)).leftArm(0, 0, (float) (-Math.PI / 2)).stand()),
    ZOMBIE("Zombie", 2f, true, t -> new Pose().head(0.05f, 0, 0.12f * sin(t, 3)).rightArm(-1.55f + 0.08f * sin(t, 5), 0, 0)
            .leftArm(-1.55f + 0.08f * cos(t, 5), 0, 0).rightLeg(0.3f * sin(t, 4), 0, 0).leftLeg(-0.3f * sin(t, 4), 0, 0)),
    SPIN("Spin", 1.2f, true, t -> new Pose().rightArm(0, 0, 1.25f).leftArm(0, 0, -1.25f).stand().spin((float) (t * Math.PI * 2 / 1.2f))),
    HEADBANG("Headbang", 1.4f, true, t -> new Pose().head(0.45f + 0.45f * sin(t, 14), 0, 0).rightArm(-2.8f, 0, 0.2f)
            .leftArm(-0.4f + 0.2f * sin(t, 14), 0, -0.1f)),
    FLEX("Flex", 2f, false, t -> {
        float p = 0.08f * sin(t, 12);
        return new Pose().head(-0.1f, 0, 0).rightArm(-0.2f, -0.6f, 2.0f + p).leftArm(-0.2f, 0.6f, -2.0f - p).stand();
    }),
    THINK("Think", 2.6f, false, t -> new Pose().head(-0.15f, 0.1f, 0.15f).rightArm(-1.95f, -0.55f, -0.1f + 0.05f * sin(t, 4))
            .leftArm(-0.6f, 0.5f, 0)),
    YES("Yes", 1.6f, false, t -> new Pose().head(0.35f * sin(t, 8), 0, 0)),
    NO("No", 1.6f, false, t -> new Pose().head(0.1f, 0.5f * sin(t, 9), 0)),
    DANCE("Dance", 1.6f, true, t -> {
        float s = sin(t, 7.85f);
        return new Pose().head(0, 0.2f * s, 0.1f * s).rightArm(-1.2f - 0.9f * s, 0, 0.3f).leftArm(-1.2f + 0.9f * s, 0, -0.3f)
                .rightLeg(-0.45f * Math.max(0, s), 0, 0).leftLeg(-0.45f * Math.max(0, -s), 0, 0).lift(1.2f * Math.abs(s));
    }),
    SIT("Sit", 4f, true, t -> new Pose().head(0.1f, 0.15f * sin(t, 0.8f), 0).rightArm(-0.65f, 0.1f, 0).leftArm(-0.65f, -0.1f, 0)
            .rightLeg(-1.42f, 0.1f, 0).leftLeg(-1.42f, -0.1f, 0).lift(-10f)),

    // ---- the troll ones: Fortnite's, more or less

    /** An L on the forehead, kicking each leg out to the side in turn. */
    TAKE_THE_L("Take the L", 2f, true, t -> {
        float s = sin(t, 12.57f);
        return new Pose().head(-0.15f, 0.15f, -0.12f).rightArm(-2.55f, -0.35f, -0.25f).leftArm(-0.45f, 0, -0.2f)
                .rightLeg(0, 0, 0.6f * Math.max(0, s)).leftLeg(0, 0, -0.6f * Math.max(0, -s)).lift(1.4f * Math.abs(s));
    }),
    /** Arms pumping one up as the other comes down, stepping side to side. */
    DEFAULT_DANCE("Default Dance", 2.4f, true, t -> {
        float s = sin(t, 10.47f);
        return new Pose().head(0.1f * Math.abs(s), 0, 0.1f * s).body(0.05f, 0, 0.08f * s)
                .rightArm(-1.4f + 0.9f * s, 0, 0.35f).leftArm(-1.4f - 0.9f * s, 0, -0.35f)
                .rightLeg(-0.35f * Math.max(0, s), 0, 0.1f).leftLeg(-0.35f * Math.max(0, -s), 0, -0.1f).lift(0.8f * Math.abs(s));
    }),
    /** Arms swinging low across the body, side to side, with the hips. */
    ORANGE_JUSTICE("Orange Justice", 2f, true, t -> {
        float s = sin(t, 6.28f);
        return new Pose().head(0, 0.2f * s, 0).body(0.15f, 0, 0.12f * s)
                .rightArm(-0.6f, 0.9f * s, 0.5f + 0.4f * s).leftArm(-0.6f, 0.9f * s, -0.5f + 0.4f * s)
                .rightLeg(-0.5f * Math.max(0, s), 0, 0.25f * s).leftLeg(-0.5f * Math.max(0, -s), 0, 0.25f * s).spin(0.25f * s);
    }),
    /** Leaning in, heel taps forward one foot then the other, arms swinging. */
    GRIDDY("Griddy", 1.4f, true, t -> {
        float s = sin(t, 9f);
        return new Pose().head(0.2f, 0, 0).body(0.25f, 0, 0)
                .rightArm(-0.8f - 0.7f * s, 0, 0.35f).leftArm(-0.8f + 0.7f * s, 0, -0.35f)
                .rightLeg(-0.6f * Math.max(0, s), 0, 0).leftLeg(-0.6f * Math.max(0, -s), 0, 0).lift(1f * Math.abs(s));
    }),
    /** Pointing straight at them and laughing, head back, holding your belly. */
    LAUGH_IT_UP("Laugh It Up", 2.8f, true, t -> {
        float l = sin(t, 18f);
        return new Pose().head(-0.35f + 0.12f * l, 0, 0.05f * l).body(-0.12f + 0.04f * l, 0, 0)
                .rightArm(-1.55f, -0.15f, 0).leftArm(-0.7f, 0.6f, -0.1f).stand().lift(0.4f * Math.abs(l));
    }),
    /** Both arms swinging forward and back together, bouncing. */
    HYPE("Hype", 1.4f, true, t -> {
        float s = sin(t, 9.4f);
        return new Pose().head(0.15f * s, 0, 0).rightArm(-1f + 1.4f * s, 0, 0.2f).leftArm(-1f + 1.4f * s, 0, -0.2f)
                .stand().lift(2f * Math.abs(s));
    }),
    /** Feet shuffling in and out, fists punching down in turn. */
    ELECTRO_SHUFFLE("Electro Shuffle", 1.6f, true, t -> {
        float s = sin(t, 12f), c = cos(t, 12f);
        return new Pose().head(0, 0.15f * s, 0).rightArm(-1.3f + 0.6f * c, 0, 0.2f).leftArm(-1.3f - 0.6f * c, 0, -0.2f)
                .rightLeg(0, 0.4f * s, 0.3f * s).leftLeg(0, 0.4f * s, 0.3f * s).spin(0.3f * s);
    }),
    /** Wrists crossed on the reins, hopping from foot to foot. */
    RIDE_THE_PONY("Ride the Pony", 1.6f, true, t -> {
        float s = sin(t, 12f);
        return new Pose().head(0.1f + 0.1f * Math.abs(s), 0, 0).rightArm(-1.45f, 0.35f, 0).leftArm(-1.45f, -0.35f, 0)
                .rightLeg(-0.35f * Math.max(0, s), 0, 0).leftLeg(-0.35f * Math.max(0, -s), 0, 0).lift(1.8f * Math.abs(s));
    }),
    /** A big bouncing hop, one arm thrown up and then the other. */
    BILLY_BOUNCE("Billy Bounce", 1.6f, true, t -> {
        float s = sin(t, 7.85f);
        return new Pose().head(-0.1f, 0.2f * s, 0).rightArm(-1.6f - 1.2f * Math.max(0, s), 0, 0.3f)
                .leftArm(-1.6f - 1.2f * Math.max(0, -s), 0, -0.3f).stand().lift(3f * Math.abs(s));
    }),
    /** The oldest troll there is: crouching up and down. */
    TEABAG("Teabag", 1.2f, true, t -> {
        float d = Math.abs(sin(t, 7.85f));
        return new Pose().head(0.15f * d, 0, 0).body(0.45f * d, 0, 0).rightArm(-0.35f * d, 0, 0.1f).leftArm(-0.35f * d, 0, -0.1f)
                .rightLeg(-0.25f * d, 0, 0).leftLeg(-0.25f * d, 0, 0).lift(-4.5f * d);
    }),
    /** Arms out like claws, scuttling side to side. */
    CRABWALK("Crabwalk", 1.6f, true, t -> {
        float s = sin(t, 9f);
        return new Pose().head(0, 0.25f * s, 0).body(0.2f, 0, 0).rightArm(-2.6f, -0.3f, 0.9f + 0.2f * s).leftArm(-2.6f, 0.3f, -0.9f + 0.2f * s)
                .rightLeg(0, 0, 0.35f + 0.25f * s).leftLeg(0, 0, -0.35f + 0.25f * s).spin(0.2f * s).lift(0.6f * Math.abs(s));
    });

    private final String label;
    private final float length;
    private final boolean loops;
    private final Function<Float, Pose> pose;

    Emote(String label, float length, boolean loops, Function<Float, Pose> pose) {
        this.label = label;
        this.length = length;
        this.loops = loops;
        this.pose = pose;
    }

    public String label() {
        return label;
    }

    /** Seconds one play takes (one round, for a looping emote). */
    public float length() {
        return length;
    }

    public boolean loops() {
        return loops;
    }

    /** The pose {@code t} seconds in. */
    public Pose pose(float t) {
        return pose.apply(t);
    }

    private static float sin(float t, float rate) {
        return (float) Math.sin(t * rate);
    }

    private static float cos(float t, float rate) {
        return (float) Math.cos(t * rate);
    }

    /** Rises to 1 and back to 0 over {@code length} seconds, holding at the top. */
    private static float bump(float t, float length) {
        float x = Math.max(0, Math.min(1, t / length));
        return (float) Math.min(1, Math.sin(x * Math.PI) * 1.6);
    }
}
