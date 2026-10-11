package dev.maro.gametest;

import dev.maro.gui.hud.CrosshairPresetsScreen;
import dev.maro.gui.render.CrosshairRenderer;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomCrosshair;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;

/** Renders every preset through the production crosshair mixin and drives the gallery with real input. */
final class CrosshairChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(CustomCrosshair module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    /** Magenta pixels in the middle of the screen, where the crosshair is. */
    private static int magenta(Path screenshot) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            int count = 0, cx = image.getWidth() / 2, cy = image.getHeight() / 2;
            for (int y = Math.max(0, cy - 120); y < Math.min(image.getHeight(), cy + 120); y++) {
                for (int x = Math.max(0, cx - 320); x < Math.min(image.getWidth(), cx + 320); x++) {
                    int rgb = image.getRGB(x, y);
                    if ((rgb >> 16 & 0xFF) > 180 && (rgb >> 8 & 0xFF) < 90 && (rgb & 0xFF) > 180) count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + screenshot, e);
        }
    }
    static void run(ClientGameTestContext context) {
        var module = ModuleManager.get(CustomCrosshair.class);
        require(module != null && CrosshairRenderer.PRESETS.size() == 25, "Crosshair presets were not registered");
        int originalScale = context.computeOnClient(client -> client.options.getGuiScale().getValue());
        try {
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(2); client.onResolutionChanged();
                module.setEnabled(true);
                client.setScreen(new CrosshairPresetsScreen(null, module));
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-crosshair-gallery");
            int[] point = context.computeOnClient(client -> {
                int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
                float panelWidth = Math.min(640, width - 24), panelHeight = Math.min(360, height - 24);
                double scale = client.getWindow().getScaleFactor();
                return new int[]{(int) (((width - panelWidth) / 2 + 40) * scale),
                    (int) (((height - panelHeight) / 2 + 66) * scale)};
            });
            context.getInput().setCursorPos(point[0], point[1]);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            require(module.preset().equals("Dot"), "Clicking the first crosshair tile did not select Dot");
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            context.waitTick();
            require(module.preset().equals("Square Dot"), "Gallery keyboard selection failed");

            context.setScreen(() -> null);
            for (String preset : CrosshairRenderer.PRESETS) {
                context.runOnClient(client -> module.selectPreset(preset));
                context.waitTicks(2);
            }
            context.runOnClient(client -> {
                module.selectPreset("Reticle");
                ((ColorSetting) module.getSettings().stream().filter(s -> s.getName().equals("Color"))
                    .findFirst().orElseThrow()).set(0xFF45EBCB);
                ((NumberSetting) module.getSettings().stream().filter(s -> s.getName().equals("Size"))
                    .findFirst().orElseThrow()).set(8.0);
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-crosshair-reticle");
            context.runOnClient(client -> module.selectPreset("Star of David"));
            context.waitTicks(3);
            context.takeScreenshot("maro-crosshair-star-of-david");
            // Spin: the crosshair keeps turning while it is on, and stays put while it is off.
            require(module.spinAngle() == 0, "Crosshair turned with Spin off");
            context.runOnClient(client -> {
                module.selectPreset("T Cross");
                ((dev.maro.setting.BooleanSetting) setting(module, "Spin")).set(true);
            });
            float before = context.computeOnClient(client -> module.spinAngle());
            context.waitTicks(5);
            float after = context.computeOnClient(client -> module.spinAngle());
            require(before != after, "Spin is on but the crosshair did not turn: " + before + " -> " + after);
            context.takeScreenshot("maro-crosshair-spin");
            context.runOnClient(client -> ((dev.maro.setting.BooleanSetting) setting(module, "Spin")).set(false));
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(4); client.onResolutionChanged();
                module.selectPreset("Dot");
                client.setScreen(new CrosshairPresetsScreen(null, module));
            });
            context.waitTicks(3);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            context.waitTick();
            require(module.preset().equals("Four Dots"), "Gallery did not scroll to the last preset on a small screen");
            context.takeScreenshot("maro-crosshair-gallery-small");

            // Text: what you type is drawn as the crosshair, cut to the character limit.
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                client.options.getGuiScale().setValue(2); client.onResolutionChanged();
                ((ModeSetting) setting(module, "Source")).set("Text");
                ((TextSetting) setting(module, "Text")).set("maro.gg is the best");
                ((ColorSetting) setting(module, "Color")).set(0xFFFF00FF);
                ((NumberSetting) setting(module, "Text Size")).set(14.0);
            });
            context.waitTicks(3);
            require(module.text().equals("maro.gg is t"),
                "Text crosshair was not cut to " + CustomCrosshair.MAX_TEXT + " characters: '" + module.text() + "'");
            int minecraftFont = magenta(context.takeScreenshot("maro-crosshair-text"));
            context.runOnClient(client -> ((ModeSetting) setting(module, "Font")).set("Clean"));
            context.waitTicks(3);
            int cleanFont = magenta(context.takeScreenshot("maro-crosshair-text-clean"));
            System.out.println("CROSSHAIR text magenta minecraft=" + minecraftFont + " clean=" + cleanFont);
            require(minecraftFont > 60 && cleanFont > 60, "Text crosshair was not drawn: " + minecraftFont + " / " + cleanFont);
            // The text box it is typed into, in the module's settings.
            context.runOnClient(client -> {
                client.setScreen(new ClickGuiScreen());
                ((ClickGuiScreen) client.currentScreen).openModuleSettings(module, "Text");
            });
            context.waitTicks(12);
            context.takeScreenshot("maro-crosshair-text-settings");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(client -> {
                module.setEnabled(false); module.getSettings().forEach(s -> s.reset());
                client.setScreen(null); client.options.getGuiScale().setValue(originalScale); client.onResolutionChanged();
            });
            context.waitTicks(3);
        }
    }
}
