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
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Acrobatics: flips and spins when you jump. Front flips, back flips, side flips, spins and
 * corkscrews, on every jump, on sprint jumps or on a key, with a puff of cloud on landing and a trail
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
    private final BooleanSetting puff = add(new BooleanSetting("Landing Puff", "A puff of cloud when you land a trick", true));
    private final BooleanSetting trail = add(new BooleanSetting("Trail", "Sparkles behind you through the trick", false));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Tricks", trick, when, key, who, turns, speed),
            SettingSection.of("Effects", camera, puff, trail));

    /** One player's trick in the air: what it is and when it started, in world ticks. */
    private record Move(Kind kind, double start, double length, int turns, boolean hop) {
    }

    private final Map<Integer, Move> moves = new HashMap<>();
    private final Map<Integer, Boolean> grounded = new HashMap<>();
    private boolean keyWasDown;
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
    protected void onDisable() {
        moves.clear();
        grounded.clear();
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
        if (!inGame()) return;
        double length = 13 / speed.get() * Math.max(1, 0.7 + turns.get() * 0.3);
        moves.put(player.getId(), new Move(pick(), now(0), length, turns.getInt(), player.isOnGround()));
    }

    private boolean keyDown() {
        int code = key.get();
        if (code == KeyUtil.NONE || mc.currentScreen != null) return false;
        if (KeyUtil.isMouse(code)) return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), code - KeyUtil.MOUSE_OFFSET) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        boolean down = keyDown();
        if (down && !keyWasDown) perform(mc.player);
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
        float angle = (float) (360 * move.turns() * e);
        float middle = height / 2f;
        if (move.hop()) matrices.translate(0, (float) Math.sin(Math.PI * e) * 0.9f, 0);
        matrices.translate(0, middle, 0);
        switch (move.kind()) {
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

    /** In first person, how far the view is turned this frame by your trick: {yaw, pitch}; null for none. */
    public static float[] cameraTurn(float delta) {
        Acrobatics a = instance;
        if (a == null || !a.isEnabled() || !inGame() || !a.camera.get() || mc.options.getPerspective() != Perspective.FIRST_PERSON) return null;
        Move move = a.moves.get(mc.player.getId());
        if (move == null) return null;
        double e = a.progress(move, delta);
        if (e < 0) return null;
        float angle = (float) (360 * move.turns() * e);
        return switch (move.kind()) {
            case FRONT -> new float[] {0, angle};
            case BACK -> new float[] {0, -angle};
            case SPIN -> new float[] {angle, 0};
            case CORKSCREW -> new float[] {angle, -angle};
            case SIDE -> null;
        };
    }

    // ---- for tests --------------------------------------------------------------------------

    /** Whether a player is in the middle of a trick now. */
    public boolean tricking(PlayerEntity player) {
        Move move = moves.get(player.getId());
        return move != null && progress(move, 0) >= 0;
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
