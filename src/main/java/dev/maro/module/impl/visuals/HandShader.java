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
 * Hand Shader: your first-person hand and what it holds drawn in a look of its own (a drifting
 * galaxy with twinkling stars, a rainbow, a see-through hologram, solid chams or glass that bends
 * the world behind it) with a glowing outline round it. The work is done in hand_shader.fsh by
 * {@link dev.maro.render.HandShaderRenderer}.
 */
public class HandShader extends Module {
    public static final String[] MODES = {"Glow", "Rainbow", "Galaxy", "Hologram", "Chams", "Glass"};

    private final ModeSetting mode = add(new ModeSetting("Mode", "Galaxy: a drifting nebula with stars. Rainbow: shifting colours. "
            + "Hologram: see-through scanlines. Chams: one colour. Glass: the world bent through it. Glow: only the outline",
            "Galaxy", MODES));
    private final ColorSetting color = add(new ColorSetting("Color", "The colour of the outline, chams, hologram and glass tint", 0xFF8A4DFF));
    private final NumberSetting fill = add(new NumberSetting("Fill Opacity", "How much the look covers the hand", 85, 0, 100, 1)
            .suffix("%").visible(() -> !mode.is("Glow")));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "A glowing line round the hand", true));
    private final NumberSetting outlineWidth = add(new NumberSetting("Outline Width", "How far the glow reaches, at 1080p", 4, 1, 12, 0.5)
            .suffix("px").visible(outline::get));
    private final NumberSetting glow = add(new NumberSetting("Glow Strength", "How bright the outline is", 120, 10, 300, 5)
            .suffix("%").visible(outline::get));
    private final BooleanSetting rainbowOutline = add(new BooleanSetting("Rainbow Outline", "The outline cycles through every colour", false)
            .visible(outline::get));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast it moves", 1, 0.1, 3, 0.05).suffix("x"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Look", mode, color, fill, speed),
            SettingSection.of("Outline", outline, outlineWidth, glow, rainbowOutline));
    private final long start = System.nanoTime();

    private static HandShader instance;

    public HandShader() {
        super("Hand Shader", "Your hand and held item as a galaxy, rainbow, hologram, chams or glass, with a glowing outline", Category.VISUALS);
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
        return mode.is("Glow") ? 0 : fill.getFloat() / 100f;
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

    /** Seconds of animation, kept small so the shader's float maths stays exact. */
    public float time() {
        return (float) ((System.nanoTime() - start) / 1e9 * speed.get() % 3600.0);
    }
}
