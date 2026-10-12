package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.KeyboardStrokes;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard Strokes: the classic seven keys by default, any key on the keyboard picture can be added
 * or taken off, held keys light, the keyboard layout keeps real key positions, and the picker draws.
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
        int guiScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        try {
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                require(module.cells().size() == 7, "The classic layout should be seven keys: " + module.cells().size());
                require(module.hudWidth() > 0 && module.hudHeight() > 0, "Keyboard Strokes should have a size");
                require(!module.isShown(GLFW.GLFW_KEY_LEFT_SHIFT), "Shift should start hidden");
                module.toggleKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                require(module.isShown(GLFW.GLFW_KEY_LEFT_SHIFT) && module.cells().size() == 8, "Choosing shift should add a key");
                module.toggleKey(GLFW.GLFW_KEY_LEFT_SHIFT);
                require(!module.isShown(GLFW.GLFW_KEY_LEFT_SHIFT) && module.cells().size() == 7, "Choosing shift again should take it off");
                module.useGameKeys();
                require(module.cells().size() == 9, "My controls should pick the nine game keys: " + module.cells().size());
                module.resetKeys();
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
            // The keyboard layout keeps the keys where they sit: the space bar is far wider than a key.
            context.runOnClient(c -> module.layoutSetting().set("Keyboard"));
            context.waitTicks(2);
            context.runOnClient(c -> require(module.hudWidth() > KeyboardStrokes.WIDTH * 1.5f * module.hudScale(),
                    "The keyboard layout should be as wide as the real key positions: " + module.hudWidth()));
            context.takeScreenshot("maro-keyboard-strokes-keyboard");
            context.runOnClient(c -> module.layoutSetting().set("Compact"));
            // The picker, at the usual GUI scale so the keys are readable.
            context.runOnClient(c -> {
                c.options.getGuiScale().setValue(2);
                c.setScreen(module.panel(null));
            });
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
                c.options.getGuiScale().setValue(guiScale);
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
            });
            context.waitTicks(2);
        }
    }
}
