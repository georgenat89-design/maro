package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.SpawnerNotifier;
import dev.maro.render.esp.BlockEspRenderer;
import dev.maro.setting.Setting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Spawner Notifier: a zombie spawner and one with no spawn data are both found, named "Zombie" and
 * "Unknown", their tags land on screen, and breaking one takes it off.
 */
final class SpawnerNotifierChecks {
    private SpawnerNotifierChecks() {
    }

    private static final BlockPos ZOMBIE = new BlockPos(2, -42, 6), EMPTY = new BlockPos(-3, -42, 8);

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String failure) {
        for (int i = 0; i < 100; i++) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return;
            context.waitTicks(3);
        }
        throw new AssertionError(failure);
    }

    private static Setting<?> setting(SpawnerNotifier module, String name) {
        return module.getSettings().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
    }

    private static String mobAt(SpawnerNotifier module, BlockPos pos) {
        return module.spawners().stream().filter(f -> f.pos().equals(pos)).map(SpawnerNotifier.Found::mob).findFirst().orElse(null);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        SpawnerNotifier module = ModuleManager.get(SpawnerNotifier.class);
        require(module != null, "Spawner Notifier was not registered");
        // The longer ranges: a config saved at the old defaults (12 chunks, 128 blocks) moves up once.
        context.runOnClient(c -> {
            var range = (dev.maro.setting.NumberSetting) setting(module, "Range");
            var tagRange = (dev.maro.setting.NumberSetting) setting(module, "Tag Range");
            range.reset();
            tagRange.reset();
            require(range.getInt() == 32 && range.getMax() >= 64 && tagRange.getInt() == 512 && tagRange.getMax() >= 1024,
                    "Spawner Notifier's ranges are not the longer ones: " + range.getInt() + " chunks, " + tagRange.getInt() + " blocks");
            range.set(12.0);
            tagRange.set(128.0);
            module.loadExtra(new com.google.gson.JsonObject());
            require(range.getInt() == 32 && tagRange.getInt() == 512, "An old config's default ranges were not made longer");
            range.set(12.0);
            module.loadExtra(module.saveExtra());
            require(range.getInt() == 12, "A range chosen after the change was moved");
            range.reset();
            tagRange.reset();
        });
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        boolean hudHidden = context.computeOnClient(c -> c.options.hudHidden);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill -6 -44 -2 6 -36 10 minecraft:air");
            world.getServer().runCommand("setblock " + at(ZOMBIE) + " minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
            world.getServer().runCommand("setblock " + at(EMPTY) + " minecraft:spawner");
            world.getServer().runCommand("tp @a 0 -38 -1 0 25");
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
                module.setEnabled(true);
            });

            await(context, () -> "Zombie".equals(mobAt(module, ZOMBIE)) && "Unknown".equals(mobAt(module, EMPTY)),
                    "Spawner Notifier did not find the zombie and empty spawners: " + context.computeOnClient(c -> module.spawners()));
            context.waitTicks(5);
            float[] tag = context.computeOnClient(c -> BlockEspRenderer.toScreen(ZOMBIE.getX() + 0.5, ZOMBIE.getY() + 1.35, ZOMBIE.getZ() + 0.5,
                    c.gameRenderer.getCamera().getCameraPos(), c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight()));
            int[] screen = context.computeOnClient(c -> new int[] {c.getWindow().getScaledWidth(), c.getWindow().getScaledHeight()});
            require(tag != null && tag[0] > 0 && tag[0] < screen[0] && tag[1] > 0 && tag[1] < screen[1],
                    "The zombie spawner's tag is not on screen: " + (tag == null ? "behind" : tag[0] + ", " + tag[1]));
            context.takeScreenshot("maro-spawner-notifier");
            System.out.println("SPAWNER NOTIFIER " + context.computeOnClient(c -> module.describe(module.spawners().getFirst())));

            // Both spawners are occluded by a solid wall; pillars must still draw with tags off.
            world.getServer().runCommand("fill -6 -44 3 6 -32 4 minecraft:stone");
            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "Tags")).set(false);
                ((BooleanSetting) setting(module, "Pillar")).set(false);
                c.options.hudHidden = false;
                c.inGameHud.getChatHud().clear(false);
            });
            context.waitTicks(8);
            int off = redPixels(context.takeScreenshot("maro-spawner-pillar-off"));
            context.runOnClient(c -> ((BooleanSetting) setting(module, "Pillar")).set(true));
            context.waitTicks(4);
            int on = redPixels(context.takeScreenshot("maro-spawner-pillar-through-wall"));
            require(on > off + 100, "Pillars did not render through blocks with tags disabled: " + off + " -> " + on);
            context.runOnClient(c -> ((NumberSetting) setting(module, "Pillar Range")).set(16.0));
            world.getServer().runCommand("tp @a 0 -38 -25 0 0");
            context.waitTicks(8);
            int outside = redPixels(context.takeScreenshot("maro-spawner-pillar-outside-range"));
            require(outside < 50, "Pillars outside their range remained visible: " + outside);
            context.runOnClient(c -> ((NumberSetting) setting(module, "Pillar Range")).reset());
            context.waitTicks(4);
            require(redPixels(context.takeScreenshot("maro-spawner-pillars")) > outside + 100, "Restoring pillar range did not restore the markers");

            // Broken: it goes from the list.
            world.getServer().runCommand("setblock " + at(ZOMBIE) + " minecraft:air");
            await(context, () -> mobAt(module, ZOMBIE) == null, "A broken spawner stayed in Spawner Notifier");
            require(context.computeOnClient(c -> mobAt(module, EMPTY) != null), "Breaking one spawner lost the other");
            world.getServer().runCommand("setblock " + at(EMPTY) + " minecraft:air");
            await(context, () -> module.spawners().isEmpty(), "Broken spawners retained their pillars");
            require(context.computeOnClient(c -> !module.hasPillars()), "Empty detection list kept the renderer active");
            System.out.println("[spawner-pillars] PASS: real detection, occluded pillars, independent tags, toggle, range and broken-spawner cleanup; pixels " + off + " -> " + on);
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
                c.options.hudHidden = hudHidden;
                c.player.getAbilities().flying = flying;
            });
            for (BlockPos pos : List.of(ZOMBIE, EMPTY)) world.getServer().runCommand("setblock " + at(pos) + " minecraft:air");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static int redPixels(Path shot) {
        try {
            var image = ImageIO.read(shot.toFile());
            int count = 0;
            // Sample the world around the markers, excluding the player's hand and notification.
            for (int y = 0; y < image.getHeight() * 0.7; y++) for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                int color = image.getRGB(x, y), r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                if (r > 80 && r > g * 1.5 && r > b * 1.5) count++;
            }
            return count;
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
