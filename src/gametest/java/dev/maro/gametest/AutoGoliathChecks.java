package dev.maro.gametest;

import dev.maro.gui.hud.GoliathPickerScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.AutoGoliath;
import dev.maro.setting.Setting;
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
            context.takeScreenshot("maro-auto-goliath-map");
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

            // A teleport into East 2 is the goliath it wanted: it stops.
            world.getServer().runCommand("tp @a 12500 200 -200000");
            for (int i = 0; i < 40 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            require(context.computeOnClient(c -> !module.isEnabled()), "Landing in East 2 did not stop Auto Goliath: " + context.computeOnClient(c -> module.status()));
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.player.getAbilities().flying = flying;
            });
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z);
            world.getServer().runOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().changeGameMode(gameMode));
            context.waitTicks(20);
        }
    }
}
