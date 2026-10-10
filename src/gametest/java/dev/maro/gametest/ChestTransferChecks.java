package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.OrderDropper;
import dev.maro.module.impl.player.ChestDumper;
import dev.maro.module.impl.player.ChestStealer;
import dev.maro.module.impl.player.ChestTransfer;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/** Real container interactions and server inventories, including rejected native shift-clicks. */
public final class ChestTransferChecks {
    private record Click(int tick, int slot, Item item) { }
    private static final List<Click> clicks = new CopyOnWriteArrayList<>();
    private static final List<Integer> sentTicks = new CopyOnWriteArrayList<>();
    private static volatile boolean recording;
    private static volatile int reject;

    public static void outbound(net.minecraft.network.packet.Packet<?> packet) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (recording && packet instanceof ClickSlotC2SPacket click && click.actionType() == SlotActionType.QUICK_MOVE
                && client.isOnThread() && client.player != null && client.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            sentTicks.add(client.player.age);
        }
    }

    public static boolean click(ServerPlayerEntity player, ClickSlotC2SPacket packet) {
        if (!recording || !player.getEntityWorld().getServer().isOnThread()
                || packet.actionType() != SlotActionType.QUICK_MOVE
                || !(player.currentScreenHandler instanceof GenericContainerScreenHandler handler)
                || packet.syncId() != handler.syncId) return false;
        clicks.add(new Click(player.getEntityWorld().getServer().getTicks(), packet.slot(), handler.getSlot(packet.slot()).getStack().getItem()));
        if (reject <= 0) return false;
        reject--;
        handler.syncState();
        return true;
    }

    private static void require(boolean okay, String why) { if (!okay) throw new AssertionError(why); }
    private static Setting<?> setting(ChestTransfer module, String name) { return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow(); }
    private static void flag(ChestTransfer module, String name, boolean value) { ((BooleanSetting) setting(module, name)).set(value); }
    private static void number(ChestTransfer module, String name, double value) { ((NumberSetting) setting(module, name)).set(value); }
    private static void await(ClientGameTestContext context, BooleanSupplier done, String why) {
        for (int i = 0; i < 160; i++) { if (done.getAsBoolean()) return; context.waitTick(); }
        throw new AssertionError(why);
    }
    private static String at(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }
    private static Inventory chest(net.minecraft.server.MinecraftServer server, BlockPos p) { return (Inventory) server.getOverworld().getBlockEntity(p); }
    private static int inventoryCount(ServerPlayerEntity player, Item item) {
        int n = 0; for (int i = 0; i < 36; i++) if (player.getInventory().getStack(i).isOf(item)) n += player.getInventory().getStack(i).getCount(); return n;
    }
    private static int chestCount(TestSingleplayerContext world, BlockPos p, Item item) {
        return world.getServer().computeOnServer(s -> {
            int n = 0;
            for (BlockPos pos : List.of(p, p.east())) {
                var block = s.getOverworld().getBlockEntity(pos);
                if (!(block instanceof Inventory inv)) continue;
                for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) n += inv.getStack(i).getCount();
            }
            return n;
        });
    }
    private static void close(ClientGameTestContext context) {
        context.runOnClient(c -> {
            ModuleManager.get(ChestStealer.class).setEnabled(false);
            ModuleManager.get(ChestDumper.class).setEnabled(false);
            if (c.player.currentScreenHandler != c.player.playerScreenHandler) c.player.closeHandledScreen();
        });
        context.waitTicks(4);
        clicks.clear(); sentTicks.clear(); reject = 0;
    }
    private static void clear(TestSingleplayerContext world, BlockPos p) {
        world.getServer().runOnServer(s -> {
            chest(s, p).clear();
            if (s.getOverworld().getBlockEntity(p.east()) instanceof Inventory inv) inv.clear();
            var player = s.getPlayerManager().getPlayerList().getFirst();
            player.getInventory().clear();
            for (EquipmentSlot slot : EquipmentSlot.values()) player.equipStack(slot, ItemStack.EMPTY);
            player.playerScreenHandler.syncState();
        });
    }
    private static void open(ClientGameTestContext context, BlockPos p) {
        context.runOnClient(c -> c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(p), Direction.NORTH, p, false)));
        await(context, () -> context.computeOnClient(c -> c.player.currentScreenHandler instanceof GenericContainerScreenHandler), "Physical chest did not open");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        ChestStealer stealer = ModuleManager.get(ChestStealer.class);
        ChestDumper dumper = ModuleManager.get(ChestDumper.class);
        require(stealer != null && dumper != null, "Chest modules not registered");
        var before = context.computeOnClient(c -> c.player.getEntityPos());
        var mode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        BlockPos p = BlockPos.ofFloored(before).add(2, 0, 2);
        world.getServer().runCommand("gamemode creative @a");
        world.getServer().runCommand("setblock " + at(p) + " chest[facing=north,type=left]");
        world.getServer().runCommand("setblock " + at(p.east()) + " chest[facing=north,type=right]");
        world.getServer().runOnServer(s -> {
            s.getCommandManager().getDispatcher().register(CommandManager.literal("chestfixture").executes(command -> {
                SimpleInventory inv = new SimpleInventory(27); inv.setStack(0, new ItemStack(Items.DIAMOND, 16));
                command.getSource().getPlayer().openHandledScreen(new SimpleNamedScreenHandlerFactory((id, inventory, player) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, id, inventory, inv, 3), Text.literal("Auction House")));
                return 1;
            }));
            s.getCommandManager().getDispatcher().register(CommandManager.literal("orders").executes(command -> 1));
        });
        context.waitTicks(8);
        recording = true;
        try {
            context.runOnClient(c -> {
                for (ChestTransfer module : List.of(stealer, dumper)) {
                    module.getSettings().forEach(Setting::reset);
                    flag(module, "Auto Close", false); number(module, "Min Delay", 0); number(module, "Max Delay", 0); number(module, "Initial Delay", 0);
                }
            });

            clear(world, p);
            world.getServer().runOnServer(s -> {
                chest(s, p).setStack(0, new ItemStack(Items.STONE, 64));
                chest(s, p).setStack(1, new ItemStack(Items.TOTEM_OF_UNDYING));
                chest(s, p).setStack(2, new ItemStack(Items.SHULKER_BOX));
                chest(s, p.east()).setStack(26, new ItemStack(Items.DIAMOND, 64));
            });
            context.runOnClient(c -> stealer.setEnabled(true)); open(context, p);
            await(context, () -> chestCount(world, p, Items.STONE) == 0 && chestCount(world, p, Items.DIAMOND) == 0, "Stealer did not empty both halves");
            require(clicks.stream().map(Click::item).toList().equals(List.of(Items.SHULKER_BOX, Items.TOTEM_OF_UNDYING, Items.DIAMOND, Items.STONE)), "Priority order changed: " + clicks);
            require(sentTicks.size() == 4 && sentTicks.stream().collect(java.util.stream.Collectors.groupingBy(tick -> tick, java.util.stream.Collectors.counting())).values().stream().allMatch(n -> n <= 2), "Per-client-tick slot limit exceeded: " + sentTicks);
            require(world.getServer().computeOnServer(s -> inventoryCount(s.getPlayerManager().getPlayerList().getFirst(), Items.DIAMOND)) == 64, "Server did not receive stolen items");
            context.takeScreenshot("maro-chest-stealer-priority");
            context.runOnClient(c -> flag(stealer, "Auto Close", true));
            await(context, () -> context.computeOnClient(c -> c.currentScreen == null), "Stealer did not auto-close");
            System.out.println("[chest-stealer-proof] native double chest,valuable-item priority,two clicks per tick,server inventory and auto-close");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 7));
                player.getInventory().setStack(9, new ItemStack(Items.STONE, 64));
                player.getInventory().setStack(10, new ItemStack(Items.DIRT, 12));
                player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                player.playerScreenHandler.syncState();
            });
            context.waitTicks(4);
            context.runOnClient(c -> { BuilderChestDelay.begin(10); dumper.setEnabled(true); }); open(context, p);
            context.waitTicks(5);
            require(clicks.isEmpty(), "Dumper clicked before server supplied chest contents");
            for (int i = 0; i < 12; i++) { context.runOnClient(c -> BuilderChestDelay.step()); context.waitTick(); }
            context.runOnClient(c -> BuilderChestDelay.end());
            await(context, () -> chestCount(world, p, Items.STONE) == 64 && chestCount(world, p, Items.DIRT) == 12, "Dumper did not store main inventory");
            require(world.getServer().computeOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                return player.getInventory().getStack(0).getCount() == 7 && player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET) && player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
            }), "Dumper took hotbar, armour or offhand");
            context.takeScreenshot("maro-chest-dumper-keeps-hotbar");
            context.runOnClient(c -> flag(dumper, "Dump Hotbar", true));
            await(context, () -> chestCount(world, p, Items.DIAMOND) == 7, "Dump Hotbar did not move hotbar");
            System.out.println("[chest-dumper-proof] delayed initial contents,main inventory,optional hotbar,armour/offhand retained");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.COBBLESTONE, 64));
                player.getInventory().setStack(9, new ItemStack(Items.DIAMOND, 63));
                chest(s, p).setStack(0, new ItemStack(Items.STONE, 64)); chest(s, p).setStack(1, new ItemStack(Items.DIAMOND, 5));
                player.playerScreenHandler.syncState();
            });
            context.waitTicks(4);
            context.runOnClient(c -> { flag(stealer, "Auto Close", false); flag(stealer, "Smart Prioritize", false); stealer.setEnabled(true); }); open(context, p);
            await(context, () -> chestCount(world, p, Items.DIAMOND) == 4, "Blocked first stack prevented a later matching stack");
            context.waitTicks(5);
            require(chestCount(world, p, Items.STONE) == 64 && clicks.size() == 1, "Full inventory caused repeated or impossible clicks: " + clicks);
            require(world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().currentScreenHandler.getCursorStack().isEmpty()), "Transfer left items on cursor");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                for (int i = 0; i < 36; i++) player.getInventory().setStack(i, new ItemStack(Items.COBBLESTONE, 64));
                ItemStack kept = new ItemStack(Items.DIAMOND, 63); kept.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Keep")); player.getInventory().setStack(9, kept);
                ItemStack different = new ItemStack(Items.DIAMOND, 64); different.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Other")); chest(s, p).setStack(0, different);
                ItemStack matching = kept.copy(); matching.setCount(1); chest(s, p).setStack(1, matching);
                player.playerScreenHandler.syncState();
            });
            context.waitTicks(4); context.runOnClient(c -> stealer.setEnabled(true)); open(context, p);
            await(context, () -> chestCount(world, p, Items.DIAMOND) == 64, "Component-aware merge failed");
            context.waitTicks(5); require(clicks.size() == 1, "Incompatible components were clicked: " + clicks);
            System.out.println("[chest-capacity-proof] blocked slots skipped;only one item fitted;different names never merged;cursor empty");
            close(context);

            clear(world, p);
            context.runOnClient(c -> { stealer.setEnabled(true); c.getNetworkHandler().sendChatCommand("chestfixture"); });
            await(context, () -> context.computeOnClient(c -> c.player.currentScreenHandler instanceof GenericContainerScreenHandler), "Fixture menu did not open");
            context.waitTicks(8);
            require(clicks.isEmpty(), "Stealer clicked an auction menu");
            context.runOnClient(c -> flag(stealer, "Containers Only", false));
            await(context, () -> world.getServer().computeOnServer(s -> inventoryCount(s.getPlayerManager().getPlayerList().getFirst(), Items.DIAMOND)) == 16, "Explicit menu opt-in did not work");
            context.runOnClient(c -> { dumper.setEnabled(true); require(!stealer.isEnabled(), "Opposite transfers ran together"); stealer.setEnabled(true); require(!dumper.isEnabled(), "Opposite transfers ran together"); });
            System.out.println("[chest-menu-proof] AH menu untouched by default;explicit opt-in works;opposite modules exclude each other");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> chest(s, p).setStack(0, new ItemStack(Items.STONE, 64)));
            context.runOnClient(c -> flag(stealer, "Containers Only", true)); open(context, p);
            world.getServer().runOnServer(s -> { var player = s.getPlayerManager().getPlayerList().getFirst(); player.currentScreenHandler.setCursorStack(new ItemStack(Items.GOLD_INGOT, 3)); player.currentScreenHandler.syncState(); });
            context.waitTicks(4); context.runOnClient(c -> stealer.setEnabled(true)); context.waitTicks(6);
            require(clicks.isEmpty() && chestCount(world, p, Items.STONE) == 64, "Held cursor did not pause transfers");
            context.runOnClient(c -> { var h = c.player.currentScreenHandler; c.interactionManager.clickSlot(h.syncId, 54, 0, SlotActionType.PICKUP, c.player); });
            await(context, () -> chestCount(world, p, Items.STONE) == 0, "Transfer did not resume after cursor cleared");
            require(world.getServer().computeOnServer(s -> inventoryCount(s.getPlayerManager().getPlayerList().getFirst(), Items.GOLD_INGOT)) == 3, "Held cursor items were lost");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> chest(s, p).setStack(0, new ItemStack(Items.STONE, 64)));
            reject = 3;
            context.runOnClient(c -> stealer.setEnabled(true)); open(context, p);
            await(context, () -> context.computeOnClient(c -> !stealer.isEnabled()), "Rejected transfers kept looping");
            require(clicks.size() == 3 && chestCount(world, p, Items.STONE) == 64 && world.getServer().computeOnServer(s -> inventoryCount(s.getPlayerManager().getPlayerList().getFirst(), Items.STONE)) == 0, "Rejection recovery changed server items: " + clicks);
            System.out.println("[chest-rejection-proof] three native shift-clicks rejected;bounded stop;server items preserved");
            close(context);

            clear(world, p);
            world.getServer().runCommand("setblock " + at(p.east()) + " air");
            world.getServer().runCommand("setblock " + at(p) + " barrel");
            world.getServer().runOnServer(s -> chest(s, p).setStack(0, new ItemStack(Items.EMERALD, 8)));
            context.waitTicks(4); context.runOnClient(c -> stealer.setEnabled(true)); open(context, p);
            await(context, () -> chestCount(world, p, Items.EMERALD) == 0, "Physical barrel did not transfer");
            require(world.getServer().computeOnServer(s -> inventoryCount(s.getPlayerManager().getPlayerList().getFirst(), Items.EMERALD)) == 8, "Barrel transfer missing on server");
            close(context);

            clear(world, p);
            world.getServer().runOnServer(s -> chest(s, p).setStack(0, new ItemStack(Items.STONE, 64)));
            open(context, p);
            context.runOnClient(c -> { stealer.setEnabled(true); ModuleManager.get(OrderDropper.class).setEnabled(true); });
            context.waitTicks(5);
            require(clicks.isEmpty() && chestCount(world, p, Items.STONE) == 64, "Order Dropper's container was modified");
            context.runOnClient(c -> ModuleManager.get(OrderDropper.class).setEnabled(false));
            System.out.println("[chest-coordination-proof] real barrel supported;active Order Dropper suspends chest transfer");
        } finally {
            recording = false; reject = 0; BuilderChestDelay.end();
            close(context);
            context.runOnClient(c -> { ModuleManager.get(OrderDropper.class).setEnabled(false); stealer.getSettings().forEach(Setting::reset); dumper.getSettings().forEach(Setting::reset); });
            world.getServer().runCommand("setblock " + at(p) + " air");
            world.getServer().runCommand("setblock " + at(p.east()) + " air");
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("kill @e[type=item]");
            world.getServer().runOnServer(s -> { var player = s.getPlayerManager().getPlayerList().getFirst(); player.changeGameMode(mode); player.teleport(s.getOverworld(), before.x, before.y, before.z, java.util.Set.of(), 0, 0, false); });
        }
    }
}
