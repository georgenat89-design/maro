package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;

/** Player ESP on yourself in third person: the silhouette must be drawn, and drawn where you are. */
final class PlayerEspChecks {
    private PlayerEspChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(PlayerESP esp, String name) {
        return esp.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        PlayerESP esp = ModuleManager.get(PlayerESP.class);
        require(esp != null, "Player ESP was not registered");
        var original = context.computeOnClient(c -> c.player.getEntityPos());
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        try {
            world.getServer().runCommand("fill -6 99 -6 6 99 6 minecraft:stone");
            world.getServer().runCommand("fill -6 100 -6 6 104 6 minecraft:air");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("tp @a 0 100 0 0 10");
            context.waitTicks(8);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                esp.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(esp, "Self")).set(true);
                ((ModeSetting) setting(esp, "Fill Style")).set("Solid");
                ((ColorSetting) setting(esp, "Color")).set(0xFFFF00FF);
                ((NumberSetting) setting(esp, "Fill Opacity")).set(100.0);
                ((NumberSetting) setting(esp, "Edge Fade")).set(0.0);
                ((BooleanSetting) setting(esp, "Glow")).set(false);
                esp.setEnabled(true);
            });
            context.waitTicks(4);
            Path solid = context.takeScreenshot("maro-player-esp-solid");
            context.runOnClient(c -> esp.setEnabled(false));
            context.waitTicks(3);
            Path off = context.takeScreenshot("maro-player-esp-off");

            int[] on = magenta(solid), baseline = magenta(off);
            System.out.println("PLAYER ESP magenta on=" + on[0] + " off=" + baseline[0] + " centre=" + on[1] + "/" + on[3] + " width=" + on[2]);
            require(on[0] > 1500 && on[0] > baseline[0] * 10, "Player ESP did not draw the silhouette: " + on[0] + " vs " + baseline[0]);
            require(Math.abs(on[1] - on[2] / 2) < on[2] * 0.12, "Player ESP silhouette is not where the player is: centre x " + on[1] + " of " + on[2]);

            context.runOnClient(c -> {
                esp.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(esp, "Self")).set(true);
                esp.setEnabled(true);
            });
            for (String style : new String[] {"Galaxy", "Lava", "Rainbow"}) {
                context.runOnClient(c -> ((ModeSetting) setting(esp, "Fill Style")).set(style));
                context.waitTicks(3);
                context.takeScreenshot("maro-player-esp-" + style.toLowerCase());
            }
        } finally {
            context.runOnClient(c -> {
                esp.setEnabled(false);
                esp.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
            });
            world.getServer().runCommand("tp @a " + original.x + " " + original.y + " " + original.z);
            context.waitTicks(3);
        }
    }

    /** {count, centre x, image width, centre y} of strongly magenta pixels. */
    private static int[] magenta(Path screenshot) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            long sumX = 0, sumY = 0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                    if (r > 200 && b > 200 && g < 90) {
                        count++;
                        sumX += x;
                        sumY += y;
                    }
                }
            }
            return new int[] {count, count == 0 ? -1 : (int) (sumX / count), image.getWidth(), count == 0 ? -1 : (int) (sumY / count)};
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + screenshot, e);
        }
    }
}
