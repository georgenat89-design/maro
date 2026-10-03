package dev.maro.module.impl.misc;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hider.HiderRenderer;
import dev.maro.gui.hider.RegionEditorScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.RegionsSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Streamer privacy: blur, pixelate or black out areas of the screen you draw yourself.
 * Areas are stored as fractions of the screen so they stay put at any resolution or GUI scale.
 */
public class ScreenHider extends Module {
    private static ScreenHider instance;

    private final ButtonSetting edit = add(new ButtonSetting("Hidden Areas", "Draw the parts of the screen to hide", "Edit",
            () -> mc.setScreen(new RegionEditorScreen(regions(), mc.currentScreen))));
    private final ModeSetting style = add(new ModeSetting("Style", "How hidden areas look", "Blur", "Blur", "Pixelate", "Solid", "Banner"));
    private final NumberSetting strength = add(new NumberSetting("Strength", "How strong the blur or pixelation is", 60, 5, 100, 1)
            .suffix("%").visible(() -> style.is("Blur") || style.is("Pixelate")));
    private final ModeSetting bannerFit = add(new ModeSetting("Banner Fit", "Fit keeps the whole banner, Fill covers the area, Stretch fills it exactly",
            "Fit", "Fit", "Fill", "Stretch").visible(() -> style.is("Banner")));
    private final ColorSetting color = add(new ColorSetting("Color", "Fill colour for Solid", 0xFF0F0F14, true)
            .visible(() -> style.is("Solid")));
    private final BooleanSetting overMenus = add(new BooleanSetting("Over Menus", "Keep areas hidden while inventories, chat and other menus are open", true));
    private final RegionsSetting areas = add(new RegionsSetting("Areas"));

    public ScreenHider() {
        super("Screen Hider", "Blur parts of your screen while streaming", Category.MISC);
        instance = this;
    }

    public static ScreenHider get() {
        return instance;
    }

    public RegionsSetting regions() {
        return areas;
    }

    public void render(DrawContext ctx) {
        HiderRenderer.draw(ctx, areas.list(), style.get(), strength.getFloat() / 100f, color.get(), bannerFit.get());
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        render(ctx);
    }

    /** Called after a menu renders (and when the HUD is hidden with F1). */
    public static void renderOver(Screen screen, DrawContext ctx) {
        ScreenHider m = instance;
        if (m == null || !m.isEnabled()) return;
        if (screen != null && (!m.overMenus.get() || screen instanceof RegionEditorScreen || screen instanceof ClickGuiScreen)) return;
        m.render(ctx);
    }
}
