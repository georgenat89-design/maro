package dev.maro.gametest;

import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

/** Real panel entry clicks must preserve the selected layout and expose its module rows. */
final class PanelsEntryChecks {
    static void run(ClientGameTestContext context) {
        String style = ClientSettings.menuStyle.get();
        boolean spatial = ClientSettings.spatialMenu.get(), screen = ClientSettings.screenGui.get();
        var module = ModuleManager.byCategory(Category.ANTI_CHEAT_OFF).getFirst();
        try {
            context.runOnClient(c -> {
                ClientSettings.menuStyle.set("Panels"); ClientSettings.spatialMenu.set(false); ClientSettings.screenGui.set(false);
                c.setScreen(new ClickGuiScreen());
            });
            context.waitTicks(15);
            context.runOnClient(c -> {
                var gui = (ClickGuiScreen)c.currentScreen;
                require(gui.panelPlace(module) == null, "Locked module row was exposed");
                var p = gui.panelPlace(Category.ANTI_CHEAT_OFF); require(p != null, "Locked panel not visible");
                gui.clickAt(p[0], p[1], 0);
                require(gui.showingPanels(), "Entry click changed Panels to the large Window");
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-panels-ac-confirm");
            context.runOnClient(c -> {
                var gui = (ClickGuiScreen)c.currentScreen; var p = gui.antiCheatCancelPlace();
                gui.clickAt(p[0], p[1], 0);
                require(gui.showingPanels() && !gui.antiCheatSectionUnlocked(), "Cancel entered or changed the layout");
            });
            context.waitTicks(4);
            context.runOnClient(c -> { var gui = (ClickGuiScreen)c.currentScreen; var p = gui.panelPlace(Category.ANTI_CHEAT_OFF); gui.clickAt(p[0],p[1],0); });
            context.waitTicks(4);
            context.runOnClient(c -> {
                var gui = (ClickGuiScreen)c.currentScreen; var p = gui.antiCheatEnterPlace(); gui.clickAt(p[0], p[1], 0);
                require(gui.showingPanels() && gui.antiCheatSectionUnlocked(), "Confirm did not keep Panels unlocked");
                require(ClientSettings.menuStyle.is("Panels"), "Entry rewrote the saved layout");
            });
            context.waitTicks(15);
            context.runOnClient(c -> require(((ClickGuiScreen)c.currentScreen).panelPlace(module) != null, "Unlocked panel has no module rows"));
            context.takeScreenshot("maro-panels-ac-unlocked");
            context.runOnClient(c -> c.setScreen(new ClickGuiScreen()));
            context.waitTicks(12);
            context.runOnClient(c -> {
                var gui = (ClickGuiScreen)c.currentScreen;
                require(!gui.antiCheatSectionUnlocked(), "Reopened menu skipped its existing confirmation");
                gui.openPage(Category.ANTI_CHEAT_OFF.ordinal());
                gui.keyPressed(new KeyInput(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                require(gui.showingPanels() && !gui.antiCheatSectionUnlocked(), "Escape closed or entered the panel");
                ClientSettings.menuStyle.set("Window"); c.setScreen(new ClickGuiScreen());
                ((ClickGuiScreen)c.currentScreen).openPage(Category.ANTI_CHEAT_OFF.ordinal());
            });
            context.waitTicks(12);
            context.runOnClient(c -> {
                var gui = (ClickGuiScreen)c.currentScreen; var p = gui.antiCheatEnterPlace(); gui.clickAt(p[0], p[1], 0);
                require(!gui.showingPanels() && gui.antiCheatSectionUnlocked(), "Window entry no longer works");
            });
            System.out.println("[panels-entry] PASS: native entry/cancel/unlock/Escape preserve Panels; Window entry retained");
        } finally {
            context.runOnClient(c -> { c.setScreen(null); ClientSettings.menuStyle.set(style); ClientSettings.spatialMenu.set(spatial); ClientSettings.screenGui.set(screen); });
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
