package dev.maro.gametest;

import dev.maro.gui.hud.BetterLooksScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BetterLooks;
import dev.maro.module.impl.visuals.NoRender;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.particle.ParticleTypes;

/**
 * Better Looks: the panel shows its sections and Color Correct's, searching narrows the settings,
 * and the switches reach their hooks (clouds, vignette through No Render, particle density).
 */
final class BetterLooksChecks {
    private BetterLooksChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(BetterLooks m, String name) {
        return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context) {
        BetterLooks module = ModuleManager.get(BetterLooks.class);
        require(module != null, "Better Looks was not registered");
        try {
            BetterLooksScreen screen = context.computeOnClient(c -> {
                BetterLooksScreen s = new BetterLooksScreen(null, module);
                c.setScreen(s);
                return s;
            });
            context.waitTicks(4);
            context.takeScreenshot("maro-better-looks");
            require(context.computeOnClient(c -> screen.sectionTitles()).containsAll(
                            java.util.List.of("World", "Particles", "Interface", "Entities", "ViewModel", "Color", "Motion Blur", "Bloom")),
                    "Better Looks is missing a section: " + context.computeOnClient(c -> screen.sectionTitles()));
            int all = context.computeOnClient(c -> screen.shownRows(-1));
            context.runOnClient(c -> screen.search("fog"));
            context.waitTicks(3);
            context.takeScreenshot("maro-better-looks-search");
            int fog = context.computeOnClient(c -> screen.shownRows(-1));
            require(fog > 0 && fog < all, "Searching fog did not narrow the settings: " + fog + " of " + all);
            context.runOnClient(c -> {
                screen.search("");
                screen.showSection(screen.sectionTitles().indexOf("Color"));
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-better-looks-color");

            // Their own modules are no longer listed: their settings are here.
            context.runOnClient(c -> {
                for (String gone : java.util.List.of("No Render", "Color Correct", "Motion Blur", "Bloom", "Hats")) {
                    require(ModuleManager.byCategory(ModuleManager.getByName(gone).getCategory()).stream().noneMatch(m -> m.getName().equals(gone))
                            && ModuleManager.search(gone).stream().noneMatch(m -> m.getName().equals(gone)), gone + " is still listed on its own");
                }
            });
            context.runOnClient(c -> {
                module.setEnabled(true);
                ((BooleanSetting) setting(module, "No Clouds")).set(true);
                ((BooleanSetting) setting(module, "No Vignette")).set(true);
                ((ModeSetting) setting(module, "Particle Mode")).set("Minimal");
                require(BetterLooks.noClouds(), "No Clouds did not reach its hook");
                require(NoRender.hides(NoRender.Part.VIGNETTE), "No Vignette did not reach No Render's hook");
                int dropped = 0;
                for (int i = 0; i < 1000; i++) if (BetterLooks.dropParticle(ParticleTypes.FLAME)) dropped++;
                require(dropped > 800, "Minimal particles kept too many: " + (1000 - dropped) + " of 1000");
                c.setScreen(null);
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-better-looks-world");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                if (c.currentScreen instanceof BetterLooksScreen) c.setScreen(null);
            });
        }
    }
}
