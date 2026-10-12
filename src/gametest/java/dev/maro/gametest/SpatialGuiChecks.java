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
        String style = context.computeOnClient(c -> ClientSettings.menuStyle.get());
        float[] rotation = context.computeOnClient(c -> new float[]{c.player.getYaw(), c.player.getPitch()});
        try {
            context.runOnClient(c -> {
                ClientSettings.menuStyle.set("Spatial");
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
            require(pointingAt(context, size[0] / 2f, size[1] / 2f),
                    "The pane did not open straight ahead: the crosshair is at " + java.util.Arrays.toString(context.computeOnClient(c -> gui(c).spatialView().pointer())));
            require(context.computeOnClient(c -> gui(c).spatialView().onPane()), "The crosshair was not on the pane");
            context.takeScreenshot("maro-spatial-menu");

            // A click with the crosshair on the Theme button opens it.
            int theme = context.computeOnClient(c -> gui(c).entryCount() - 2);
            float[] button = context.computeOnClient(c -> gui(c).entryPlace(theme));
            require(button != null, "The Theme button was not drawn");
            lookAt(context, context.computeOnClient(c -> gui(c).spatialView().worldAt(button[0], button[1])));
            require(pointingAt(context, button[0], button[1]), "Looking at the Theme button did not put the crosshair on it: "
                    + java.util.Arrays.toString(context.computeOnClient(c -> gui(c).spatialView().pointer())) + " for " + button[0] + ", " + button[1]);
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
        } finally {
            context.runOnClient(c -> {
                if (c.currentScreen instanceof ClickGuiScreen) c.setScreen(null);
                ClientSettings.menuStyle.set(style);
                c.player.setYaw(rotation[0]);
                c.player.setPitch(rotation[1]);
            });
            context.waitTicks(3);
        }
    }
}
