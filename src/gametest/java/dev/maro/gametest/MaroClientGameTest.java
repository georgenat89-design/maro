package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.ExampleModule;
import dev.maro.module.impl.visuals.StretchRes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Smoke test run in CI against the production jar: if a mixin, the renderer or any page
 * throws, the game crashes and the build fails. Screenshots of every page are uploaded.
 */
public class MaroClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            if (ModuleManager.getByName("Example") == null) ModuleManager.register(new ExampleModule());
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            // make sure nothing (pause menu, toasts...) is in the way before testing the keybind
            context.setScreen(() -> null);
            context.waitTicks(5);
            String before = context.computeOnClient(client -> describe(client));
            context.takeScreenshot("maro-00-world");

            // open through the real keybind path (KeyboardMixin -> Maro.onKey)
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(5);
            boolean opened = context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen);
            if (!opened) {
                String after = context.computeOnClient(client -> describe(client));
                context.takeScreenshot("maro-00-keybind-failed");
                throw new AssertionError("Right Shift did not open the menu. before=[" + before + "] after=[" + after + "]");
            }
            settle(context);
            context.takeScreenshot("maro-01-open");

            String[] pages = {"combat", "movement", "player", "visuals", "misc", "settings", "configs", "theme", "socials"};
            for (int i = 0; i < pages.length; i++) {
                final int index = i;
                context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen).openPage(index));
                settle(context);
                context.takeScreenshot("maro-page-" + (i + 1 < 10 ? "0" : "") + (i + 1) + "-" + pages[i]);
            }

            context.runOnClient(client -> {
                Module example = ModuleManager.getByName("Example");
                example.setEnabled(true);
                ((ClickGuiScreen) client.currentScreen).openModuleSettings(example);
            });
            settle(context);
            context.takeScreenshot("maro-module-settings");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // back to the grid
            settle(context);
            context.getInput().typeChars("exa");
            settle(context);
            context.takeScreenshot("maro-search");

            // Escape clears the search, then unfocuses it, then closes the menu
            for (int i = 0; i < 4 && context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen); i++) {
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                settle(context);
            }
            context.waitForScreen(null);

            // Stretch Res must actually change the world projection. Build a brick wall in front of
            // the camera so the horizontal stretch is obvious in the screenshots.
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-6 ^-1 ^7 ^6 ^5 ^7 minecraft:bricks");
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-1 ^1 ^7 ^1 ^3 ^7 minecraft:gold_block");
            settle(context);
            settle(context);
            context.takeScreenshot("maro-stretch-off");
            float[] m00 = context.computeOnClient(client -> {
                org.joml.Matrix4f base = client.gameRenderer.getBasicProjectionMatrix(70f);
                ModuleManager.get(StretchRes.class).setEnabled(true);
                return new float[]{base.m00(), StretchRes.apply(base).m00()};
            });
            if (Math.abs(m00[0] - m00[1]) < 1e-4f) {
                throw new AssertionError("Stretch Res did not change the projection (m00 " + m00[0] + " -> " + m00[1] + ")");
            }
            settle(context);
            settle(context);
            context.takeScreenshot("maro-stretch-on");
            context.runOnClient(client -> ModuleManager.get(StretchRes.class).setEnabled(false));
        }
    }

    private static String describe(net.minecraft.client.MinecraftClient client) {
        return "screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName())
                + " player=" + (client.player != null) + " world=" + (client.world != null);
    }

    /** Lets time-based animations finish (the game only advances inside wait calls). */
    private static void settle(ClientGameTestContext context) {
        long end = System.currentTimeMillis() + 900;
        while (System.currentTimeMillis() < end) context.waitTick();
    }
}
