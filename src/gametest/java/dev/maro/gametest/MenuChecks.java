package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.config.ModuleFavorites;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu: a right click on a module's card turns it on, a left click opens its settings in the
 * panel beside the window and Escape closes it; the star and the Starred filter keep a module at
 * hand; a mode setting's list opens beside its row; typing searches.
 */
final class MenuChecks {
    private MenuChecks() {
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
        Module example = ModuleManager.getByName("Example");
        require(example != null, "No Example module");
        boolean wasOn = context.computeOnClient(c -> example.isEnabled());
        boolean wasStarred = context.computeOnClient(c -> ModuleFavorites.contains(example));
        context.runOnClient(c -> screen(c).openPage(example.getCategory().ordinal()));
        settle(context);
        float[] card = context.computeOnClient(c -> screen(c).modulePlace(example));
        require(card != null, "The Example module has no card in its category");

        // A right click turns it on, with the UI click heard through the game's own sound system.
        List<String> heard = new java.util.concurrent.CopyOnWriteArrayList<>();
        net.minecraft.client.sound.SoundInstanceListener listener = (sound, set, range) -> heard.add(sound.getId().toString());
        context.runOnClient(c -> c.getSoundManager().registerListener(listener));
        context.runOnClient(c -> screen(c).clickAt(card[0], card[1], GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        require(context.computeOnClient(c -> example.isEnabled()) != wasOn, "Right clicking a module's card did not turn it on or off");
        context.waitTicks(3);
        context.runOnClient(c -> c.getSoundManager().unregisterListener(listener));
        System.out.println("MENU sounds heard on a toggle: " + heard);
        require(heard.stream().anyMatch(id -> id.endsWith("ui.button.click")), "Toggling a module made no UI click sound: " + heard);
        require(context.computeOnClient(c -> screen(c).settingsModule() == null), "A right click opened the settings");
        List<Module> lit = new ArrayList<>();
        context.runOnClient(c -> {
            for (String name : List.of("Coord Snapper", "Spotify Playlists", "Skin Changer")) {
                Module m = ModuleManager.getByName(name);
                if (m != null && !m.isEnabled()) {
                    m.setEnabled(true);
                    lit.add(m);
                }
            }
        });
        settle(context);
        context.takeScreenshot("maro-menu-grid");

        // A left click opens its settings beside the window; Escape closes just them.
        float[] moved = context.computeOnClient(c -> screen(c).modulePlace(example));
        context.runOnClient(c -> screen(c).clickAt(moved[0], moved[1], GLFW.GLFW_MOUSE_BUTTON_LEFT));
        settle(context);
        require(context.computeOnClient(c -> screen(c).settingsModule() == example), "Left clicking a module did not open its settings");
        float[] shifted = context.computeOnClient(c -> screen(c).modulePlace(example));
        require(shifted != null && shifted[0] < moved[0] - 50, "The window did not move aside for the settings panel");
        context.takeScreenshot("maro-menu-settings");

        // A mode setting's list opens beside its row; Escape closes the list first, then the panel.
        dev.maro.setting.ModeSetting mode = (dev.maro.setting.ModeSetting) ModuleManager.getByName("Custom Sky").getSettings().stream()
                .filter(s -> s.getName().equals("Sky")).findFirst().orElseThrow();
        context.runOnClient(c -> screen(c).openModuleSettings(ModuleManager.getByName("Custom Sky"), "Sky"));
        settle(context);
        context.runOnClient(c -> dev.maro.gui.widget.Dropdown.toggle(mode));
        settle(context);
        require(context.computeOnClient(c -> dev.maro.gui.widget.Dropdown.isOpen(mode)), "A mode setting's list did not stay open beside its row");
        context.takeScreenshot("maro-menu-dropdown");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        settle(context);
        require(context.computeOnClient(c -> !dev.maro.gui.widget.Dropdown.anyOpen() && screen(c).settingsModule() != null),
                "Escape did not close just the mode list");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        settle(context);
        require(context.computeOnClient(c -> c.currentScreen instanceof ClickGuiScreen && screen(c).settingsModule() == null),
                "Escape did not close the settings panel and stay in the menu");

        // The star keeps a module under the Starred filter.
        context.runOnClient(c -> screen(c).openPage(example.getCategory().ordinal()));
        settle(context);
        context.runOnClient(c -> {
            if (ModuleFavorites.contains(example)) ModuleFavorites.toggle(example);
            ModuleFavorites.toggle(example);
            var chip = screen(c).placeOf(ClickGuiScreen.Filter.STARRED);
            require(chip != null, "The Starred filter is missing");
            screen(c).clickAt(chip[0], chip[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        settle(context);
        require(context.computeOnClient(c -> screen(c).filter() == ClickGuiScreen.Filter.STARRED && screen(c).modulePlace(example) != null
                && screen(c).modulePlace(ModuleManager.getByName("Spotify Playlists")) == null), "The Starred filter did not show just starred modules");
        context.takeScreenshot("maro-menu-starred");
        context.runOnClient(c -> {
            var chip = screen(c).placeOf(ClickGuiScreen.Filter.ALL);
            screen(c).clickAt(chip[0], chip[1], GLFW.GLFW_MOUSE_BUTTON_LEFT);
        });
        settle(context);
        require(context.computeOnClient(c -> screen(c).filter() == ClickGuiScreen.Filter.ALL), "The All filter did not come back");

        // The client pages open in the same window.
        for (String page : List.of("theme", "socials")) {
            int index = page.equals("theme") ? 8 : 9;
            context.runOnClient(c -> screen(c).openPage(index));
            settle(context);
            context.takeScreenshot("maro-menu-" + page);
        }

        // Typing on a module page searches every category at once.
        context.runOnClient(c -> screen(c).openPage(example.getCategory().ordinal()));
        settle(context);
        context.getInput().typeChars("sky");
        settle(context);
        context.takeScreenshot("maro-menu-search");
        require(context.computeOnClient(c -> screen(c).getSearch().getText()).equals("sky"), "Typing in the menu did not search");
        require(context.computeOnClient(c -> screen(c).modulePlace(ModuleManager.getByName("Custom Sky")) != null), "Search did not find Custom Sky");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // clears the search
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // leaves the search box
        settle(context);
        require(context.computeOnClient(c -> c.currentScreen instanceof ClickGuiScreen && screen(c).getSearch().getText().isEmpty()),
                "Escape did not clear the search");

        context.runOnClient(c -> {
            example.setEnabled(wasOn);
            if (ModuleFavorites.contains(example) != wasStarred) ModuleFavorites.toggle(example);
            lit.forEach(m -> m.setEnabled(false));
        });
    }
}
