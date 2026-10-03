package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoMine;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Survival route changes, checked against the server rather than predicted client blocks. */
final class AutoMineRouteChecks {
    static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        BlockPos start = context.computeOnClient(client -> client.player.getBlockPos().up(30));
        AutoMine mine = ModuleManager.get(AutoMine.class);
        try {
            fixture(context, singleplayer, mine, start);
            setBlock(singleplayer, start.add(0, 0, 6), "lava");
            setBlock(singleplayer, start.add(2, 0, 3), "water");
            start(context, mine, 7, true);
            awaitStop(context, mine);
            context.takeScreenshot("maro-auto-mine-safe-turn");
            context.runOnClient(client -> {
                require(mine.rerouteCount() == 1 && mine.routeDirection() == Direction.WEST,
                        "Auto Mine did not reject the water route and choose the dry side");
                require(mine.travelledDistance() >= 7 && mine.activity().equals("Reached 7 blocks"),
                        "Turning reset the distance limit or stopped mining");
                require(client.player.getBlockZ() >= start.getZ() + 2 && client.player.getBlockX() <= start.getX() - 3,
                        "Auto Mine did not advance on both legs of the route");
            });
            String mismatch = singleplayer.getServer().computeOnServer(server -> {
                var world = server.getOverworld();
                if (!world.getBlockState(start.add(0, 0, 6)).isOf(Blocks.LAVA)) return "lava was opened";
                if (!world.getBlockState(start.add(2, 0, 3)).isOf(Blocks.WATER)) return "water was opened";
                for (int step = 1; step <= 3; step++) for (int row = 0; row <= 1; row++) {
                    if (!world.getBlockState(start.add(-step, row, 3)).isAir()) return "side tunnel was not server-mined";
                }
                return "";
            });
            require(mismatch.isEmpty(), mismatch);

            // Bedrock in every candidate: stop, without repeatedly flipping between sides.
            fixture(context, singleplayer, mine, start);
            for (BlockPos block : java.util.List.of(start.add(0, 0, 2), start.add(-2, 0, 0), start.add(2, 0, 0)))
                setBlock(singleplayer, block, "bedrock");
            start(context, mine, 7, true);
            context.waitTicks(5);
            context.runOnClient(client -> {
                require(!mine.isEnabled() && mine.rerouteCount() == 0 && mine.activity().startsWith("No safe turn"),
                        "Auto Mine kept trying unsafe turns at a dead end");
                require(!client.options.forwardKey.isPressed() && !AutoMine.holdingBreak(),
                        "Dead-end stop retained mining or movement input");
            });

            // A floor gap with filling disabled should also choose a safe side tunnel.
            fixture(context, singleplayer, mine, start);
            setBlock(singleplayer, start.add(0, -1, 3), "air");
            start(context, mine, 5, true);
            awaitStop(context, mine);
            context.runOnClient(client -> require(mine.rerouteCount() == 1 && mine.routeDirection() == Direction.EAST
                            && mine.travelledDistance() >= 5 && mine.activity().equals("Reached 5 blocks"),
                    "Auto Mine did not route around an unsafe floor gap"));
            require(singleplayer.getServer().computeOnServer(server ->
                    server.getOverworld().getBlockState(start.add(0, -1, 3)).isAir()), "Floor-gap route modified the unsafe floor");

            // Turning off routing must retain the original stop-at-lava behaviour.
            fixture(context, singleplayer, mine, start);
            setBlock(singleplayer, start.add(0, 0, 3), "lava");
            start(context, mine, 7, false);
            context.waitTicks(5);
            context.runOnClient(client -> require(!mine.isEnabled() && mine.rerouteCount() == 0
                            && mine.activity().equals("Lava ahead"),
                    "Disabling rerouting did not retain the lava stop"));
        } finally {
            context.runOnClient(client -> {
                mine.setEnabled(false);
                for (var setting : mine.getSettings()) setting.reset();
            });
            singleplayer.getServer().runCommand("gamemode creative @a");
        }
    }

    private static void fixture(ClientGameTestContext context, TestSingleplayerContext singleplayer, AutoMine mine, BlockPos p) {
        context.runOnClient(client -> mine.setEnabled(false));
        for (String command : java.util.List.of(
                "gamemode survival @a", "clear @a",
                "give @a minecraft:diamond_pickaxe[enchantments={efficiency:5}]",
                "fill " + p.add(-10, -1, -3).toShortString().replace(",", "") + " "
                        + p.add(10, 4, 12).toShortString().replace(",", "") + " minecraft:stone",
                "fill " + p.toShortString().replace(",", "") + " "
                        + p.up().toShortString().replace(",", "") + " minecraft:air",
                "tp @a " + (p.getX() + .5) + " " + p.getY() + " " + (p.getZ() + .5) + " 0 0"))
            singleplayer.getServer().runCommand(command);
        context.waitTicks(10);
        context.runOnClient(client -> {
            for (var setting : mine.getSettings()) setting.reset();
            setting(mine, "Torches", false); setting(mine, "Mine Ores", false);
            setting(mine, "Short Breaks", false); setting(mine, "Fill Holes", false);
            setting(mine, "Min Health", 0); setting(mine, "Turn Preference", "Left");
            client.player.setYaw(0); client.player.setPitch(0);
            client.player.getInventory().setSelectedSlot(0);
            client.options.attackKey.setPressed(false); client.options.useKey.setPressed(false);
        });
    }

    private static void setBlock(TestSingleplayerContext singleplayer, BlockPos pos, String block) {
        singleplayer.getServer().runCommand("setblock " + pos.toShortString().replace(",", "") + " minecraft:" + block);
    }

    private static void start(ClientGameTestContext context, AutoMine mine, int distance, boolean reroute) {
        context.waitTicks(3);
        context.runOnClient(client -> {
            setting(mine, "Max Distance", distance); setting(mine, "Reroute Obstacles", reroute);
            mine.setEnabled(true);
        });
    }

    private static void awaitStop(ClientGameTestContext context, AutoMine mine) {
        for (int tick = 0; tick < 600 && context.computeOnClient(client -> mine.isEnabled()); tick++) context.waitTick();
        require(!context.computeOnClient(client -> mine.isEnabled()), "Auto Mine got stuck after an obstacle turn");
    }

    private static void setting(AutoMine mine, String name, Object value) {
        for (var setting : mine.getSettings()) if (setting.getName().equals(name)) {
            setting.fromJson(value instanceof Boolean b ? new JsonPrimitive(b)
                    : value instanceof Number n ? new JsonPrimitive(n) : new JsonPrimitive(value.toString()));
            return;
        }
        throw new AssertionError("Missing Auto Mine setting: " + name);
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
