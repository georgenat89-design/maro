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
 * Order Dropper against a stand-in DonutSMP. Collect &amp; Drop goes /orders, Your Orders, the order,
 * Collect Items and throws every stack out, page after page, for every order, never touching your own
 * items. Selling: /orders <item> opens a small-caps "Orders" menu whose orders
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

    /** One of your own orders on the stand-in server, with what has been delivered and not collected yet. */
    private static final class Own {
        final Item item;
        final List<ItemStack> waiting = new ArrayList<>();

        Own(Item item, int stacks) {
            this.item = item;
            for (int i = 0; i < stacks; i++) waiting.add(new ItemStack(item, 64));
        }

        int left() {
            int n = 0;
            for (ItemStack stack : waiting) if (stack != null) n += stack.getCount();
            return n;
        }
    }

    private static final List<Own> own = new ArrayList<>();
    private static int thrownOnServer, dropAllPresses;
    private record Delayed(int tick, Runnable action) { }
    private static final List<Delayed> delayed = new ArrayList<>();
    static {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server ->
                delayed.removeIf(task -> { if (server.getTicks() < task.tick()) return false; task.action().run(); return true; }));
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

    /** /orders as on DonutSMP: everyone's orders, with Your Orders (a chest) in the bottom row. */
    private static void openMain(ServerPlayerEntity player) {
        SimpleInventory menu = new SimpleInventory(54);
        Item[] others = {Items.DIRT, Items.BONE, Items.OAK_LOG, Items.CHEST, Items.IRON_INGOT, Items.SAND};
        for (int i = 0; i < 30; i++) menu.setStack(i, named(others[i % others.length], null, "§a$" + (10 + i) + " ᴇᴀᴄʜ", "Click to deliver"));
        menu.setStack(47, named(Items.HOPPER, "Sort"));
        menu.setStack(48, named(Items.AMETHYST_SHARD, "Filter"));
        menu.setStack(49, named(Items.BOOK, "Help"));
        menu.setStack(50, named(Items.OAK_SIGN, "Search"));
        menu.setStack(53, named(Items.ARROW, "Next Page"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, inventory, menu, 6) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        if (slot == 51) openYours(player);
                        else syncState();
                    }
                }, Text.literal("Orders (Page 1)")));
        var opened = player.currentScreenHandler;
        delayed.add(new Delayed(player.getEntityWorld().getServer().getTicks() + 8, () -> {
            if (player.currentScreenHandler != opened) return;
            menu.setStack(51, named(Items.CHEST, "ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ"));
            opened.syncState();
        }));
    }

    private static void openYours(ServerPlayerEntity player) {
        SimpleInventory menu = new SimpleInventory(54);
        for (int i = 0; i < own.size(); i++) menu.setStack(i, named(own.get(i).item, null, "Click to edit"));
        for (int i = 18; i < 45; i++) menu.setStack(i, named(Items.RED_STAINED_GLASS_PANE, "Locked"));
        menu.setStack(47, named(Items.HOPPER, "Sort"));
        menu.setStack(51, named(Items.CHEST, "ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, inventory, menu, 6) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        if (slot >= 0 && slot < own.size()) openEdit(player, own.get(slot));
                        else syncState();
                    }
                }, Text.literal("Orders -> Your Orders")));
    }

    private static void openEdit(ServerPlayerEntity player, Own order) {
        SimpleInventory menu = new SimpleInventory(27);
        for (int i : new int[] {0, 1, 2, 9, 11, 18, 19, 20}) menu.setStack(i, named(Items.GRAY_STAINED_GLASS_PANE, " "));
        menu.setStack(10, new ItemStack(order.item));
        menu.setStack(13, named(Items.CHEST, "ᴄᴏʟʟᴇᴄᴛ ɪᴛᴇᴍꜱ"));
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X3, syncId, inventory, menu, 3) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        if (slot == 13) openCollect(player, order, 0);
                        else syncState();
                    }
                }, Text.literal("Orders -> Edit Order")));
    }

    /**
     * Collect Items: 45 stacks a page, previous and next arrows in the bottom row, and the order's item
     * beside the emerald, which drops the whole page; Ctrl+Q throws one stack out.
     */
    private static void openCollect(ServerPlayerEntity player, Own order, int page) {
        SimpleInventory menu = new SimpleInventory(54);
        Runnable fill = () -> {
            for (int i = 0; i < 45; i++) {
                int at = page * 45 + i;
                ItemStack stack = at < order.waiting.size() ? order.waiting.get(at) : null;
                menu.setStack(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
            menu.setStack(45, page > 0 ? named(Items.ARROW, "Previous Page") : ItemStack.EMPTY);
            menu.setStack(51, named(Items.EMERALD, "Info"));
            menu.setStack(52, new ItemStack(order.item));
            menu.setStack(53, (page + 1) * 45 < order.waiting.size() ? named(Items.ARROW, "Next Page") : ItemStack.EMPTY);
        };
        fill.run();
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inventory, owner) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, inventory, menu, 6) {
                    @Override
                    public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
                        int at = page * 45 + slot;
                        if (slot == 52 && action == SlotActionType.PICKUP) {
                            dropAllPresses++;
                            for (int i = 0; i < 45; i++) {
                                int index = page * 45 + i;
                                if (index >= order.waiting.size() || order.waiting.get(index) == null) continue;
                                ItemStack stack = order.waiting.set(index, null);
                                thrownOnServer += stack.getCount();
                                player.dropItem(stack, false, true);
                                menu.setStack(i, ItemStack.EMPTY);
                            }
                        } else if (slot >= 0 && slot < 45 && at < order.waiting.size() && order.waiting.get(at) != null && action == SlotActionType.THROW) {
                            ItemStack stack = order.waiting.set(at, null);
                            thrownOnServer += stack.getCount();
                            player.dropItem(stack, false, true);
                            menu.setStack(slot, ItemStack.EMPTY);
                        } else if (slot == 53 && !menu.getStack(53).isEmpty()) {
                            openCollect(player, order, page + 1);
                            return;
                        } else if (slot == 45 && page > 0) {
                            openCollect(player, order, page - 1);
                            return;
                        }
                        syncState();
                    }
                }, Text.literal("Orders -> Collect Items")));
    }

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
            dispatcher.register(CommandManager.literal("orders").executes(command -> {
                        if (command.getSource().getPlayer() != null) openMain(command.getSource().getPlayer());
                        return 1;
                    }).then(CommandManager.argument("query", StringArgumentType.greedyString())
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
            // ---- collect and drop, as on DonutSMP: /orders, Your Orders, the order, Collect Items, throw
            // everything out page by page. Stone has 65 stacks waiting (two pages), cobblestone 10.
            world.getServer().runOnServer(server -> {
                own.clear();
                own.add(new Own(Items.STONE, 65));
                own.add(new Own(Items.COBBLESTONE, 10));
                thrownOnServer = 0;
                dropAllPresses = 0;
            });
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("give @a minecraft:diamond_sword");
            context.waitTicks(5);
            // With no block picked it asks for one.
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            context.waitTicks(2);
            require(!context.computeOnClient(c -> module.isEnabled()) && context.computeOnClient(c -> module.result()).contains("Pick the block"),
                    "With no block picked it should ask for one: " + context.computeOnClient(c -> module.result()));
            context.runOnClient(c -> {
                ((dev.maro.setting.ModeSetting) setting(module, "Orders To Empty")).set("All");
                module.setEnabled(true);
            });
            boolean seenCollect = false;
            for (int i = 0; i < 40 && !seenCollect; i++) {
                context.waitTick();
                seenCollect = context.computeOnClient(c -> c.currentScreen != null && c.currentScreen.getTitle().getString().contains("Collect"));
            }
            require(seenCollect, "Order Dropper did not get to Collect Items: " + context.computeOnClient(c -> module.status()));
            context.takeScreenshot("maro-order-dropper-collect");
            for (int i = 0; i < 120 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            String dropResult = context.computeOnClient(c -> module.result());
            int thrown = world.getServer().computeOnServer(s -> thrownOnServer);
            int presses = world.getServer().computeOnServer(s -> dropAllPresses);
            System.out.println("ORDER DROPPER drop: " + dropResult + " | server thrown " + thrown + " · drop all pressed " + presses);
            require(presses >= 3 && presses <= 4, "The drop all button should be pressed about once a page (2 stone pages, 1 cobblestone): " + presses);
            require(!context.computeOnClient(c -> module.isEnabled()), "Order Dropper did not finish dropping: " + context.computeOnClient(c -> module.status()));
            require(thrown == 75 * 64, "Not every stack was thrown out: " + thrown + " of " + 75 * 64 + " · " + dropResult);
            require(world.getServer().computeOnServer(s -> own.get(0).left() == 0 && own.get(1).left() == 0), "An order still has items waiting");
            require(context.computeOnClient(c -> module.ordersEmptied()) == 2 && context.computeOnClient(c -> module.droppedCount()) == 75 * 64,
                    "The count is off: " + dropResult);
            require(context.computeOnClient(c -> c.player.getInventory().count(Items.DIAMOND_SWORD)) == 1, "Your own sword was thrown out");
            world.getServer().runCommand("kill @e[type=minecraft:item]");

            // Only the orders for the picked block (both cobblestone ones): the stone order is left alone.
            world.getServer().runOnServer(server -> {
                own.clear();
                own.add(new Own(Items.STONE, 3));
                own.add(new Own(Items.COBBLESTONE, 4));
                own.add(new Own(Items.COBBLESTONE, 2));
                thrownOnServer = 0;
            });
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                ((TextSetting) setting(module, "Block To Drop")).set("minecraft:cobblestone");
                ((dev.maro.setting.ModeSetting) setting(module, "Drop How")).set("Throw Each Stack");
                module.setEnabled(true);
            });
            for (int i = 0; i < 80 && context.computeOnClient(c -> module.isEnabled()); i++) context.waitTicks(5);
            System.out.println("ORDER DROPPER picked: " + context.computeOnClient(c -> module.result()));
            require(world.getServer().computeOnServer(s -> own.get(0).left() == 3 * 64 && own.get(1).left() == 0 && own.get(2).left() == 0),
                    "Picked Block emptied the wrong orders: " + context.computeOnClient(c -> module.result()));
            require(context.computeOnClient(c -> module.ordersEmptied()) == 2, "Both cobblestone orders should be emptied");
            world.getServer().runCommand("kill @e[type=minecraft:item]");
            context.runOnClient(c -> module.getSettings().forEach(Setting::reset));

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
                ((dev.maro.setting.ModeSetting) setting(module, "Mode")).set("Sell To Orders");
                ((BooleanSetting) setting(module, "Use Held Item")).set(false);
                ((TextSetting) setting(module, "Item")).set("minecraft:diamond");
                ((NumberSetting) setting(module, "Action Delay")).set(1.0);
                module.setEnabled(true);
            });
            context.waitTicks(10);
            context.takeScreenshot("maro-order-dropper-sell");
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
