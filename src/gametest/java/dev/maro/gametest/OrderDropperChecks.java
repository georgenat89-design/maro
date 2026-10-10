package dev.maro.gametest;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maro.donut.OrderMarket;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.OrderDropper;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Order Dropper against a stand-in DonutSMP: /orders opens a small-caps "Orders" menu whose orders
 * say "$150 each" and "16/20 Delivered", clicking one opens a delivery window that takes what the
 * order still wants when it closes and hands the rest back, and /ah sells listings behind a
 * confirmation. Selling fills the best-paying order first and goes on to the next; flipping buys
 * only what is cheap enough and sells it into the order.
 */
final class OrderDropperChecks {
    private OrderDropperChecks() {
    }

    /** An order on the stand-in server. */
    private static final class Order {
        final Item item;
        final double each;
        final int total;
        int delivered;

        Order(Item item, double each, int delivered, int total) {
            this.item = item;
            this.each = each;
            this.delivered = delivered;
            this.total = total;
        }
    }

    private static final List<Order> orders = new ArrayList<>();
    private static final List<ItemStack> listings = new ArrayList<>();
    private static final List<Integer> prices = new ArrayList<>();
    private static int delivered, purchases;
    private static double paid;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(OrderDropper module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static ItemStack named(Item item, String name, String... lore) {
        ItemStack stack = new ItemStack(item);
        if (name != null) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        List<Text> lines = new ArrayList<>();
        for (String line : lore) lines.add(Text.literal(line));
        if (!lines.isEmpty()) stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
        return stack;
    }

    // ---- the stand-in server ------------------------------------------------------------------

    private static void openOrders(ServerPlayerEntity player) {
        SimpleInventory menu = new SimpleInventory(27);
        List<Order> shown = new ArrayList<>();
        for (Order order : orders) {
            if (order.delivered >= order.total) continue;
            ItemStack icon = new ItemStack(order.item);
            icon.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("§a$" + (int) order.each + " ᴇᴀᴄʜ"),
                    Text.literal(order.delivered + "/" + order.total + " Delivered"),
                    Text.literal("Click to deliver"))));
            menu.setStack(shown.size(), icon);
            shown.add(order);
        }
        menu.setStack(22, named(Items.BARRIER, "Close"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, inventory, menu, 3) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        // A plugin menu: nothing moves, a click on an order opens its delivery window.
                        if (slot >= 0 && slot < shown.size()) openDelivery(player, shown.get(slot));
                        else syncState();
                    }
                }, Text.literal("ᴏʀᴅᴇʀꜱ")));
    }

    private static void openDelivery(ServerPlayerEntity player, Order order) {
        SimpleInventory box = new SimpleInventory(27);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, inventory, box, 3) {
                    @Override
                    public void onClosed(PlayerEntity entity) {
                        super.onClosed(entity);
                        // Takes what the order still wants, pays for it, and hands back the rest.
                        for (int i = 0; i < box.size(); i++) {
                            ItemStack stack = box.getStack(i);
                            if (stack.isEmpty()) continue;
                            if (stack.isOf(order.item)) {
                                int take = Math.min(stack.getCount(), order.total - order.delivered);
                                order.delivered += take;
                                delivered += take;
                                paid += take * order.each;
                                stack.decrement(take);
                            }
                            if (!stack.isEmpty()) player.getInventory().offerOrDrop(stack.copy());
                            box.setStack(i, ItemStack.EMPTY);
                        }
                    }
                }, Text.literal("ᴅᴇʟɪᴠᴇʀ ɪᴛᴇᴍꜱ")));
    }

    private static void openAuction(ServerPlayerEntity player) {
        SimpleInventory menu = new SimpleInventory(27);
        for (int i = 0; i < listings.size(); i++) menu.setStack(i, listings.get(i).copy());
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, inventory, menu, 3) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        if (slot >= 0 && slot < listings.size()) openConfirm(player, slot);
                        else syncState();
                    }
                }, Text.literal("Auction House")));
    }

    private static void openConfirm(ServerPlayerEntity player, int listing) {
        SimpleInventory menu = new SimpleInventory(27);
        menu.setStack(13, listings.get(listing).copy());
        menu.setStack(11, named(Items.LIME_STAINED_GLASS_PANE, "Confirm"));
        menu.setStack(15, named(Items.RED_STAINED_GLASS_PANE, "Cancel"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, inventory, menu, 3) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        if (slot == 11 && listing < listings.size()) {
                            ItemStack bought = listings.remove(listing);
                            prices.remove(listing);
                            purchases++;
                            player.closeHandledScreen();
                            bought.remove(DataComponentTypes.LORE);
                            player.getInventory().offerOrDrop(bought);
                        } else if (slot == 15) {
                            player.closeHandledScreen();
                        } else {
                            syncState();
                        }
                    }
                }, Text.literal("Confirm Purchase")));
    }

    private static void listing(Item item, int count, int total) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Price: $" + String.format(java.util.Locale.ROOT, "%,d", total)), Text.literal("Seller: Someone"))));
        listings.add(stack);
        prices.add(total);
    }

    // ---- the checks ---------------------------------------------------------------------------

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        OrderDropper module = ModuleManager.get(OrderDropper.class);
        require(module != null, "Order Dropper was not registered");

        // The parser on DonutSMP-style lore.
        require(Math.abs(OrderMarket.priceEach(List.of("§a$1.2K ᴇᴀᴄʜ", "120/2,304 Delivered"), "each;per;price") - 1200) < 0.01,
                "$1.2K ᴇᴀᴄʜ did not read as 1200");
        require(OrderMarket.remaining(List.of("§a$1.2K ᴇᴀᴄʜ", "120/2,304 Delivered")) == 2184, "120/2,304 did not leave 2184");
        require(OrderMarket.has("ᴏʀᴅᴇʀꜱ (Page 1)", "order"), "Small capitals were not read");
        require(Double.isNaN(OrderMarket.priceEach(List.of("Delivered 4/9"), "each")), "A line with no price read as one");

        world.getServer().computeOnServer(server -> {
            var dispatcher = server.getCommandManager().getDispatcher();
            dispatcher.register(CommandManager.literal("orders").then(CommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(command -> {
                        if (command.getSource().getPlayer() != null) openOrders(command.getSource().getPlayer());
                        return 1;
                    })));
            dispatcher.register(CommandManager.literal("ah").then(CommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(command -> {
                        if (command.getSource().getPlayer() != null) openAuction(command.getSource().getPlayer());
                        return 1;
                    })));
            server.getPlayerManager().getPlayerList().forEach(server.getCommandManager()::sendCommandTree);
            return true;
        });

        try {
            // ---- selling: the $150 order (wanting 4) first, then the $100 one (wanting 32)
            world.getServer().runOnServer(server -> {
                orders.clear();
                orders.add(new Order(Items.DIAMOND, 150, 16, 20));
                orders.add(new Order(Items.DIAMOND, 100, 0, 32));
                orders.add(new Order(Items.STONE, 999, 0, 64));
                delivered = 0;
                paid = 0;
            });
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a minecraft:diamond 40");
            context.waitTicks(5);
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(module, "Use Held Item")).set(false);
                ((TextSetting) setting(module, "Item")).set("minecraft:diamond");
                ((NumberSetting) setting(module, "Action Delay")).set(1.0);
                module.setEnabled(true);
            });
            context.waitTicks(10);
            context.takeScreenshot("maro-order-dropper");
            for (int i = 0; i < 120 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            String sellResult = context.computeOnClient(c -> module.result());
            System.out.println("ORDER DROPPER sell: " + sellResult + " | server delivered " + world.getServer().computeOnServer(s -> delivered));
            require(!context.computeOnClient(c -> module.isEnabled()), "Order Dropper did not finish: " + context.computeOnClient(c -> module.status()));
            require(world.getServer().computeOnServer(s -> delivered) == 36, "The orders did not get 36 diamonds: " + world.getServer().computeOnServer(s -> delivered));
            require(context.computeOnClient(c -> module.soldCount()) == 36 && Math.abs(context.computeOnClient(c -> module.earned()) - 3800) < 0.01,
                    "Sold or earned is off: " + sellResult);
            require(context.computeOnClient(c -> c.player.getInventory().count(Items.DIAMOND)) == 4, "The 4 diamonds no order wanted were not kept");

            // ---- flipping: buy the $50-each listing, not the $250-each one, and sell into the $100 order
            world.getServer().runOnServer(server -> {
                orders.clear();
                orders.add(new Order(Items.DIAMOND, 100, 0, 16));
                listings.clear();
                prices.clear();
                listing(Items.DIAMOND, 8, 400);
                listing(Items.DIAMOND, 8, 2000);
                delivered = 0;
                paid = 0;
                purchases = 0;
            });
            world.getServer().runCommand("clear @a");
            context.waitTicks(5);
            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "Flip")).set(true);
                ((TextSetting) setting(module, "Max Spend")).set("1k");
                ((NumberSetting) setting(module, "Flip Rounds")).set(1.0);
                module.setEnabled(true);
            });
            for (int i = 0; i < 160 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            String flipResult = context.computeOnClient(c -> module.result());
            System.out.println("ORDER DROPPER flip: " + flipResult + " | purchases " + world.getServer().computeOnServer(s -> purchases));
            require(world.getServer().computeOnServer(s -> purchases) == 1, "It should buy only the cheap listing: " + flipResult);
            require(Math.abs(context.computeOnClient(c -> module.spent()) - 400) < 0.01 && context.computeOnClient(c -> module.soldCount()) == 8
                    && Math.abs(context.computeOnClient(c -> module.earned()) - 800) < 0.01, "The flip is off: " + flipResult);
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                if (c.currentScreen != null) c.player.closeHandledScreen();
            });
            world.getServer().runCommand("clear @a");
            context.waitTicks(3);
        }
    }
}
