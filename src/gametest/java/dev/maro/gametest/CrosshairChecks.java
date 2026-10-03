package dev.maro.gametest;

import dev.maro.gui.hud.CrosshairPresetsScreen;
import dev.maro.gui.render.CrosshairRenderer;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomCrosshair;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

/** Renders every preset through the production crosshair mixin and drives the gallery with real input. */
final class CrosshairChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void run(ClientGameTestContext context) {
        var module = ModuleManager.get(CustomCrosshair.class);
        require(module != null && CrosshairRenderer.PRESETS.size() == 24, "Crosshair presets were not registered");
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
        } finally {
            context.runOnClient(client -> {
                module.setEnabled(false); module.getSettings().forEach(s -> s.reset());
                client.setScreen(null); client.options.getGuiScale().setValue(originalScale); client.onResolutionChanged();
            });
            context.waitTicks(3);
        }
    }
}
