package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoTrident;
import dev.maro.setting.NumberSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/** Verifies actual server-accepted throws with held mouse input, not just client prediction. */
final class AutoTridentChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int throwsAccepted(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList().getFirst()
            .getStatHandler().getStat(Stats.USED, Items.TRIDENT));
    }

    private static void equip(ClientGameTestContext context, TestSingleplayerContext world, boolean offhand) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.getInventory().setStack(player.getInventory().getSelectedSlot(),
                new ItemStack(offhand ? Items.STONE : Items.TRIDENT));
            player.setStackInHand(Hand.OFF_HAND, offhand ? new ItemStack(Items.TRIDENT) : ItemStack.EMPTY);
        });
        context.waitTicks(5);
        require(context.computeOnClient(client -> (offhand ? client.player.getOffHandStack()
            : client.player.getMainHandStack()).isOf(Items.TRIDENT)), "Trident fixture did not synchronize");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(AutoTrident.class);
        require(module != null, "Auto Trident was not registered");
        var speed = (NumberSetting) module.getSettings().stream()
            .filter(s -> s.getName().equals("Speed")).findFirst().orElseThrow();
        var original = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            return new ItemStack[]{player.getMainHandStack().copy(), player.getOffHandStack().copy()};
        });
        var gameMode = world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList()
            .getFirst().interactionManager.getGameMode());
        float pitch = context.computeOnClient(client -> client.player.getPitch());
        try {
            world.getServer().runCommand("gamemode creative @a");
            context.runOnClient(client -> { client.player.setPitch(-70); speed.set(10.0); module.setEnabled(true); });
            equip(context, world, false);
            int before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            require(throwsAccepted(world) - before >= 3, "Fast main-hand mode did not repeat server-accepted throws");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);
            int stopped = throwsAccepted(world);
            context.waitTicks(30);
            require(throwsAccepted(world) == stopped, "Trident kept throwing after mouse release");

            context.runOnClient(client -> speed.set(1.0));
            equip(context, world, false);
            before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            require(throwsAccepted(world) - before == 1, "Slow speed did not hold the charge longer");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);

            context.runOnClient(client -> speed.set(10.0));
            equip(context, world, true);
            before = throwsAccepted(world);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(45);
            int offhandThrows = throwsAccepted(world) - before;
            // The production harness runs client/server tick clocks independently.
            require(offhandThrows >= 2, "Offhand trident did not repeat server-accepted throws: " + offhandThrows);
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(5);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(player.getInventory().getSelectedSlot(), new ItemStack(Items.BOW));
                player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            });
            context.waitTicks(5);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(35);
            require(context.computeOnClient(client -> client.player.isUsingItem()
                && client.player.getActiveItem().isOf(Items.BOW) && client.player.getItemUseTime() >= 20),
                "Auto Trident interrupted a bow charge");
        } finally {
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(client -> { module.setEnabled(false); speed.reset(); client.player.setPitch(pitch); });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(player.getInventory().getSelectedSlot(), original[0]);
                player.setStackInHand(Hand.OFF_HAND, original[1]);
                player.changeGameMode(gameMode);
            });
            context.waitTicks(5);
        }
    }
}
