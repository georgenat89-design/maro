package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.KeyBinding;

/**
 * Keyboard Strokes: the classic seven keys by default, any keybind can be added or taken off from
 * the chooser, and the chooser draws.
 */
final class KeyboardStrokesChecks {
    private KeyboardStrokesChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        KeyboardStrokes module = ModuleManager.get(KeyboardStrokes.class);
        require(module != null, "Keyboard Strokes was not registered");
        try {
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                require(module.cells().size() == 7, "The classic layout should be seven keys: " + module.cells().size());
                require(module.hudWidth() > 0 && module.hudHeight() > 0, "Keyboard Strokes should have a size");
                KeyBinding sneak = c.options.sneakKey;
                require(!module.isShown(sneak), "Sneak should start hidden");
                module.toggleKey(sneak);
                require(module.isShown(sneak) && module.cells().size() == 8, "Choosing sneak should add a key");
                module.toggleKey(sneak);
                require(!module.isShown(sneak) && module.cells().size() == 7, "Choosing sneak again should take it off");
            });
            context.takeScreenshot("maro-keyboard-strokes");
            // Held keys light up: hold forward and jump, and the screenshot should show W and SPACE lit.
            context.getInput().holdKey(o -> o.forwardKey);
            context.getInput().holdKey(o -> o.jumpKey);
            try {
                context.waitTicks(4);
                require(context.computeOnClient(c -> c.options.forwardKey.isPressed()), "Forward should read as held");
                context.takeScreenshot("maro-keyboard-strokes-held");
            } finally {
                context.getInput().releaseKey(o -> o.forwardKey);
                context.getInput().releaseKey(o -> o.jumpKey);
            }
            context.runOnClient(c -> c.setScreen(module.panel(null)));
            context.waitTicks(10);
            context.takeScreenshot("maro-keyboard-strokes-menu");
            context.runOnClient(c -> {
                module.clearKeys();
                require(module.cells().isEmpty(), "Clearing should remove every key");
                c.setScreen(null);
            });
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
            });
            context.waitTicks(2);
        }
    }
}
