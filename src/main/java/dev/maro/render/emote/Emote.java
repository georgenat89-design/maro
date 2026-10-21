package dev.maro.render.emote;

import dev.maro.render.character.skin.Human;
import dev.maro.render.character.skin.HumanClip;
import java.util.function.Supplier;

/**
 * The emotes: each a full-body clip on the shared skeleton (see {@link EmoteClips}). A looping one
 * plays until stopped; the others play once and ease back. Time is in seconds from the start.
 * Characters and the bendable default skin play the clip bone for bone; a plain six-part model
 * (and the armour drawn over it) gets the same clip folded onto its parts by {@link #pose}.
 */
public enum Emote {
    WAVE("Wave", 2.6f, true, EmoteClips::wave),
    CLAP("Clap", 2.4f, true, EmoteClips::clap),
    DAB("Dab", 1.8f, false, EmoteClips::dab),
    FLOSS("Floss", 2f, true, EmoteClips::floss),
    CHEER("Cheer", 2f, true, EmoteClips::cheer),
    SALUTE("Salute", 2.2f, false, EmoteClips::salute),
    FACEPALM("Facepalm", 2.4f, false, EmoteClips::facepalm),
    POINT("Point", 2f, false, EmoteClips::point),
    SHRUG("Shrug", 1.6f, false, EmoteClips::shrug),
    BOW("Bow", 2.2f, false, EmoteClips::bow),
    T_POSE("T-Pose", 3f, true, EmoteClips::tPose),
    ZOMBIE("Zombie", 2f, true, EmoteClips::zombie),
    SPIN("Spin", 1.2f, true, EmoteClips::spin),
    HEADBANG("Headbang", 1.4f, true, EmoteClips::headbang),
    FLEX("Flex", 2f, false, EmoteClips::flex),
    THINK("Think", 2.6f, false, EmoteClips::think),
    YES("Yes", 1.6f, false, EmoteClips::yes),
    NO("No", 1.6f, false, EmoteClips::no),
    DANCE("Dance", 1.6f, true, EmoteClips::dance),
    SIT("Sit", 4f, true, EmoteClips::sit),

    // ---- the troll ones: Fortnite's, more or less

    /** An L on the forehead, kicking each leg out to the side in turn. */
    TAKE_THE_L("Take the L", 2f, true, EmoteClips::takeTheL),
    /** Arms pumping one up as the other comes down, stepping side to side. */
    DEFAULT_DANCE("Default Dance", 2.4f, true, EmoteClips::defaultDance),
    /** Arms swinging low across the body, side to side, with the hips. */
    ORANGE_JUSTICE("Orange Justice", 2f, true, EmoteClips::orangeJustice),
    /** Leaning in, heel taps forward one foot then the other, arms swinging. */
    GRIDDY("Griddy", 1.4f, true, EmoteClips::griddy),
    /** Pointing straight at them and laughing, head back, holding your belly. */
    LAUGH_IT_UP("Laugh It Up", 2.8f, true, EmoteClips::laughItUp),
    /** Both arms swinging forward and back together, bouncing. */
    HYPE("Hype", 1.4f, true, EmoteClips::hype),
    /** Feet shuffling in and out, fists punching down in turn. */
    ELECTRO_SHUFFLE("Electro Shuffle", 1.6f, true, EmoteClips::electroShuffle),
    /** Wrists crossed on the reins, hopping from foot to foot. */
    RIDE_THE_PONY("Ride the Pony", 1.6f, true, EmoteClips::rideThePony),
    /** A big bouncing hop, one arm thrown up and then the other. */
    BILLY_BOUNCE("Billy Bounce", 1.6f, true, EmoteClips::billyBounce),
    /** The oldest troll there is: crouching up and down. */
    TEABAG("Teabag", 1.2f, true, EmoteClips::teabag),
    /** Arms out like claws, scuttling side to side. */
    CRABWALK("Crabwalk", 1.6f, true, EmoteClips::crabwalk),

    // ---- full-body dances

    /** High kicks, one leg then the other. */
    HIGH_KICK("High Kick", 2f, true, EmoteClips::highKick),
    /** Deep side lunges from one side to the other. */
    LUNGE_DANCE("Lunge Dance", 2.4f, true, EmoteClips::lungeDance),
    /** Up on the toes, reaching up and out. */
    REACH_OUT("Reach Out", 2.6f, true, EmoteClips::reachOut);

    private final String label;
    private final float length;
    private final boolean loops;
    private final Supplier<HumanClip> build;
    private volatile HumanClip clip;

    Emote(String label, float length, boolean loops, Supplier<HumanClip> build) {
        this.label = label;
        this.length = length;
        this.loops = loops;
        this.build = build;
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

    /** The full-body clip. */
    public HumanClip clip() {
        HumanClip c = clip;
        if (c == null) {
            c = build.get();
            clip = c;
        }
        return c;
    }

    private static final ThreadLocal<float[][]> SAMPLE = ThreadLocal.withInitial(() -> new float[][]{new float[Human.COUNT * 3], new float[5]});

    /**
     * The clip folded onto a six-part model at {@code t} seconds: the head takes the neck's and
     * head's turns, the body the spine's and chest's, each arm its shoulder's and upper arm's, each
     * leg its thigh's; the hips' turn and the lift move the whole model.
     */
    public Pose pose(float t) {
        float[][] s = SAMPLE.get();
        float[] a = s[0], body = s[1];
        clip().angles(t, a, body);
        float d = (float) (Math.PI / 180);
        Pose p = new Pose();
        p.head(d * (sum(a, Human.NECK, 0) + sum(a, Human.HEAD, 0)), d * (sum(a, Human.NECK, 1) + sum(a, Human.HEAD, 1)),
                d * (sum(a, Human.NECK, 2) + sum(a, Human.HEAD, 2)));
        p.body(d * (sum(a, Human.SPINE, 0) + sum(a, Human.CHEST, 0)), d * (sum(a, Human.SPINE, 1) + sum(a, Human.CHEST, 1)),
                d * (sum(a, Human.SPINE, 2) + sum(a, Human.CHEST, 2)));
        p.rightArm(d * (sum(a, Human.RIGHT_UPPER_ARM, 0) + sum(a, Human.RIGHT_SHOULDER, 0)), d * (sum(a, Human.RIGHT_UPPER_ARM, 1) + sum(a, Human.RIGHT_SHOULDER, 1)),
                d * (sum(a, Human.RIGHT_UPPER_ARM, 2) + sum(a, Human.RIGHT_SHOULDER, 2)));
        p.leftArm(d * (sum(a, Human.LEFT_UPPER_ARM, 0) + sum(a, Human.LEFT_SHOULDER, 0)), d * (sum(a, Human.LEFT_UPPER_ARM, 1) + sum(a, Human.LEFT_SHOULDER, 1)),
                d * (sum(a, Human.LEFT_UPPER_ARM, 2) + sum(a, Human.LEFT_SHOULDER, 2)));
        p.rightLeg(d * sum(a, Human.RIGHT_THIGH, 0), d * sum(a, Human.RIGHT_THIGH, 1), d * sum(a, Human.RIGHT_THIGH, 2));
        p.leftLeg(d * sum(a, Human.LEFT_THIGH, 0), d * sum(a, Human.LEFT_THIGH, 1), d * sum(a, Human.LEFT_THIGH, 2));
        float spin = sum(a, Human.HIPS, 1) + body[4];
        if (spin != 0) p.spin(d * spin);
        // Lift in a twelve-pixel leg's lengths; a sitting clip lowers the body (its legs reach forward).
        float lift = body[1] * 12;
        if (lift != 0) p.lift(lift);
        return p;
    }

    private static float sum(float[] a, Human bone, int axis) {
        float v = a[bone.ordinal() * 3 + axis];
        return Float.isNaN(v) ? 0 : v;
    }
}
