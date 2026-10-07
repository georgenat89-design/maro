package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Custom Sky: every sky is painted in place of the game's own, and each one looks different. */
final class SkyChecks {
    private SkyChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(CustomSky sky, String name) {
        return sky.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        CustomSky sky = ModuleManager.get(CustomSky.class);
        require(sky != null, "Custom Sky was not registered");
        // High up on a little platform, facing south and looking 20 degrees up.
        world.getServer().runCommand("fill -2 140 -2 2 140 2 minecraft:stone");
        world.getServer().runCommand("time set noon");
        world.getServer().runCommand("weather clear");
        world.getServer().runCommand("tp @a 0 141 0 0 -20");
        context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        context.waitTicks(20);
        BufferedImage vanilla = read(context.takeScreenshot("maro-sky-vanilla"));
        try {
            context.runOnClient(c -> {
                sky.getSettings().forEach(Setting::reset);
                sky.setEnabled(true);
            });
            BufferedImage previous = vanilla;
            for (String name : CustomSky.SKIES) {
                context.runOnClient(c -> ((ModeSetting) setting(sky, "Sky")).set(name));
                context.waitTicks(32); // past the 1.5 s fade from the sky before
                BufferedImage shot = read(context.takeScreenshot("maro-sky-" + name.toLowerCase(Locale.ROOT).replace(' ', '-')));
                double fromVanilla = difference(vanilla, shot), fromPrevious = difference(previous, shot);
                System.out.printf(Locale.ROOT, "CUSTOM SKY %s: vs normal sky %.1f, vs previous %.1f%n", name, fromVanilla, fromPrevious);
                require(fromVanilla > 8, name + " looks like the normal sky (difference " + fromVanilla + ")");
                require(fromPrevious > 3, name + " looks like the sky before it (difference " + fromPrevious + ")");
                previous = shot;
            }
        } finally {
            context.runOnClient(c -> sky.setEnabled(false));
            world.getServer().runCommand("fill -2 140 -2 2 140 2 minecraft:air");
        }
    }

    private static BufferedImage read(Path screenshot) {
        try {
            return ImageIO.read(screenshot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + screenshot, e);
        }
    }

    /** Mean difference per colour channel, 0 to 255, over the top half of the screen, where the sky is. */
    private static double difference(BufferedImage a, BufferedImage b) {
        int width = Math.min(a.getWidth(), b.getWidth()), height = Math.min(a.getHeight(), b.getHeight()) / 2;
        long sum = 0, count = 0;
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                sum += Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF)) + Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF))
                        + Math.abs((p & 0xFF) - (q & 0xFF));
                count += 3;
            }
        }
        return count == 0 ? 0 : sum / (double) count;
    }
}
