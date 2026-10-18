package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.player.AutoReplenish;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Actual server inventories, native clicks and rejected transactions; no client-only count checks. */
public final class AutoReplenishChecks {
    private static volatile boolean recording, reject;
    private static final List<ClickSlotC2SPacket> sent = new CopyOnWriteArrayList<>();
    private static final List<ClickSlotC2SPacket> received = new CopyOnWriteArrayList<>();

    public static void outbound(Packet<?> packet) {
        if (recording && packet instanceof ClickSlotC2SPacket click
            && net.minecraft.client.MinecraftClient.getInstance().isOnThread()) sent.add(click);
    }

    public static boolean click(ServerPlayerEntity player, ClickSlotC2SPacket packet) {
        if (!recording || !player.getEntityWorld().getServer().isOnThread()) return false;
        received.add(packet);
        if (!reject) return false;
        player.currentScreenHandler.syncState();
        return true;
    }

    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private static Setting<?> setting(AutoReplenish module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
    private static void flag(AutoReplenish module, String name, boolean value) { ((BooleanSetting) setting(module, name)).set(value); }
    private static void await(ClientGameTestContext context, BooleanSupplier done, String why) {
        for (int i = 0; i < 120; i++) { if (done.getAsBoolean()) return; context.waitTick(); }
        throw new AssertionError(why);
    }
    private static int count(TestSingleplayerContext world, int slot) {
        return world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().getInventory().getStack(slot).getCount());
    }
    private static void change(TestSingleplayerContext world, Consumer<ServerPlayerEntity> setup) {
        world.getServer().runOnServer(s -> {
            var player = s.getPlayerManager().getPlayerList().getFirst();
            setup.accept(player);
            player.playerScreenHandler.syncState();
        });
    }
    private static void fixture(ClientGameTestContext context, TestSingleplayerContext world,
                                AutoReplenish module, Consumer<ServerPlayerEntity> setup) {
        reject = false; recording = false;
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.runOnClient(c -> {
            module.setEnabled(false);
            module.getSettings().forEach(Setting::reset);
            if (c.player.currentScreenHandler != c.player.playerScreenHandler) c.player.closeHandledScreen();
            c.setScreen(null);
            c.player.getInventory().setSelectedSlot(0);
        });
        change(world, p -> {
            p.getInventory().clear();
            p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
            p.getInventory().setSelectedSlot(0);
            setup.accept(p);
        });
        context.waitTicks(6);
        sent.clear(); received.clear(); recording = true;
    }
    private static void enable(ClientGameTestContext context, AutoReplenish module) {
        context.runOnClient(c -> module.setEnabled(true));
    }
    private static void cleanCursor(ClientGameTestContext context, TestSingleplayerContext world) {
        require(context.computeOnClient(c -> c.player.playerScreenHandler.getCursorStack().isEmpty()), "Refill left a client cursor item");
        require(world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().playerScreenHandler.getCursorStack().isEmpty()),
            "Refill left a server cursor item");
        require(context.computeOnClient(c -> c.player.getInventory().getSelectedSlot() == 0), "Refill changed the selected slot");
        require(sent.stream().noneMatch(p -> p.slot() < 0 || p.actionType() == SlotActionType.THROW), "Refill dropped an item");
    }
    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name)); return stack;
    }
    private static ItemStack tool(ServerPlayerEntity p, int level, int damage) {
        ItemStack stack = named(new ItemStack(Items.DIAMOND_PICKAXE), "Miner");
        stack.addEnchantment(p.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), level);
        stack.setDamage(damage); return stack;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var module = ModuleManager.get(AutoReplenish.class);
        require(module != null, "Auto Replenish was not registered");
        world.getServer().runCommand("gamemode survival @a");
        try {
            fixture(context, world, module, p -> {
                for (int i = 0; i < 36; i++) p.getInventory().setStack(i, new ItemStack(Items.DIRT, 64));
                p.getInventory().setStack(0, new ItemStack(Items.STONE, 8));
                p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            });
            enable(context, module);
            await(context, () -> count(world, 0) == 64 && count(world, 9) == 8, "Full inventory merge failed or lost overflow");
            context.waitTicks(6);
            require(sent.size() == 3 && received.size() == 3, "Full stack refill did not use one bounded native transaction: " + sent.size());
            cleanCursor(context, world);
            System.out.println("[auto-replenish-proof] full inventory: 8 + 64 -> 64 + 8; three native clicks; cursor empty; selected slot unchanged");

            fixture(context, world, module, p -> {
                p.getInventory().setStack(0, new ItemStack(Items.STONE, 9));
                p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            });
            enable(context, module); context.waitTicks(10);
            require(sent.isEmpty() && count(world, 0) == 9, "Refilled above threshold");
            change(world, p -> p.getInventory().getStack(0).setCount(8));
            await(context, () -> count(world, 0) == 64, "Threshold refill did not reach server");
            cleanCursor(context, world);

            fixture(context, world, module, p -> p.getInventory().setStack(0, new ItemStack(Items.ENDER_PEARL)));
            enable(context, module); context.waitTicks(3);
            change(world, p -> p.getInventory().setStack(0, ItemStack.EMPTY));
            context.waitTicks(15);
            change(world, p -> p.getInventory().setStack(9, new ItemStack(Items.ENDER_PEARL, 16)));
            await(context, () -> count(world, 0) == 16 && count(world, 9) == 0, "Empty-slot identity was forgotten while supplies were missing");
            cleanCursor(context, world);
            int stopped = sent.size(); context.waitTicks(10);
            require(sent.size() == stopped, "Empty storage caused repeated clicks");

            fixture(context, world, module, p -> {
                p.getInventory().setStack(0, new ItemStack(Items.TOTEM_OF_UNDYING));
                p.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                p.getInventory().setStack(9, new ItemStack(Items.TOTEM_OF_UNDYING));
                p.getInventory().setStack(10, new ItemStack(Items.TOTEM_OF_UNDYING));
            });
            context.runOnClient(c -> { flag(module, "Offhand", false); flag(module, "Unstackables", false); });
            enable(context, module); context.waitTicks(3);
            change(world, p -> { p.getInventory().setStack(0, ItemStack.EMPTY); p.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY); });
            context.waitTicks(8);
            require(sent.isEmpty(), "Disabled unstackable/offhand refill still ran");
            context.runOnClient(c -> { flag(module, "Unstackables", true); flag(module, "Offhand", true); });
            await(context, () -> count(world, 0) == 1 && count(world, 40) == 1, "Consumed totems did not replenish both hands");
            require(sent.size() == 2 && sent.getFirst().button() == 40, "Offhand did not get first priority or atomic swap");
            cleanCursor(context, world);

            fixture(context, world, module, p -> {
                p.getInventory().setStack(0, named(new ItemStack(Items.STONE, 8), "Keep"));
                p.getInventory().setStack(9, named(new ItemStack(Items.STONE, 64), "Other"));
                p.getInventory().setStack(10, new ItemStack(Items.STONE, 64));
                p.getInventory().setStack(11, named(new ItemStack(Items.STONE, 64), "Keep"));
            });
            enable(context, module);
            await(context, () -> count(world, 0) == 64, "Matching named stack was not replenished");
            require(count(world, 9) == 64 && count(world, 10) == 64 && count(world, 11) == 8, "Refill mixed different custom names");
            cleanCursor(context, world);

            fixture(context, world, module, p -> {
                ItemStack healing = new ItemStack(Items.POTION);
                healing.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.HEALING));
                ItemStack poison = new ItemStack(Items.POTION);
                poison.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.POISON));
                p.getInventory().setStack(0, healing.copy());
                p.getInventory().setStack(9, poison);
                p.getInventory().setStack(10, healing.copy());
            });
            enable(context, module); context.waitTicks(3);
            change(world, p -> p.getInventory().setStack(0, ItemStack.EMPTY));
            await(context, () -> count(world, 0) == 1 && count(world, 10) == 0, "Potion components were not preserved");
            require(count(world, 9) == 1, "Refill used the wrong potion");
            cleanCursor(context, world);

            fixture(context, world, module, p -> {
                p.getInventory().setStack(0, tool(p, 4, Items.DIAMOND_PICKAXE.getDefaultStack().getMaxDamage() - 1));
                p.getInventory().setStack(9, tool(p, 5, 0));
                p.getInventory().setStack(10, tool(p, 4, 0));
                p.getInventory().setStack(11, tool(p, 4, 100));
            });
            enable(context, module); context.waitTicks(8);
            require(sent.isEmpty(), "Worn tool replacement ignored its toggle");
            context.runOnClient(c -> flag(module, "Replace Worn Tools", true));
            await(context, () -> world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().getMainHandStack().getDamage() == 0),
                "Matching healthy tool was not selected");
            require(count(world, 9) == 1 && count(world, 10) == 1, "Tool swap lost a pickaxe");
            require(world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().getInventory().getStack(10).getDamage() > 1500),
                "Old worn tool was not returned to the matching spare's storage slot");
            cleanCursor(context, world);

            fixture(context, world, module, p -> {
                p.getInventory().setStack(0, new ItemStack(Items.DIRT, 64));
                p.getInventory().setStack(1, new ItemStack(Items.STONE, 8));
                p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            });
            context.runOnClient(c -> ((TextSetting) setting(module, "Excluded Items")).set("stone"));
            enable(context, module); context.waitTicks(8);
            require(sent.isEmpty(), "Excluded item was replenished");
            context.runOnClient(c -> { ((TextSetting) setting(module, "Excluded Items")).set(""); ((ModeSetting) setting(module, "Hotbar")).set("Held Slot"); });
            context.waitTicks(8);
            require(sent.isEmpty(), "Held Slot mode changed another hotbar slot");
            context.runOnClient(c -> ((ModeSetting) setting(module, "Hotbar")).set("All Slots"));
            await(context, () -> count(world, 1) == 64, "All Slots mode did not replenish another hotbar slot");
            cleanCursor(context, world);

            screensAndUse(context, world, module);
            rejection(context, world, module);
            dimensionReset(context, world, module);
            System.out.println("AUTO REPLENISH passed: server-accepted top-ups/overflow, threshold, persistent empty memory, offhand/unstackables, names/potions/enchants, healthiest tools, scope/filter, screens/cursor/item-use pauses, rejection backoff and dimension reset");
        } finally {
            reject = recording = false;
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                if (c.player.currentScreenHandler != c.player.playerScreenHandler) c.player.closeHandledScreen();
                c.setScreen(null);
            });
        }
    }

    private static void screensAndUse(ClientGameTestContext context, TestSingleplayerContext world, AutoReplenish module) {
        fixture(context, world, module, p -> {
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 8));
            p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
        });
        context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
        enable(context, module); context.waitTicks(8);
        require(sent.isEmpty(), "Refill ran during inventory editing");
        change(world, p -> p.getInventory().setStack(0, ItemStack.EMPTY));
        context.waitTicks(3); context.setScreen(() -> null); context.waitTicks(8);
        require(count(world, 0) == 0 && sent.isEmpty(), "Manual inventory clearing was undone");
        change(world, p -> p.getInventory().setStack(0, new ItemStack(Items.STONE, 8)));
        await(context, () -> count(world, 0) == 64, "Refill did not resume after inventory editing");

        fixture(context, world, module, p -> {
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 8));
            p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            p.getInventory().setStack(20, new ItemStack(Items.DIRT, 32));
        });
        context.runOnClient(c -> c.interactionManager.clickSlot(0, 20, 0, SlotActionType.PICKUP, c.player));
        context.waitTicks(3); sent.clear(); received.clear();
        enable(context, module); context.waitTicks(8);
        require(sent.isEmpty() && count(world, 0) == 8, "Refill stole an occupied cursor");
        context.runOnClient(c -> c.interactionManager.clickSlot(0, 20, 0, SlotActionType.PICKUP, c.player));
        await(context, () -> count(world, 0) == 64, "Refill did not resume after cursor was returned");
        cleanCursor(context, world);

        fixture(context, world, module, p -> {
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 8));
            p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            p.openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inv, who) ->
                GenericContainerScreenHandler.createGeneric9x3(id, inv), Text.literal("Refill pause")));
        });
        await(context, () -> context.computeOnClient(c -> c.player.currentScreenHandler != c.player.playerScreenHandler), "Container fixture did not open");
        enable(context, module); context.waitTicks(8);
        require(sent.isEmpty(), "Refill clicked an open container");
        context.runOnClient(c -> c.player.closeHandledScreen());
        await(context, () -> count(world, 0) == 64, "Refill did not resume after container close");
        cleanCursor(context, world);

        fixture(context, world, module, p -> {
            p.getInventory().setStack(0, new ItemStack(Items.BOW));
            p.getInventory().setStack(1, new ItemStack(Items.STONE, 8));
            p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
            p.getInventory().setStack(10, new ItemStack(Items.ARROW, 64));
        });
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitTicks(3);
        require(context.computeOnClient(c -> c.player.isUsingItem()), "Bow fixture did not start charging");
        enable(context, module); context.waitTicks(12);
        require(sent.isEmpty() && count(world, 1) == 8, "Refill interrupted item use");
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        await(context, () -> count(world, 1) == 64, "Refill did not resume after item use");
        cleanCursor(context, world);
    }

    private static void rejection(ClientGameTestContext context, TestSingleplayerContext world, AutoReplenish module) {
        fixture(context, world, module, p -> {
            p.getInventory().setStack(0, new ItemStack(Items.STONE, 8));
            p.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
        });
        reject = true;
        enable(context, module); context.waitTicks(40);
        require(count(world, 0) == 8 && count(world, 9) == 64, "Rejected clicks changed server inventory");
        require(sent.size() >= 6 && sent.size() <= 9, "Rejected refills did not stop after three transactions: " + sent.size());
        int backedOff = sent.size(); context.waitTicks(20);
        require(sent.size() == backedOff, "Refill spammed the server during rejection backoff");
        cleanCursor(context, world);
        reject = false;
        await(context, () -> count(world, 0) == 64, "Refill did not recover after rejection backoff");
        cleanCursor(context, world);
        System.out.println("[auto-replenish-rejection-proof] rejected clicks=" + backedOff + "; no drops/cursor leftovers; automatic recovery passed");
    }

    private static void dimensionReset(ClientGameTestContext context, TestSingleplayerContext world, AutoReplenish module) {
        fixture(context, world, module, p -> p.getInventory().setStack(0, new ItemStack(Items.ENDER_PEARL)));
        enable(context, module); context.waitTicks(3);
        change(world, p -> p.getInventory().setStack(0, ItemStack.EMPTY));
        context.waitTicks(6);
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
        await(context, () -> context.computeOnClient(c -> c.world.getRegistryKey() == net.minecraft.world.World.NETHER), "Dimension fixture did not change world");
        change(world, p -> p.getInventory().setStack(9, new ItemStack(Items.ENDER_PEARL, 16)));
        context.waitTicks(12);
        require(count(world, 0) == 0 && sent.isEmpty(), "Old-world empty-slot memory affected the new dimension");
        world.getServer().runCommand("execute in minecraft:overworld run tp @a 0 80 0");
        await(context, () -> context.computeOnClient(c -> c.world.getRegistryKey() == net.minecraft.world.World.OVERWORLD), "Return dimension did not synchronize");
        context.runOnClient(c -> module.setEnabled(false));
    }
}
