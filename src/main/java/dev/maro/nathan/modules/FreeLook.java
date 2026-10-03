package dev.maro.nathan.modules;

import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.EnumSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.systems.modules.Modules;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.nbt.NbtCompound;
import org.lwjgl.glfw.GLFW;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Look around without turning.
 *
 * <p>While it is on, the mouse and the arrow keys turn the <em>camera</em>. Your
 * player is not turned by either: your yaw and pitch - what you aim with, what
 * you walk by, what the server is told - stay exactly where they were, so you
 * keep running, falling or gliding the way you were going while you look behind
 * you. Let go, and the view is back where you are really looking.
 *
 * <p><b>Player</b> mode swings the camera round you on the end of an arm, like
 * F5 with the angle in your hands. <b>Camera</b> mode turns the view where the
 * camera already is, like turning your head. Either way the camera is put in
 * place once a frame from where your eyes are drawn that frame, so it rides
 * with you through a walk, a fall or a dive with nothing lagging behind.
 *
 * <p>It is a separate thing from Free Cam, which lets the camera leave you. The
 * two are not run together: turning one on turns the other off, and each puts
 * back what it changed before the other starts.
 *
 * <p>It is called "freelook" and not "free-look" because Meteor has a module of
 * that name, and a module added under a name that is taken replaces the one that
 * had it, which Meteor's own camera code then cannot find.
 */
public class FreeLook extends Module {
    /** What the camera turns about. */
    public enum Mode {
        Player,
        Camera
    }

    /** How the keybind works. */
    public enum Activation {
        Toggle,
        Hold
    }

    private static FreeLook instance;

    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);

    private final Setting<Activation> activation = sgMain.add(new EnumSetting.Builder<Activation>()
        .name("activation")
        .description("Toggle: press the keybind to turn it on and again to turn it off. Hold: it is on only while the keybind is held.")
        .defaultValue(Activation.Hold)
        .onChanged(how -> toggleOnBindRelease = how == Activation.Hold)
        .build()
    );

    private final Setting<Mode> mode = sgMain.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Player swings the camera round you at Camera Distance. Camera turns the view where the camera already is.")
        .defaultValue(Mode.Player)
        .build()
    );

    private final Setting<Boolean> togglePerspective = sgMain.add(new BoolSetting.Builder()
        .name("toggle-perspective")
        .description("Go to third person while it is on, and back to whatever you were in afterwards.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgMain.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Let the camera swing through blocks. Off, it is pulled in in front of whatever is in the way, as the game's own third person is.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Player)
        .build()
    );

    private final Setting<Double> distance = sgMain.add(new DoubleSetting.Builder()
        .name("camera-distance")
        .description("How far from you the camera swings, in blocks.")
        .defaultValue(4)
        .range(0.5, 32)
        .sliderRange(1, 16)
        .decimalPlaces(2)
        .visible(() -> mode.get() == Mode.Player)
        .build()
    );

    private final Setting<Double> sensitivity = sgMain.add(new DoubleSetting.Builder()
        .name("camera-sensitivity")
        .description("How far the camera turns for a given move of the mouse. 10 is how the game turns you; your own mouse sensitivity applies on top.")
        .defaultValue(10)
        .range(0.5, 50)
        .sliderRange(1, 30)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Boolean> arrowsOpposite = sgAdvanced.add(new BoolSetting.Builder()
        .name("arrows-control-opposite")
        .description("Turn the camera the other way for each arrow key.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> arrowSpeed = sgAdvanced.add(new DoubleSetting.Builder()
        .name("arrow-speed")
        .description("How fast the arrow keys turn the camera. 0.50 is a quarter turn a second.")
        .defaultValue(0.5)
        .range(0.05, 5)
        .sliderRange(0.05, 2)
        .decimalPlaces(2)
        .build()
    );

    private float yaw;
    private float pitch;
    private long lastAt;

    /** The perspective to go back to, if it was changed. */
    private Perspective before;

    public FreeLook() {
        super(NameeProtectAddon.CATEGORY, "freelook", "Look around with the camera while your player keeps facing, aiming and moving the way it was.");

        instance = this;
        toggleOnBindRelease = true;
    }

    /** Meteor keeps hold-or-toggle on the module itself; after loading, it is made to agree with the setting here. */
    @Override
    public Module fromTag(NbtCompound tag) {
        Module loaded = super.fromTag(tag);

        toggleOnBindRelease = activation.get() == Activation.Hold;
        return loaded;
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        // One camera at a time. Free Cam puts back what it changed as it goes.
        FreeCam freeCam = Modules.get().get(FreeCam.class);
        if (freeCam != null && freeCam.isActive()) freeCam.toggle();

        // From where you are looking now, so nothing jumps as it comes on.
        yaw = mc.player.getYaw(1);
        pitch = mc.player.getPitch(1);
        lastAt = 0;
        before = null;

        if (togglePerspective.get() && mc.options.getPerspective().isFirstPerson()) {
            before = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    @Override
    public void onDeactivate() {
        if (before != null && mc.options != null) mc.options.setPerspective(before);

        before = null;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    // ------------------------------------------------------ for the mixins

    public static boolean active() {
        FreeLook module = instance;

        return module != null && module.isActive();
    }

    /** The mouse, which while this is on turns the camera and not you. */
    public static void look(double byX, double byY) {
        FreeLook module = instance;
        if (module == null) return;

        // The game turns you by 0.15 of what the mouse reports; at a
        // sensitivity of 10 the camera turns by the same.
        float turn = (float) (0.015 * module.sensitivity.get());

        module.yaw += (float) byX * turn;
        module.pitch = Math.max(-90, Math.min(90, module.pitch + (float) byY * turn));
    }

    /**
     * Called once a frame as the camera is placed: the arrow keys are taken, by
     * the clock, and the camera's angles are given back. Null while it is off.
     */
    public static float[] frame() {
        FreeLook module = instance;

        if (module == null || !module.isActive()) return null;

        return module.advance();
    }

    private float[] advance() {
        long now = System.nanoTime();
        double seconds = lastAt == 0 ? 0 : Math.min((now - lastAt) / 1.0e9, 0.1);

        lastAt = now;

        if (mc.currentScreen == null) {
            long window = mc.getWindow().getHandle();
            double turn = arrowSpeed.get() * 180 * seconds * (arrowsOpposite.get() ? -1 : 1);

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS) yaw -= (float) turn;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) yaw += (float) turn;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) pitch -= (float) turn;
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) pitch += (float) turn;

            pitch = Math.max(-90, Math.min(90, pitch));
        }

        return new float[] {yaw, pitch};
    }

    /** Whether the camera swings round the player, and how far out, and whether blocks stop it. */
    public static boolean orbits() {
        FreeLook module = instance;

        return module != null && module.mode.get() == Mode.Player;
    }

    public static float reach() {
        FreeLook module = instance;

        return module == null ? 4 : (float) (double) module.distance.get();
    }

    public static boolean throughWalls() {
        FreeLook module = instance;

        return module != null && module.throughWalls.get();
    }
}
