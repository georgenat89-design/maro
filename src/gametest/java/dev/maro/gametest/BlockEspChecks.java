package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.BlockESP;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.BlockPos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Block ESP: picked blocks underground are found and drawn, one above the Y limit is left out until
 * the limit is off, breaking one takes it off without a rescan, and the boxes, tracers and bloom
 * all draw (more lit pixels with bloom than without).
 */
final class BlockEspChecks {
    private BlockEspChecks() {
    }

    private static final BlockPos SPAWNER = new BlockPos(6, -40, 6), CHEST = new BlockPos(-6, -38, 4),
            DIAMOND = new BlockPos(4, -36, -7), HIGH_SPAWNER = new BlockPos(-3, 30, -3);

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(BlockESP module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String failure) {
        for (int i = 0; i < 100; i++) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return;
            context.waitTicks(3);
        }
        throw new AssertionError(failure);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        BlockESP module = ModuleManager.get(BlockESP.class);
        require(module != null, "Block ESP was not registered");
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -12 -44 -12 12 -28 12 minecraft:air");
            world.getServer().runCommand("setblock " + at(SPAWNER) + " minecraft:spawner");
            world.getServer().runCommand("setblock " + at(CHEST) + " minecraft:chest");
            world.getServer().runCommand("setblock " + at(DIAMOND) + " minecraft:deepslate_diamond_ore");
            world.getServer().runCommand("setblock " + at(HIGH_SPAWNER) + " minecraft:spawner");
            world.getServer().runCommand("tp @a 0 -30 -12 0 35");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.getAbilities().flying = true;
                player.sendAbilitiesUpdate();
            });
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = true;
                c.player.setVelocity(0, 0, 0);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                module.getSettings().forEach(Setting::reset);
            });
            // The same view with nothing drawn: the sky and grass of the test world are coloured too.
            context.waitTicks(5);
            int plain = lit(context.takeScreenshot("maro-block-esp-off"));
            context.runOnClient(c -> module.setEnabled(true));

            // Spawners, chests and diamonds are on by default, and the Y limit keeps to Y 0 and below.
            long searching = System.nanoTime();
            await(context, () -> module.shownPositions().containsAll(List.of(SPAWNER, CHEST, DIAMOND)),
                    "Block ESP did not find the underground spawner, chest and diamond ore: " + context.computeOnClient(c -> module.shownPositions()));
            System.out.printf(Locale.ROOT, "BLOCK ESP found the blocks after %.0f ms%n", (System.nanoTime() - searching) / 1e6);
            require(context.computeOnClient(c -> !module.shownPositions().contains(HIGH_SPAWNER)), "A spawner above the Y limit was drawn");
            context.waitTicks(10);
            int withBloom = lit(context.takeScreenshot("maro-block-esp"));

            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "ESP Bloom")).set(false);
                ((BooleanSetting) setting(module, "Tracer Bloom")).set(false);
            });
            context.waitTicks(5);
            int withoutBloom = lit(context.takeScreenshot("maro-block-esp-no-bloom"));
            System.out.println("BLOCK ESP lit pixels: off " + plain + ", bloom " + withBloom + ", no bloom " + withoutBloom);
            require(withoutBloom - plain > 400, "Block ESP drew too little: " + withoutBloom + " vs " + plain + " off");
            require(withBloom > withoutBloom, "Bloom added no glow: " + withBloom + " vs " + withoutBloom);

            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "ESP Bloom")).set(true);
                ((BooleanSetting) setting(module, "Tracer Bloom")).set(true);
                ((ModeSetting) setting(module, "Tracer Color")).set("Rainbow");
                ((ModeSetting) setting(module, "Style")).set("Outline");
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-block-esp-rainbow-outline");

            // The limit off: the high spawner comes in. Broken: the spawner goes, with no rescan.
            context.runOnClient(c -> ((ModeSetting) setting(module, "Y Limit")).set("Off"));
            await(context, () -> module.shownPositions().contains(HIGH_SPAWNER), "Turning the Y limit off did not show the high spawner");
            context.runOnClient(c -> {
                ((ModeSetting) setting(module, "Y Limit")).set("You");
                ((NumberSetting) setting(module, "Max Y")).set(-64.0);
            });
            context.waitTicks(3);
            int hidden = lit(context.takeScreenshot("maro-block-esp-you-above"));
            require(Math.abs(hidden - plain) < (withoutBloom - plain) / 4,
                    "Y Limit You drew while you were above Max Y: " + hidden + " vs " + plain + " off");
            context.runOnClient(c -> ((ModeSetting) setting(module, "Y Limit")).set("Blocks"));
            context.runOnClient(c -> ((NumberSetting) setting(module, "Max Y")).set(0.0));
            world.getServer().runCommand("setblock " + at(SPAWNER) + " minecraft:air");
            await(context, () -> !module.shownPositions().contains(SPAWNER), "A broken spawner stayed in Block ESP");
            world.getServer().runCommand("setblock " + at(SPAWNER) + " minecraft:spawner");
            await(context, () -> module.shownPositions().contains(SPAWNER), "A placed spawner did not show in Block ESP");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
                c.player.getAbilities().flying = flying;
            });
            for (BlockPos pos : List.of(SPAWNER, CHEST, DIAMOND, HIGH_SPAWNER)) world.getServer().runCommand("setblock " + at(pos) + " minecraft:air");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * Pixels bright and strongly coloured: the ESP's boxes, tracers and glow, but also the test
     * world's sky and grass, so a count means something only next to one with Block ESP off. The
     * bottom fifth, where the hotbar and chat are, is left out.
     */
    private static int lit(Path shot) {
        BufferedImage image;
        try {
            image = ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + shot, e);
        }
        int count = 0;
        for (int y = 0; y < image.getHeight() * 4 / 5; y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
                if (max > 90 && max - min > 60) count++;
            }
        }
        return count;
    }
}
