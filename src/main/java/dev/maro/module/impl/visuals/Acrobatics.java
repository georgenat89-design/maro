package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.KeybindSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.util.KeyUtil;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import dev.maro.render.emote.Pose;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Acrobatics: flips and spins when you jump, moving like a person would: arms thrown up to jump,
 * knees pulled in with the hands on the shins through a flip, a star for a side flip, arms tucked in
 * for a spin, opening out to land. Front flips, back flips, side flips, spins and corkscrews, on every jump, on sprint jumps or on a key, with a puff of cloud on landing and a trail
 * through the air if you like; in first person the view can flip with you. Everyone else's jumps can
 * be turned into tricks too.
 *
 * <p>Only the drawing changes: you jump exactly as you always do, and nobody else sees the tricks.
 */
public class Acrobatics extends Module {
    private static Acrobatics instance;

    private enum Kind {FRONT, BACK, SIDE, SPIN, CORKSCREW}

    private final ModeSetting trick = add(new ModeSetting("Trick", "What you do in the air", "Back Flip",
            "Front Flip", "Back Flip", "Side Flip", "Spin", "Corkscrew", "Random"));
    private final ModeSetting when = add(new ModeSetting("When", "Every jump, only sprinting jumps, or only when you press the Trick Key",
            "Every Jump", "Every Jump", "Sprint Jumps", "Key Only"));
    private final KeybindSetting key = add(new KeybindSetting("Trick Key", "Press for a trick, even standing still", GLFW.GLFW_KEY_G));
    private final ModeSetting who = add(new ModeSetting("Who", "Just you, or every player you can see", "Me", "Me", "Everyone"));
    private final NumberSetting turns = add(new NumberSetting("Turns", "How many times round", 1, 1, 4, 1));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the trick goes", 1, 0.4, 2.5, 0.05).suffix("x"));
    private final BooleanSetting camera = add(new BooleanSetting("First Person Flip", "In first person the view flips and spins with you", false));
    private final ModeSetting trickView = add(new ModeSetting("Trick View", "Auto shows your model during a trick, then restores your view; Follow Game keeps your chosen camera",
            "Auto Third Person", "Auto Third Person", "Follow Game").visible(() -> !camera.get()));
    private final BooleanSetting puff = add(new BooleanSetting("Landing Puff", "A puff of cloud when you land a trick", true));
    private final BooleanSetting trail = add(new BooleanSetting("Trail", "Sparkles behind you through the trick", false));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Tricks", trick, when, key, who, turns, speed),
            SettingSection.of("Effects", camera, trickView, puff, trail));

    /** One player's trick in the air: what it is and when it started, in world ticks. */
    private record Move(Kind kind, double start, double length, int turns, boolean hop) {
    }

    private final Map<Integer, Move> moves = new HashMap<>();
    private final Map<Integer, Boolean> grounded = new HashMap<>();
    private boolean keyWasDown;
    private boolean requested;
    private ClientWorld activeWorld;
    private Perspective savedPerspective;
    private int done;

    public Acrobatics() {
        super("Acrobatics", "Flips, spins and corkscrews when you jump, only on your screen", Category.VISUALS);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        reset(); activeWorld = null; syncWorld();
    }

    @Override
    protected void onDisable() {
        reset(); activeWorld = null;
    }

    private void reset() {
        moves.clear();
        grounded.clear();
        keyWasDown = requested = false;
        restoreView();
    }

    private void syncWorld() {
        if (activeWorld == mc.world) return;
        reset(); activeWorld = mc.world;
        if (activeWorld != null) for (PlayerEntity player : activeWorld.getPlayers())
            grounded.put(player.getId(), player.isOnGround());
    }

    private void restoreView() {
        if (savedPerspective == null) return;
        // A manual F5 change takes priority over the temporary trick camera.
        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK)
            mc.options.setPerspective(savedPerspective);
        savedPerspective = null;
    }

    /** Queue the press event so a tap between client ticks cannot be missed by GLFW polling. */
    public void onActionBind(int code) {
        if (!isEnabled() || !inGame() || mc.currentScreen != null || code == KeyUtil.NONE || code != key.get()) return;
        syncWorld(); requested = true;
    }

    private Kind pick() {
        return switch (trick.get()) {
            case "Front Flip" -> Kind.FRONT;
            case "Side Flip" -> Kind.SIDE;
            case "Spin" -> Kind.SPIN;
            case "Corkscrew" -> Kind.CORKSCREW;
            case "Random" -> Kind.values()[ThreadLocalRandom.current().nextInt(Kind.values().length)];
            default -> Kind.BACK;
        };
    }

    private double now(float delta) {
        return mc.world.getTime() + delta;
    }

    /** Starts a trick for a player, a little hop added if they are on the ground. */
    public void perform(PlayerEntity player) {
        if (!isEnabled() || !inGame()) return;
        syncWorld();
        double length = 13 / speed.get() * Math.max(1, 0.7 + turns.get() * 0.3);
        moves.put(player.getId(), new Move(pick(), now(0), length, turns.getInt(), player.isOnGround()));
        if (player == mc.player && !camera.get() && trickView.is("Auto Third Person")
                && savedPerspective == null && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            savedPerspective = Perspective.FIRST_PERSON;
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    private boolean keyDown() {
        int code = key.get();
        if (code == KeyUtil.NONE || mc.currentScreen != null) return false;
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }

    @Override
    public void onTick() {
        syncWorld();
        if (!inGame()) return;
        boolean down = keyDown();
        if (requested || down && !keyWasDown) perform(mc.player);
        requested = false;
        keyWasDown = down;

        double time = now(0);
        for (PlayerEntity p : mc.world.getPlayers()) {
            boolean mine = p == mc.player;
            if (!mine && !who.is("Everyone")) continue;
            int id = p.getId();
            boolean onGround = p.isOnGround();
            Boolean was = grounded.put(id, onGround);
            Move move = moves.get(id);
            // A jump: off the ground and going up (others' speed is not known here, only that they rose).
            boolean jumped = was != null && was && !onGround && (mine ? p.getVelocity().y > 0.2 : p.getY() > p.lastY);
            if (move == null && jumped) {
                boolean allowed = when.is("Every Jump") || when.is("Sprint Jumps") && p.isSprinting();
                if (allowed) perform(p);
                move = moves.get(id);
            }
            if (move == null) continue;
            double t = (time - move.start()) / move.length();
            if (trail.get() && t < 1) {
                mc.particleManager.addParticle(ParticleTypes.END_ROD, p.getX(), p.getY() + p.getHeight() / 2, p.getZ(), 0, 0, 0);
            }
            if (t >= 1 && (onGround || t > 3)) {
                moves.remove(id);
                done++;
                if (puff.get()) {
                    for (int i = 0; i < 10; i++) {
                        double a = Math.PI * 2 * i / 10;
                        mc.particleManager.addParticle(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), Math.cos(a) * 0.12, 0.02, Math.sin(a) * 0.12);
                    }
                }
            }
        }
        grounded.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
        moves.keySet().removeIf(id -> mc.world.getEntityById(id) == null || id != mc.player.getId() && !who.is("Everyone"));
        Move local = moves.get(mc.player.getId());
        if (local == null || progress(local, 0) < 0 || camera.get() || !trickView.is("Auto Third Person")) restoreView();
    }

    /** How far through its trick a player is, 0 to 1 eased; -1 when none. */
    private double progress(Move move, float delta) {
        double t = (now(delta) - move.start()) / move.length();
        if (t < 0 || t >= 1) return -1;
        return t * t * (3 - 2 * t);
    }

    /**
     * Turns a player's model through their trick, about the middle of their body. Called once the
     * game has turned the model the way they face; their front is towards -z here.
     */
    public static void transform(int entityId, MatrixStack matrices, float height, float delta) {
        Acrobatics a = instance;
        if (a == null || !a.isEnabled() || !inGame()) return;
        Move move = a.moves.get(entityId);
        if (move == null) return;
        double e = a.progress(move, delta);
        if (e < 0) return;
        transformMove(move.kind(),move.turns(),move.hop(),e,matrices,height);
    }

    private static void transformMove(Kind kind,int turns,boolean hop,double e,MatrixStack matrices,float height){
        float angle = (float) (360 * turns * e);
        float middle = height / 2f;
        if (hop) matrices.translate(0, (float) Math.sin(Math.PI * e) * 0.9f, 0);
        matrices.translate(0, middle, 0);
        switch (kind) {
            case FRONT -> matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(angle));
            case BACK -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
            case SIDE -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            case SPIN -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            case CORKSCREW -> {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
            }
        }
        matrices.translate(0, -middle, 0);
    }

    private record PreviewMove(Kind kind,double position) { }

    /** Samples the same model transform and key poses without starting a live trick or camera. */
    private PreviewMove previewMove(double seconds){
        double length=13/speed.get()*Math.max(1,.7+turns.get()*.3),cycle=length+12;
        double ticks=Math.max(0,seconds)*20,t=(ticks%cycle)/length;
        if(t>=1)return null;
        Kind kind=trick.is("Random")?Kind.values()[(int)(ticks/cycle)%Kind.values().length]:switch(trick.get()){
            case "Front Flip"->Kind.FRONT;case "Side Flip"->Kind.SIDE;case "Spin"->Kind.SPIN;case "Corkscrew"->Kind.CORKSCREW;default->Kind.BACK;
        };
        return new PreviewMove(kind,t);
    }

    public void previewTransform(MatrixStack matrices,float height,double seconds){
        var sample=previewMove(seconds);if(sample==null)return;
        double t=sample.position();transformMove(sample.kind(),turns.getInt(),true,t*t*(3-2*t),matrices,height);
    }
    public Pose previewPose(double seconds){var sample=previewMove(seconds);return sample==null?null:limbPose(sample.kind(),sample.position());}
    public float previewBlend(double seconds){var sample=previewMove(seconds);return sample==null?0:(float)MathHelper.clamp(Math.min(sample.position()/.06,(1-sample.position())/.08),0,1);}

    // ---- arms and legs, as a person would move them ---------------------------------------------

    /*
     * Key poses through a trick, the right side's angles in radians (the left mirrors them):
     * {time 0-1, arm pitch, arm yaw, arm roll, leg pitch, leg roll, head pitch}. An arm's pitch of -PI
     * is straight up and -PI/2 forward, its roll outwards; a leg's negative pitch lifts it forward.
     */
    /** Flips: arms thrown up to jump, knees pulled to the chest with the hands on the shins, then opening out to land. */
    private static final float[][] TUCK = {
            {0.00f, -2.9f, 0f, 0.15f, 0f, 0f, 0f},
            {0.18f, -1.25f, 0f, -0.25f, -1.5f, 0.1f, 0.35f},
            {0.30f, -1.05f, 0f, -0.3f, -1.75f, 0.12f, 0.4f},
            {0.70f, -1.05f, 0f, -0.3f, -1.75f, 0.12f, 0.4f},
            {0.86f, -0.35f, 0f, 1.15f, -0.45f, 0.05f, 0.05f},
            {1.00f, -0.15f, 0f, 0.35f, 0f, 0f, 0f}};
    /** A side flip: arms up, then a star, arms and legs spread wide, closing to land. */
    private static final float[][] STAR = {
            {0.00f, -2.9f, 0f, 0.1f, 0f, 0f, 0f},
            {0.20f, -0.1f, 0f, 2.3f, 0f, 0.45f, 0f},
            {0.78f, -0.1f, 0f, 2.3f, 0f, 0.45f, 0f},
            {1.00f, -0.1f, 0f, 0.35f, 0f, 0.05f, 0f}};
    /** A spin: arms pulled in across the chest and legs together to turn fast, opening to land. */
    private static final float[][] TWIST = {
            {0.00f, -2.6f, 0f, 0.1f, 0f, 0f, 0f},
            {0.18f, -1.35f, -0.7f, 0f, 0f, -0.04f, 0.1f},
            {0.80f, -1.35f, -0.7f, 0f, 0f, -0.04f, 0.1f},
            {1.00f, -0.2f, 0f, 0.3f, 0f, 0f, 0f}};

    /** The limbs' angles part way through a trick, eased between the key poses. */
    private static float[] limbs(Kind kind, double t) {
        float[][] keys = switch (kind) {
            case SIDE -> STAR;
            case SPIN -> TWIST;
            default -> TUCK;
        };
        int i = 0;
        while (i < keys.length - 2 && t > keys[i + 1][0]) i++;
        float[] a = keys[i], b = keys[i + 1];
        double w = MathHelper.clamp((t - a[0]) / (b[0] - a[0]), 0, 1);
        w = w * w * (3 - 2 * w);
        float[] out = new float[6];
        for (int k = 0; k < 6; k++) out[k] = (float) (a[k + 1] + (b[k + 1] - a[k + 1]) * w);
        return out;
    }

    /** A player's arms, legs and head through their trick, eased in from and back out to the game's own pose; null for none. */
    public static Pose pose(int entityId, float delta) {
        Acrobatics a = instance;
        if (a == null || !a.isEnabled() || !inGame()) return null;
        Move move = a.moves.get(entityId);
        if (move == null) return null;
        double t = (a.now(delta) - move.start()) / move.length();
        if (t < 0 || t >= 1) return null;
        return limbPose(move.kind(),t);
    }

    private static Pose limbPose(Kind kind,double t){
        float[] l = limbs(kind, t);
        return new Pose()
                .rightArm(l[0], l[1], l[2]).leftArm(l[0], -l[1], -l[2])
                .rightLeg(l[3], 0, l[4]).leftLeg(l[3], 0, -l[4])
                .head(l[5], Float.NaN, Float.NaN);
    }

    /** How much of the trick's pose shows: eased in over the first moment and out over the last. */
    public static float poseBlend(int entityId, float delta) {
        Acrobatics a = instance;
        Move move = a == null ? null : a.moves.get(entityId);
        if (move == null) return 0;
        double t = (a.now(delta) - move.start()) / move.length();
        return (float) MathHelper.clamp(Math.min(t / 0.06, (1 - t) / 0.08), 0, 1);
    }

    /** In first person, how far the view turns this frame: {yaw, pitch, roll}; null for none. */
    public static float[] cameraTurn(float delta) {
        Acrobatics a = instance;
        if (a == null || !a.isEnabled() || !inGame() || !a.camera.get() || mc.options.getPerspective() != Perspective.FIRST_PERSON) return null;
        Move move = a.moves.get(mc.player.getId());
        if (move == null) return null;
        double e = a.progress(move, delta);
        if (e < 0) return null;
        float angle = (float) (360 * move.turns() * e);
        return switch (move.kind()) {
            case FRONT -> new float[] {0, angle, 0};
            case BACK -> new float[] {0, -angle, 0};
            case SPIN -> new float[] {angle, 0, 0};
            case CORKSCREW -> new float[] {angle, -angle, 0};
            case SIDE -> new float[] {0, 0, angle};
        };
    }

    // ---- for tests --------------------------------------------------------------------------

    /** Whether a player is in the middle of a trick now. */
    public boolean tricking(PlayerEntity player) {
        Move move = moves.get(player.getId());
        return move != null && progress(move, 0) >= 0;
    }

    /** A player's arm and leg pitch now, {arm, leg}; null when not in a trick. */
    public float[] limbsNow(PlayerEntity player) {
        Move move = moves.get(player.getId());
        if (move == null) return null;
        double t = (now(0) - move.start()) / move.length();
        if (t < 0 || t >= 1) return null;
        float[] l = limbs(move.kind(), t);
        return new float[] {l[0], l[3]};
    }

    /** How many tricks have been landed since the game started. */
    public int landed() {
        return done;
    }

    /** The turn a player's trick has reached, 0 to 360 times the turns; -1 for none. */
    public float angle(PlayerEntity player, float delta) {
        Move move = moves.get(player.getId());
        if (move == null) return -1;
        double e = progress(move, delta);
        return e < 0 ? -1 : (float) (360 * move.turns() * e);
    }
}
