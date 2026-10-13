package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.Geeked;
import dev.maro.module.impl.visuals.BetterLooks;
import dev.maro.render.GeekedRenderer;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Production GPU output, interface isolation, resize and resource lifetime. */
final class GeekedChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(Geeked module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static BufferedImage read(Path file) {
        try { return ImageIO.read(file.toFile()); }
        catch (Exception error) { throw new AssertionError("Could not read " + file, error); }
    }

    private static int changed(BufferedImage a, BufferedImage b, int x0, int y0, int x1, int y1, int threshold) {
        int changed = 0;
        for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2) {
            int first = a.getRGB(x, y), second = b.getRGB(x, y);
            int delta = Math.abs((first >> 16 & 255) - (second >> 16 & 255))
                    + Math.abs((first >> 8 & 255) - (second >> 8 & 255)) + Math.abs((first & 255) - (second & 255));
            if (delta > threshold) changed++;
        }
        return changed;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Geeked module = ModuleManager.get(Geeked.class);
        require(module != null && ModuleManager.search("geeked").contains(module), "Geeked is missing from Visuals/search");
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        int guiScale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        boolean hudHidden = context.computeOnClient(c -> c.options.hudHidden);
        BetterLooks looks = ModuleManager.get(BetterLooks.class);
        BooleanSetting noVignette = (BooleanSetting) looks.getSettings().stream().filter(s -> s.getName().equals("No Vignette")).findFirst().orElseThrow();
        boolean looksEnabled = context.computeOnClient(c -> looks.isEnabled());
        boolean hidVignette = context.computeOnClient(c -> noVignette.get());
        var initialMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("gamerule minecraft:advance_time false");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("fill -14 -61 -3 14 -61 18 minecraft:polished_deepslate");
            String[] blocks = {"red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "cyan_concrete", "purple_concrete", "pink_concrete"};
            for (int i = 0; i < blocks.length; i++) {
                int x = -14 + i * 4;
                world.getServer().runCommand("fill " + x + " -60 15 " + (x + 3) + " -50 15 minecraft:" + blocks[i]);
            }
            world.getServer().runCommand("fill -5 -60 7 -4 -57 8 minecraft:sea_lantern");
            world.getServer().runCommand("fill 4 -60 10 5 -55 11 minecraft:quartz_pillar");
            world.getServer().runCommand("tp @a 0 -60 0 0 0");
            context.waitTicks(8);
            world.getClientWorld().waitForChunksRender();
            // Settle the newly placed light sources and the initial sky/fog transition.
            context.waitTicks(40);
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.getGuiScale().setValue(3);
                c.onResolutionChanged();
                c.options.hudHidden = false;
                // Vanilla's vignette brightness eases for many frames after joining a world.
                // Remove that changing overlay from the before/after GPU comparison.
                noVignette.set(true);
                looks.setEnabled(true);
            });
            context.waitTicks(5);
            BufferedImage plain = read(context.takeScreenshot("maro-geeked-off"));
            float yaw = context.computeOnClient(c -> c.player.getYaw()), pitch = context.computeOnClient(c -> c.player.getPitch());
            for (String mode : new String[] {"Mellow", "Dreamy", "Trippy"}) {
                context.runOnClient(c -> {((ModeSetting) setting(module, "Mode")).set(mode);module.setEnabled(true);});
                int frames = context.computeOnClient(c -> GeekedRenderer.appliedFrames());
                context.waitTicks(18);
                BufferedImage active = read(context.takeScreenshot("maro-geeked-" + mode.toLowerCase()));
                int pixels = changed(plain, active, 0, 0, 1280, 600, 18);
                require(context.computeOnClient(c -> module.isEnabled() && GeekedRenderer.appliedFrames() > frames), mode + " shader did not run");
                require(pixels > 3000, mode + " did not visibly change the world: " + pixels);
                System.out.println("[geeked-mode-proof] " + mode + " changed=" + pixels + " sampled world pixels");
            }
            context.runOnClient(c -> require(c.player.getYaw() == yaw && c.player.getPitch() == pitch, "Geeked changed player aim"));
            BufferedImage first = read(context.takeScreenshot("maro-geeked-animation-a"));
            context.waitTicks(15);
            BufferedImage second = read(context.takeScreenshot("maro-geeked-animation-b"));
            require(changed(first, second, 0, 0, 1280, 600, 12) > 1000, "Vision did not animate");

            context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
            context.waitTicks(5);
            BufferedImage menuOn = read(context.takeScreenshot("maro-geeked-inventory"));
            context.runOnClient(c -> module.setEnabled(false));
            context.waitTicks(5);
            BufferedImage menuOff = read(context.takeScreenshot("maro-geeked-inventory-off"));
            require(changed(menuOn, menuOff, 660, 400, 850, 510, 6) == 0, "Geeked altered the inventory interface");
            require(!context.computeOnClient(c -> GeekedRenderer.hasResources()), "Disabling left GPU resources allocated");
            context.runOnClient(c -> {c.setScreen(null);((NumberSetting) setting(module, "Intensity")).set(0.0);module.setEnabled(true);});
            context.waitTicks(5);
            int stopped = context.computeOnClient(c -> GeekedRenderer.appliedFrames());
            context.waitTicks(5);
            require(context.computeOnClient(c -> GeekedRenderer.appliedFrames() == stopped && !GeekedRenderer.hasResources()), "Zero intensity still processed frames");
            context.runOnClient(c -> ((NumberSetting) setting(module, "Intensity")).set(100.0));
            context.getInput().resizeWindow(1600, 900);
            context.waitTicks(5);
            BufferedImage resized = read(context.takeScreenshot("maro-geeked-resized"));
            require(resized.getWidth() == 1600 && context.computeOnClient(c -> module.isEnabled() && GeekedRenderer.appliedFrames() > stopped), "Effect failed after resizing");
            for (int i = 0; i < 3; i++) {
                context.runOnClient(c -> {module.setEnabled(false);require(!GeekedRenderer.hasResources(), "Toggle leaked resources");module.setEnabled(true);});
                context.waitTicks(3);
                require(context.computeOnClient(c -> module.isEnabled() && GeekedRenderer.hasResources()), "Toggle did not recreate resources");
            }
            context.runOnClient(c -> module.setEnabled(false));
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(5);
            BufferedImage restored = read(context.takeScreenshot("maro-geeked-restored"));
            int residual = changed(plain, restored, 80, 80, 1200, 500, 18);
            require(residual < 500, "Disabling did not restore the original scene: " + residual);
            System.out.println("[geeked-lifecycle-proof] animated output; aim unchanged; inventory pixels untouched; zero intensity skips GPU; resize and three toggles restore/release resources; off restores scene");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.setScreen(null);
                c.options.setPerspective(perspective);
                c.options.getGuiScale().setValue(guiScale);
                c.options.hudHidden = hudHidden;
                noVignette.set(hidVignette);
                looks.setEnabled(looksEnabled);
                c.onResolutionChanged();
            });
            context.getInput().resizeWindow(1280, 720);
            // Later checks open the survival inventory, which creative mode swaps for its own.
            world.getServer().runCommand("gamemode " + initialMode.getId() + " @a");
            context.waitFor(c -> c.interactionManager.getCurrentGameMode() == initialMode);
        }
    }
}
