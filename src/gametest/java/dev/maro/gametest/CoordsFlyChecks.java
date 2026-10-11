package dev.maro.gametest;

import dev.maro.module.Category;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.CoordsFly;
import dev.maro.module.impl.visuals.Compass;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

/**
 * Coords Fly: typed coords read in their usual forms, the distance and turn are right, Nether
 * coords are worked out in the Overworld, standing on the coords counts as there, and with an
 * elytra it turns you round towards them and fires rockets to keep you going.
 */
final class CoordsFlyChecks {
    private CoordsFlyChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(CoordsFly module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        CoordsFly module = ModuleManager.get(CoordsFly.class);
        Compass compass = ModuleManager.get(Compass.class);
        require(module != null, "Coords Fly was not registered");
        require(module.getCategory() == Category.MOVEMENT, "Coords Fly is not under Movement");

        double[] full = CoordsFly.parse("100 64 -200");
        require(full != null && full[0] == 100 && full[1] == 64 && full[2] == -200, "X Y Z did not read");
        double[] labelled = CoordsFly.parse("x: 1.5k, z: -2k");
        require(labelled != null && labelled[0] == 1500 && Double.isNaN(labelled[1]) && labelled[2] == -2000, "x: 1.5k, z: -2k did not read");
        require(CoordsFly.parse("hello") == null && CoordsFly.parse("5") == null && CoordsFly.parse("1 2 3 4") == null, "Nonsense read as coords");
        require(CoordsFly.distanceText(640).equals("640 m"), "640 m reads " + CoordsFly.distanceText(640));
        require(CoordsFly.distanceText(2410).equals("2.41 km"), "2410 m reads " + CoordsFly.distanceText(2410));
        require(CoordsFly.distanceText(18500).equals("18.5 km"), "18500 m reads " + CoordsFly.distanceText(18500));
        require(CoordsFly.kmAndM(2410).equals("2 km 410 m"), "2410 m in km and m reads " + CoordsFly.kmAndM(2410));

        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        int selected = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
        boolean compassOn = context.computeOnClient(c -> compass.isEnabled());
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 49 -60 30 0 0");
            context.waitTicks(3);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                module.getSettings().forEach(Setting::reset);
                ((TextSetting) setting(module, "Coords")).set("49 -60 -2380");
                module.setEnabled(true);
            });
            context.waitTicks(3);

            // 2,410 blocks due north, while facing south: behind you; facing east, to your left.
            context.runOnClient(c -> {
                var t = module.target();
                require(t != null, "Typed coords did not read in game");
                double d = module.distance(t);
                require(Math.abs(d - 2410) < 1.5, "The coords are 2410 away, not " + d);
                double rel = module.relative(t);
                require(Math.abs(Math.abs(rel) - 180) < 2 && module.turnText(rel).startsWith("Behind"),
                        "Facing south, coords to the north are not behind: " + rel + " " + module.turnText(rel));
                c.player.setYaw(-90);
                rel = module.relative(t);
                require(Math.abs(rel + 90) < 2 && module.turnText(rel).equals("Turn left 90°"),
                        "Facing east, coords to the north are not to the left: " + rel + " " + module.turnText(rel));

                // Nether coords, in the Overworld, are eight times as far.
                ((ModeSetting) setting(module, "Coords Are For")).set("Nether");
                ((TextSetting) setting(module, "Coords")).set("6 -297");
                var nether = module.target();
                require(nether != null && nether.x() == 48 && nether.z() == -2376 && !nether.hasY(),
                        "Nether coords were not worked out for the Overworld: " + nether);
                ((ModeSetting) setting(module, "Coords Are For")).reset();
            });

            // On screen: the card, the beam, the marker, the guide arrows and the compass marker.
            context.runOnClient(c -> {
                ((TextSetting) setting(module, "Coords")).set("70 -60 120");
                c.player.setYaw(0);
                c.player.setPitch(8);
                compass.setEnabled(true);
            });
            context.waitTicks(8);
            context.takeScreenshot("maro-coords-fly");
            context.runOnClient(c -> c.player.setYaw(180));
            context.waitTicks(3);
            context.takeScreenshot("maro-coords-fly-behind");

            // Standing on the coords is being there.
            context.runOnClient(c -> ((ButtonSetting) setting(module, "Set To Here")).press());
            context.waitTicks(3);
            require(context.computeOnClient(c -> module.arrived()), "Standing on the coords did not count as there");

            // With an elytra, facing away: it turns you round and fires rockets from the hotbar.
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:elytra");
            world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:firework_rocket 16");
            world.getServer().runCommand("tp @a 49 40 30 0 0");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
            });
            context.runOnClient(c -> {
                c.player.getAbilities().flying = false;
                c.player.getInventory().setSelectedSlot(0);
                ((TextSetting) setting(module, "Coords")).set("49 -60 -1500");
                ((BooleanSetting) setting(module, "Auto Steer")).set(true);
                ((NumberSetting) setting(module, "Turn Speed")).set(12.0);
                ((BooleanSetting) setting(module, "Hold Height")).set(true);
                ((NumberSetting) setting(module, "Cruise Height")).set(40.0);
                ((BooleanSetting) setting(module, "Auto Rocket")).set(true);
                ((NumberSetting) setting(module, "Rocket Below")).set(60.0);
                ((NumberSetting) setting(module, "Rocket Delay")).set(1.0);
            });
            context.waitTicks(4);
            boolean gliding = false;
            for (int i = 0; i < 20 && !gliding; i++) {
                context.runOnClient(c -> {
                    if (!c.player.isGliding() && c.player.checkGliding()) {
                        c.player.networkHandler.sendPacket(new ClientCommandC2SPacket(c.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    }
                });
                context.waitTick();
                gliding = context.computeOnClient(c -> c.player.isGliding());
            }
            require(gliding, "Could not start gliding for the elytra check");
            context.waitTicks(25);
            context.takeScreenshot("maro-coords-fly-elytra");
            context.waitTicks(25);
            context.runOnClient(c -> {
                var t = module.target();
                double rel = module.relative(t);
                System.out.println("COORDS FLY elytra: turn=" + Math.round(rel) + " rockets=" + module.rocketsUsed() + " boost=" + module.boostSeen()
                        + " steered=" + module.steeredTicks() + " z=" + Math.round(c.player.getZ()) + " y=" + Math.round(c.player.getY())
                        + " closing=" + Math.round(module.closingSpeed()) + " m/s gliding=" + c.player.isGliding());
                require(module.steeredTicks() > 0 && Math.abs(rel) < 25, "Auto Steer did not turn you to the coords: " + rel);
                require(module.rocketsUsed() > 0, "Auto Rocket fired no rockets");
                require(module.boostSeen(), "No rocket pushed you");
                require(c.player.getZ() < 25, "You did not fly towards the coords: z " + c.player.getZ());
            });
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                compass.setEnabled(compassOn);
                c.options.setPerspective(perspective);
                c.player.getInventory().setSelectedSlot(selected);
            });
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:air");
            world.getServer().runCommand("item replace entity @a hotbar.4 with minecraft:air");
            world.getServer().runCommand("kill @e[type=minecraft:firework_rocket]");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.changeGameMode(gameMode);
                player.getAbilities().flying = flying;
                player.sendAbilitiesUpdate();
            });
            context.runOnClient(c -> {
                c.player.getAbilities().flying = flying;
                c.player.setVelocity(0, 0, 0);
            });
            context.waitTicks(5);
        }
    }
}
