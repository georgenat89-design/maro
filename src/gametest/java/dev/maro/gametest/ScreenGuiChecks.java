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
                ClientSettings.spatialMenu.set(false);
                // Upgrade an existing saved theme without losing its keybind or accent.
                ClientSettings.accent.set(0xFF33BBAA);
                ClientSettings.capsText.set(false);
                ClientSettings.backgroundBlur.set(true);
                ClientSettings.opacity.set(100d);
                ClientSettings.guiSize.set(85d);
                ConfigManager.saveClient();
                try {
                    var file = ConfigManager.DIR.resolve("client.json");
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    root.addProperty("version", 4);
                    root.addProperty("lookRevision", 2);
                    Files.writeString(file, root.toString());
                } catch (Exception error) { throw new AssertionError(error); }
                int bind = ClientSettings.guiBind.get();
                ConfigManager.loadClient();
                require(ClientSettings.capsText.get() && ClientSettings.customFont.get() && ClientSettings.fontFace.is("Sora") && !ClientSettings.backgroundBlur.get()
                        && ClientSettings.opacity.get().equals(ClientSettings.opacity.getDefault()), "Old menu look was not migrated");
                require(ClientSettings.accent.get() == 0xFF33BBAA && ClientSettings.guiBind.get() == bind, "Migration lost accent or menu key");
                require(ClientSettings.guiSize.getInt() == 75, "Old default menu size was not compacted");
                try {
                    var file = ConfigManager.DIR.resolve("client.json");
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    root.addProperty("lookRevision", 3);
                    root.getAsJsonObject("settings").addProperty("GUI Size", 90);
                    Files.writeString(file, root.toString()); ConfigManager.loadClient();
                    require(ClientSettings.guiSize.getInt() == 90, "Compact migration replaced a custom menu size");
                    root.addProperty("lookRevision", 4);
                    root.getAsJsonObject("settings").addProperty("GUI Size", 85);
                    Files.writeString(file, root.toString()); ConfigManager.loadClient();
                    require(ClientSettings.guiSize.getInt() == 85, "Compact migration repeated after opt-out");
                    root.addProperty("lookRevision", 5);
                    root.getAsJsonObject("settings").addProperty("Font", "Inter");
                    Files.writeString(file, root.toString()); ConfigManager.loadClient();
                    require(ClientSettings.fontFace.is("Inter"), "Sora migration replaced a later font choice");
                    ClientSettings.fontFace.set("Sora");
                    ClientSettings.guiSize.reset(); ConfigManager.saveClient();
                } catch (Exception error) { throw new AssertionError(error); }

                try {
                    var file = ConfigManager.DIR.resolve("client.json");
                    var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    root.getAsJsonObject("settings").addProperty("Menu Style", "Spatial");
                    root.getAsJsonObject("settings").remove("Floating Window");
                    Files.writeString(file, root.toString());
                    ConfigManager.loadClient();
                    require(ClientSettings.menuStyle.is("Panel") && ClientSettings.spatialMenu.get(), "Build 690 Spatial preference was not migrated");
                    ClientSettings.spatialMenu.set(false);
                    ConfigManager.saveClient();
                    ClientSettings.spatialMenu.set(true);
                    ConfigManager.loadClient();
                    require(!ClientSettings.spatialMenu.get(), "Floating Window opt-out did not persist");
                    root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    root.remove("spatialMenu");
                    root.getAsJsonObject("settings").addProperty("Screen GUI", true);
                    Files.writeString(file, root.toString());
                    ConfigManager.loadClient();
                    require(ClientSettings.screenGui.get() && !ClientSettings.spatialMenu.get(), "Migration replaced an existing Screen GUI setup");
                } catch (Exception error) { throw new AssertionError(error); }
                ClientSettings.accent.reset();
                ClientSettings.capsText.set(true);
                ClientSettings.backgroundDim.set(false);
                ClientSettings.backgroundBlur.set(false);
                ClientSettings.screenDistance.set(4d);
                ClientSettings.menuStyle.set("Panels");
                ClientSettings.screenGui.set(true);
                ConfigManager.saveClient();
                ClientSettings.screenGui.set(false);
                ClientSettings.screenDistance.set(2d);
                ConfigManager.loadClient();
                require(ClientSettings.screenGui.get() && ClientSettings.screenDistance.getInt() == 4, "Screen GUI settings did not persist");
            });
            for (String layout : new String[] {"Panels", "Window"}) {
                for (boolean floating : new boolean[] {false,true}) {
                    context.runOnClient(c -> {
                        ClientSettings.menuStyle.set(layout);
                        ClientSettings.screenGui.set(floating);
                        ClientSettings.customFont.set(true);
                        ClickGuiScreen.resetPosition();
                        c.setScreen(new ClickGuiScreen());
                        var gui = (ClickGuiScreen) c.currentScreen;
                        if (layout.equals("Window")) gui.openPage(Category.VISUALS.ordinal());
                        gui.mouseMoved(gui.width * .85, gui.height * .22);
                    });
                    context.waitTicks(22);
                    int frames = context.computeOnClient(c -> ScreenGuiSurface.drawnFrames());
                    require(context.computeOnClient(c -> ClientSettings.screenGui.get()) == floating, "Screen GUI fell back after a rendering error");
                    require(context.computeOnClient(c -> ScreenGuiSurface.hasResources()) == floating, "Screen GUI allocation state differs from setting");
                    if (floating) {
                        require(frames > 0, "No perspective GPU frames drawn");
                        require(context.computeOnClient(c -> ScreenGuiSurface.textureWidth() > c.getWindow().getFramebufferWidth()
                                && ScreenGuiSurface.textureHeight() > c.getWindow().getFramebufferHeight()), "World menu was not rendered at higher resolution");
                    }
                    context.takeScreenshot("maro-screen-gui-" + layout.toLowerCase() + (floating ? "-floating" : "-flat"));
                    context.runOnClient(c -> {
                        var gui = (ClickGuiScreen) c.currentScreen;
                        for (int x = 0; x <= gui.width; x += 17) for (int y = 0; y <= gui.height; y += 19) {
                            double[] projected = gui.screenPose().project(x, y);
                            double[] picked = gui.screenPose().unproject(projected[0], projected[1]);
                            require(Math.abs(picked[0] - x) < .01 && Math.abs(picked[1] - y) < .01, "Projective picking drift");
                        }
                        // Screen GUI clicks are crosshair rays, not absolute 2D coordinates.
                        // WorldScreenGuiChecks below exercises those through native mouse input.
                        if (layout.equals("Panels") && !floating) {
                            float[] row = gui.panelPlace(example);
                            require(row != null, "Example row missing");
                            double[] point = gui.screenPose().project(row[0], row[1]);
                            boolean before = example.isEnabled();
                            Click click = new Click(point[0], point[1], new MouseInput(0, 0));
                            gui.mouseClicked(click, false); gui.mouseReleased(click);
                            require(example.isEnabled() != before, "Projected row click missed");
                            Click right = new Click(point[0], point[1], new MouseInput(1, 0));
                            gui.mouseClicked(right, false); gui.mouseReleased(right);
                            require(gui.panelSettingsModule() == example, "Projected settings click missed");
                        } else gui.openModuleSettings(example);
                    });
                    context.waitTicks(12);
                    context.takeScreenshot("maro-screen-gui-" + layout.toLowerCase() + (floating ? "-floating-settings" : "-flat-settings"));
                    boolean windowOptions = layout.equals("Window") || floating;
                    var dropdownModule = windowOptions ? ModuleManager.getByName("Custom Sky") : example;
                    if (windowOptions) {
                        context.runOnClient(c -> ((ClickGuiScreen) c.currentScreen).openModuleSettings(dropdownModule, "Sky"));
                        context.waitTicks(12);
                    }
                    String dropdownName = windowOptions ? "Sky" : "Mode";
                    var mode = (dev.maro.setting.ModeSetting) dropdownModule.getSettings().stream().filter(s -> s.getName().equals(dropdownName)).findFirst().orElseThrow();
                    context.runOnClient(c -> dev.maro.gui.widget.Dropdown.toggle(mode));
                    context.waitTicks(6);
                    require(context.computeOnClient(c -> dev.maro.gui.widget.Dropdown.isOpen(mode)), "Settings dropdown lost its projected anchor");
                    context.takeScreenshot("maro-screen-gui-" + layout.toLowerCase() + (floating ? "-floating-dropdown" : "-flat-dropdown"));
                    context.runOnClient(c -> dev.maro.gui.widget.Dropdown.close());
                    context.runOnClient(c -> ClientSettings.customFont.set(false));
                    context.waitTicks(5);
                    context.takeScreenshot("maro-screen-gui-" + layout.toLowerCase() + (floating ? "-floating-vanilla-font" : "-flat-vanilla-font"));
                    if (layout.equals("Window") && !floating) {
                        context.runOnClient(c -> {
                            var gui = (ClickGuiScreen) c.currentScreen;
                            double[] point = gui.screenPose().project(66, gui.menuHeight() - 36.5);
                            Click click = new Click(point[0], point[1], new MouseInput(0, 0));
                            gui.mouseClicked(click, false); gui.mouseReleased(click);
                        });
                        context.waitTicks(5);
                        require(context.computeOnClient(c -> ClientSettings.screenGui.get()) != floating, "Sidebar Screen GUI shortcut missed");
                        require(context.computeOnClient(c -> ScreenGuiSurface.hasResources()) != floating, "Live Screen GUI toggle did not change allocation state");
                    }
                    context.runOnClient(c -> c.setScreen(null));
                    context.waitTicks(2);
                    require(!context.computeOnClient(c -> ScreenGuiSurface.hasResources()), "Closing the menu leaked GPU resources");
                    System.out.println("[screen-gui-proof] " + layout + " floating=" + floating + " render/picking/fonts/close passed");
                }
            }
            WorldScreenGuiChecks.run(context);
            context.getInput().resizeWindow(854, 480);
            context.runOnClient(c -> {
                ClientSettings.menuStyle.set("Window"); ClientSettings.screenGui.set(true); ClientSettings.customFont.set(true);
                c.setScreen(new ClickGuiScreen());
                ((ClickGuiScreen) c.currentScreen).openPage(Category.VISUALS.ordinal());
            });
            context.waitTicks(15);
            context.takeScreenshot("maro-screen-gui-small");
            context.getInput().resizeWindow(1440, 900);
            context.waitTicks(10);
            context.takeScreenshot("maro-screen-gui-resized");

            System.out.println("[screen-gui-lifecycle-proof] settings persistence, both layouts, inverse picking, world placement and crosshair control, resize and resource cleanup passed");
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
