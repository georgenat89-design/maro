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
import net.minecraft.block.Blocks;
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
            DIAMOND = new BlockPos(4, -36, -7), HIGH_SPAWNER = new BlockPos(-3, 30, -3), GOLD = new BlockPos(-4, -34, 8);

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
        boolean hudHidden = context.computeOnClient(c -> c.options.hudHidden);
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
                // As F1: no hand, chat or hotbar, so between screenshots only the ESP can change.
                c.options.hudHidden = true;
                module.getSettings().forEach(Setting::reset);
                module.resetBlocks();
            });
            // The same view with nothing drawn, to compare every screenshot after with.
            context.waitTicks(5);
            BufferedImage plain = read(context.takeScreenshot("maro-block-esp-off"));
            context.runOnClient(c -> module.setEnabled(true));

            // Spawners, chests and diamonds are on by default, and the Y limit keeps to Y 0 and below.
            long searching = System.nanoTime();
            await(context, () -> module.shownPositions().containsAll(List.of(SPAWNER, CHEST, DIAMOND)),
                    "Block ESP did not find the underground spawner, chest and diamond ore: " + context.computeOnClient(c -> module.shownPositions()));
            System.out.printf(Locale.ROOT, "BLOCK ESP found the blocks after %.0f ms%n", (System.nanoTime() - searching) / 1e6);
            require(context.computeOnClient(c -> !module.shownPositions().contains(HIGH_SPAWNER)), "A spawner above the Y limit was drawn");
            context.waitTicks(10);
            int withBloom = changed(plain, read(context.takeScreenshot("maro-block-esp")));

            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "ESP Bloom")).set(false);
                ((BooleanSetting) setting(module, "Tracer Bloom")).set(false);
            });
            context.waitTicks(5);
            int withoutBloom = changed(plain, read(context.takeScreenshot("maro-block-esp-no-bloom")));
            System.out.println("BLOCK ESP pixels changed from Block ESP off: bloom " + withBloom + ", no bloom " + withoutBloom);
            require(withoutBloom > 400, "Block ESP drew too little: " + withoutBloom);
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
            int hidden = changed(plain, read(context.takeScreenshot("maro-block-esp-you-above")));
            require(hidden < withoutBloom / 4, "Y Limit You drew while you were above Max Y: " + hidden);
            context.runOnClient(c -> ((ModeSetting) setting(module, "Y Limit")).set("Blocks"));
            context.runOnClient(c -> ((NumberSetting) setting(module, "Max Y")).set(0.0));
            world.getServer().runCommand("setblock " + at(SPAWNER) + " minecraft:air");
            await(context, () -> !module.shownPositions().contains(SPAWNER), "A broken spawner stayed in Block ESP");
            world.getServer().runCommand("setblock " + at(SPAWNER) + " minecraft:spawner");
            await(context, () -> module.shownPositions().contains(SPAWNER), "A placed spawner did not show in Block ESP");

            // The picker: search, pick gold blocks, colour them, and the pick and colour are saved.
            world.getServer().runCommand("setblock " + at(GOLD) + " minecraft:gold_block");
            context.runOnClient(c -> {
                c.options.hudHidden = false;
                var picker = new dev.maro.gui.hud.BlockEspScreen(null, module);
                c.setScreen(picker);
                picker.search("gold");
                require(picker.results().contains(Blocks.GOLD_BLOCK) && picker.results().contains(Blocks.GOLD_ORE),
                        "Searching gold did not find gold blocks and ore: " + picker.results().size());
                require(!picker.results().contains(Blocks.STONE), "Searching gold found stone");
                picker.toggle(Blocks.GOLD_BLOCK);
                require(module.isPicked(Blocks.GOLD_BLOCK), "Clicking gold block did not pick it");
                picker.expand(Blocks.GOLD_BLOCK);
                module.setColor(Blocks.GOLD_BLOCK, 0xFF12AB34);
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-block-esp-picker");
            // Blocks with no item of their own still get a picture: potted plants are the plant in a pot.
            context.runOnClient(c -> {
                var picker = (dev.maro.gui.hud.BlockEspScreen) c.currentScreen;
                picker.search("potted");
                require(picker.results().contains(Blocks.POTTED_MANGROVE_PROPAGULE), "Searching potted did not find potted plants");
            });
            context.waitTicks(4);
            context.takeScreenshot("maro-block-esp-picker-potted");
            context.runOnClient(c -> {
                var picker = (dev.maro.gui.hud.BlockEspScreen) c.currentScreen;
                picker.search("");
                c.setScreen(null);
                c.options.hudHidden = true;
                var saved = module.saveExtra();
                var copy = new BlockESP();
                copy.loadExtra(saved);
                require(copy.isPicked(Blocks.GOLD_BLOCK) && (copy.colorOf(Blocks.GOLD_BLOCK) & 0xFFFFFF) == 0x12AB34,
                        "The picked block or its colour was not saved: " + saved);
                require(copy.isPicked(Blocks.SPAWNER), "Saving lost the starting pick: " + saved);
            });
            await(context, () -> module.shownPositions().contains(GOLD), "A newly picked gold block was not found");
            context.runOnClient(c -> module.unpick(Blocks.GOLD_BLOCK));
            await(context, () -> !module.shownPositions().contains(GOLD), "An unpicked block stayed in Block ESP");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                module.resetBlocks();
                if (c.currentScreen instanceof dev.maro.gui.hud.BlockEspScreen) c.setScreen(null);
                c.options.setPerspective(perspective);
                c.options.hudHidden = hudHidden;
                c.player.getAbilities().flying = flying;
            });
            for (BlockPos pos : List.of(SPAWNER, CHEST, DIAMOND, HIGH_SPAWNER, GOLD)) world.getServer().runCommand("setblock " + at(pos) + " minecraft:air");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static BufferedImage read(Path shot) {
        try {
            return ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + shot, e);
        }
    }

    /** Pixels clearly different from the same view with Block ESP off: what its boxes, tracers and glow drew. */
    private static int changed(BufferedImage before, BufferedImage after) {
        int count = 0;
        int width = Math.min(before.getWidth(), after.getWidth()), height = Math.min(before.getHeight(), after.getHeight());
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int a = before.getRGB(x, y), b = after.getRGB(x, y);
                int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF))
                        + Math.abs((a & 0xFF) - (b & 0xFF));
                if (d > 60) count++;
            }
        }
        return count;
    }
}
