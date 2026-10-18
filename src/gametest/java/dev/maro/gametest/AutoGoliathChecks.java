package dev.maro.gametest;

import dev.maro.gui.hud.GoliathPickerScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.AutoGoliath;
import dev.maro.setting.Setting;
import dev.maro.nathan.regionmap.RegionGrid;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Auto Goliath: aimed at East 2 while standing in East 1, it bounces out through another region
 * rather than /rtp east again; landing in East 2 (a teleport there) stops it.
 */
final class AutoGoliathChecks {
    private AutoGoliathChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        AutoGoliath module = ModuleManager.get(AutoGoliath.class);
        require(module != null, "Auto Goliath was not registered");
        require(AutoGoliath.count(0) > 10, "East has too few goliaths on the map: " + AutoGoliath.count(0));
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.pick(0, 2);
                c.setScreen(new GoliathPickerScreen(null, module));
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                var picker = (GoliathPickerScreen) c.currentScreen;
                click(picker, 0, 3);
                click(picker, 1, 2);
                require(module.selectedTargets().size() == 3 && module.isPicked(0, 2) && module.isPicked(0, 3) && module.isPicked(1, 2),
                        "Clicking multiple tiles replaced an earlier choice");
                click(picker, 0, 2);
                require(module.selectedTargets().size() == 2 && !module.isPicked(0, 2), "Clicking a selected primary tile did not remove it");
                var saved = module.saveExtra();
                module.pick(0, 1);
                module.loadExtra(saved);
                require(module.selectedTargets().size() == 2 && module.isPicked(0, 3) && module.isPicked(1, 2), "Multiple selections did not survive config round-trip");
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-auto-goliath-multiple-map");
            context.runOnClient(c -> {
                module.loadExtra(new com.google.gson.JsonObject());
                require(module.selectedTargets().size() == 1, "Legacy single-target config retained extra selections");
                module.pick(0, 2);
                module.togglePick(0, 3);
            });
            context.runOnClient(c -> c.setScreen(null));

            // In East 1: the next /rtp must go elsewhere first.
            world.getServer().runCommand("tp @a -12500 200 -200000");
            context.waitTicks(20);
            String here = context.computeOnClient(c -> module.here() == null ? "nowhere" : AutoGoliath.name(module.here()));
            require(here.equals("East 1"), "The test spot is not East 1 but " + here);
            context.runOnClient(c -> {
                c.player.getAbilities().flying = true;
                module.setEnabled(true);
            });
            context.waitTicks(3);
            String first = context.computeOnClient(c -> module.lastCommand());
            System.out.println("AUTO GOLIATH from East 1 sent /" + first);
            require(first.startsWith("rtp ") && !first.equals("rtp east"), "From the wrong East goliath it did not bounce through another region: " + first);
            context.takeScreenshot("maro-auto-goliath-running");

            // Landing in the second selected Goliath must stop too.
            world.getServer().runCommand("tp @a 37500 200 -200000");
            for (int i = 0; i < 40 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            require(context.computeOnClient(c -> !module.isEnabled()), "Landing in the second selected Goliath did not stop Auto Goliath: " + context.computeOnClient(c -> module.status()));
            context.runOnClient(c -> {
                module.setEnabled(true);
                require(!module.isEnabled(), "Starting in a secondary selected Goliath did not immediately stop");
                module.pick(0, 2);
                module.togglePick(1, 2);
            });
            world.getServer().runCommand("tp @a -12500 200 -200000");
            context.waitTicks(10);
            context.runOnClient(c -> module.setEnabled(true));
            context.waitTicks(3);
            require(context.computeOnClient(c -> module.lastCommand().equals("rtp west")), "Cross-region selections did not route directly to another selected region");
            RegionGrid.Shard west = null;
            for (int i = 0; i < RegionGrid.count(); i++) {
                var shard = RegionGrid.shard(i);
                if (shard.locale() == AutoGoliath.locale(1) && shard.number() == 2) west = shard;
            }
            require(west != null, "Missing West 2 fixture");
            double x = RegionGrid.ORIGIN + (west.col() + west.width() * 0.5) * RegionGrid.CELL_BLOCKS;
            double z = RegionGrid.ORIGIN + (west.row() + west.height() * 0.5) * RegionGrid.CELL_BLOCKS;
            world.getServer().runCommand("tp @a " + x + " 200 " + z);
            for (int i = 0; i < 40 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            require(context.computeOnClient(c -> !module.isEnabled()), "Landing in a selected Goliath in another region did not stop");
            context.runOnClient(c -> {
                module.pick(0, 2);
                module.togglePick(0, 2);
                require(module.selectedTargets().size() == 1, "Removing the last selection left no destination");
            });
            System.out.println("[auto-goliath-multiple] PASS: real map clicks, remove primary, config round-trip, legacy config, same-region bounce, secondary arrival stop, direct selected-region routing and cross-region arrival");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.pick(0, 1);
                module.getSettings().forEach(Setting::reset);
                c.player.getAbilities().flying = flying;
            });
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(20);
        }
    }

    private static void click(GoliathPickerScreen picker, int region, int number) {
        double[] point = picker.tilePoint(region, number);
        picker.mouseClicked(new Click(point[0], point[1], new MouseInput(0, 0)), false);
    }
}
