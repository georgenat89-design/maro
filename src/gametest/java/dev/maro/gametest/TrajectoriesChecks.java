package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.FakePlayer;
import dev.maro.module.impl.visuals.Trajectories;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Trajectories: holding a pearl shows a path that lands, with a timer, and that hits Fake Player
 * standing in its way; an arrow in the air is followed.
 */
final class TrajectoriesChecks {
    private TrajectoriesChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Trajectories module = ModuleManager.get(Trajectories.class);
        FakePlayer fakes = ModuleManager.get(FakePlayer.class);
        require(module != null, "Trajectories was not registered");
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.player.getInventory().setSelectedSlot(0);
                c.player.getInventory().setStack(0, new ItemStack(Items.ENDER_PEARL, 16));
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
                c.player.setPitch(-10);
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                var paths = module.lastHeldPaths();
                require(!paths.isEmpty(), "Holding a pearl showed no path");
                require(paths.getFirst().landed() && paths.getFirst().ticks() > 3, "The pearl's path does not land: " + paths.getFirst());
                require(module.lastLabels().stream().anyMatch(l -> l.endsWith("s")), "No timer on the landing: " + module.lastLabels());
            });
            context.takeScreenshot("maro-trajectories");

            // Fake Player in the way: the path hits it.
            context.runOnClient(c -> {
                fakes.getSettings().forEach(Setting::reset);
                fakes.setEnabled(true);
            });
            context.waitTicks(3);
            var fake = context.computeOnClient(c -> fakes.entity());
            require(fake != null, "No fake player to aim at");
            context.runOnClient(c -> c.player.refreshPositionAndAngles(fake.getX(), fake.getY(), fake.getZ() - 5, 0, 0));
            context.waitTicks(3);
            context.runOnClient(c -> {
                var paths = module.lastHeldPaths();
                require(!paths.isEmpty() && paths.getFirst().hit() == fake, "The pearl's path does not hit the player in front: "
                        + (paths.isEmpty() ? "none" : paths.getFirst().hit()));
            });
            context.takeScreenshot("maro-trajectories-hit");
            context.runOnClient(c -> fakes.setEnabled(false));

            // An arrow in the air is followed.
            world.getServer().runCommand("summon minecraft:arrow ~ ~3 ~4 {Motion:[0.0d,0.6d,0.8d]}");
            boolean seen = false;
            for (int i = 0; i < 30 && !seen; i++) {
                context.waitTick();
                seen = context.computeOnClient(c -> module.lastThrownCount() > 0);
            }
            require(seen, "An arrow in the air was not followed");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                fakes.setEnabled(false);
                c.player.getInventory().setStack(0, ItemStack.EMPTY);
                c.options.setPerspective(perspective);
            });
            world.getServer().runCommand("kill @e[type=minecraft:arrow]");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(3);
        }
    }
}
