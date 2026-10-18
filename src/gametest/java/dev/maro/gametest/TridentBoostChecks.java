package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.TridentBoost;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Exercises manual native item methods, actual impulses and removal of automatic charging. */
public final class TridentBoostChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(dev.maro.module.Module module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static boolean release(MinecraftClient client, int ticks, Vec3d momentum) {
        client.player.setVelocity(momentum);
        client.player.setOnGround(false);
        var stack = client.player.getMainHandStack();
        return stack.getItem().onStoppedUsing(stack, client.world, client.player, stack.getMaxUseTime(client.player) - ticks);
    }

    private static Vec3d launch(MinecraftClient client, Vec3d momentum) {
        require(release(client, 10, momentum), "Native Riptide did not launch");
        return client.player.getVelocity();
    }

    private static void equal(Vec3d actual, Vec3d expected, String message) {
        require(actual.squaredDistanceTo(expected) < 1.0e-10, message + ": " + actual + " vs " + expected);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(TridentBoost.class);
        require(module != null, "Trident Boost was not registered");
        require(ModuleManager.getByName("Auto Trident") == null, "Auto Trident was not removed");
        require(ModuleManager.getByName("Trident Util") == null && ModuleManager.getByName("God Trident") == null,
            "Removed trident module was registered");
        var boost = (NumberSetting) setting(module, "Boost");
        var outOfWater = (BooleanSetting) setting(module, "Out Of Water");
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("weather clear");
        world.getServer().runCommand("tp @a 200.5 95 200.5 0 -90");
        world.getServer().runCommand("item replace entity @a weapon.offhand with air");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:trident[minecraft:enchantments={\"minecraft:riptide\":3}]");
        world.getServer().runOnServer(s -> {
            var player = s.getPlayerManager().getPlayerList().getFirst();
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        });
        context.waitTicks(8);
        try {
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                require(boost.get() == 2.0 && outOfWater.get(), "Anubis defaults changed");
                require(!c.player.isTouchingWaterOrRain(), "Dry fixture was wet");
                require(Items.TRIDENT.use(c.world, c.player, Hand.MAIN_HAND) == ActionResult.FAIL
                    && !release(c, 10, Vec3d.ZERO), "Disabled module changed dry use/release");
                module.setEnabled(true);
                require(Items.TRIDENT.use(c.world, c.player, Hand.MAIN_HAND) == ActionResult.CONSUME,
                    "Out Of Water did not permit native dry charging");
                c.player.stopUsingItem();
                require(!release(c, 9, Vec3d.ZERO), "Boost shortened the native charge");
                c.player.setYaw(31);
                c.player.setPitch(-27);
                boost.set(1.0);
                Vec3d baseline = launch(c, Vec3d.ZERO);
                require(Math.abs(baseline.x) > .1 && Math.abs(baseline.y) > .1 && Math.abs(baseline.z) > .1,
                    "Impulse fixture did not cover all axes");
                for (double multiplier : new double[]{.1, 2, 5, 10}) {
                    boost.set(multiplier);
                    equal(launch(c, Vec3d.ZERO), baseline.multiply(multiplier), "Incorrect impulse at " + multiplier);
                }
                Vec3d momentum = new Vec3d(.3, -.2, .7);
                boost.set(2.0);
                equal(launch(c, momentum), momentum.add(baseline.multiply(2)), "Boost multiplied existing momentum");
                outOfWater.set(false);
                require(Items.TRIDENT.use(c.world, c.player, Hand.MAIN_HAND) == ActionResult.FAIL
                    && !release(c, 10, Vec3d.ZERO), "Out Of Water=false retained dry use/release");
                outOfWater.set(true);
                c.player.setVelocity(Vec3d.ZERO);
                c.player.setYaw(0);
                c.player.setPitch(-90);
            });
            require(world.getServer().computeOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                var stack = player.getMainHandStack();
                return Items.TRIDENT.use(player.getEntityWorld(), player, Hand.MAIN_HAND) == ActionResult.FAIL
                    && !Items.TRIDENT.onStoppedUsing(stack, player.getEntityWorld(), player, stack.getMaxUseTime(player) - 10);
            }), "Boost changed integrated-server dry-use rules");
            world.getServer().runCommand("fill 196 80 196 204 83 204 minecraft:water");
            world.getServer().runCommand("tp @a 200.5 82 200.5 31 -27");
            context.waitTicks(8);
            Vec3d wetBaseline = context.computeOnClient(c -> {
                require(c.player.isTouchingWaterOrRain(), "Wet fixture did not synchronize");
                outOfWater.set(false);
                boost.set(1.0);
                Vec3d baseline = launch(c, Vec3d.ZERO);
                boost.set(2.0);
                equal(launch(c, Vec3d.ZERO), baseline.multiply(2), "Wet boost failed with Out Of Water=false");
                return baseline;
            });
            Vec3d serverImpulse = world.getServer().computeOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.setYaw(31);
                player.setPitch(-27);
                player.setOnGround(false);
                player.setVelocity(Vec3d.ZERO);
                var stack = player.getMainHandStack();
                require(Items.TRIDENT.onStoppedUsing(stack, player.getEntityWorld(), player, stack.getMaxUseTime(player) - 10),
                    "Native server wet release failed");
                return player.getVelocity();
            });
            equal(serverImpulse, wetBaseline, "Boost modified integrated-server Riptide strength");
            context.runOnClient(c -> {
                module.setEnabled(false);
                equal(launch(c, Vec3d.ZERO), wetBaseline, "Disabling Boost retained multiplied wet velocity");
                c.player.setVelocity(Vec3d.ZERO);
            });
            world.getServer().runCommand("tp @a 200.5 95 200.5 0 -90");
            context.waitTicks(25);
            context.runOnClient(c -> require(Items.TRIDENT.use(c.world, c.player, Hand.MAIN_HAND) == ActionResult.FAIL
                && !release(c, 10, Vec3d.ZERO), "Disabling Boost retained dry use/release"));
            System.out.println("TRIDENT BOOST passed: native impulses 0.1/1/2/5/10x, momentum, wet/dry toggles, ten-tick charge, disable cleanup and native integrated-server rules");
        } finally {
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.player.setVelocity(Vec3d.ZERO);
            });
            world.getServer().runCommand("fill 196 80 196 204 83 204 minecraft:air");
            world.getServer().runCommand("weather clear");
        }
    }
}
