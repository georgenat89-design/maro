package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/**
 * The Spatial menu: it opens on a pane straight ahead with the crosshair on its middle, a click
 * with the crosshair on the Theme button opens that page, the pane stays put in the world as you
 * turn away, holding its bar swings it round with your look, and its red dot closes the menu.
 */
final class SpatialGuiChecks {
    private SpatialGuiChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static boolean await(ClientGameTestContext context, BooleanSupplier condition, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return true;
            context.waitTicks(1);
        }
        return context.computeOnClient(c -> condition.getAsBoolean());
    }

    private static ClickGuiScreen gui(MinecraftClient client) {
        return client.currentScreen instanceof ClickGuiScreen screen ? screen : null;
    }

    /** Turns the player to look straight at a point in the world. */
    private static void lookAt(ClientGameTestContext context, Vec3d target) {
        context.runOnClient(c -> {
            Vec3d eye = c.gameRenderer.getCamera().getCameraPos();
            double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
            c.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90));
            c.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        });
        context.waitTicks(4);
    }

    /** Whether the crosshair is within a few pixels of {@code x, y} on the menu. */
    private static boolean pointingAt(ClientGameTestContext context, float x, float y) {
        double[] at = context.computeOnClient(c -> gui(c).spatialView().pointer());
        return Math.abs(at[0] - x) < 4 && Math.abs(at[1] - y) < 4;
    }

    static void run(ClientGameTestContext context) {
        boolean floating = context.computeOnClient(c -> ClientSettings.spatialMenu.get());
        boolean walking = context.computeOnClient(c -> ClientSettings.spatialWalk.get());
        boolean remember = context.computeOnClient(c -> ClientSettings.spatialRemember.get());
        int guiScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        boolean blur = context.computeOnClient(c -> ClientSettings.backgroundBlur.get());
        float[] rotation = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        try {
            context.runOnClient(c -> {

                ClientSettings.spatialMenu.set(true);
                ClientSettings.spatialWalk.set(true);
                ClientSettings.spatialRemember.set(true);
                ClientSettings.backgroundBlur.set(true);
                c.inGameHud.getChatHud().clear(false);
                c.inGameHud.setOverlayMessage(net.minecraft.text.Text.empty(), false);
                c.player.setYaw(30);
                c.player.setPitch(-4);
            });
            context.waitTicks(3);
            context.runOnClient(c -> c.setScreen(new ClickGuiScreen()));
            require(await(context, () -> gui(MinecraftClient.getInstance()) != null && gui(MinecraftClient.getInstance()).spatialView().isOpen(), 20),
                    "The Spatial menu did not open");
            context.waitTicks(15);
            context.runOnClient(c -> gui(c).spatialView().recenter());
            context.waitTicks(3);
            int[] size = context.computeOnClient(c -> new int[]{gui(c).width, gui(c).height});
            double[] opened = context.computeOnClient(c -> gui(c).spatialView().pointer());
            require(pointingAt(context, size[0] / 2f, size[1] / 2f),
                    "The pane did not open straight ahead: the crosshair is at " + opened[0] + ", " + opened[1]);
            require(context.computeOnClient(c -> gui(c).spatialView().onPane()), "The crosshair was not on the pane");
            require(!context.computeOnClient(c -> gui(c).shouldPause()), "Spatial menu paused the world");
            context.takeScreenshot("maro-spatial-menu");

            // A click with the crosshair on the Theme button opens it.
            int theme = context.computeOnClient(c -> gui(c).entryCount() - 2);
            float[] button = context.computeOnClient(c -> gui(c).entryPlace(theme));
            require(button != null, "The Theme button was not drawn");
            lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(button[0], button[1])));
            double[] aimed = context.computeOnClient(c -> gui(c).spatialView().pointer());
            require(pointingAt(context, button[0], button[1]), "Looking at the Theme button did not put the crosshair on it: "
                    + aimed[0] + ", " + aimed[1] + " for " + button[0] + ", " + button[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(await(context, () -> gui(MinecraftClient.getInstance()).selectedEntry() == theme, 10),
                    "Clicking the Theme button with the crosshair did not open it");
            context.waitTicks(8);
            context.takeScreenshot("maro-spatial-theme");

            // Turning away leaves the pane where it is.
            Vec3d centre = context.computeOnClient(c -> gui(c).spatialView().centre());
            context.runOnClient(c -> c.player.setYaw(c.player.getYaw() - 40));
            context.waitTicks(5);
            require(context.computeOnClient(c -> gui(c).spatialView().centre()).distanceTo(centre) < 1e-6, "The pane moved when only the head turned");
            context.takeScreenshot("maro-spatial-turned");

            // Holding the bar swings the pane round with the look.
            float[] bar = context.computeOnClient(c -> gui(c).spatialView().barCenter());
            lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(bar[0], bar[1])));
            require(pointingAt(context, bar[0], bar[1]), "Looking at the bar did not put the crosshair on it");
            context.takeScreenshot("maro-spatial-bar-target");
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            require(context.computeOnClient(c -> gui(c).spatialView().moving()), "Holding the bar did not take hold of the pane");
            context.runOnClient(c -> c.player.setYaw(c.player.getYaw() + 55));
            context.waitTicks(20);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            Vec3d moved = context.computeOnClient(c -> gui(c).spatialView().centre());
            require(!context.computeOnClient(c -> gui(c).spatialView().moving()), "Letting go of the bar did not let go of the pane");
            require(moved.distanceTo(centre) > 1.2, "The pane did not follow the look while its bar was held: moved " + moved.distanceTo(centre));
            require(pointingAt(context, bar[0], bar[1]), "The bar did not stay under the crosshair while moving the pane");
            context.waitTicks(5);
            context.takeScreenshot("maro-spatial-moved");

            // The red dot closes the menu.
            float[] dot = context.computeOnClient(c -> gui(c).spatialView().dotCenter());
            lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(dot[0], dot[1])));
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            require(await(context, () -> MinecraftClient.getInstance().currentScreen == null, 60), "The red dot did not close the menu");

            // Opened again, it is back where it was left.
            context.runOnClient(c -> c.setScreen(new ClickGuiScreen()));
            context.waitTicks(15);
            Vec3d again = context.computeOnClient(c -> gui(c).spatialView().centre());
            require(again.distanceTo(moved) < 1e-3, "The window did not open where it was left: " + again + " not " + moved);

            // Walking with it open: the player moves, the window stays.
            Vec3d from = context.computeOnClient(c -> c.player.getEntityPos());
            context.getInput().holdKey(o -> o.backKey);
            context.waitTicks(12);
            context.getInput().releaseKey(o -> o.backKey);
            context.waitTicks(3);
            Vec3d to = context.computeOnClient(c -> c.player.getEntityPos());
            require(to.distanceTo(from) > 0.8, "Walking with the floating window open did not move the player: " + from.distanceTo(to));
            require(context.computeOnClient(c -> gui(c).spatialView().centre()).distanceTo(moved) < 1e-3, "Walking moved the window");
            require(context.computeOnClient(c -> gui(c).getSearch().getText().isEmpty()), "Walking typed into search");
            context.takeScreenshot("maro-spatial-walked");
            // Right up to it and partly through: it is cut cleanly where it meets the eye, not warped.
            context.getInput().holdKey(o -> o.forwardKey);
            context.waitTicks(16);
            context.getInput().releaseKey(o -> o.forwardKey);
            context.waitTicks(4);
            context.takeScreenshot("maro-spatial-close");

            // Opting out, typing, losing focus and closing must release the movement keys.
            context.runOnClient(c -> ClientSettings.spatialWalk.set(false));
            context.getInput().holdKey(o -> o.backKey);
            context.waitTicks(3);
            require(!context.computeOnClient(c -> c.options.backKey.isPressed()), "Walk With Menu Open off still held a movement key");
            context.getInput().releaseKey(o -> o.backKey);
            context.runOnClient(c -> {
                ClientSettings.spatialWalk.set(true);
                gui(c).keyPressed(new net.minecraft.client.input.KeyInput(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_CONTROL));
            });
            context.getInput().typeChars("Example");
            context.getInput().holdKey(o -> o.backKey);
            context.waitTicks(3);
            require(!context.computeOnClient(c -> c.options.backKey.isPressed()), "Typing kept a movement key pressed");
            require(context.computeOnClient(c -> gui(c).getSearch().getText().equals("Example")), "Focused search did not accept text");
            context.getInput().releaseKey(o -> o.backKey);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.getInput().holdKey(o -> o.backKey);
            context.waitTicks(3);
            require(context.computeOnClient(c -> c.options.backKey.isPressed()), "Leaving search did not resume walking");
            context.runOnClient(c -> {
                c.onWindowFocusChanged(false); gui(c).tick();
                require(!c.options.backKey.isPressed(), "Focus loss retained floating-window movement");
                c.onWindowFocusChanged(true);
                gui(c).close(); gui(c).tick();
                require(!c.options.backKey.isPressed(), "Closing retained floating-window movement");
            });
            context.getInput().releaseKey(o -> o.backKey);
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(3);

            // A right click with the crosshair on a module's card switches it.
            context.runOnClient(c -> {

                c.player.setYaw(10);
                c.player.setPitch(0);
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                c.setScreen(new ClickGuiScreen());
                gui(c).spatialView().recenter();
            });
            context.waitTicks(18);
            context.runOnClient(c -> gui(c).getSearch().setText("Example"));
            context.waitTicks(6);
            require(context.computeOnClient(c -> gui(c).spatialView().isOpen()), "The menu did not float");
            dev.maro.module.Module example = dev.maro.module.ModuleManager.getByName("Example");
            boolean wasOn = context.computeOnClient(c -> example.isEnabled());
            float[] row = context.computeOnClient(c -> gui(c).modulePlace(example));
            require(row != null, "The Example module has no card on the floating window");
            context.takeScreenshot("maro-spatial-card");
            lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(row[0], row[1])));
            require(pointingAt(context, row[0], row[1]), "Looking at a module's row did not put the crosshair on it");
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(await(context, () -> example.isEnabled() != wasOn, 10), "Right clicking a module's card on the floating window did not switch it");
            context.runOnClient(c -> example.setEnabled(wasOn));
            context.waitTicks(4);
            context.takeScreenshot("maro-spatial-card-angle");
            context.runOnClient(c -> c.setScreen(null));
            for (int scale : new int[]{2, 3, 4}) {
                context.runOnClient(c -> {
                    c.options.getGuiScale().setValue(scale); c.onResolutionChanged();
                    c.setScreen(new ClickGuiScreen());
                    gui(c).spatialView().recenter();
                    gui(c).openPage(0);
                });
                context.waitTicks(15);
                int page = context.computeOnClient(c -> gui(c).entryCount() - 2);
                float[] target = context.computeOnClient(c -> gui(c).entryPlace(page));
                lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(target[0],target[1])));
                require(pointingAt(context,target[0],target[1]),"Spatial picking drift at GUI scale " + scale);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(await(context, () -> gui(MinecraftClient.getInstance()).selectedEntry() == page, 10),"Spatial click failed at GUI scale " + scale);
                context.takeScreenshot("maro-spatial-scale-" + scale);
                context.runOnClient(c -> c.setScreen(null));
            }
            System.out.println("[floating-window-proof] window, crosshair clicks, move/close, remembered placement, walking, text/focus guards and GUI scales 2/3/4 passed");
        } finally {
            context.runOnClient(c -> {
                if (c.currentScreen instanceof ClickGuiScreen) c.setScreen(null);

                ClientSettings.backgroundBlur.set(blur);
                c.options.getGuiScale().setValue(guiScale); c.onResolutionChanged();
                ClientSettings.spatialMenu.set(floating);
                ClientSettings.spatialWalk.set(walking);
                ClientSettings.spatialRemember.set(remember);
                c.player.setYaw(rotation[0]);
                c.player.setPitch(rotation[1]);
            });
            context.waitTicks(3);
        }
    }
}
