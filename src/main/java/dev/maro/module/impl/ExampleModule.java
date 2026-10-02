package dev.maro.module.impl;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;

/**
 * Template showing every setting type. It is NOT registered by default - add
 * {@code register(new ExampleModule());} to ModuleManager#init() to preview it in the GUI,
 * then delete this file once you have your own modules.
 */
public class ExampleModule extends Module {
    private final BooleanSetting flag = add(new BooleanSetting("Enabled Thing", "A simple on/off option", true));
    private final NumberSetting amount = add(new NumberSetting("Amount", "A slider value", 4.5, 0, 10, 0.5));
    private final ModeSetting mode = add(new ModeSetting("Mode", "Pick one option", "Fast", "Fast", "Smooth", "Legit"));
    private final NumberSetting smoothness = add(new NumberSetting("Smoothness", "Only visible in Smooth mode", 50, 0, 100, 1)
            .suffix("%").visible(() -> mode.is("Smooth")));
    private final ColorSetting color = add(new ColorSetting("Color", "A colour with alpha", 0xFF2F7BFF, true));

    public ExampleModule() {
        super("Example", "Shows how modules and settings work", Category.MISC);
    }

    @Override
    protected void onEnable() {
        // called when toggled on
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        // your logic, e.g. mc.player.setSprinting(true);
    }
}
