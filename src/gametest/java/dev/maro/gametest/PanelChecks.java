package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel menu: it opens on a panel per category; clicking a module's row turns it on, a right
 * click opens its settings in the window and Escape comes back; a right click on a header folds
 * that panel; typing searches.
 */
final class PanelChecks {
    private PanelChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ClickGuiScreen screen(MinecraftClient client) {
        return (ClickGuiScreen) client.currentScreen;
    }

    private static void settle(ClientGameTestContext context) {
        context.waitTicks(12);
    }

    static void run(ClientGameTestContext context) {
        require(context.computeOnClient(c -> screen(c).showingPanels()), "The menu did not open on the panels");
        Module example = ModuleManager.getByName("Example");
        require(example != null, "No Example module");
        boolean wasOn = context.computeOnClient(c -> example.isEnabled());
        float[] row = context.computeOnClient(c -> screen(c).panelPlace(example));
        require(row != null, "The Example module has no row in the panels");

        context.runOnClient(c -> screen(c).clickAt(row[0], row[1], GLFW.GLFW_MOUSE_BUTTON_LEFT));
        require(context.computeOnClient(c -> example.isEnabled()) != wasOn, "Clicking a module's row did not turn it on or off");
        List<Module> lit = new ArrayList<>();
        context.runOnClient(c -> {
            for (String name : List.of("Fullbright", "Keystrokes", "Custom Sky")) {
                Module m = ModuleManager.getByName(name);
                if (m != null && !m.isEnabled()) {
                    m.setEnabled(true);
                    lit.add(m);
                }
            }
        });
        settle(context);
        context.takeScreenshot("maro-panels-on");

        // Right click: its settings, in the window over the panels; Escape twice comes back.
        context.runOnClient(c -> screen(c).clickAt(row[0], row[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        settle(context);
        require(context.computeOnClient(c -> !screen(c).showingPanels()), "Right clicking a module did not open its settings");
        context.takeScreenshot("maro-panels-settings");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        settle(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        settle(context);
        require(context.computeOnClient(c -> c.currentScreen instanceof ClickGuiScreen && screen(c).showingPanels()),
                "Escape did not go back from the settings to the panels");

        // A right click on a header folds the panel, and another opens it again.
        float[] head = context.computeOnClient(c -> screen(c).panelPlace(Category.VISUALS));
        require(head != null, "The Visuals panel has no header");
        context.runOnClient(c -> screen(c).clickAt(head[0], head[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        settle(context);
        boolean folded = context.computeOnClient(c -> screen(c).panelPlace(dev.maro.module.ModuleManager.byCategory(Category.VISUALS).getFirst()) == null);
        context.takeScreenshot("maro-panels-folded");
        context.runOnClient(c -> screen(c).clickAt(head[0], head[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        settle(context);
        require(folded, "Right clicking the Visuals header did not fold it");

        // Typing searches every panel at once.
        context.getInput().typeChars("sky");
        settle(context);
        context.takeScreenshot("maro-panels-search");
        require(context.computeOnClient(c -> screen(c).getSearch().getText()).equals("sky"), "Typing over the panels did not search");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // clears the search
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // leaves the search box
        settle(context);
        require(context.computeOnClient(c -> c.currentScreen instanceof ClickGuiScreen && screen(c).getSearch().getText().isEmpty()),
                "Escape did not clear the search");

        context.runOnClient(c -> {
            example.setEnabled(wasOn);
            lit.forEach(m -> m.setEnabled(false));
        });
    }
}
