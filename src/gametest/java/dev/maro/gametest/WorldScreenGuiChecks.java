package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.ScreenGuiSurface;
import dev.maro.module.ModuleManager;
import dev.maro.gametest.mixin.PhoneMouseProbe;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

final class WorldScreenGuiChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static ClickGuiScreen gui(MinecraftClient c) { return (ClickGuiScreen) c.currentScreen; }
    private static void aim(ClientGameTestContext context, Vec3d target) {
        context.runOnClient(c -> {
            var d = target.subtract(c.player.getEyePos());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            c.player.setYaw(yaw); c.player.setPitch(pitch); c.player.setHeadYaw(yaw);
        });
        context.waitTicks(4);
    }
    private static double[] row(ClickGuiScreen gui, dev.maro.module.Module module) {
        if (gui.showingPanels()) { var p = gui.panelPlace(module); return new double[] {p[0], p[1]}; }
        try {
            var field = ClickGuiScreen.class.getDeclaredField("hits"); field.setAccessible(true);
            for (var hit : (java.util.List<?>) field.get(gui)) {
                var type = hit.getClass(); var h = type.getDeclaredMethod("h"); h.setAccessible(true);
                if (Math.abs((float) h.invoke(hit) - 40) > .01) continue;
                var x = type.getDeclaredMethod("x"); var y = type.getDeclaredMethod("y");
                x.setAccessible(true); y.setAccessible(true);
                return new double[] {(float) x.invoke(hit) + 20, (float) y.invoke(hit) + 20};
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        throw new AssertionError("No module card was drawn");
    }
    private static Vec3d settingPoint(ClickGuiScreen gui, Object setting, double fraction) {
        try {
            var list = ClickGuiScreen.class.getDeclaredField("hits"); list.setAccessible(true);
            for (var hit : (java.util.List<?>) list.get(gui)) {
                var handlerMethod = hit.getClass().getDeclaredMethod("handler"); handlerMethod.setAccessible(true);
                var handler = handlerMethod.invoke(hit);
                for (var captured : handler.getClass().getDeclaredFields()) {
                    captured.setAccessible(true);
                    if (captured.get(handler) != setting) continue;
                    double[] rectangle = new double[4]; int i = 0;
                    for (String name : new String[]{"x", "y", "w", "h"}) {
                        var m = hit.getClass().getDeclaredMethod(name); m.setAccessible(true);
                        rectangle[i++] = ((Number) m.invoke(hit)).doubleValue();
                    }
                    return gui.screenPose().worldPoint(rectangle[0] + rectangle[2] * fraction, rectangle[1] + rectangle[3] / 2);
                }
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        throw new AssertionError("No visible control for setting");
    }
    static void run(ClientGameTestContext context) {
        var example = ModuleManager.getByName("Example");
        boolean on = example.isEnabled();
        var savedSettings = example.getSettings().stream().map(dev.maro.setting.Setting::toJson).toList();
        try {
            for (String layout : new String[] {"Panels", "Window"}) {
                context.runOnClient(c -> {
                    ClientSettings.menuStyle.set(layout); ClientSettings.screenGui.set(true); ClientSettings.screenDistance.set(3d);
                    ClientSettings.customFont.set(true); c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                    c.player.setYaw(0); c.player.setPitch(0);
                    c.setScreen(new ClickGuiScreen()); gui(c).getSearch().setText("Example");
                });
                context.waitTicks(15);
                var centre = context.computeOnClient(c -> gui(c).screenPose().centre());
                require(context.computeOnClient(c -> c.mouse.isCursorLocked() && gui(c).worldControls() && !gui(c).shouldPause()), "World GUI should capture mouse and keep the game running");
                context.takeScreenshot("maro-world-gui-" + layout.toLowerCase() + "-placed");
                // Send the real cursor callback: the player must turn, rather than a fake UI camera.
                context.runOnClient(c -> {
                    var probe = (PhoneMouseProbe) c.mouse;
                    c.mouse.setResolutionChanged(); probe.maro$cursor(c.getWindow().getHandle(), 100, 100); c.mouse.tick();
                    float yaw = c.player.getYaw();
                    probe.maro$cursor(c.getWindow().getHandle(), 148, 80); c.mouse.tick();
                    require(c.player.getYaw() != yaw, "Captured mouse did not turn the player normally");
                });
                context.waitTicks(3);
                require(context.computeOnClient(c -> gui(c).screenPose().centre().squaredDistanceTo(centre) < 1e-8), "Looking around moved the menu in the world");
                var start = context.computeOnClient(c -> c.player.getEntityPos());
                context.getInput().holdKey(GLFW.GLFW_KEY_D); context.waitTicks(8); context.getInput().releaseKey(GLFW.GLFW_KEY_D);
                context.waitTicks(4);
                require(context.computeOnClient(c -> c.player.getEntityPos().squaredDistanceTo(start) > .03), "Player did not walk while world GUI was open");
                require(context.computeOnClient(c -> gui(c).getSearch().getText().equals("Example")), "Movement typed into the search box");
                require(context.computeOnClient(c -> gui(c).screenPose().centre().squaredDistanceTo(centre) < 1e-8), "Walking dragged the menu along with the player");
                context.takeScreenshot("maro-world-gui-" + layout.toLowerCase() + "-walked-around");
                var target = context.computeOnClient(c -> { var p = row(gui(c), example); return gui(c).screenPose().worldPoint(p[0], p[1]); });
                aim(context, target);
                boolean before = context.computeOnClient(c -> example.isEnabled());
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(context.computeOnClient(c -> example.isEnabled()) != before, "Native crosshair click missed module in " + layout);
                context.runOnClient(c -> require(!c.options.attackKey.isPressed() && !c.options.useKey.isPressed(), "World GUI clicks leaked mining or item use"));
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                context.waitTicks(8);
                require(context.computeOnClient(c -> c.currentScreen instanceof ClickGuiScreen), "Crosshair settings click closed the world menu");
                var flag = (dev.maro.setting.BooleanSetting) example.getSettings().get(0);
                var amount = (dev.maro.setting.NumberSetting) example.getSettings().get(1);
                aim(context, context.computeOnClient(c -> settingPoint(gui(c), flag, .75)));
                boolean previousFlag = flag.get(); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(flag.get() != previousFlag, "Crosshair did not toggle a setting in " + layout);
                aim(context, context.computeOnClient(c -> settingPoint(gui(c), amount, .8)));
                context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); context.waitTicks(3);
                context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(amount.get() > 6, "Crosshair did not drag the slider in " + layout);
                context.takeScreenshot("maro-world-gui-" + layout.toLowerCase() + "-settings-crosshair");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); context.waitTicks(5);
                // Looking away must neither stick the view to the plane nor dispatch hidden controls.
                context.runOnClient(c -> { c.player.setYaw(c.player.getYaw() + 180); c.player.setPitch(0); });
                context.waitTicks(4);
                boolean away = example.isEnabled(); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(example.isEnabled() == away, "Clicking away from the menu changed a hidden control");
                context.runOnClient(c -> {
                    gui(c).keyPressed(new KeyInput(GLFW.GLFW_KEY_W, 0, 0));
                    c.onWindowFocusChanged(false); gui(c).tick();
                    require(!c.mouse.isCursorLocked() && !gui(c).movementInput().forward(), "Focus loss retained movement or mouse capture");
                    c.onWindowFocusChanged(true); gui(c).tick();
                    require(c.mouse.isCursorLocked(), "Focus return did not recapture mouse");
                    c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));
                    require(!c.mouse.isCursorLocked(), "World GUI mouse capture leaked into inventory");
                    c.setScreen(null);
                });
                context.waitTicks(2);
                require(!context.computeOnClient(c -> ScreenGuiSurface.hasResources()), "World GUI leaked GPU resources");
                System.out.println("[world-crosshair-gui-proof] " + layout + " normal mouse look, real walking, fixed placement, native crosshair clicks, focus/close passed");
            }
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null); example.setEnabled(on); ClientSettings.screenGui.set(false);
                for (int i = 0; i < savedSettings.size(); i++) example.getSettings().get(i).fromJson(savedSettings.get(i));
            });
        }
    }
}
