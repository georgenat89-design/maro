/*
 * The zoom in this file is adapted from Logical Zoom by LogicalGeekBoy and
 * contributors - https://github.com/LogicalGeekBoy/logical_zoom - which is
 * under the MIT licence: Copyright 2019 LogicalGeekBoy. The full notice is in
 * LICENSES/MIT-LogicalZoom.txt, which ships in the jar.
 *
 * What is taken from it: zooming by magnifying the picture rather than by
 * subtracting degrees, turning the game's smooth ("cinematic") camera on for as
 * long as the key is held and putting back whatever it was before, and hiding
 * your hands in first person while zoomed. What is added here: a strength you
 * can set, an eased transition, the mouse wheel and a level it remembers, and
 * mouse sensitivity scaled to match.
 */
package dev.maro.nathan.modules;

import java.io.File;

import dev.maro.runtime.MeteorClient;
import dev.maro.runtime.events.game.ChangePerspectiveEvent;
import dev.maro.runtime.events.game.GameLeftEvent;
import dev.maro.runtime.events.meteor.MouseScrollEvent;
import dev.maro.runtime.events.render.GetFovEvent;
import dev.maro.runtime.events.render.Render3DEvent;
import dev.maro.runtime.gui.GuiTheme;
import dev.maro.runtime.gui.widgets.WWidget;
import dev.maro.runtime.gui.widgets.containers.WHorizontalList;
import dev.maro.runtime.gui.widgets.pressable.WButton;
import dev.maro.runtime.settings.BoolSetting;
import dev.maro.runtime.settings.DoubleSetting;
import dev.maro.runtime.settings.IntSetting;
import dev.maro.runtime.settings.KeybindSetting;
import dev.maro.runtime.settings.Setting;
import dev.maro.runtime.settings.SettingGroup;
import dev.maro.runtime.systems.modules.Module;
import dev.maro.runtime.utils.misc.Keybind;

import dev.maro.runtime.event.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import org.lwjgl.glfw.GLFW;

import dev.maro.nathan.NameeProtectAddon;

/**
 * Hold a key to zoom, and F5 that moves instead of cutting.
 *
 * <p><b>Zoom.</b> The module being on only arms it. Nothing changes until the
 * key is held, and the moment it is let go - or the module is turned off, a
 * screen opens, or you leave the world - the field of view, the mouse
 * sensitivity and the smooth camera are all exactly what they were.
 *
 * <p>The way it zooms is Logical Zoom's: the picture is magnified, as that mod
 * does by scaling the projection matrix, rather than a number of degrees being
 * taken off the field of view. The two are not the same thing - a projection is
 * in tangents of angles, so ten times bigger is a tenth of the tangent, not a
 * tenth of the angle - and magnifying is the one that gives the zoom you asked
 * for whatever your field of view is set to. It is handed to the game as a
 * field of view all the same, through Meteor's hook, and not as a change to the
 * matrix: the game on this version works its culling and Meteor its tracers and
 * tags from the camera's field of view, and a matrix changed behind their backs
 * would leave all of them drawn for a view that is no longer the one on screen.
 *
 * <p>The ease is in the logarithm of the magnification. From one times to ten
 * in equal steps spends nearly all of the movement at the start, where a step is
 * a large part of what there is; in equal ratios it is one steady push in.
 *
 * <p><b>Smooth F5</b> is a toggle of its own and has nothing to do with the
 * key. All it changes is how far behind you the camera is. The game still does
 * its own check for walls - this only ever scales down the distance that check
 * comes back with, and a shorter way along a clear line is clear - so the camera
 * never goes through anything it did not before. Going out, the game is in third
 * person at once and the distance grows. Coming back, first person has no
 * distance to shrink, so the switch itself is held back, the camera is brought
 * in while the game is still in third person, and first person is switched to
 * when it arrives.
 *
 * <p>Both are positions walked at a fixed pace by the clock and stopped dead at
 * either end, so they take the same time at any frame rate and leave no tail of
 * ever-smaller movement behind.
 *
 * <p>It is not called "zoom" because Meteor has a module of that name, a module
 * added under a name that is taken replaces the one that had it, and Meteor's
 * own nametag code asks for its Zoom module by class and would not find it.
 */
public class KeyZoom extends Module {
    /**
     * As close as the F5 camera comes. Closer than this it is inside your own
     * head, and what you would see is the inside of your own face.
     */
    private static final double NEAREST = 0.14;

    /** The least and the most the wheel can take the zoom to, as strengths. */
    private static final double LEAST = 0.10;
    private static final double MOST = 0.98;

    private static KeyZoom instance;

    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgZoom = settings.createGroup("Zoom");
    private final SettingGroup sgAnimation = settings.createGroup("Animation");
    private final SettingGroup sgView = settings.createGroup("View");

    private final Setting<Keybind> zoomKey = sgZoom.add(new KeybindSetting.Builder()
        .name("zoom-key")
        .description("Hold to zoom, let go to stop.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_C))
        .build()
    );

    private final Setting<Double> initialZoom = sgZoom.add(new DoubleSetting.Builder()
        .name("initial-zoom")
        .description("How strong the zoom is when the key goes down. 0.90 shows a tenth as much, which is ten times closer; Logical Zoom's own fixed zoom is 0.77.")
        .defaultValue(0.90)
        .range(LEAST, MOST)
        .sliderRange(LEAST, MOST)
        .build()
    );

    private final Setting<Boolean> rememberZoom = sgZoom.add(new BoolSetting.Builder()
        .name("remember-zoom-level")
        .description("Start each zoom at the level you last left the mouse wheel on, instead of at Initial Zoom. Letting go of the key still gives you your normal view back.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> sensitivityMultiplier = sgZoom.add(new DoubleSetting.Builder()
        .name("zoom-sensitivity-multiplier")
        .description("The mouse while zoomed, on top of the slowing that matches the zoom. 1 leaves that as it is, less is slower, more is faster.")
        .defaultValue(1)
        .range(0.1, 4)
        .sliderRange(0.25, 2)
        .build()
    );

    private final Setting<Boolean> smoothF5 = sgAnimation.add(new BoolSetting.Builder()
        .name("smooth-f5")
        .description("Move the camera out and back when you change perspective, instead of cutting. Nothing to do with the zoom key.")
        .defaultValue(true)
        .onChanged(on -> {
            if (!on) finishPerspective();
        })
        .build()
    );

    private final Setting<Boolean> smooth = sgAnimation.add(new BoolSetting.Builder()
        .name("smooth")
        .description("Ease in and out of the zoom rather than jumping.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> transitionTime = sgAnimation.add(new IntSetting.Builder()
        .name("transition-time")
        .description("How long the zoom's ease takes, in milliseconds.")
        .defaultValue(140)
        .range(40, 600)
        .sliderRange(40, 400)
        .visible(smooth::get)
        .build()
    );

    private final Setting<Boolean> scrollAdjust = sgZoom.add(new BoolSetting.Builder()
        .name("scroll-to-adjust")
        .description("While zoomed, the mouse wheel zooms further in or back out, and does not change your hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> adjustSensitivity = sgZoom.add(new BoolSetting.Builder()
        .name("adjust-sensitivity")
        .description("Slow the mouse by as much as the picture is magnified, so aiming feels the same zoomed as not.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> cinematicCamera = sgView.add(new BoolSetting.Builder()
        .name("cinematic-camera")
        .description("Turn the game's smooth camera on while the key is held, as Logical Zoom does, and put back whatever it was when you let go.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> hideHands = sgView.add(new BoolSetting.Builder()
        .name("hide-hands")
        .description("Do not draw your hands in first person while zoomed, as Logical Zoom does. At ten times closer they are most of the screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> f5Time = sgAnimation.add(new IntSetting.Builder()
        .name("f5-transition-time")
        .description("How long the camera takes to move out or back when you change perspective, in milliseconds.")
        .defaultValue(160)
        .range(60, 500)
        .sliderRange(60, 400)
        .visible(smoothF5::get)
        .build()
    );

    // Never shown: where the wheel was last left, as a strength like Initial
    // Zoom's, or less than nothing for nowhere yet. It is a setting so that it
    // is saved with the rest and is still there after a restart.
    private final Setting<Double> rememberedZoom = sgZoom.add(new DoubleSetting.Builder()
        .name("remembered-zoom")
        .description("The zoom level last chosen with the mouse wheel.")
        .defaultValue(-1)
        .range(-1, MOST)
        .visible(() -> false)
        .build()
    );

    // ------------------------------------------------------------ zoom state

    /** 0 not zoomed, 1 fully, moved at a fixed pace by the clock. */
    private double progress;

    /**
     * How far in the zoom is and how far in it is going, both as the logarithm
     * of how many times bigger the picture is. The wheel moves where it is
     * going; where it is follows, eased, so a notch is a glide and not a jump.
     * In logarithms because that is the scale a zoom is even on: a notch is the
     * same push at every level.
     */
    private double level;
    private double wanted;
    private boolean wasHeld;

    /** The sensitivity to put back, or NaN while it has not been touched. */
    private double savedSensitivity = Double.NaN;

    /** Whether the smooth camera was on before the zoom turned it on, or null while it has not been touched. */
    private Boolean savedSmoothCamera;

    // ------------------------------------------------------ perspective state

    /** 0 at your eyes, 1 at full distance. */
    private double distance = 1;

    /** On the way back to first person, which is switched to on arrival. */
    private boolean returning;

    /** Set while this module makes the switch itself, so that it does not hold its own switch back. */
    private boolean switching;

    public KeyZoom() {
        super(NameeProtectAddon.CATEGORY, "key-zoom", "Hold a key to zoom in, with the mouse slowed to match - and an F5 that moves the camera instead of cutting.");

        instance = this;
    }

    @Override
    public void onActivate() {
        distance = 1;
        returning = false;
    }

    @Override
    public void onDeactivate() {
        restore();
        finishPerspective();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        restore();
        finishPerspective();
    }

    /** Everything the zoom touches, put back. Safe to call when it has touched nothing. */
    private void restore() {
        progress = 0;
        wasHeld = false;

        restoreSensitivity();
        restoreSmoothCamera();
    }

    // ------------------------------------------------------------------ zoom

    private boolean held() {
        return mc.currentScreen == null && zoomKey.get().isPressed();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        double seconds = Math.min(event.frameTime, 0.1);

        stepPerspective(seconds);

        boolean held = held();

        if (held && !wasHeld) {
            // Where this zoom starts: where the wheel was last left, if that is
            // being remembered and there is such a place, and Initial Zoom if
            // not. No glide to it - the ease in is the glide.
            double remembered = rememberedZoom.get();
            double start = rememberZoom.get() && remembered >= LEAST ? remembered : initialZoom.get();

            wanted = toLevel(start);
            level = wanted;
        }

        wasHeld = held;

        // Closes a fixed share of what is left in a fixed time, whatever the
        // frame rate: the same glide at 40 frames a second as at 400.
        level += (wanted - level) * (1 - Math.exp(-seconds * 16));

        if (smooth.get()) {
            double step = seconds / (transitionTime.get() / 1000.0);

            progress = held ? Math.min(1, progress + step) : Math.max(0, progress - step);
        } else {
            progress = held ? 1 : 0;
        }

        // Logical Zoom's: on when the zoom starts, back to what it was when it
        // stops. Tied to the key and not to the ease, so that it is off again
        // the moment you let go and does not drag the camera on afterwards.
        if (held && cinematicCamera.get()) {
            if (savedSmoothCamera == null) savedSmoothCamera = mc.options.smoothCameraEnabled;

            mc.options.smoothCameraEnabled = true;
        } else {
            restoreSmoothCamera();
        }

        // With a screen open the mouse is a pointer and the options may be on
        // show, so the real sensitivity goes back at once rather than easing.
        if (progress <= 0 || mc.currentScreen != null || (!adjustSensitivity.get() && sensitivityMultiplier.get() == 1)) {
            restoreSensitivity();
            return;
        }

        if (Double.isNaN(savedSensitivity)) savedSensitivity = mc.options.getMouseSensitivity().getValue();

        // The game turns the setting into a speed as (0.6 s + 0.2) cubed. That
        // is undone, the speed divided by the magnification, and the setting
        // that gives that speed put in. It stops at the bottom of the slider,
        // which the game will not go below.
        //
        // The multiplier goes on top of that, and is eased in with the zoom -
        // in ratios, like the zoom - so the mouse does not change speed at a
        // stroke when the key goes down. It is read every frame, so moving the
        // slider is felt at once.
        double eased = progress * progress * (3 - 2 * progress);
        double speed = Math.pow(savedSensitivity * 0.6 + 0.2, 3)
            / (adjustSensitivity.get() ? magnification() : 1)
            * Math.exp(Math.log(sensitivityMultiplier.get()) * eased);
        double setting = (Math.cbrt(speed) - 0.2) / 0.6;

        mc.options.getMouseSensitivity().setValue(Math.max(0, Math.min(1, setting)));
    }

    private void restoreSensitivity() {
        if (Double.isNaN(savedSensitivity)) return;

        mc.options.getMouseSensitivity().setValue(savedSensitivity);
        savedSensitivity = Double.NaN;
    }

    private void restoreSmoothCamera() {
        if (savedSmoothCamera == null) return;

        mc.options.smoothCameraEnabled = savedSmoothCamera;
        savedSmoothCamera = null;
    }

    /** How many times bigger the picture is this frame: 1 when not zoomed. */
    private double magnification() {
        if (progress <= 0) return 1;

        double eased = progress * progress * (3 - 2 * progress);

        return Math.exp(level * eased);
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        double times = magnification();
        if (times <= 1) return;

        // Logical Zoom scales the projection matrix by this. A projection is in
        // tangents of half the field of view, so the field of view that comes
        // to the same picture is the one whose half-tangent is that much less.
        double half = Math.toRadians(event.fov) / 2;

        event.fov = (float) Math.toDegrees(2 * Math.atan(Math.tan(half) / times));
    }

    @EventHandler
    private void onMouseScroll(MouseScrollEvent event) {
        // With Free Cam out the wheel is the camera's speed, and its alone.
        if (FreeCam.active()) return;

        if (!held()) return;

        // While zooming the wheel is the zoom's, whether or not it is allowed
        // to change it: it is taken here so that it does not also walk along
        // the hotbar. Any other time it is not touched.
        event.cancel();

        if (!scrollAdjust.get()) return;

        // A notch is a seventh more, or a seventh less, of whatever the zoom
        // is, so it feels the same at every level - and it stops at the ends.
        wanted = Math.max(toLevel(LEAST), Math.min(toLevel(MOST), wanted + event.value * Math.log(1.15)));

        if (rememberZoom.get()) rememberedZoom.set(toStrength(wanted));
    }

    private static double toLevel(double strength) {
        return -Math.log(1 - Math.max(LEAST, Math.min(MOST, strength)));
    }

    private static double toStrength(double level) {
        return Math.max(LEAST, Math.min(MOST, 1 - Math.exp(-level)));
    }

    /** Under the settings: forget where the wheel was left, so the next zoom starts at Initial Zoom. */
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();

        WButton reset = list.add(theme.button("Reset Remembered Zoom")).expandX().widget();
        reset.action = rememberedZoom::reset;

        return list;
    }

    /** For the hand renderer: whether your hands should be left out this frame. */
    public static boolean hidingHands() {
        KeyZoom module = instance;

        return module != null && module.isActive() && module.hideHands.get() && module.progress > 0
            && module.mc.options.getPerspective().isFirstPerson();
    }

    // ----------------------------------------------------------- perspective

    @EventHandler
    private void onChangePerspective(ChangePerspectiveEvent event) {
        if (switching || !smoothF5.get()) return;

        boolean fromFirst = mc.options.getPerspective().isFirstPerson();

        if (!event.perspective.isFirstPerson()) {
            // Out from first person, or round from behind to in front: either
            // way the camera starts at your head and moves out.
            distance = 0;
            returning = false;
        } else if (!fromFirst) {
            // Held back until the camera has come in. Pressing again while it
            // is on its way changes nothing: it is already going where that
            // press would send it.
            event.cancel();
            returning = true;
        }
    }

    private void stepPerspective(double seconds) {
        if (!smoothF5.get()) return;

        double step = seconds / (f5Time.get() / 1000.0);

        if (returning) {
            distance = Math.max(0, distance - step);

            if (distance <= 0) finishPerspective();
        } else {
            distance = Math.min(1, distance + step);
        }
    }

    /** Whatever was under way is completed at once, so nothing is left half done. */
    private void finishPerspective() {
        if (returning) toFirstPerson();

        distance = 1;
        returning = false;
    }

    /** The switch the game was stopped from making, with what the game does after it. */
    private void toFirstPerson() {
        if (mc.options == null || mc.options.getPerspective().isFirstPerson()) return;

        switching = true;

        try {
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        } finally {
            switching = false;
        }

        if (mc.gameRenderer != null) mc.gameRenderer.onCameraEntitySet(mc.getCameraEntity());
    }

    /** For the camera: what to multiply its distance by this frame. 1 when there is nothing to do. */
    public static float distanceFactor() {
        KeyZoom module = instance;

        if (module == null || !module.isActive() || !module.smoothF5.get() || module.distance >= 1) return 1;

        double eased = module.distance * module.distance * (3 - 2 * module.distance);

        return (float) (NEAREST + (1 - NEAREST) * eased);
    }

    // ------------------------------------------------------------- migration

    /**
     * Smooth F5 was a module of its own for one version. Its transition time is
     * carried over, once: only while this module has nothing saved for its own.
     * When Meteor next saves, the old module's entry is not written back, and
     * there is nothing left to carry.
     */
    public void migrateOldSettings() {
        try {
            File file = new File(MeteorClient.FOLDER, "modules.nbt");
            if (!file.exists()) return;

            NbtCompound root = NbtIo.read(file.toPath());
            if (root == null) return;

            NbtCompound own = null;
            NbtCompound old = null;

            for (NbtElement tag : root.getListOrEmpty("modules")) {
                if (!(tag instanceof NbtCompound module)) continue;

                switch (module.getString("name", "")) {
                    case "key-zoom" -> own = module;
                    case "smooth-f5" -> old = module;
                    default -> {
                    }
                }
            }

            if (old == null || (own != null && saved(own, "f5-transition-time") != null)) return;

            NbtCompound time = saved(old, "transition-time");

            if (time != null) {
                f5Time.fromTag(time);
                NameeProtectAddon.LOG.info("key-zoom adopted the old smooth-f5 module's transition time");
            }
        } catch (Exception e) {
            // Defaults are a fine place to land; a settings file that will not
            // parse is not a reason to stop the addon loading.
            NameeProtectAddon.LOG.warn("key-zoom could not read the old smooth-f5 module's settings, using defaults", e);
        }
    }

    private static NbtCompound saved(NbtCompound module, String name) {
        for (NbtElement groupTag : module.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(groupTag instanceof NbtCompound group)) continue;

            for (NbtElement settingTag : group.getListOrEmpty("settings")) {
                if (settingTag instanceof NbtCompound setting && name.equals(setting.getString("name", ""))) return setting;
            }
        }

        return null;
    }
}
