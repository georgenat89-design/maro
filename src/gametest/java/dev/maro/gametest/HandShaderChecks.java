package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.HandShader;
import dev.maro.render.HandShaderRenderer;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Hand Shader: with a sword in hand, each look runs and Galaxy visibly changes the hand's corner
 * of the screen against the same view with it off.
 */
final class HandShaderChecks {
    private HandShaderChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static BufferedImage read(Path shot) {
        try {
            return ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + shot, e);
        }
    }

    /** Pixels clearly different in the lower right quarter, where the hand is. */
    private static int changed(BufferedImage before, BufferedImage after) {
        int count = 0;
        int width = Math.min(before.getWidth(), after.getWidth()), height = Math.min(before.getHeight(), after.getHeight());
        for (int y = height / 2; y < height; y += 2) {
            for (int x = width / 2; x < width; x += 2) {
                int a = before.getRGB(x, y), b = after.getRGB(x, y);
                int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF))
                        + Math.abs((a & 0xFF) - (b & 0xFF));
                if (d > 60) count++;
            }
        }
        return count;
    }

    static void run(ClientGameTestContext context) {
        HandShader module = ModuleManager.get(HandShader.class);
        require(module != null, "Hand Shader was not registered");
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean hudHidden = context.computeOnClient(c -> c.options.hudHidden);
        try {
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.hudHidden = false;
                c.player.getInventory().setSelectedSlot(0);
                c.player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
                c.player.setPitch(10);
                module.getSettings().forEach(Setting::reset);
            });
            context.waitTicks(10);
            BufferedImage plain = read(context.takeScreenshot("maro-hand-shader-off"));
            int before = context.computeOnClient(c -> HandShaderRenderer.appliedFrames());
            context.runOnClient(c -> module.setEnabled(true));
            context.waitTicks(5);
            int galaxy = changed(plain, read(context.takeScreenshot("maro-hand-shader-galaxy")));
            System.out.println("HAND SHADER galaxy changed " + galaxy + " pixels; frames " + context.computeOnClient(c -> HandShaderRenderer.appliedFrames()));
            require(context.computeOnClient(c -> HandShaderRenderer.appliedFrames()) > before, "The hand shader never ran");
            // The sword fills a good part of the lower right; most of it should change.
            require(galaxy > 3000, "Galaxy did not change the hand: " + galaxy + " pixels");
            for (String mode : HandShader.MODES) {
                if (mode.equals("Galaxy")) continue;
                context.runOnClient(c -> ((ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals("Mode")).findFirst().orElseThrow()).set(mode));
                context.waitTicks(3);
                BufferedImage shot = read(context.takeScreenshot("maro-hand-shader-" + mode.toLowerCase()));
                if (mode.equals("Flame")) {
                    int flame = changed(plain, shot);
                    System.out.println("HAND SHADER flame changed " + flame + " pixels");
                    require(flame > 3000, "Flame did not set the hand on fire: " + flame + " pixels");
                }
            }
            require(context.computeOnClient(c -> module.isEnabled()), "Hand Shader turned itself off (it failed to draw)");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.player.getInventory().setStack(0, ItemStack.EMPTY);
                c.options.setPerspective(perspective);
                c.options.hudHidden = hudHidden;
            });
        }
    }
}
