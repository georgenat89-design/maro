package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.ExampleModule;
import dev.maro.module.impl.visuals.StretchRes;
import dev.maro.module.impl.player.FastPlace;
import dev.maro.module.impl.misc.ScreenHider;
import dev.maro.gui.hider.RegionEditorScreen;
import dev.maro.mixin.MinecraftClientAccessor;
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

            // Fast Place: with a block in hand the vanilla 4-tick cooldown is cut to the delay (0)
            singleplayer.getServer().runCommand("give @a minecraft:stone 64");
            settle(context);
            int cooldown = context.computeOnClient(client -> {
                client.player.getInventory().setSelectedSlot(0);
                FastPlace fastPlace = ModuleManager.get(FastPlace.class);
                fastPlace.setEnabled(true);
                ((MinecraftClientAccessor) client).maro$setItemUseCooldown(4);
                fastPlace.onTick();
                int value = ((MinecraftClientAccessor) client).maro$getItemUseCooldown();
                fastPlace.setEnabled(false);
                return value;
            });
            if (cooldown != 0) throw new AssertionError("Fast Place did not shorten the cooldown (got " + cooldown + ")");

            // Screen Hider: scribble an area in the editor with the real mouse, then check it is blurred
            context.runOnClient(client -> {
                ScreenHider hider = ModuleManager.get(ScreenHider.class);
                hider.setEnabled(true);
                hider.regions().list().clear();
                client.setScreen(new RegionEditorScreen(hider.regions(), null));
            });
            settle(context);
            context.getInput().setCursorPos(420, 220);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            for (int i = 1; i <= 12; i++) {
                context.getInput().setCursorPos(420 + i * 36, i % 2 == 0 ? 230 : 470);
                context.waitTick();
            }
            settle(context);
            context.takeScreenshot("maro-hider-drawing");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            settle(context);
            int areas = context.computeOnClient(client -> ModuleManager.get(ScreenHider.class).regions().list().size());
            if (areas != 1) throw new AssertionError("Drawing in the Screen Hider editor made " + areas + " areas (expected 1)");
            context.takeScreenshot("maro-hider-editor");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
            settle(context);
            settle(context);
            context.takeScreenshot("maro-hider-blur");
            context.runOnClient(client -> ModuleManager.get(ScreenHider.class).setEnabled(false));
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
