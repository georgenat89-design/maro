package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.Vehicles;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;

/**
 * Vehicles: Free Drive drives a car of its own down the road while you stay where you were, and
 * steers; Drive moves you for real, turned like a car; each vehicle draws.
 */
final class VehiclesChecks {
    private VehiclesChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void set(Vehicles module, String name, String value) {
        ((ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow()).set(value);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        Vehicles module = ModuleManager.get(Vehicles.class);
        require(module != null, "Vehicles was not registered");
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 49 -60 30 0 0");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
            });
            context.runOnClient(c -> {
                c.player.getAbilities().flying = false;
                c.options.setPerspective(Perspective.FIRST_PERSON);
                module.getSettings().forEach(Setting::reset);
                set(module, "Mode", "Free Drive");
                module.setEnabled(true);
            });
            context.waitTicks(10);
            var start = context.computeOnClient(c -> c.player.getEntityPos());

            // Foot down for two seconds: the car goes, you stay.
            context.getInput().holdKey(o -> o.forwardKey);
            context.waitTicks(40);
            context.takeScreenshot("maro-vehicles-free-drive");
            context.getInput().releaseKey(o -> o.forwardKey);
            context.runOnClient(c -> {
                var car = module.carPosition();
                double moved = Math.hypot(car.x - start.x, car.z - start.z);
                double stood = c.player.getEntityPos().distanceTo(start);
                System.out.println("VEHICLES free drive moved " + Math.round(moved) + " blocks at " + Math.round(module.speed() * 3.6) + " km/h; you moved " + stood);
                require(moved > 10, "Free Drive's car hardly moved: " + moved);
                require(stood < 0.3, "Free Drive moved you, not just the car: " + stood);
                require(car.z > start.z, "Facing south, the car should go south: " + car);
            });

            // The wheel: holding D turns it right.
            float before = context.computeOnClient(c -> module.carYaw());
            context.getInput().holdKey(o -> o.forwardKey);
            context.getInput().holdKey(o -> o.rightKey);
            context.waitTicks(15);
            context.getInput().releaseKey(o -> o.rightKey);
            context.getInput().releaseKey(o -> o.forwardKey);
            float turned = context.computeOnClient(c -> module.carYaw()) - before;
            require(turned > 15, "Holding D did not turn the car right: " + turned);
            context.runOnClient(c -> set(module, "Vehicle", "Truck"));
            context.waitTicks(3);
            context.takeScreenshot("maro-vehicles-truck");

            // Getting out (sneak) puts the view back on you.
            context.getInput().holdKey(o -> o.sneakKey);
            context.waitTicks(3);
            context.getInput().releaseKey(o -> o.sneakKey);
            context.waitTicks(2);
            require(!context.computeOnClient(c -> module.isEnabled()), "Sneaking did not get you out");

            // Drive: you move for real, along the car.
            world.getServer().runCommand("tp @a 49 -60 30 0 0");
            context.waitTicks(3);
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                set(module, "Vehicle", "Sports Car");
                module.setEnabled(true);
            });
            var from = context.computeOnClient(c -> c.player.getEntityPos());
            context.getInput().holdKey(o -> o.forwardKey);
            context.waitTicks(30);
            context.takeScreenshot("maro-vehicles-drive");
            context.getInput().releaseKey(o -> o.forwardKey);
            context.runOnClient(c -> {
                double moved = c.player.getEntityPos().distanceTo(from);
                System.out.println("VEHICLES drive moved you " + Math.round(moved * 10) / 10.0 + " blocks");
                require(moved > 4, "Drive did not move you: " + moved);
            });
            context.runOnClient(c -> {
                set(module, "Vehicle", "Kart");
                set(module, "Camera", "Driver");
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-vehicles-kart-driver");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
            });
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.changeGameMode(gameMode);
                player.getAbilities().flying = flying;
                player.sendAbilitiesUpdate();
            });
            context.runOnClient(c -> c.player.getAbilities().flying = flying);
            context.waitTicks(3);
        }
    }
}
