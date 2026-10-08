package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomSky;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
        var original = context.computeOnClient(c -> c.player.getEntityPos());
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
                if (name.equals("Image")) continue; // needs a picture; checked below
                context.runOnClient(c -> ((ModeSetting) setting(sky, "Sky")).set(name));
                context.waitTicks(32); // past the 1.5 s fade from the sky before
                BufferedImage shot = read(context.takeScreenshot("maro-sky-" + name.toLowerCase(Locale.ROOT).replace(' ', '-')));
                double fromVanilla = difference(vanilla, shot), fromPrevious = difference(previous, shot);
                System.out.printf(Locale.ROOT, "CUSTOM SKY %s: vs normal sky %.1f, vs previous %.1f%n", name, fromVanilla, fromPrevious);
                require(fromVanilla > 8, name + " looks like the normal sky (difference " + fromVanilla + ")");
                require(fromPrevious > 3, name + " looks like the sky before it (difference " + fromPrevious + ")");
                previous = shot;
            }

            // Your own picture: one of each shape, imported the way the Choose screen does it.
            Path folder = context.computeOnClient(c -> c.runDirectory.toPath().resolve("maro-test-skies"));
            String[][] pictures = {{"panorama", "1024", "512"}, {"cube", "1024", "768"}, {"picture", "1280", "720"}};
            for (String[] picture : pictures) {
                Path file = writePicture(folder.resolve(picture[0] + ".png"), picture[0], Integer.parseInt(picture[1]), Integer.parseInt(picture[2]));
                CompletableFuture<Boolean> loaded = context.computeOnClient(c -> sky.importPicture(file));
                for (int i = 0; i < 400 && !loaded.isDone(); i++) context.waitTick();
                require(loaded.getNow(false), "Could not load the " + picture[0] + " sky: " + sky.pictureStatus());
                require(sky.showingPicture(), "Loading a picture did not switch the sky to it");
                context.waitTicks(32);
                BufferedImage shot = read(context.takeScreenshot("maro-sky-image-" + picture[0]));
                double fromVanilla = difference(vanilla, shot);
                System.out.printf(Locale.ROOT, "CUSTOM SKY image %s (%s): vs normal sky %.1f%n", picture[0], sky.pictureStatus(), fromVanilla);
                require(fromVanilla > 8, "The " + picture[0] + " picture was not drawn as the sky (difference " + fromVanilla + ")");
            }
        } finally {
            context.runOnClient(c -> sky.setEnabled(false));
            // Back down before the platform goes, or a survival player falls to their death.
            world.getServer().runCommand("tp @a " + original.x + " " + original.y + " " + original.z);
            context.waitTicks(2);
            world.getServer().runCommand("fill -2 140 -2 2 140 2 minecraft:air");
        }
    }

    /** A test picture whose orientation is easy to read off a screenshot. */
    private static Path writePicture(Path file, String kind, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, h / 8));
            switch (kind) {
                case "panorama" -> {
                    g.setPaint(new GradientPaint(0, 0, new Color(0x0B1A5C), 0, h / 2f, new Color(0x6AA8FF)));
                    g.fillRect(0, 0, w, h / 2);
                    g.setColor(new Color(0x3B7A2A));
                    g.fillRect(0, h / 2, w, h - h / 2);
                    g.setColor(new Color(255, 255, 255, 120));
                    for (int i = 0; i <= 36; i++) g.drawLine(i * w / 36, 0, i * w / 36, h);
                    for (int j = 0; j <= 18; j++) g.drawLine(0, j * h / 18, w, j * h / 18);
                    g.setColor(new Color(0xFFEB3B));
                    String[] letters = {"N", "E", "S", "W", "N"};
                    for (int i = 0; i < letters.length; i++) g.drawString(letters[i], i * w / 4 - g.getFontMetrics().stringWidth(letters[i]) / 2, (int) (h * 0.42));
                }
                case "cube" -> {
                    int s = w / 4;
                    Object[][] faces = {{"TOP", 1, 0, 0x5C6BC0}, {"E", 0, 1, 0x26A69A}, {"S", 1, 1, 0xEF5350},
                            {"W", 2, 1, 0xFFA726}, {"N", 3, 1, 0xAB47BC}, {"DOWN", 1, 2, 0x8D6E63}};
                    for (Object[] face : faces) {
                        int fx = (int) face[1] * s, fy = (int) face[2] * s;
                        g.setColor(new Color((int) face[3]));
                        g.fillRect(fx, fy, s, s);
                        g.setColor(Color.WHITE);
                        g.setStroke(new BasicStroke(4));
                        g.drawRect(fx + 2, fy + 2, s - 4, s - 4);
                        String label = (String) face[0];
                        g.drawString(label, fx + (s - g.getFontMetrics().stringWidth(label)) / 2, fy + s / 2 + h / 24);
                    }
                }
                default -> {
                    g.setPaint(new GradientPaint(0, 0, new Color(0xFF9A9E), 0, h, new Color(0xFAD0C4)));
                    g.fillRect(0, 0, w, h);
                    g.setColor(Color.WHITE);
                    g.fillOval((int) (w * 0.3) - h / 6, (int) (h * 0.4) - h / 6, h / 3, h / 3);
                    g.setColor(new Color(0x5E35B1));
                    g.drawString("MY PICTURE", (int) (w * 0.32), (int) (h * 0.75));
                }
            }
        } finally {
            g.dispose();
        }
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot write " + file, e);
        }
        return file;
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
