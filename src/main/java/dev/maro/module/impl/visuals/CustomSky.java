package dev.maro.module.impl.visuals;

import dev.maro.Maro;
import dev.maro.gui.notification.Notifications;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.sky.CustomSkyRenderer;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import org.joml.Vector4f;

import java.util.List;

/**
 * Replaces the sky with one of fifteen animated skies. They are painted by {@code custom_sky.fsh}
 * through {@link CustomSkyRenderer}, in place of the game's own sky (see
 * {@link dev.maro.mixin.CustomSkyMixins}). Changing the sky fades from the old one to the new one.
 */
public class CustomSky extends Module {
    /** Order matches sky() in custom_sky.fsh. */
    public static final String[] SKIES = {"Neon Waves", "Aurora", "Nebula", "Synthwave", "Golden Hour", "Cotton Candy",
            "Blood Moon", "Starry Night", "Matrix", "Inferno", "Frozen", "Deep Ocean", "Black Hole", "Prism", "Thunderstorm"};
    /** Each sky's colour just above the horizon, which far-away land fades into when Match Fog is on. */
    private static final int[] HORIZONS = {0x23093C, 0x092728, 0x170C1D, 0xF95D72, 0xFC955A, 0xFBBFDF,
            0x590808, 0x181F42, 0x020B05, 0x831E03, 0xD7EBFF, 0x07304B, 0x0A050C, 0x9A92A6, 0x1C1F29};
    private static final double FADE_SECONDS = 1.5;

    private static CustomSky instance;

    private final ModeSetting sky = add(new ModeSetting("Sky", "Which sky to show", "Neon Waves", SKIES)
            .onChange(v -> skyChanged()));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the sky moves", 100, 0, 300, 5).suffix("%"));
    private final NumberSetting brightness = add(new NumberSetting("Brightness", "How bright the sky is", 100, 20, 150, 5).suffix("%"));
    private final BooleanSetting cycle = add(new BooleanSetting("Cycle", "Move on to the next sky every so often", false)
            .onChange(on -> pickedAt = System.nanoTime()));
    private final NumberSetting cycleTime = add(new NumberSetting("Cycle Time", "How long each sky stays before the next", 60, 10, 600, 5)
            .suffix("s").visible(cycle::get));
    private final BooleanSetting matchFog = add(new BooleanSetting("Match Fog", "Far-away land fades into the sky's colour instead of the normal fog", true));
    private final BooleanSetting sunMoon = add(new BooleanSetting("Sun & Moon", "Keep the normal sun and moon in front of the sky", false));
    private final BooleanSetting clouds = add(new BooleanSetting("Clouds", "Keep the normal clouds", false));
    private final BooleanSetting end = add(new BooleanSetting("In The End", "Replace the End's sky too", true));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Sky", sky, speed, brightness, cycle, cycleTime),
            SettingSection.of("World", matchFog, sunMoon, clouds, end));

    /** The sky showing, the one fading out and when that fade began (0 for none). */
    private int shown;
    private int previous;
    private long changedAt;
    /** When the sky showing was picked, or the module turned on; Cycle counts from here. */
    private long pickedAt;

    /** Animation clock in seconds, advanced each frame at the Speed setting. */
    private double clock;
    private long lastFrame;

    public CustomSky() {
        super("Custom Sky", "Swap the sky for one of fifteen animated skies", Category.VISUALS);
        instance = this;
        shown = previous = sky.index();
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    @Override
    protected void onEnable() {
        // Turning on shows the chosen sky straight away; only changing it fades.
        previous = shown = sky.index();
        changedAt = 0;
        pickedAt = System.nanoTime();
        lastFrame = 0;
    }

    @Override
    public void onTick() {
        if (cycle.get() && (System.nanoTime() - pickedAt) / 1e9 >= cycleTime.get()) sky.cycle(1);
    }

    private void skyChanged() {
        previous = shown;
        shown = sky.index();
        changedAt = pickedAt = System.nanoTime();
    }

    /** How much of the previous sky is still showing, 1 just after a change down to 0. */
    private float fadeLeft() {
        if (changedAt == 0 || previous == shown) return 0f;
        double t = (System.nanoTime() - changedAt) / 1e9 / FADE_SECONDS;
        if (t >= 1) return 0f;
        double eased = t * t * (3 - 2 * t);
        return (float) (1 - eased);
    }

    // ---- what the renderer and hooks ask --------------------------------------------------

    private static boolean on() {
        return instance != null && instance.isEnabled() && mc.world != null;
    }

    /** Whether to paint the custom sky over the overworld-style sky. */
    public static boolean active() {
        return on();
    }

    /** Whether to paint the custom sky over the End's sky. */
    public static boolean activeInEnd() {
        return on() && instance.end.get();
    }

    public static boolean keepsSunAndMoon() {
        return instance != null && instance.sunMoon.get();
    }

    public static boolean hidesClouds() {
        return on() && !instance.clouds.get();
    }

    /**
     * The fog colour to use instead of {@code original}, or null to keep it. Only while the custom
     * sky was actually drawn on the last frame, so water, lava, blindness and the Nether keep theirs.
     */
    public static Vector4f fogColor(Vector4f original) {
        if (!on() || !instance.matchFog.get() || !CustomSkyRenderer.drewLastFrame()) return null;
        float fade = instance.fadeLeft();
        int now = HORIZONS[instance.shown], before = HORIZONS[instance.previous];
        float bright = instance.brightness.getFloat() / 100f;
        float r = channel(now, before, 16, fade) * bright;
        float g = channel(now, before, 8, fade) * bright;
        float b = channel(now, before, 0, fade) * bright;
        return new Vector4f(Math.min(r, 1f), Math.min(g, 1f), Math.min(b, 1f), original.w);
    }

    private static float channel(int now, int before, int shift, float fade) {
        float a = (now >> shift & 0xFF) / 255f, b = (before >> shift & 0xFF) / 255f;
        return a + (b - a) * fade;
    }

    /** The SkyData values after the matrix: Params then View, as eight floats. */
    public static float[] uniformValues(float pixelAngle) {
        CustomSky m = instance;
        long now = System.nanoTime();
        if (m.lastFrame != 0) {
            // A long pause (a loading screen) must not make the sky jump.
            double dt = Math.min((now - m.lastFrame) / 1e9, 0.25);
            m.clock = (m.clock + dt * m.speed.get() / 100.0) % 7200.0;
        }
        m.lastFrame = now;
        return new float[] {
                (float) m.clock, m.shown, m.previous, m.fadeLeft(),
                m.brightness.getFloat() / 100f, pixelAngle, 0, 0
        };
    }

    /** The graphics driver would not compile the sky shader; the game's own sky stays. */
    public static void shaderFailed() {
        Maro.LOGGER.error("Custom Sky: the graphics driver could not compile the custom_sky shader (its errors are logged above)");
        if (instance == null || !instance.isEnabled()) return;
        Notifications.push(instance.getName(), "Your graphics driver could not build the sky - see the log. Turned off.", Notifications.Type.ERROR);
        instance.setEnabled(false);
    }

    public static void renderFailed(RuntimeException e) {
        Maro.LOGGER.error("Custom Sky could not render", e);
        if (instance == null) return;
        Notifications.push(instance.getName(), "Rendering failed - see the log. Turned off.", Notifications.Type.ERROR);
        instance.setEnabled(false);
    }
}
