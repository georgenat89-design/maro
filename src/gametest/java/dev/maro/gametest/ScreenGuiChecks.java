package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.config.ConfigManager;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.render.ScreenGuiSurface;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.setting.Setting;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real GUI rendering, projective picking, font/scale compatibility and allocation lifetime. */
final class ScreenGuiChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context) {
        Map<Setting<?>, JsonElement> saved = new LinkedHashMap<>();
        ClientSettings.ALL.forEach(section -> section.getSettings().forEach(setting -> saved.put(setting, setting.toJson())));
        var example = ModuleManager.getByName("Example");
        boolean exampleOn = example.isEnabled();
        float[] aim = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        try {
            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(c -> {
                // Upgrade an existing saved theme without losing its keybind or accent.
                ClientSettings.accent.set(0xFF33BBAA);
                ClientSettings.capsText.set(true);
                ClientSettings.backgroundBlur.set(true);
                ClientSettings.opacity.set(100d);
                ConfigManager.saveClient();
                try {
                    var file = ConfigManager.DIR.resolve("client.json");
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    root.addProperty("version", 4);
                    Files.writeString(file, root.toString());
                } catch (Exception error) { throw new AssertionError(error); }
                int bind = ClientSettings.guiBind.get();
                ConfigManager.loadClient();
                require(!ClientSettings.capsText.get() && !ClientSettings.backgroundBlur.get()
                        && ClientSettings.opacity.getInt() == 82, "Old menu look was not migrated");
                require(ClientSettings.accent.get() == 0xFF33BBAA && ClientSettings.guiBind.get() == bind, "Migration lost accent or menu key");
                ClientSettings.accent.reset();
                ClientSettings.capsText.set(false);
                ClientSettings.backgroundDim.set(false);
                ClientSettings.backgroundBlur.set(false);
                ClientSettings.screenDistance.set(4d);
                ClientSettings.screenGui.set(true);
                ConfigManager.saveClient();
                ClientSettings.screenGui.set(false);
                ClientSettings.screenDistance.set(2d);
                ConfigManager.loadClient();
                require(ClientSettings.screenGui.get() && ClientSettings.screenDistance.getInt() == 4, "Screen GUI settings did not persist");
            });
            for (boolean floating : new boolean[] {false}) {
                context.runOnClient(c -> {
                    ClientSettings.screenGui.set(floating);
                    ClientSettings.customFont.set(true);
                    ClickGuiScreen.resetPosition();
                    c.setScreen(new ClickGuiScreen());
                    var gui = (ClickGuiScreen) c.currentScreen;
                    gui.openPage(example.getCategory().ordinal());
                    gui.mouseMoved(gui.width * .85, gui.height * .22);
                });
                context.waitTicks(22);
                int frames = context.computeOnClient(c -> ScreenGuiSurface.drawnFrames());
                require(context.computeOnClient(c -> ClientSettings.screenGui.get()) == floating, "Screen GUI fell back after a rendering error");
                require(context.computeOnClient(c -> ScreenGuiSurface.hasResources()) == floating, "Screen GUI allocation state differs from setting");
                if (floating) require(frames > 0, "No perspective GPU frames drawn");
                context.takeScreenshot("maro-screen-gui-window" + (floating ? "-floating" : "-flat"));
                context.runOnClient(c -> {
                    var gui = (ClickGuiScreen) c.currentScreen;
                    for (int x = 0; x <= gui.width; x += 17) for (int y = 0; y <= gui.height; y += 19) {
                        double[] projected = gui.screenPose().project(x, y);
                        double[] picked = gui.screenPose().unproject(projected[0], projected[1]);
                        require(Math.abs(picked[0] - x) < .01 && Math.abs(picked[1] - y) < .01, "Projective picking drift");
                    }
                    float[] card = gui.modulePlace(example);
                    require(card != null, "Example card missing");
                    double[] point = gui.screenPose().project(card[0], card[1]);
                    boolean before = example.isEnabled();
                    Click right = new Click(point[0], point[1], new MouseInput(1, 0));
                    gui.mouseClicked(right, false); gui.mouseReleased(right);
                    require(example.isEnabled() != before, "Projected card right click missed");
                    Click left = new Click(point[0], point[1], new MouseInput(0, 0));
                    gui.mouseClicked(left, false); gui.mouseReleased(left);
                    require(gui.settingsModule() == example, "Projected settings click missed");
                });
                context.waitTicks(12);
                context.takeScreenshot("maro-screen-gui-window" + (floating ? "-floating-settings" : "-flat-settings"));
                var dropdownModule = ModuleManager.getByName("Custom Sky");
                context.runOnClient(c -> ((ClickGuiScreen) c.currentScreen).openModuleSettings(dropdownModule, "Sky"));
                context.waitTicks(12);
                var mode = (dev.maro.setting.ModeSetting) dropdownModule.getSettings().stream().filter(s -> s.getName().equals("Sky")).findFirst().orElseThrow();
                context.runOnClient(c -> dev.maro.gui.widget.Dropdown.toggle(mode));
                context.waitTicks(6);
                require(context.computeOnClient(c -> dev.maro.gui.widget.Dropdown.isOpen(mode)), "Settings dropdown lost its projected anchor");
                context.takeScreenshot("maro-screen-gui-window" + (floating ? "-floating-dropdown" : "-flat-dropdown"));
                context.runOnClient(c -> dev.maro.gui.widget.Dropdown.close());
                context.runOnClient(c -> ClientSettings.customFont.set(false));
                context.waitTicks(5);
                context.takeScreenshot("maro-screen-gui-window" + (floating ? "-floating-vanilla-font" : "-flat-vanilla-font"));
                context.runOnClient(c -> {
                    var gui = (ClickGuiScreen) c.currentScreen;
                    float[] at = gui.placeOf(ClickGuiScreen.SCREEN_GUI_SWITCH);
                    require(at != null, "Sidebar Screen GUI switch missing");
                    double[] point = gui.screenPose().project(at[0], at[1]);
                    Click click = new Click(point[0], point[1], new MouseInput(0, 0));
                    gui.mouseClicked(click, false); gui.mouseReleased(click);
                });
                context.waitTicks(5);
                require(context.computeOnClient(c -> ClientSettings.screenGui.get()) != floating, "Sidebar Screen GUI shortcut missed");
                require(context.computeOnClient(c -> ScreenGuiSurface.hasResources()) != floating, "Live Screen GUI toggle did not change allocation state");
                context.runOnClient(c -> c.setScreen(null));
                context.waitTicks(2);
                require(!context.computeOnClient(c -> ScreenGuiSurface.hasResources()), "Closing the menu leaked GPU resources");
                System.out.println("[screen-gui-proof] window floating=" + floating + " render/picking/fonts/close passed");
            }
            WorldScreenGuiChecks.run(context);
            context.getInput().resizeWindow(854, 480);
            context.runOnClient(c -> {
                ClientSettings.screenGui.set(true); ClientSettings.customFont.set(true);
                c.setScreen(new ClickGuiScreen());
                ((ClickGuiScreen) c.currentScreen).openPage(Category.VISUALS.ordinal());
            });
            context.waitTicks(15);
            context.takeScreenshot("maro-screen-gui-small");
            context.getInput().resizeWindow(1440, 900);
            context.waitTicks(10);
            context.takeScreenshot("maro-screen-gui-resized");

            System.out.println("[screen-gui-lifecycle-proof] settings persistence, the window, inverse picking, world placement and crosshair control, resize and resource cleanup passed");
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                saved.forEach(Setting::fromJson);
                example.setEnabled(exampleOn); ConfigManager.saveClient();
            });
            context.getInput().resizeWindow(1280, 720);
        }
    }
}
