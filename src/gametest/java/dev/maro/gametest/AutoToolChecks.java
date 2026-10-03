package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoTool;
import dev.maro.setting.BooleanSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

/** Exercises the production mining hooks, tool choice and hotbar packet synchronization. */
final class AutoToolChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(AutoTool.class);
        require(module != null, "Auto Tool was not registered");
        var switchBack = (BooleanSetting) module.getSettings().stream()
            .filter(s -> s.getName().equals("Switch Back")).findFirst().orElseThrow();
        var protect = (BooleanSetting) module.getSettings().stream()
            .filter(s -> s.getName().equals("Protect Tools")).findFirst().orElseThrow();
        var pos = context.computeOnClient(client -> client.player.getBlockPos().add(0, 1, 2));
        var originalBlock = world.getServer().computeOnServer(server -> server.getOverworld().getBlockState(pos));
        var original = world.getServer().computeOnServer(server -> {
            var player = server.getPlayerManager().getPlayerList().getFirst();
            ItemStack[] stacks = new ItemStack[9];
            for (int slot = 0; slot < 9; slot++) stacks[slot] = player.getInventory().getStack(slot).copy();
            return stacks;
        });
        int originalSlot = context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());
        var gameMode = world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList()
            .getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                player.changeGameMode(GameMode.SURVIVAL);
                var inventory = player.getInventory();
                for (int slot = 0; slot < 9; slot++) inventory.setStack(slot, ItemStack.EMPTY);
                inventory.setStack(0, new ItemStack(Items.STONE));
                inventory.setStack(1, new ItemStack(Items.DIAMOND_PICKAXE));
                inventory.setStack(2, new ItemStack(Items.GOLDEN_PICKAXE));
                inventory.setStack(3, new ItemStack(Items.DIAMOND_AXE));
                inventory.setStack(4, new ItemStack(Items.DIAMOND_SHOVEL));
                var efficiency = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(Enchantments.EFFICIENCY);
                var iron = new ItemStack(Items.IRON_PICKAXE);
                iron.addEnchantment(efficiency, 5);
                inventory.setStack(5, iron);
                var worn = new ItemStack(Items.DIAMOND_PICKAXE);
                worn.addEnchantment(efficiency, 5);
                worn.setDamage(worn.getMaxDamage() - 1);
                inventory.setStack(6, worn);
                inventory.setStack(8, new ItemStack(Items.BOW));
                server.getOverworld().setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                switchBack.set(false); module.setEnabled(true);
                client.player.getInventory().setSelectedSlot(0);
                client.interactionManager.attackBlock(pos, Direction.NORTH);
                require(client.player.getInventory().getSelectedSlot() == 1,
                    "First mining action did not select a pickaxe able to harvest obsidian");
            });
            context.waitTicks(3);
            require(world.getServer().computeOnServer(server -> server.getPlayerManager().getPlayerList()
                .getFirst().getInventory().getSelectedSlot()) == 1, "Auto Tool did not synchronize its slot to the server");
            world.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos, Blocks.STONE.getDefaultState()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                client.player.getInventory().setSelectedSlot(0);
                client.interactionManager.attackBlock(pos, Direction.NORTH);
                require(client.player.getInventory().getSelectedSlot() == 5,
                    "Efficiency or durability protection was ignored");
                protect.set(false);
                client.interactionManager.updateBlockBreakingProgress(pos, Direction.NORTH);
                require(client.player.getInventory().getSelectedSlot() == 6,
                    "Disabling durability protection did not allow the faster worn tool");
                protect.set(true);
                client.interactionManager.cancelBlockBreaking();
                module.setEnabled(false);
                require(client.player.getInventory().getSelectedSlot() == 6, "Switch Back off still restored the old slot");
            });
            world.getServer().runOnServer(server -> server.getOverworld().setBlockState(pos, Blocks.OAK_LOG.getDefaultState()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                switchBack.set(true); module.setEnabled(true);
                client.player.getInventory().setSelectedSlot(0);
                client.interactionManager.updateBlockBreakingProgress(pos, Direction.NORTH);
                require(client.player.getInventory().getSelectedSlot() == 3, "Ongoing mining did not select the axe");
                client.options.attackKey.setPressed(false);
                module.onTick();
                require(client.player.getInventory().getSelectedSlot() == 0, "Mouse release did not restore the previous slot");
                client.interactionManager.attackBlock(pos, Direction.NORTH);
                client.player.getInventory().setSelectedSlot(8);
                module.setEnabled(false);
                require(client.player.getInventory().getSelectedSlot() == 8, "Switch Back overwrote a manual slot change");
                client.interactionManager.cancelBlockBreaking();
            });
        } finally {
            context.runOnClient(client -> { module.setEnabled(false); switchBack.reset(); protect.reset(); });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                for (int slot = 0; slot < 9; slot++) player.getInventory().setStack(slot, original[slot]);
                player.changeGameMode(gameMode);
                server.getOverworld().setBlockState(pos, originalBlock);
            });
            context.runOnClient(client -> client.player.getInventory().setSelectedSlot(originalSlot));
            context.waitTicks(5);
        }
    }
}
