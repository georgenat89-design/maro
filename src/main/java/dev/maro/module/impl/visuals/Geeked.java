package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.render.GeekedRenderer;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.util.List;

/** A cosmetic, client-only altered-vision effect over the world, before the interface. */
public final class Geeked extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Mellow haze, dreamy colors, or stronger trippy distortion",
            "Mellow", "Mellow", "Dreamy", "Trippy"));
    private final NumberSetting intensity = percent("Intensity", "Overall strength of the effect", 65);
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the colors and waves drift", 1, 0.2, 2, 0.05).suffix("x"));
    private final NumberSetting wobble = percent("Wobble", "Slow breathing zoom and flowing screen distortion", 45);
    private final NumberSetting colors = percent("Color Shift", "Warm drifting colors, stronger in Dreamy and Trippy", 60);
    private final NumberSetting haze = percent("Haze", "Soft focus and a glow around bright objects", 40);
    private final NumberSetting split = percent("Color Separation", "Colored fringes and gentle double vision near the edges", 30);
    private final NumberSetting vignette = percent("Vignette", "Soft colored shading around the edges", 35);
    private final List<SettingSection> sections = List.of(
            SettingSection.of("Feeling", mode, intensity, speed),
            SettingSection.of("Vision", wobble, colors, haze, split, vignette));
    private static Geeked instance;
    private long started;

    public Geeked() {
        super("Geeked", "Get geeked: breathing visuals, dreamy colors, soft haze and color separation", Category.VISUALS);
        instance = this;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> GeekedRenderer.release());
    }

    private NumberSetting percent(String name, String description, double value) {
        return add(new NumberSetting(name, description, value, 0, 100, 1).suffix("%"));
    }

    public static Geeked active() {
        return instance != null && instance.isEnabled() && inGame() ? instance : null;
    }

    @Override public List<SettingSection> getSettingSections() { return sections; }

    @Override protected void onEnable() { started = System.nanoTime(); }
    @Override protected void onDisable() { GeekedRenderer.release(); }

    public float time() {
        return (float) (((System.nanoTime() - started) / 1e9 * speed.get()) % 3600);
    }

    public float strength() {
        float fade = Math.min(1, (System.nanoTime() - started) / 650_000_000f);
        return intensity.getFloat() / 100 * fade * fade * (3 - 2 * fade);
    }

    public int modeIndex() { return mode.index(); }
    public float wobble() { return wobble.getFloat() / 100; }
    public float colors() { return colors.getFloat() / 100; }
    public float haze() { return haze.getFloat() / 100; }
    public float separation() { return split.getFloat() / 100; }
    public float vignette() { return vignette.getFloat() / 100; }
}
