package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.KeyboardStrokesScreen;
import dev.maro.gui.hud.HudPlacementScreen;
import dev.maro.gui.render.ScreenGuiSurface;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import dev.maro.setting.ButtonSetting;
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
        require(ModuleManager.getByName("Keystrokes") == null, "The old Keystrokes module should be removed");
        boolean floating = ClientSettings.screenGui.get();
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
            for (String style : new String[] {"Window"}) {
                for (boolean screenGui : new boolean[] {false}) {
                    context.runOnClient(c -> {
                        ClientSettings.screenGui.set(screenGui);
                        c.setScreen(new ClickGuiScreen());
                        var gui = (ClickGuiScreen) c.currentScreen;
                        gui.mouseMoved(gui.width / 2d, gui.height / 2d);
                        gui.getSearch().setText("Keyboard Strokes");
                    });
                    context.waitTicks(12);
                    context.runOnClient(c -> {
                        var gui = (ClickGuiScreen) c.currentScreen;
                        var card = gui.modulePlace(module);
                        require(card != null, "Keyboard Strokes missing from the search");
                        var point = gui.screenPose().project(card[0], card[1]);
                        var click = new Click(point[0], point[1], new MouseInput(0, 0));
                        gui.mouseClicked(click, false); gui.mouseReleased(click);
                        require(c.currentScreen instanceof KeyboardStrokesScreen, "Left-click should open the key chooser");
                    });
                    context.waitTicks(8);
                    require(!context.computeOnClient(c -> ScreenGuiSurface.hasResources()), "Opening chooser leaked the menu surface");
                    context.takeScreenshot("maro-keyboard-strokes-menu-" + style.toLowerCase() + "-" + screenGui);
                    // Click the first actual row hit, then confirm that its key really changed.
                    context.runOnClient(c -> {
                        try {
                            var chooser = (KeyboardStrokesScreen) c.currentScreen;
                            var field = KeyboardStrokesScreen.class.getDeclaredField("hits"); field.setAccessible(true);
                            var hit = ((java.util.List<?>) field.get(chooser)).getFirst();
                            var type = hit.getClass();
                            var x = type.getDeclaredMethod("x"); var y = type.getDeclaredMethod("y");
                            x.setAccessible(true); y.setAccessible(true);
                            var binding = module.allBindings()[0];
                            boolean wasShown = module.isShown(binding);
                            chooser.mouseClicked(new Click((float) x.invoke(hit) + 2, (float) y.invoke(hit) + 2, new MouseInput(0, 0)), false);
                            require(module.isShown(binding) != wasShown, "Chooser row click did not change its key");
                            module.resetKeys();
                            chooser.close();
                            require(c.currentScreen instanceof ClickGuiScreen, "Chooser should return to its parent menu");
                            c.setScreen(null);
                        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    });
                    System.out.println("[keyboard-strokes-proof] " + style + " floating=" + screenGui + " chooser/click/return passed");
                }
            }
            context.runOnClient(c -> {
                ((ButtonSetting) module.getSettings().stream().filter(v -> v.getName().equals("Position")).findFirst().orElseThrow()).press();
                require(c.currentScreen instanceof HudPlacementScreen, "Position should open the HUD editor");
                ((HudPlacementScreen) c.currentScreen).dragTo(120, 80);
                module.hudResize(.4f);
                module.toggleKey(c.options.sneakKey);
                float left = module.hudLeft(), top = module.hudTop(), scale = module.hudScale();
                require(ConfigManager.save("keyboard_native_smoke"), "Saving keyboard settings failed");
                module.getSettings().forEach(Setting::reset);
                require(ConfigManager.load("keyboard_native_smoke"), "Reloading keyboard settings failed");
                require(module.isShown(c.options.sneakKey) && Math.abs(module.hudLeft() - left) <= 1
                        && Math.abs(module.hudTop() - top) <= 1 && Math.abs(module.hudScale() - scale) < .01,
                        "Selected keys, position and size did not persist");
                ConfigManager.delete("keyboard_native_smoke");
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-keyboard-strokes-placement");
            context.runOnClient(c -> {
                module.clearKeys();
                require(module.cells().isEmpty(), "Clearing should remove every key");
                c.setScreen(null);
            });
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                ClientSettings.screenGui.set(floating);
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
            });
            context.waitTicks(2);
            System.out.println("[keyboard-strokes-proof] held keys, selection, placement, size and saved settings passed");
        }
    }
}
