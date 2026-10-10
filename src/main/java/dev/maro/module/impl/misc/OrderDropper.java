package dev.maro.module.impl.misc;

import dev.maro.builder.AuctionMarket;
import dev.maro.donut.OrderMarket;
import dev.maro.gui.hud.ItemPickerScreen;
import dev.maro.gui.notification.Notifications;
import dev.maro.gui.render.Fonts;
import dev.maro.gui.render.Render2D;
import dev.maro.gui.theme.Theme;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.SettingSection;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Order Dropper, for DonutSMP's orders. Collect &amp; Drop (the usual way) empties your own orders: it
 * opens /orders, goes to Your Orders, opens each order's Collect Items window and throws every stack
 * out, a whole page at a time, flipping through the pages until the order is empty, then goes on to
 * your next order. Repeat starts it again after a while for what has been delivered since.
 *
 * <p>Sell To Orders instead sells an item into the orders that pay most for it: it picks the best
 * order, drops your stacks into its delivery window and goes on to the next until you are sold out.
 * Flip buys the item off the auction house first, below what the best order pays by your margin.
 *
 * <p>Every menu word is a setting, as server menus change. Anything unexpected (a menu that does not
 * open, a price that moved, an order that takes nothing) stops it with a message rather than guessing.
 */
public class OrderDropper extends Module {
    private enum Stage {SCAN_OPEN, SCAN_READ, BUY_OPEN, BUY_PICK, BUY_CONFIRM, SELL_OPEN, SELL_PICK, DELIVER_OPEN, DELIVER, DELIVER_FINISH,
        DROP_OPEN, DROP, DROP_AGAIN}

    private static final String DROP = "Collect & Drop", SELL = "Sell To Orders";

    private final dev.maro.setting.ModeSetting mode = add(new dev.maro.setting.ModeSetting("Mode",
            "Collect & Drop: empty your own orders by throwing out what was delivered. Sell To Orders: sell an item into other people's orders",
            DROP, DROP, SELL));

    // ---- collect and drop
    private final dev.maro.setting.ModeSetting which = add(new dev.maro.setting.ModeSetting("Orders To Empty",
            "Picked Block: only your orders for the block you pick. Held Item: for what is in your hand. All: every order you have",
            "Picked Block", "Picked Block", "Held Item", "All").visible(this::dropping));
    private final TextSetting dropItem = add(new TextSetting("Block To Drop", "The block (or item) whose orders it empties: every order of yours for it",
            "", 64, "Pick one").visible(() -> dropping() && which.is("Picked Block")));
    private final ButtonSetting pickDrop = add(new ButtonSetting("Pick Block", "Choose the block to drop from every block and item", "Pick",
            () -> mc.setScreen(new ItemPickerScreen(mc.currentScreen, "Block To Drop", picked -> dropItem.set(Registries.ITEM.getId(picked).toString()))))
            .visible(() -> dropping() && which.is("Picked Block")));
    private final dev.maro.setting.ModeSetting how = add(new dev.maro.setting.ModeSetting("Drop How",
            "Throw the stacks straight out of the Collect Items window (fastest), or take them and throw them from your inventory",
            "Throw From Menu", "Throw From Menu", "Take Then Throw").visible(this::dropping));
    private final NumberSetting dropsPerTick = add(new NumberSetting("Drops Per Tick", "How many stacks it throws each tick: 45 is a whole page at once",
            45, 1, 45, 1).visible(this::dropping));
    private final NumberSetting pageDelay = add(new NumberSetting("Page Delay", "Ticks to wait for the next page or menu (more if the server is slow)",
            2, 0, 20, 1).suffix(" ticks").visible(this::dropping));
    private final BooleanSetting repeat = add(new BooleanSetting("Repeat", "When every order is empty, start again after a while for what was delivered since",
            false).visible(this::dropping));
    private final NumberSetting repeatEvery = add(new NumberSetting("Repeat Every", "How long to wait before going round again", 30, 5, 600, 5)
            .suffix("s").visible(() -> dropping() && repeat.get()));

    // ---- what to sell
    private final BooleanSetting useHeld = add(new BooleanSetting("Use Held Item", "Sell what is in your hand when it starts", true).visible(this::selling));
    private final TextSetting itemId = add(new TextSetting("Item", "The item to sell or flip, or whose order to empty", "", 64, "None")
            .visible(this::picksItem));
    private final ButtonSetting pickItem = add(new ButtonSetting("Pick Item", "Choose it from every item", "Edit",
            () -> mc.setScreen(new ItemPickerScreen(mc.currentScreen, "Item", picked -> itemId.set(Registries.ITEM.getId(picked).toString()))))
            .visible(this::picksItem));

    // ---- selling
    private final TextSetting minPrice = add(new TextSetting("Min Price", "Only orders paying at least this per item (1.5k, 2m)", "0", 16, "0").visible(this::selling));
    private final NumberSetting keep = add(new NumberSetting("Keep", "How many to keep for yourself", 0, 0, 2304, 1).visible(this::selling));
    private final BooleanSetting skipSpecial = add(new BooleanSetting("Skip Special Items", "Never sell enchanted, damaged or renamed ones", true).visible(this::selling));
    private final NumberSetting stacksPerTick = add(new NumberSetting("Stacks Per Tick", "How many stacks it drops into an order each tick", 4, 1, 9, 1).visible(this::selling));
    private final NumberSetting delay = add(new NumberSetting("Action Delay", "Ticks between menu clicks (more if the server is slow)", 2, 0, 20, 1).suffix(" ticks").visible(this::selling));
    private final BooleanSetting keepGoing = add(new BooleanSetting("Keep Going", "After an order, go on to the next until sold out", true).visible(this::selling));
    private final NumberSetting pages = add(new NumberSetting("Pages To Search", "How many pages of orders or listings it looks through", 3, 1, 10, 1).visible(this::selling));

    // ---- flipping
    private final BooleanSetting flip = add(new BooleanSetting("Flip", "Buy it off the auction house below the order price, then sell it into orders", false).visible(this::selling));
    private final NumberSetting margin = add(new NumberSetting("Min Margin", "Buy only this much under what the best order pays", 10, 1, 90, 1)
            .suffix("%").visible(this::flipping));
    private final TextSetting maxSpend = add(new TextSetting("Max Spend", "The most it spends on the auction house in one go", "100k", 16, "100k")
            .visible(this::flipping));
    private final TextSetting maxBuyEach = add(new TextSetting("Max Buy Each", "Never pay more than this per item (empty: only the margin)", "", 16, "None")
            .visible(this::flipping));
    private final NumberSetting rounds = add(new NumberSetting("Flip Rounds", "Buy and sell rounds before it stops", 3, 1, 50, 1).visible(this::flipping));

    // ---- the server's menus
    private final TextSetting ordersCommand = add(new TextSetting("Orders Command", "Opens the orders for the item ({item} is its id, {name} its name)", "orders {item}", 64, "orders {item}").visible(this::selling));
    private final TextSetting ahCommand = add(new TextSetting("AH Command", "Searches the auction house for the item", "ah {item}", 64, "ah {item}").visible(this::flipping));
    private final TextSetting ordersTitle = add(new TextSetting("Orders Title", "Words in the orders menu's title, separated by ;", "order", 64, "order").visible(this::selling));
    private final TextSetting deliverTitle = add(new TextSetting("Deliver Title", "Words in the delivery window's title", "deliver;sell;fill", 64, "deliver").visible(this::selling));
    private final TextSetting ahTitle = add(new TextSetting("AH Title", "Words in the auction house's title", "auction;ah", 64, "auction").visible(this::flipping));
    private final TextSetting priceWords = add(new TextSetting("Price Words", "Words on the line with an order's price per item", "each;per;price", 64, "each").visible(this::selling));
    private final TextSetting confirmWords = add(new TextSetting("Confirm Words", "Names of confirm buttons", "confirm;deliver;sell;yes;buy;purchase", 128, "confirm").visible(this::selling));
    private final TextSetting cancelWords = add(new TextSetting("Cancel Words", "Names of cancel buttons", "cancel;no;back;close;deny", 64, "cancel").visible(this::selling));
    private final TextSetting nextWords = add(new TextSetting("Next Page Words", "Names of the next page button", "next", 64, "next"));
    private final TextSetting openCommand = add(new TextSetting("Open Command", "Opens the orders menu", "orders", 64, "orders").visible(this::dropping));
    private final TextSetting yourOrdersWords = add(new TextSetting("Your Orders Words", "The Your Orders button and window", "your orders;my orders", 64, "your orders")
            .visible(this::dropping));
    private final TextSetting collectWords = add(new TextSetting("Collect Words", "The Collect Items button and window", "collect", 64, "collect")
            .visible(this::dropping));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Order Dropper", mode, which, dropItem, pickDrop, how, dropsPerTick, pageDelay, repeat, repeatEvery),
            SettingSection.of("Item", useHeld, itemId, pickItem),
            SettingSection.of("Selling", minPrice, keep, skipSpecial, stacksPerTick, delay, keepGoing, pages),
            SettingSection.of("Flip", flip, margin, maxSpend, maxBuyEach, rounds),
            SettingSection.of("Menus", openCommand, yourOrdersWords, collectWords, ordersCommand, ahCommand, ordersTitle, deliverTitle, ahTitle,
                    priceWords, confirmWords, cancelWords, nextWords));

    /** Saved before you picked the block to drop: All becomes Picked Block, once. */
    private static final int DROP_REVISION = 1;

    @Override
    public com.google.gson.JsonObject saveExtra() {
        var data = super.saveExtra();
        data.addProperty("drop-revision", DROP_REVISION);
        return data;
    }

    @Override
    public void loadExtra(com.google.gson.JsonObject data) {
        super.loadExtra(data);
        if (!data.has("drop-revision") && which.is("All")) which.set("Picked Block");
    }

    private boolean dropping() {
        return mode.is(DROP);
    }

    private boolean selling() {
        return mode.is(SELL);
    }

    private boolean flipping() {
        return selling() && flip.get();
    }

    private boolean picksItem() {
        return selling() && !useHeld.get();
    }

    private Item item = Items.AIR;
    private Stage stage;
    private int wait, deadline, ticks, page, roundsDone, deliverSync = -1, menuSync = -1, phase;
    private double orderEach, bestOrder, spent, earned;
    private int sold, bought, before, moved, remaining;
    private boolean clicked;
    private AuctionMarket.Offer offer;
    private volatile boolean listingGone;
    private String status = "Off", result = "";

    // ---- collect and drop state
    /** The orders emptied this round, by what they are, so a list that shifts does not repeat or skip one. */
    private final java.util.Set<String> emptied = new java.util.HashSet<>();
    /** Inventory slots that were empty at the start: the only ones Take Then Throw throws from. */
    private final java.util.Set<Integer> freeSlots = new java.util.HashSet<>();
    private String currentOrder;
    private int dropped, stacksDropped, ordersEmptied, dropPage, refills, lastDropTick, clickedSync = -1, clickedAt, repeatAt;
    private boolean pageEmptied, lookAlikes, checking;
    private int droppedAtOrder;

    public OrderDropper() {
        super("Order Dropper", "Empties your DonutSMP orders fast: opens Collect Items and throws every stack out, page after page", Category.MISC);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (stage == Stage.BUY_CONFIRM && AuctionMarket.unavailable(message.getString())) listingGone = true;
        });
    }

    @Override
    public List<SettingSection> getSettingSections() {
        return sections;
    }

    // ---- start and stop -----------------------------------------------------------------------

    @Override
    protected void onEnable() {
        result = "";
        sold = bought = roundsDone = page = dropped = stacksDropped = ordersEmptied = 0;
        spent = earned = 0;
        if (!inGame()) {
            setEnabled(false);
            return;
        }
        if (dropping()) {
            startDropping();
            return;
        }
        item = useHeld.get() ? mc.player.getMainHandStack().getItem() : itemOf(itemId.get());
        if (item == Items.AIR) {
            finish(useHeld.get() ? "Hold the item to sell first" : "Pick an item first", false);
            return;
        }
        if (!useHeld.get() || itemId.get().isBlank()) itemId.set(Registries.ITEM.getId(item).toString());
        go(flip.get() ? Stage.SCAN_OPEN : Stage.SELL_OPEN, 0);
    }

    @Override
    protected void onDisable() {
        if (stage != null && mc.player != null && mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
        stage = null;
        status = result.isEmpty() ? "Off" : result;
    }

    private void finish(String why, boolean good) {
        result = why;
        String summary = why;
        if (dropping() && dropped > 0) summary += " · dropped " + dropped + " items";
        if (sold > 0) summary += " · sold " + sold + " for " + OrderMarket.money(earned);
        if (spent > 0) summary += " · spent " + OrderMarket.money(spent) + " · profit " + OrderMarket.money(Math.max(0, earned - spent));
        Notifications.push("Order Dropper", summary, good ? Notifications.Type.SUCCESS : Notifications.Type.WARNING, 7000);
        result = summary;
        setEnabled(false);
    }

    private void go(Stage next, int pause) {
        stage = next;
        wait = pause;
        deadline = ticks + 100;
        clicked = false;
        phase = 0;
    }

    // ---- the work, a step a tick -----------------------------------------------------------------

    @Override
    public void onTick() {
        ticks++;
        if (stage == null || !inGame()) return;
        if (wait > 0) {
            wait--;
            return;
        }
        if (ticks > deadline) {
            finish(switch (stage) {
                case DROP_OPEN -> "The orders menu did not open";
                case DROP -> "The orders menu stopped answering";
                case SCAN_OPEN, SELL_OPEN -> "The orders menu did not open";
                case BUY_OPEN -> "The auction house did not open";
                case DELIVER_OPEN -> "The order's delivery window did not open";
                case BUY_CONFIRM -> "The purchase was not confirmed";
                default -> "The server stopped answering";
            }, false);
            return;
        }
        switch (stage) {
            case DROP_OPEN -> openForDrop();
            case DROP -> dropStep();
            case DROP_AGAIN -> {
                status = "Again in " + Math.max(0, (repeatAt - ticks + 19) / 20) + "s";
                deadline = ticks + 100;
                if (ticks >= repeatAt) {
                    emptied.clear();
                    lookAlikes = checking = false;
                    go(Stage.DROP_OPEN, 0);
                }
            }
            case SCAN_OPEN, SELL_OPEN -> openOrders();
            case SCAN_READ, SELL_PICK -> readOrders();
            case BUY_OPEN -> openAuction();
            case BUY_PICK -> pickListing();
            case BUY_CONFIRM -> confirmPurchase();
            case DELIVER_OPEN -> awaitDelivery();
            case DELIVER -> deliver();
            case DELIVER_FINISH -> finishDelivery();
        }
    }

    private void openOrders() {
        if (stage == Stage.SELL_OPEN && sellable() <= keep.getInt()) {
            afterSelling();
            return;
        }
        if (!clicked) {
            close();
            command(ordersCommand.get());
            clicked = true;
            page = 1;
            status = "Opening orders";
            return;
        }
        ScreenHandler menu = menu(ordersTitle.get());
        if (menu == null) return;
        menuSync = menu.syncId;
        go(stage == Stage.SCAN_OPEN ? Stage.SCAN_READ : Stage.SELL_PICK, 2);
    }

    private void readOrders() {
        ScreenHandler menu = menu(ordersTitle.get());
        if (menu == null) {
            finish("The orders menu closed", false);
            return;
        }
        double min = stage == Stage.SCAN_READ ? 0 : Math.max(0, parse(minPrice.get(), 0));
        OrderMarket.Order best = OrderMarket.best(OrderMarket.orders(menu, mc.player.getInventory(), item, priceWords.get()), min);
        if (best == null) {
            if (page < pages.getInt() && clickNamed(menu, nextWords.get())) {
                page++;
                status = "Orders page " + page;
                wait = delay.getInt() + 4;
                deadline = ticks + 100;
                return;
            }
            if (stage == Stage.SCAN_READ) finish("No orders for " + name() + " to flip into", false);
            else afterSelling(sold > 0 ? "No more orders paying " + OrderMarket.money(min) + "+" : "No orders for " + name() + " paying " + OrderMarket.money(min) + "+");
            return;
        }
        if (stage == Stage.SCAN_READ) {
            bestOrder = best.each();
            close();
            status = "Best order pays " + OrderMarket.money(bestOrder) + " each";
            go(Stage.BUY_OPEN, delay.getInt());
            return;
        }
        orderEach = best.each();
        remaining = best.remaining();
        status = "Order paying " + OrderMarket.money(orderEach) + " each";
        click(menu, best.slot(), SlotActionType.PICKUP);
        go(Stage.DELIVER_OPEN, 0);
    }

    private void awaitDelivery() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        ScreenHandler handler = screen.getScreenHandler();
        if (handler == mc.player.playerScreenHandler) return;
        String title = screen.getTitle().getString();
        // The delivery window: one titled so, or any new window after the click that is not the orders menu again.
        boolean delivery = OrderMarket.has(title, deliverTitle.get()) || handler.syncId != menuSync && menu(ordersTitle.get()) == null;
        if (!delivery) return;
        deliverSync = handler.syncId;
        before = sellable();
        moved = 0;
        go(Stage.DELIVER, delay.getInt());
    }

    /** Drops your stacks of the item into the delivery window, several a tick. */
    private void deliver() {
        ScreenHandler handler = current();
        if (handler == null || handler.syncId != deliverSync) {
            finish("The delivery window closed", false);
            return;
        }
        int done = 0, have = sellable();
        for (Slot slot : handler.slots) {
            if (done >= stacksPerTick.getInt()) break;
            if (slot.inventory != mc.player.getInventory() || !eligible(slot.getStack())) continue;
            int count = slot.getStack().getCount();
            if (have - count < keep.getInt()) continue;
            if (remaining >= 0 && moved >= remaining) break;
            if (!roomFor(handler)) break;
            click(handler, slot.id, SlotActionType.QUICK_MOVE);
            moved += count;
            have -= count;
            done++;
        }
        deadline = ticks + 100;
        status = "Dropping into an order · " + moved;
        if (done == 0) go(Stage.DELIVER_FINISH, delay.getInt());
        else wait = Math.max(0, delay.getInt() - 1);
    }

    /** Confirms the delivery (or closes the window, which delivers), then counts what was taken. */
    private void finishDelivery() {
        if (phase == 0) {
            ScreenHandler handler = current();
            if (handler != null && handler.syncId == deliverSync && !clickNamed(handler, confirmWords.get())) close();
            phase = 1;
            wait = delay.getInt() + 6;
            return;
        }
        if (phase == 1) {
            // Still open after a confirm button: closing hands back whatever the order did not take.
            if (mc.currentScreen instanceof HandledScreen<?> screen && screen.getScreenHandler().syncId == deliverSync) close();
            phase = 2;
            wait = 8;
            return;
        }
        int taken = before - sellable();
        if (taken <= 0) {
            afterSelling(moved == 0 ? "Nothing to drop into the order" : "The order took nothing");
            return;
        }
        sold += taken;
        earned += taken * orderEach;
        status = "Sold " + taken + " for " + OrderMarket.money(taken * orderEach);
        if (!keepGoing.get()) {
            afterSelling("Order filled");
            return;
        }
        go(Stage.SELL_OPEN, delay.getInt());
    }

    /** Sold out (or no order left): another flip round, or done. */
    private void afterSelling() {
        afterSelling(sold > 0 ? "Sold out" : "Nothing to sell");
    }

    private void afterSelling(String why) {
        close();
        if (flip.get() && ++roundsDone < rounds.getInt() && spent < parse(maxSpend.get(), 0) && bought > 0) {
            bought = 0;
            go(Stage.SCAN_OPEN, delay.getInt());
            return;
        }
        finish(why, sold > 0);
    }

    // ---- collect and drop ---------------------------------------------------------------------------

    private void startDropping() {
        emptied.clear();
        lookAlikes = checking = false;
        freeSlots.clear();
        dropped = stacksDropped = ordersEmptied = 0;
        item = switch (which.get()) {
            case "Held Item" -> mc.player.getMainHandStack().getItem();
            case "Picked Block" -> itemOf(dropItem.get());
            default -> Items.AIR;
        };
        if (!which.is("All") && item == Items.AIR) {
            finish(which.is("Held Item") ? "Hold the block whose orders to empty first" : "Pick the block to drop first (Block To Drop)", false);
            return;
        }
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) freeSlots.add(i);
        go(Stage.DROP_OPEN, 0);
    }

    private void openForDrop() {
        if (!clicked) {
            close();
            mc.getNetworkHandler().sendChatCommand(openCommand.get().trim().replaceFirst("^/", ""));
            clicked = true;
            status = "Opening orders";
            return;
        }
        if (filled() == null) return;
        clickedSync = -1;
        go(Stage.DROP, pageDelay.getInt());
    }

    /** The open menu once its slots have arrived, else null. */
    private ScreenHandler filled() {
        ScreenHandler handler = current();
        if (handler == null) return null;
        for (Slot slot : handler.slots) if (slot.inventory != mc.player.getInventory() && !slot.getStack().isEmpty()) return handler;
        return null;
    }

    /** Works out which orders menu is open and does the next thing in it. */
    private void dropStep() {
        ScreenHandler handler = filled();
        if (handler == null) return;
        String title = ((HandledScreen<?>) mc.currentScreen).getTitle().getString();
        if (OrderMarket.has(title, collectWords.get())) {
            collectPage(handler);
            return;
        }
        // A click in this window is on its way: give the server a moment before trying again.
        if (handler.syncId == clickedSync && ticks - clickedAt < 20) return;
        if (OrderMarket.has(title, yourOrdersWords.get())) {
            pickOrder(handler);
            return;
        }
        Slot button = named(handler, collectWords.get(), false);
        if (button == null && OrderMarket.has(title, "edit")) button = chest(handler, false);
        if (button != null) {
            status = "Opening Collect Items";
            menuClick(handler, button);
            return;
        }
        button = named(handler, yourOrdersWords.get(), true);
        if (button == null) button = chest(handler, true);
        if (button == null) {
            finish("No Your Orders button in the orders menu", false);
            return;
        }
        status = "Opening your orders";
        menuClick(handler, button);
    }

    private void menuClick(ScreenHandler handler, Slot slot) {
        click(handler, slot.id, SlotActionType.PICKUP);
        clickedSync = handler.syncId;
        clickedAt = ticks;
        deadline = ticks + 100;
        wait = pageDelay.getInt();
    }

    /** The container's own slots: everything but your inventory. */
    private int containerSize(ScreenHandler handler) {
        int n = 0;
        for (Slot slot : handler.slots) if (slot.inventory != mc.player.getInventory()) n++;
        return n;
    }

    /** Whether a slot is in the bottom row, where menus keep their buttons. */
    private boolean bottomRow(ScreenHandler handler, Slot slot) {
        return slot.id >= containerSize(handler) - 9;
    }

    /** A button named with one of the words; only in the bottom row if asked. */
    private Slot named(ScreenHandler handler, String words, boolean bottom) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() || slot.getStack().isEmpty()) continue;
            if (bottom && !bottomRow(handler, slot)) continue;
            if (OrderMarket.has(slot.getStack().getName().getString(), words)) return slot;
        }
        return null;
    }

    /** A chest-like button: DonutSMP's Your Orders and Collect Items buttons are chests. */
    private Slot chest(ScreenHandler handler, boolean bottom) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() || bottom && !bottomRow(handler, slot)) continue;
            ItemStack stack = slot.getStack();
            if (stack.isOf(Items.CHEST) || stack.isOf(Items.BARREL) || stack.isOf(Items.ENDER_CHEST)) return slot;
        }
        return null;
    }

    private static boolean filler(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("glass_pane");
    }

    private static String key(ItemStack stack) {
        StringBuilder key = new StringBuilder(Registries.ITEM.getId(stack.getItem()).toString()).append('|').append(stack.getName().getString());
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) for (var line : lore.lines()) key.append('|').append(line.getString());
        return key.toString();
    }

    /** In Your Orders: opens the next order not emptied yet, or ends the round. */
    private void pickOrder(ScreenHandler handler) {
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (slot.inventory == mc.player.getInventory() || stack.isEmpty() || bottomRow(handler, slot) || filler(stack)) continue;
            if (!which.is("All") && !stack.isOf(item)) continue;
            if (OrderMarket.has(stack.getName().getString(), "create;new order")) continue;
            // Two orders can look the same: told apart by which of them it is.
            int nth = seen.merge(key(stack), 1, Integer::sum) - 1;
            if (nth > 0) lookAlikes = true;
            String key = key(stack) + "#" + nth;
            if (emptied.contains(key)) continue;
            currentOrder = key;
            droppedAtOrder = dropped;
            dropPage = 1;
            refills = 0;
            pageEmptied = false;
            status = "Opening your " + stack.getItem().getName().getString() + " order";
            menuClick(handler, slot);
            return;
        }
        roundDone();
    }

    /** Every order is empty: again after a while, or done. */
    private void roundDone() {
        close();
        // Orders that look the same may have moved up as others went: one more look round catches any left.
        if (lookAlikes && !checking && dropped > 0) {
            checking = true;
            emptied.clear();
            status = "Checking your orders once more";
            go(Stage.DROP_OPEN, pageDelay.getInt());
            return;
        }
        String none = which.is("All") ? "No orders to empty" : "You have no orders for " + name();
        String summary = ordersEmptied == 0 && dropped == 0 ? none : "Emptied " + ordersEmptied + " order" + (ordersEmptied == 1 ? "" : "s");
        if (repeat.get()) {
            result = summary;
            Notifications.push("Order Dropper", summary + " · dropped " + dropped + " · again in " + repeatEvery.getInt() + "s", Notifications.Type.INFO);
            repeatAt = ticks + repeatEvery.getInt() * 20;
            go(Stage.DROP_AGAIN, 0);
            return;
        }
        finish(summary, dropped > 0);
    }

    /** In Collect Items: throws the page out, then flips to the next page, then on to the next order. */
    private void collectPage(ScreenHandler handler) {
        boolean take = how.is("Take Then Throw");
        if (take && throwTaken(handler)) return;
        int done = 0;
        for (Slot slot : handler.slots) {
            if (done >= dropsPerTick.getInt()) break;
            ItemStack stack = slot.getStack();
            if (slot.inventory == mc.player.getInventory() || stack.isEmpty() || bottomRow(handler, slot)) continue;
            if (pageEmptied) {
                // Filled again without a page flip: the server put more on this page.
                pageEmptied = false;
                if (++refills > 400) {
                    finish("The order keeps filling up again, stopped to be safe", false);
                    return;
                }
            }
            dropped += stack.getCount();
            stacksDropped++;
            // Ctrl+Q on the slot throws the whole stack; or shift-click it into your inventory.
            if (take) click(handler, slot.id, SlotActionType.QUICK_MOVE);
            else mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
            done++;
        }
        if (take) throwTaken(handler);
        if (done > 0) {
            lastDropTick = ticks;
            deadline = ticks + 100;
            status = "Dropping · page " + dropPage + " · " + dropped + " items";
            return;
        }
        pageEmptied = true;
        // Let the server catch up with the throws (and refill the page if it does) before flipping.
        if (ticks - lastDropTick < pageDelay.getInt() + 2) return;
        if (handler.syncId == clickedSync && ticks - clickedAt < pageDelay.getInt() + 4) return;
        Slot next = named(handler, nextWords.get(), true);
        if (next == null) next = nextArrow(handler);
        if (next != null) {
            dropPage++;
            pageEmptied = false;
            status = "Page " + dropPage;
            menuClick(handler, next);
            return;
        }
        emptied.add(currentOrder == null ? "" : currentOrder);
        if (dropped > droppedAtOrder) ordersEmptied++;
        status = "Order empty · " + dropped + " items dropped";
        // Back round for the next order (another for the same block, or any with All).
        go(Stage.DROP_OPEN, pageDelay.getInt());
    }

    /** The arrow in the right half of the bottom row: the next page. */
    private Slot nextArrow(ScreenHandler handler) {
        int size = containerSize(handler);
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() || !slot.getStack().isOf(Items.ARROW) || !bottomRow(handler, slot)) continue;
            if (slot.id >= size - 4) return slot;
        }
        return null;
    }

    /** Take Then Throw: throws what landed in the inventory slots that were empty at the start; true if it threw any. */
    private boolean throwTaken(ScreenHandler handler) {
        boolean any = false;
        for (Slot slot : handler.slots) {
            if (slot.inventory != mc.player.getInventory() || slot.getStack().isEmpty() || !freeSlots.contains(slot.getIndex())) continue;
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
            any = true;
        }
        return any;
    }

    // ---- flipping: buying off the auction house ----------------------------------------------------

    private void openAuction() {
        if (!clicked) {
            if (spent >= parse(maxSpend.get(), 0) || !hasRoom()) {
                go(Stage.SELL_OPEN, delay.getInt());
                return;
            }
            close();
            command(ahCommand.get());
            clicked = true;
            page = 1;
            status = "Opening the auction house";
            return;
        }
        ScreenHandler auction = menu(ahTitle.get());
        if (auction == null) return;
        menuSync = auction.syncId;
        go(Stage.BUY_PICK, 2);
    }

    private void pickListing() {
        ScreenHandler menu = menu(ahTitle.get());
        if (menu == null) {
            finish("The auction house closed", false);
            return;
        }
        double cap = bestOrder * (1 - margin.get() / 100.0);
        double limit = parse(maxBuyEach.get(), Double.NaN);
        if (Double.isFinite(limit) && limit > 0) cap = Math.min(cap, limit);
        double budget = parse(maxSpend.get(), 0) - spent;
        int room = roomForItems();
        double maxEach = cap;
        offer = AuctionMarket.offers(menu, mc.player.getInventory(), item, "$").stream()
                .filter(o -> o.count() <= room && o.total() <= budget && o.each() <= maxEach)
                .min(java.util.Comparator.comparingDouble(AuctionMarket.Offer::each)).orElse(null);
        if (offer == null) {
            if (page < pages.getInt() && clickNamed(menu, nextWords.get())) {
                page++;
                status = "Auction page " + page;
                wait = delay.getInt() + 4;
                deadline = ticks + 100;
                return;
            }
            close();
            status = bought > 0 ? "Bought " + bought + ", selling" : "Nothing cheap enough on the auction house";
            if (bought == 0 && sellable() <= keep.getInt()) {
                finish("Nothing on the auction house under " + OrderMarket.money(cap) + " each", false);
                return;
            }
            go(Stage.SELL_OPEN, delay.getInt());
            return;
        }
        before = count();
        listingGone = false;
        status = "Buying " + offer.count() + " at " + OrderMarket.money(offer.each()) + " each";
        click(menu, offer.slot(), SlotActionType.PICKUP);
        go(Stage.BUY_CONFIRM, delay.getInt());
    }

    /** Waits for the items, confirming the purchase once if asked and only at the price chosen. */
    private void confirmPurchase() {
        if (count() >= before + offer.count()) {
            bought += offer.count();
            spent += offer.total();
            close();
            go(Stage.BUY_OPEN, delay.getInt());
            return;
        }
        if (listingGone) {
            close();
            go(Stage.BUY_OPEN, delay.getInt());
            return;
        }
        if (clicked) return;
        ScreenHandler handler = current();
        if (handler == null) return;
        // Still the listing page: the confirmation has not opened yet.
        if (handler.syncId == menuSync && menu(ahTitle.get()) != null) return;
        Slot yes = null;
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() || slot.getStack().isEmpty()) continue;
            String label = slot.getStack().getName().getString();
            if (slot.getStack().isOf(item)) {
                double shown = AuctionMarket.price(slot.getStack(), "$");
                if (Double.isFinite(shown) && Math.abs(shown - offer.total()) >= 0.01) {
                    finish("The price changed on the confirmation, so nothing was bought", false);
                    return;
                }
                continue;
            }
            if (OrderMarket.has(label, confirmWords.get()) && !OrderMarket.has(label, cancelWords.get())) yes = slot;
        }
        if (yes == null) return;
        click(handler, yes.id, SlotActionType.PICKUP);
        clicked = true;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private ScreenHandler current() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return null;
        ScreenHandler handler = screen.getScreenHandler();
        return handler == mc.player.playerScreenHandler ? null : handler;
    }

    /** The open menu if its title has one of the words and its slots have arrived. */
    private ScreenHandler menu(String words) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return null;
        ScreenHandler handler = screen.getScreenHandler();
        if (handler == mc.player.playerScreenHandler || !OrderMarket.has(screen.getTitle().getString(), words)) return null;
        for (Slot slot : handler.slots) if (slot.inventory != mc.player.getInventory() && !slot.getStack().isEmpty()) return handler;
        return null;
    }

    private boolean clickNamed(ScreenHandler handler, String words) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory() || slot.getStack().isEmpty() || slot.getStack().isOf(item)) continue;
            String label = slot.getStack().getName().getString();
            if (OrderMarket.has(label, words) && !OrderMarket.has(label, cancelWords.get())) {
                click(handler, slot.id, SlotActionType.PICKUP);
                return true;
            }
        }
        return false;
    }

    private void click(ScreenHandler handler, int slot, SlotActionType action) {
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, action, mc.player);
    }

    private void close() {
        if (mc.currentScreen instanceof HandledScreen<?>) mc.player.closeHandledScreen();
    }

    private void command(String template) {
        String id = Registries.ITEM.getId(item).getPath();
        String text = template.trim().replace("{item}", id).replace("{name}", name());
        if (text.startsWith("/")) text = text.substring(1);
        mc.getNetworkHandler().sendChatCommand(text);
    }

    private String name() {
        return item.getName().getString();
    }

    private boolean eligible(ItemStack stack) {
        if (!stack.isOf(item)) return false;
        return !skipSpecial.get() || !stack.isDamaged() && !stack.hasEnchantments() && !stack.contains(DataComponentTypes.CUSTOM_NAME);
    }

    /** How many of the item you have that may be sold. */
    private int sellable() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (eligible(stack)) n += stack.getCount();
        }
        return n;
    }

    private int count() {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) n += stack.getCount();
        }
        return n;
    }

    /** Whether the delivery window still has a slot a stack can go into. */
    private boolean roomFor(ScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || stack.isOf(item) && stack.getCount() < stack.getMaxCount()) return true;
        }
        return false;
    }

    private boolean hasRoom() {
        return roomForItems() > 0;
    }

    /** How many more of the item fit in your inventory. */
    private int roomForItems() {
        int room = 0, max = new ItemStack(item).getMaxCount();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) room += max;
            else if (stack.isOf(item)) room += Math.max(0, max - stack.getCount());
        }
        return room;
    }

    private static double parse(String text, double fallback) {
        if (text == null || text.isBlank()) return fallback;
        double v = OrderMarket.amount(text);
        return Double.isFinite(v) ? v : fallback;
    }

    private static Item itemOf(String id) {
        Identifier parsed = id == null || id.isBlank() ? null : Identifier.tryParse(id.trim());
        return parsed != null && Registries.ITEM.containsId(parsed) ? Registries.ITEM.get(parsed) : Items.AIR;
    }

    // ---- the status card ----------------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (stage == null || mc.options.hudHidden) return;
        String line1 = dropping() ? (which.is("All") ? "Emptying your orders" : "Dropping your " + name() + " orders") : (flip.get() ? "Flipping " : "Selling ") + name();
        String line2 = status;
        String line3 = dropping() ? "Dropped " + dropped + " items · " + stacksDropped + " stacks · " + ordersEmptied + " done"
                : "Sold " + sold + " · " + OrderMarket.money(earned) + (spent > 0 ? " · spent " + OrderMarket.money(spent) : "");
        Fonts.beginRaw();
        try {
            float w = Math.max(Fonts.width(line1, true, 0.75f), Math.max(Fonts.width(line2, false, 0.65f), Fonts.width(line3, false, 0.65f))) + 34;
            float x = (ctx.getScaledWindowWidth() - w) / 2f, y = 30;
            Render2D.roundRect(ctx, x, y, w, 36, 6, 0xE00D0F14);
            Render2D.roundRect(ctx, x, y, 2.5f, 36, 1.25f, Theme.accent());
            var matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x + 7, y + 10);
            ctx.drawItem(new ItemStack(item == Items.AIR ? Items.CHEST : item), 0, 0);
            matrices.popMatrix();
            Fonts.drawV(ctx, line1, x + 28, y + 9, Theme.TEXT, true, 0.75f);
            Fonts.drawV(ctx, line2, x + 28, y + 19, Theme.TEXT_DIM, false, 0.65f);
            Fonts.drawV(ctx, line3, x + 28, y + 28, 0xFF3DDC97, false, 0.65f);
        } finally {
            Fonts.endRaw();
        }
    }

    // ---- for tests ----------------------------------------------------------------------------------

    public String status() {
        return status;
    }

    public String result() {
        return result;
    }

    public int soldCount() {
        return sold;
    }

    public double earned() {
        return earned;
    }

    public double spent() {
        return spent;
    }

    public boolean running() {
        return stage != null;
    }

    public int droppedCount() {
        return dropped;
    }

    public int ordersEmptied() {
        return ordersEmptied;
    }
}
