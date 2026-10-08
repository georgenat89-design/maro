package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomTotem;
import dev.maro.render.TotemTexture;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Custom Totem: a magenta picture imported the way the Choose screen does it shows up as the totem
 * in your hand and in the pop animation, wrapped over the totem's own shape; a logo on a black
 * background can be wrapped or cut out of it; the pop can be resized and hidden; and switching the
 * module off brings the normal totem back.
 */
final class TotemChecks {
    private TotemChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(CustomTotem totem, String name) {
        return totem.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        CustomTotem totem = ModuleManager.get(CustomTotem.class);
        require(totem != null, "Custom Totem was not registered");
        world.getServer().runCommand("time set noon");
        // Only the one in your hand, so the pop is the only totem on screen once that is put away.
        world.getServer().runCommand("clear @a minecraft:totem_of_undying");
        context.runOnClient(c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.player.getInventory().setSelectedSlot(0);
            c.player.setPitch(10f);
        });
        context.waitTicks(3);
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:totem_of_undying");
        context.waitTicks(10);
        int normal = magenta(context.takeScreenshot("maro-custom-totem-normal"));
        try {
            Path folder = context.computeOnClient(c -> c.runDirectory.toPath().resolve("maro-test-totems"));
            Path file = folder.resolve("magenta.png");
            writePicture(file);
            context.runOnClient(c -> totem.getSettings().forEach(Setting::reset));
            CompletableFuture<Boolean> loaded = context.computeOnClient(c -> totem.importImage(file));
            for (int i = 0; i < 400 && !loaded.isDone(); i++) context.waitTick();
            require(loaded.getNow(false), "Could not load the totem picture: " + totem.imageStatus());
            require(context.computeOnClient(c -> totem.isEnabled()), "Choosing a picture did not turn Custom Totem on");
            settleTexture(context);
            context.waitTicks(10);
            int hand = magenta(context.takeScreenshot("maro-custom-totem-hand"));
            System.out.println("CUSTOM TOTEM " + totem.imageStatus() + ": magenta pixels normal=" + normal + " custom=" + hand);
            require(hand > normal + 400, "The totem in your hand did not show the picture (" + normal + " -> " + hand + " magenta pixels)");

            // The pop animation, with the hand empty so only the floating totem counts.
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            context.waitTicks(5);
            int empty = magenta(context.takeScreenshot("maro-custom-totem-empty-hand"));
            int popped = pop(context, "maro-custom-totem-pop") - empty;
            context.runOnClient(c -> ((NumberSetting) setting(totem, "Pop Size")).set(200.0));
            int bigger = pop(context, "maro-custom-totem-pop-200") - empty;
            context.runOnClient(c -> ((BooleanSetting) setting(totem, "Pop Animation")).set(false));
            int hidden = pop(context, "maro-custom-totem-pop-hidden") - empty;
            System.out.println("CUSTOM TOTEM pop magenta pixels over an empty hand (" + empty + "): 100%=" + popped + " 200%=" + bigger
                    + " hidden=" + hidden);
            require(popped > 2000, "The pop animation did not show the picture (" + popped + " magenta pixels)");
            require(bigger > popped * 1.4, "Pop Size 200% did not make the pop bigger (" + popped + " -> " + bigger + ")");
            require(hidden < 50, "Pop Animation off still showed the pop (" + hidden + " magenta pixels)");
            context.runOnClient(c -> totem.getSettings().forEach(Setting::reset));

            checkShapes(file, writeLogo(folder.resolve("logo.png")));

            // A logo on a black background, as people use: wrapped over the totem, then cut out of the black.
            Path logo = folder.resolve("logo.png");
            CompletableFuture<Boolean> logoLoaded = context.computeOnClient(c -> totem.importImage(logo));
            for (int i = 0; i < 400 && !logoLoaded.isDone(); i++) context.waitTick();
            require(logoLoaded.getNow(false), "Could not load the logo: " + totem.imageStatus());
            settleTexture(context);
            int wrapped = pop(context, "maro-custom-totem-logo-wrap") - empty;
            context.runOnClient(c -> ((ModeSetting) setting(totem, "Shape")).set(CustomTotem.CUT_OUT));
            settleTexture(context);
            int cut = pop(context, "maro-custom-totem-logo-cutout") - empty;
            System.out.println("CUSTOM TOTEM logo pop magenta pixels: wrap=" + wrapped + " cut out=" + cut);
            require(wrapped > 1000, "The wrapped logo did not show in the pop (" + wrapped + " magenta pixels)");
            require(cut > 1000, "The cut-out logo did not show in the pop (" + cut + " magenta pixels)");
        } finally {
            context.runOnClient(c -> {
                totem.setEnabled(false);
                totem.getSettings().forEach(Setting::reset);
            });
        }
        settleTexture(context);
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:totem_of_undying");
        context.waitTicks(10);
        int back = magenta(context.takeScreenshot("maro-custom-totem-off"));
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        System.out.println("CUSTOM TOTEM off: magenta pixels=" + back);
        require(back < normal + 50, "Switching Custom Totem off did not bring the normal totem back (" + back + " magenta pixels)");
    }

    private static void settleTexture(ClientGameTestContext context) {
        context.waitTicks(3);
        context.waitFor(c -> CustomTotem.textureSettled(), 2400);
        context.waitTicks(5);
    }

    /** Shows the totem pop and screenshots it a quarter of the way through, when it is near its biggest. */
    private static int pop(ClientGameTestContext context, String name) {
        context.runOnClient(c -> c.gameRenderer.showFloatingItem(new ItemStack(Items.TOTEM_OF_UNDYING)));
        context.waitTicks(8);
        int count = magenta(context.takeScreenshot(name));
        context.waitTicks(40); // let it finish before the next one
        return count;
    }

    /**
     * The shapes, worked out directly from the game's own totem texture: Wrap keeps exactly the
     * totem's outline, Cut Out takes the black away from a logo, Square keeps everything.
     */
    private static void checkShapes(Path square, Path logo) {
        var base = CustomTotem.totemBase();
        require(base != null, "Custom Totem never saw the game's totem texture");
        try {
            var wrapped = TotemTexture.make(TotemTexture.decode(square), TotemTexture.Shape.WRAP, base, 0.5f);
            int size = wrapped.size(), outside = 0, missing = 0, drawn = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    boolean inTotem = base.argb()[(y * base.height() / size) * base.width() + x * base.width() / size] >>> 24 >= 128;
                    boolean there = wrapped.argb()[y * size + x] >>> 24 > 0;
                    if (there && !inTotem) outside++;
                    if (inTotem && !there) missing++;
                    if (there) drawn++;
                }
            }
            System.out.println("CUSTOM TOTEM wrap: " + base.width() + "x" + base.height() + " totem, " + size + "x" + size + " texture, " + drawn
                    + " pixels drawn, " + outside + " outside the totem, " + missing + " of it missing");
            require(drawn > 0 && outside == 0 && missing == 0, "Wrap did not follow the totem's outline (" + outside + " outside, " + missing + " missing)");

            var picture = TotemTexture.decode(logo);
            int[] cut = counts(TotemTexture.make(picture, TotemTexture.Shape.CUT_OUT, base, 0));
            int[] whole = counts(TotemTexture.make(picture, TotemTexture.Shape.SQUARE, base, 0));
            System.out.println("CUSTOM TOTEM logo (drawn, black): cut out " + cut[0] + ", " + cut[1] + "; square " + whole[0] + ", " + whole[1]);
            require(cut[0] > 200 && cut[1] * 20 < cut[0], "Cut Out did not take the black background away (" + cut[1] + " of " + cut[0] + " black)");
            require(whole[1] * 2 > whole[0], "Square did not keep the whole picture (" + whole[1] + " of " + whole[0] + " black)");
        } catch (IOException e) {
            throw new AssertionError("Cannot read the test pictures", e);
        }
    }

    /** Pixels drawn, and how many of them are black. */
    private static int[] counts(TotemTexture.Made made) {
        int drawn = 0, black = 0;
        for (int p : made.argb()) {
            if (p >>> 24 == 0) continue;
            drawn++;
            if ((p >> 16 & 0xFF) < 40 && (p >> 8 & 0xFF) < 40 && (p & 0xFF) < 40) black++;
        }
        return new int[] {drawn, black};
    }

    /** "ZA" in magenta on black, like a logo saved without a see-through background. */
    private static Path writeLogo(Path file) {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 512, 512);
            g.setColor(new Color(0xFF00FF));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 300));
            g.drawString("ZA", 40, 370);
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

    /** A 64 × 64 magenta square with a see-through border, a colour nothing else in the scene has. */
    private static void writePicture(Path file) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 4; y < 60; y++) {
            for (int x = 4; x < 60; x++) image.setRGB(x, y, 0xFFFF00FF);
        }
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(image, "png", file.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot write " + file, e);
        }
    }

    /** Pixels that are clearly magenta, however the item is lit. */
    private static int magenta(Path screenshot) {
        BufferedImage image;
        try {
            image = ImageIO.read(screenshot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + screenshot, e);
        }
        int count = 0;
        long sumX = 0, sumY = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                if (r > 90 && b > 90 && g * 2 < Math.min(r, b) && Math.abs(r - b) * 3 < Math.max(r, b)) {
                    count++;
                    sumX += x;
                    sumY += y;
                }
            }
        }
        // Where it sits on screen, as a fraction of the width and height, to see what the pop size grows about.
        if (count > 0) {
            System.out.printf(java.util.Locale.ROOT, "CUSTOM TOTEM %s: %d magenta pixels centred at %.2f, %.2f%n", screenshot.getFileName(), count,
                    sumX / (double) count / image.getWidth(), sumY / (double) count / image.getHeight());
        }
        return count;
    }
}
