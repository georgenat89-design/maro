package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.HoleESP;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Hole ESP: a 1x1 and a 2x1 hole walled and floored with bedrock are found, a pocket with one side
 * open is not, an obsidian hole only with Obsidian Holes on, and filling a hole takes it off.
 */
final class HoleEspChecks {
    private HoleEspChecks() {
    }

    private static final BlockPos SINGLE = new BlockPos(40, -40, 40), DOUBLE_A = new BlockPos(46, -40, 40), DOUBLE_B = new BlockPos(47, -40, 40),
            OPEN = new BlockPos(52, -40, 40), OBSIDIAN = new BlockPos(58, -40, 40);

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(HoleESP module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void await(ClientGameTestContext context, java.util.function.BooleanSupplier condition, String failure) {
        for (int i = 0; i < 100; i++) {
            if (context.computeOnClient(c -> condition.getAsBoolean())) return;
            context.waitTicks(3);
        }
        throw new AssertionError(failure);
    }

    private static String at(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** Floor under each cell and a wall on every side not taken by another cell. */
    private static void hole(TestSingleplayerContext world, String block, BlockPos... cells) {
        List<BlockPos> list = List.of(cells);
        for (BlockPos cell : cells) {
            world.getServer().runCommand("setblock " + at(cell.down()) + " " + block);
            world.getServer().runCommand("setblock " + at(cell) + " minecraft:air");
            world.getServer().runCommand("setblock " + at(cell.up()) + " minecraft:air");
            for (BlockPos side : List.of(cell.north(), cell.south(), cell.east(), cell.west())) {
                if (!list.contains(side)) world.getServer().runCommand("setblock " + at(side) + " " + block);
            }
        }
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        HoleESP module = ModuleManager.get(HoleESP.class);
        require(module != null, "Hole ESP was not registered");
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            // Creative and on the ground, so nothing here can hurt the player for the tests after.
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("fill 36 -42 36 62 -36 44 minecraft:air");
            hole(world, "minecraft:bedrock", SINGLE);
            hole(world, "minecraft:bedrock", DOUBLE_A, DOUBLE_B);
            hole(world, "minecraft:bedrock", OPEN);
            world.getServer().runCommand("setblock " + at(OPEN.east()) + " minecraft:air");
            hole(world, "minecraft:obsidian", OBSIDIAN);
            world.getServer().runCommand("tp @a 49 -60 30 0 -20");
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            await(context, () -> module.shownPositions().containsAll(List.of(SINGLE, DOUBLE_A, DOUBLE_B)),
                    "Hole ESP did not find the 1x1 and 2x1 bedrock holes: " + context.computeOnClient(c -> module.shownPositions()));
            context.runOnClient(c -> require(!module.shownPositions().contains(OPEN) && !module.shownPositions().contains(OBSIDIAN),
                    "Hole ESP drew an open pocket or an obsidian hole: " + module.shownPositions()));
            context.waitTicks(5);
            context.takeScreenshot("maro-hole-esp");

            context.runOnClient(c -> ((BooleanSetting) setting(module, "Obsidian Holes")).set(true));
            await(context, () -> module.shownPositions().contains(OBSIDIAN), "Obsidian Holes did not show the obsidian hole");

            // Filling a hole takes it off without anything else.
            world.getServer().runCommand("setblock " + at(SINGLE) + " minecraft:stone");
            await(context, () -> !module.shownPositions().contains(SINGLE), "A filled hole stayed in Hole ESP");
            require(context.computeOnClient(c -> module.shownPositions().contains(DOUBLE_A)), "Filling one hole lost another");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
            });
            world.getServer().runCommand("fill 36 -42 36 62 -36 44 minecraft:air");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(5);
        }
    }
}
