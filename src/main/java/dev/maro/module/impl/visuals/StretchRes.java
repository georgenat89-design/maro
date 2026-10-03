package dev.maro.module.impl.visuals;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import org.joml.Matrix4f;

/**
 * Stretched resolution: renders the world with a different aspect ratio than the window, so e.g.
 * 4:3 on a 16:9 monitor stretches everything horizontally (like stretched res in shooters).
 * The HUD and menus are untouched. Applied in {@link dev.maro.mixin.GameRendererMixin}.
 */
public class StretchRes extends Module {
    private static StretchRes instance;

    private final ModeSetting ratio = add(new ModeSetting("Ratio", "Aspect ratio the world is rendered at",
            "4:3", "4:3", "5:4", "3:2", "16:10", "1:1", "Custom"));
    private final NumberSetting custom = add(new NumberSetting("Custom Ratio", "Width divided by height (1.33 = 4:3)",
            1.33, 0.8, 2.4, 0.01).visible(() -> ratio.is("Custom")));

    public StretchRes() {
        super("Stretch Res", "Stretch the world to another aspect ratio, like 4:3 stretched", Category.VISUALS);
        instance = this;
    }

    public static StretchRes get() {
        return instance;
    }

    /**
     * Returns a stretched copy of a perspective projection, or the matrix itself when the module is
     * off or the matrix is orthographic. Scaling row 0 by window/target aspect is exactly what
     * rebuilding the perspective with the target aspect would give.
     */
    public static Matrix4f apply(Matrix4f projection) {
        StretchRes m = instance;
        if (m == null || !m.isEnabled() || projection == null || Math.abs(projection.m23() + 1f) > 1e-3f) return projection;
        var window = mc.getWindow();
        if (window.getFramebufferHeight() <= 0) return projection;
        float windowAspect = (float) window.getFramebufferWidth() / window.getFramebufferHeight();
        return new Matrix4f(projection).scaleLocal(windowAspect / m.targetAspect(), 1f, 1f);
    }

    private static final boolean DEBUG = Boolean.getBoolean("maro.debug");
    private static long lastDebug;

    /** Logs the matrices passing through the hooks (only with -Dmaro.debug=true). */
    public static void debug(String where, Matrix4f m) {
        if (!DEBUG || m == null) return;
        long now = System.currentTimeMillis();
        if (now - lastDebug < 500) return;
        lastDebug = now;
        dev.maro.Maro.LOGGER.info("[stretch-debug] {} enabled={} m00={} m11={} m22={} m23={} m32={} m33={}",
                where, instance != null && instance.isEnabled(), m.m00(), m.m11(), m.m22(), m.m23(), m.m32(), m.m33());
    }

    /** Aspect ratio the world should be projected with. */
    public float targetAspect() {
        return switch (ratio.get()) {
            case "5:4" -> 5f / 4f;
            case "3:2" -> 3f / 2f;
            case "16:10" -> 16f / 10f;
            case "1:1" -> 1f;
            case "Custom" -> custom.getFloat();
            default -> 4f / 3f;
        };
    }
}
