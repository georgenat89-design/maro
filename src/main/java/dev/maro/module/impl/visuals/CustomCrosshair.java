package dev.maro.module.impl.visuals;

import dev.maro.gui.hud.CrosshairPresetsScreen;
import dev.maro.gui.render.CrosshairRenderer;
import dev.maro.gui.render.Render2D;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import java.util.List;

public final class CustomCrosshair extends Module {
    private final ModeSetting preset = add(new ModeSetting("Preset", "Choose a crosshair shape", "Cross + Dot",
        CrosshairRenderer.PRESETS.toArray(String[]::new)));
    private final ButtonSetting gallery = add(new ButtonSetting("Preset Gallery", "See all 24 shapes and click to select", "Browse",
        () -> mc.setScreen(new CrosshairPresetsScreen(mc.currentScreen, this))));
    private final NumberSetting size = add(new NumberSetting("Size", "Arm length or ring radius in GUI pixels", 5, 2, 24, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Space between the center and crosshair arms", 2, 0, 12, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1.2, 0.5, 5, 0.1));
    private final NumberSetting dotSize = add(new NumberSetting("Dot Size", "Radius of dots in GUI pixels", 1.2, 0.5, 5, 0.1));
    private final BooleanSetting centerDot = add(new BooleanSetting("Center Dot", "Add a dot to any shape", false));
    private final ColorSetting color = add(new ColorSetting("Color", "Crosshair color and opacity", 0xFFEAF4FF, true));
    private final BooleanSetting targetHighlight = add(new BooleanSetting("Target Highlight", "Change color when aiming at a living entity", false));
    private final ColorSetting targetColor = add(new ColorSetting("Target Color", "Crosshair color on a living target", 0xFFFF718C, true)
        .visible(targetHighlight::get));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", "Contrasting edges for visibility", true));
    private final NumberSetting outlineWidth = add(new NumberSetting("Outline Width", "Thickness around the crosshair", 0.8, 0.5, 3, 0.1)
        .visible(outline::get));
    private final ColorSetting outlineColor = add(new ColorSetting("Outline Color", "Outline color and opacity", 0xDD080B12, true)
        .visible(outline::get));

    public CustomCrosshair() {
        super("Custom Crosshair", "24 crisp crosshair styles with a visual preset gallery", Category.VISUALS);
    }

    @Override public List<SettingSection> getSettingSections() {
        var styles = new SettingSection("Presets"); styles.add(preset); styles.add(gallery);
        var shape = new SettingSection("Shape");
        shape.add(size); shape.add(gap); shape.add(thickness); shape.add(dotSize); shape.add(centerDot);
        var colors = new SettingSection("Colors"); colors.add(color); colors.add(targetHighlight); colors.add(targetColor);
        var edges = new SettingSection("Outline"); edges.add(outline); edges.add(outlineWidth); edges.add(outlineColor);
        return List.of(styles, shape, colors, edges);
    }

    public String preset() { return preset.get(); }
    public void selectPreset(String name) { preset.set(name); }

    /** Called in vanilla's crosshair draw, so F1, perspective and spectator rules still apply. */
    public void renderCrosshair(DrawContext ctx) {
        int tint = targetHighlight.get() && mc.targetedEntity instanceof LivingEntity target && target.isAlive()
            ? targetColor.get() : color.get();
        draw(ctx, preset.get(), ctx.getScaledWindowWidth() / 2f, ctx.getScaledWindowHeight() / 2f,
            size.getFloat(), gap.getFloat(), thickness.getFloat(), dotSize.getFloat(), tint);
    }

    public void renderPreview(DrawContext ctx, String style, float x, float y) {
        draw(ctx, style, x, y, 5, 2, 1.2f, 1.2f, color.get());
    }

    private void draw(DrawContext ctx, String style, float x, float y, float s, float g, float t, float dot, int tint) {
        float oldAlpha = Render2D.getAlpha();
        ctx.getMatrices().pushMatrix();
        try {
            Render2D.setAlpha(1);
            ctx.getMatrices().translate(x, y);
            CrosshairRenderer.draw(ctx, style, s, g, t, dot, centerDot.get(), tint,
                outline.get() ? outlineWidth.getFloat() : 0, outlineColor.get());
        } finally {
            ctx.getMatrices().popMatrix();
            Render2D.setAlpha(oldAlpha);
        }
    }
}
