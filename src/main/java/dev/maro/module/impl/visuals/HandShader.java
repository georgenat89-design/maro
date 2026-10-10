package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;

import java.util.List;

/**
 * Hand Shader: your first-person hand and what it holds drawn in a look of its own (on fire with
 * flames rising off it, a drifting galaxy with twinkling stars, a rainbow, a see-through hologram,
 * solid chams or glass that bends the world behind it) with a clean glowing outline round it. The work is done in hand_shader.fsh by
 * {@link dev.maro.render.HandShaderRenderer}.
 */
public class HandShader extends Module {
    public static final String[] MODES = {"Glow", "Rainbow", "Galaxy", "Hologram", "Chams", "Glass", "Flame"};

    private final ModeSetting mode = add(new ModeSetting("Mode", "Galaxy: a drifting nebula with stars. Rainbow: shifting colours. "
            + "Hologram: see-through scanlines. Chams: one colour. Glass: the world bent through it. Flame: your hand on fire. Glow: only the outline",
            "Galaxy", MODES));
    private final ColorSetting color = add(new ColorSetting("Color", "The colour of the outline, chams, hologram, glass tint and coloured flames", 0xFF8A4DFF));
    private final NumberSetting fill = add(new NumberSetting("Fill Opacity", "How much the look covers the hand", 85, 0, 100, 1)
            .suffix("%").visible(() -> !mode.is("Glow") && !mode.is("Flame")));

    // ---- flame
    private final NumberSetting flameRise = add(new NumberSetting("Flame Rise", "How fast the flames climb", 0.55, 0, 1, 0.01)
            .visible(() -> mode.is("Flame")));
    private final NumberSetting flameWobble = add(new NumberSetting("Flame Wobble", "How much the flames sway", 0.65, 0, 1, 0.01)
            .visible(() -> mode.is("Flame")));
    private final NumberSetting flameLength = add(new NumberSetting("Flame Length", "How high the flames reach", 0.95, 0, 1, 0.01)
            .visible(() -> mode.is("Flame")));
    private final NumberSetting flameBrightness = add(new NumberSetting("Flame Brightness", "How bright the fire is", 0.9, 0, 1, 0.01)
            .visible(() -> mode.is("Flame")));
    private final NumberSetting strength = add(new NumberSetting("Strength", "How much the fire covers the hand and how thick the flames are",
            0.72, 0, 1, 0.01).visible(() -> mode.is("Flame")));
    private final ModeSetting flameColors = add(new ModeSetting("Flame Colors", "Natural: red, orange and yellow fire. Color: fire in the colour above",
            "Natural", "Natural", "Color").visible(() -> mode.is("Flame")));
    private final NumberSetting shaderFps = add(new NumberSetting("Shader FPS", "How many times a second it moves: lower looks choppier, like old fire",
            60, 10, 240, 1));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "A glowing line round the hand", true));
    private final NumberSetting outlineWidth = add(new NumberSetting("Outline Width", "How far the glow reaches, at 1080p", 4, 1, 12, 0.5)
            .suffix("px").visible(outline::get));
    private final NumberSetting glow = add(new NumberSetting("Glow Strength", "How bright the outline is", 120, 10, 300, 5)
            .suffix("%").visible(outline::get));
    private final BooleanSetting rainbowOutline = add(new BooleanSetting("Rainbow Outline", "The outline cycles through every colour", false)
            .visible(outline::get));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast it moves", 1, 0.1, 3, 0.05).suffix("x"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Look", mode, color, fill, speed, shaderFps),
            SettingSection.of("Flame", flameRise, flameWobble, flameLength, flameBrightness, strength, flameColors),
            SettingSection.of("Outline", outline, outlineWidth, glow, rainbowOutline));
    private final long start = System.nanoTime();

    private static HandShader instance;

    public HandShader() {
        super("Hand Shader", "Your hand and held item on fire, or as a galaxy, rainbow, hologram, chams or glass, with a glowing outline", Category.VISUALS);
        instance = this;
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    /** The module, while it is on. */
    public static HandShader active() {
        HandShader m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    public int modeIndex() {
        for (int i = 0; i < MODES.length; i++) if (mode.is(MODES[i])) return i;
        return 2;
    }

    public int color() {
        return color.get();
    }

    public float fill() {
        if (mode.is("Flame")) return strength.getFloat();
        return mode.is("Glow") ? 0 : fill.getFloat() / 100f;
    }

    /** Flame Rise, Wobble, Length and Brightness, each 0 to 1. */
    public float[] flame() {
        return new float[] {flameRise.getFloat(), flameWobble.getFloat(), flameLength.getFloat(), flameBrightness.getFloat()};
    }

    public boolean naturalFire() {
        return flameColors.is("Natural");
    }

    /** Outline width in pixels for a frame {@code height} pixels tall; 0 with the outline off. */
    public float outlinePx(int height) {
        return outline.get() ? outlineWidth.getFloat() * height / 1080f : 0;
    }

    public float glowStrength() {
        return outline.get() ? glow.getFloat() / 100f : 0;
    }

    public boolean rainbowOutline() {
        return rainbowOutline.get();
    }

    /** Seconds of animation, stepped at Shader FPS and kept small so the shader's float maths stays exact. */
    public float time() {
        double fps = shaderFps.get();
        double seconds = Math.floor((System.nanoTime() - start) / 1e9 * fps) / fps;
        return (float) (seconds * speed.get() % 3600.0);
    }
}
