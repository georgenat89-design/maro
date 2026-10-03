package dev.maro.gametest;

import com.google.gson.JsonPrimitive;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.combat.AutoTotem;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import java.util.ArrayList;

/** Exercises the production inventory interaction path and checks the server's inventory, not just prediction. */
final class AutoTotemChecks {
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void setting(AutoTotem module, String name, JsonPrimitive value) {
        module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow().fromJson(value);
    }
    private static void prepare(ClientGameTestContext context, TestSingleplayerContext world, int source) {
        context.runOnClient(client -> ModuleManager.get(AutoTotem.class).setEnabled(false));
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            // Fill every inventory slot so the swap cannot depend on an empty slot to preserve the shield.
            for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
            player.getInventory().setStack(0, new ItemStack(Items.STONE, 64));
            if (source >= 0) player.getInventory().setStack(source, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.getInventory().setStack(40, new ItemStack(Items.SHIELD));
            player.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
            player.setHealth(20); player.setAbsorptionAmount(0);
            player.playerScreenHandler.sendContentUpdates();
        });
        context.waitTicks(5);
        context.runOnClient(client -> {
            var module = ModuleManager.get(AutoTotem.class);
            module.getSettings().forEach(s -> s.reset());
            client.player.getInventory().setSelectedSlot(0);
            require(client.player.getOffHandStack().isOf(Items.SHIELD), "Inventory fixture did not synchronize");
        });
    }
    private static void assertSwap(TestSingleplayerContext world, int source) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            require(player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING), "Server rejected the totem swap");
            require(player.getInventory().getStack(source).isOf(Items.SHIELD), "Replaced offhand item was lost or moved to the wrong slot");
            require(player.getInventory().getStack(0).isOf(Items.STONE) && player.getInventory().getStack(0).getCount() == 64,
                "Auto Totem changed the selected hotbar stack");
            require(player.playerScreenHandler.getCursorStack().isEmpty(), "Swap left a cursor item");
        });
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(AutoTotem.class);
        require(module != null, "Auto Totem was not registered");
        var original = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            var stacks = new ArrayList<ItemStack>();
            for (int i = 0; i < player.getInventory().size(); i++) stacks.add(player.getInventory().getStack(i).copy());
            return stacks;
        });
        double originalAbsorptionLimit = world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList().getFirst()
            .getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_ABSORPTION).getBaseValue());
        try {
            prepare(context, world, 12);
            context.runOnClient(client -> {
                setting(module, "No Delay", new JsonPrimitive(false));
                setting(module, "Swap Delay", new JsonPrimitive(2));
                module.setEnabled(true);
                module.onTick(); module.onTick();
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Swap ignored the two-tick delay");
                module.onTick();
            });
            context.waitTicks(5); assertSwap(world, 12);
            // Simulate consumption, then verify the next totem is equipped from a different slot.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(40, ItemStack.EMPTY);
                player.getInventory().setStack(15, new ItemStack(Items.TOTEM_OF_UNDYING));
                player.playerScreenHandler.sendContentUpdates();
            });
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                require(player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && player.getInventory().getStack(15).isEmpty(),
                    "Auto Totem failed to refill after consumption");
            });

            prepare(context, world, 4);
            context.runOnClient(client -> {
                setting(module, "Swap Delay", new JsonPrimitive(20)); setting(module, "Retry Delay", new JsonPrimitive(40));
                setting(module, "Strength", new JsonPrimitive(1)); module.setEnabled(true); module.onTick();
                require(client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING), "No Delay failed to override all timing settings");
            });
            context.waitTicks(8); assertSwap(world, 4);

            prepare(context, world, 7);
            context.runOnClient(client -> {
                setting(module, "No Delay", new JsonPrimitive(false)); setting(module, "Strength", new JsonPrimitive(1));
                module.setEnabled(true);
                for (int i = 0; i < 9; i++) module.onTick();
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Low strength ignored its reaction wait");
                setting(module, "Strength", new JsonPrimitive(10)); module.onTick();
                require(client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING), "Maximum strength did not react immediately");
            });
            context.waitTicks(5); assertSwap(world, 7);

            prepare(context, world, -1);
            context.runOnClient(client -> module.setEnabled(true));
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                require(player.getOffHandStack().isOf(Items.SHIELD) && player.playerScreenHandler.getCursorStack().isEmpty(),
                    "Auto Totem changed inventory without an available totem");
            });

            prepare(context, world, 13);
            context.runOnClient(client -> {
                setting(module, "Mode", new JsonPrimitive("Low Health")); module.setEnabled(true);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Low Health mode swapped at full health"); module.setEnabled(false);
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_ABSORPTION).setBaseValue(8);
                player.setAbsorptionAmount(8); player.setHealth(8);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                require(client.player.getAbsorptionAmount() == 8 && client.player.getHealth() == 8, "Absorption fixture did not synchronize");
                module.setEnabled(true);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Absorption was not included in the health threshold");
                setting(module, "Include Absorption", new JsonPrimitive(false));
            });
            context.waitTicks(8); assertSwap(world, 13);

            prepare(context, world, 14);
            context.runOnClient(client -> {
                setting(module, "Inventory Only", new JsonPrimitive(true)); module.setEnabled(true);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Inventory Only swapped with the screen closed");
                client.setScreen(new InventoryScreen(client.player));
            });
            context.waitTicks(8); assertSwap(world, 14);
            context.setScreen(() -> null);

            prepare(context, world, 16);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, owner) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inventory), Text.literal("Totem test container")));
            });
            context.waitForScreen(GenericContainerScreen.class);
            context.runOnClient(client -> module.setEnabled(true));
            context.waitTicks(8);
            world.getServer().runOnServer(server -> require(server.getPlayerManager().getPlayerList().getFirst().getOffHandStack().isOf(Items.SHIELD),
                "Auto Totem touched inventory while a container was open"));
            context.runOnClient(client -> client.player.closeHandledScreen());
            context.waitForScreen(null);

            prepare(context, world, 17);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.playerScreenHandler.setCursorStack(new ItemStack(Items.DIAMOND, 3)); player.playerScreenHandler.sendContentUpdates();
            });
            context.waitTicks(4);
            context.runOnClient(client -> module.setEnabled(true));
            context.waitTicks(8);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                require(player.getOffHandStack().isOf(Items.SHIELD) && player.playerScreenHandler.getCursorStack().getCount() == 3,
                    "Auto Totem interfered with a cursor item");
            });

            prepare(context, world, 18);
            context.runOnClient(client -> {
                module.setEnabled(true);
                ItemStack previous = client.player.getInventory().getStack(0).copy();
                client.player.getInventory().setStack(0, new ItemStack(Items.GOLDEN_APPLE));
                client.player.setCurrentHand(Hand.MAIN_HAND);
                require(client.player.isUsingItem(), "Item use fixture did not start");
                for (int i = 0; i < 5; i++) module.onTick();
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Auto Totem swapped during item use");
                client.player.clearActiveItem(); client.player.getInventory().setStack(0, previous); module.setEnabled(false);
            });

            prepare(context, world, 19);
            context.runOnClient(client -> {
                setting(module, "Empty Offhand Only", new JsonPrimitive(true)); module.setEnabled(true);
            });
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(client.player.getOffHandStack().isOf(Items.SHIELD), "Empty Offhand Only replaced the shield");
                client.setScreen(new ClickGuiScreen());
                ((ClickGuiScreen)client.currentScreen).openModuleSettings(module);
            });
            context.waitTicks(5); context.takeScreenshot("maro-auto-totem-settings");
            context.setScreen(() -> null);
        } finally {
            context.runOnClient(client -> { module.setEnabled(false); module.getSettings().forEach(s -> s.reset()); });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                for (int i = 0; i < original.size(); i++) player.getInventory().setStack(i, original.get(i));
                player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_ABSORPTION).setBaseValue(originalAbsorptionLimit);
                player.playerScreenHandler.setCursorStack(ItemStack.EMPTY); player.setHealth(20); player.setAbsorptionAmount(0);
                player.playerScreenHandler.sendContentUpdates();
            });
            context.waitTicks(5);
        }
    }
}
