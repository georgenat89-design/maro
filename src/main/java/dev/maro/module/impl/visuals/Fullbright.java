package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;

/**
 * See in the dark. The lightmap is built as if the Brightness option went far past the 100% the
 * game allows, while the option itself, and options.txt, are left alone. Fades in and out instead
 * of snapping. Applied in {@link dev.maro.mixin.LightmapMixin}.
 */
public class Fullbright extends Module {
    /** The gamma the lightmap is given at a Strength of 100%: enough to light a sealed cave. */
    private static final double FULL = 16;

    private static Fullbright instance;

    private final NumberSetting strength = add(new NumberSetting("Strength", "How bright the dark gets", 100, 10, 100, 1).suffix("%"));
    private final BooleanSetting fade = add(new BooleanSetting("Fade", "Brighten and darken smoothly when toggled", true));
    private final NumberSetting fadeTime = add(new NumberSetting("Fade Time", "How long the fade takes", 300, 50, 1500, 10)
            .suffix("ms").visible(fade::get));

    /** How far through the fade the light is, 0 off to 1 on, and where the last fade started. */
    private double from;
    private long toggledAt;

    public Fullbright() {
        super("Fullbright", "See in the dark without changing your brightness setting", Category.VISUALS);
        instance = this;
    }

    @Override
    protected void onEnable() {
        startFade();
    }

    @Override
    protected void onDisable() {
        startFade();
    }

    private void startFade() {
        from = amount(!isEnabled());
        toggledAt = System.nanoTime();
    }

    /** How lit the world is right now, 0 to 1, given which way it is fading. */
    private double amount(boolean on) {
        double target = on ? 1 : 0;
        if (!fade.get() || toggledAt == 0) return target;
        double t = Math.min(1, (System.nanoTime() - toggledAt) / 1e6 / fadeTime.get());
        double eased = t * t * (3 - 2 * t);
        return from + (target - from) * eased;
    }

    /** The gamma the lightmap should use instead of the option's own. */
    public static double gamma(double option) {
        Fullbright m = instance;
        if (m == null) return option;
        double amount = m.amount(m.isEnabled());
        if (amount <= 0) return option;
        double full = 1 + (FULL - 1) * m.strength.get() / 100.0;
        return option + (Math.max(option, full) - option) * amount;
    }
}
