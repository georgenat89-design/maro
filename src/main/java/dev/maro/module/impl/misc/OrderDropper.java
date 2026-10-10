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
 * Order Dropper, for DonutSMP's orders: sells an item into the orders that pay most for it, fast.
 * It opens the orders for the item, picks the best-paying order that still wants some, clicks it
 * and drops your stacks into the delivery window several a tick, then goes on to the next order
 * until you are sold out or no order pays your minimum.
 *
 * <p>Flip buys the item off the auction house first, below what the best order pays by your
 * margin and within your spend, then sells it into the orders. A purchase is only confirmed when
 * the confirmation shows the price it was chosen at.
 *
 * <p>Every menu word is a setting, as server menus change. Anything unexpected (a menu that does not
 * open, a price that moved, an order that takes nothing) stops it with a message rather than guessing.
 */
public class OrderDropper extends Module {
    private enum Stage {SCAN_OPEN, SCAN_READ, BUY_OPEN, BUY_PICK, BUY_CONFIRM, SELL_OPEN, SELL_PICK, DELIVER_OPEN, DELIVER, DELIVER_FINISH}

    // ---- what to sell
    private final BooleanSetting useHeld = add(new BooleanSetting("Use Held Item", "Sell what is in your hand when it starts", true));
    private final TextSetting itemId = add(new TextSetting("Item", "The item to sell or flip", "", 64, "None").visible(() -> !useHeld.get()));
    private final ButtonSetting pickItem = add(new ButtonSetting("Pick Item", "Choose it from every item", "Edit",
            () -> mc.setScreen(new ItemPickerScreen(mc.currentScreen, "Item", picked -> itemId.set(Registries.ITEM.getId(picked).toString()))))
            .visible(() -> !useHeld.get()));

    // ---- selling
    private final TextSetting minPrice = add(new TextSetting("Min Price", "Only orders paying at least this per item (1.5k, 2m)", "0", 16, "0"));
    private final NumberSetting keep = add(new NumberSetting("Keep", "How many to keep for yourself", 0, 0, 2304, 1));
    private final BooleanSetting skipSpecial = add(new BooleanSetting("Skip Special Items", "Never sell enchanted, damaged or renamed ones", true));
    private final NumberSetting stacksPerTick = add(new NumberSetting("Stacks Per Tick", "How many stacks it drops into an order each tick", 4, 1, 9, 1));
    private final NumberSetting delay = add(new NumberSetting("Action Delay", "Ticks between menu clicks (more if the server is slow)", 2, 0, 20, 1).suffix(" ticks"));
    private final BooleanSetting keepGoing = add(new BooleanSetting("Keep Going", "After an order, go on to the next until sold out", true));
    private final NumberSetting pages = add(new NumberSetting("Pages To Search", "How many pages of orders or listings it looks through", 3, 1, 10, 1));

    // ---- flipping
    private final BooleanSetting flip = add(new BooleanSetting("Flip", "Buy it off the auction house below the order price, then sell it into orders", false));
    private final NumberSetting margin = add(new NumberSetting("Min Margin", "Buy only this much under what the best order pays", 10, 1, 90, 1)
            .suffix("%").visible(flip::get));
    private final TextSetting maxSpend = add(new TextSetting("Max Spend", "The most it spends on the auction house in one go", "100k", 16, "100k")
            .visible(flip::get));
    private final TextSetting maxBuyEach = add(new TextSetting("Max Buy Each", "Never pay more than this per item (empty: only the margin)", "", 16, "None")
            .visible(flip::get));
    private final NumberSetting rounds = add(new NumberSetting("Flip Rounds", "Buy and sell rounds before it stops", 3, 1, 50, 1).visible(flip::get));

    // ---- the server's menus
    private final TextSetting ordersCommand = add(new TextSetting("Orders Command", "Opens the orders for the item ({item} is its id, {name} its name)", "orders {item}", 64, "orders {item}"));
    private final TextSetting ahCommand = add(new TextSetting("AH Command", "Searches the auction house for the item", "ah {item}", 64, "ah {item}").visible(flip::get));
    private final TextSetting ordersTitle = add(new TextSetting("Orders Title", "Words in the orders menu's title, separated by ;", "order", 64, "order"));
    private final TextSetting deliverTitle = add(new TextSetting("Deliver Title", "Words in the delivery window's title", "deliver;sell;fill", 64, "deliver"));
    private final TextSetting ahTitle = add(new TextSetting("AH Title", "Words in the auction house's title", "auction;ah", 64, "auction").visible(flip::get));
    private final TextSetting priceWords = add(new TextSetting("Price Words", "Words on the line with an order's price per item", "each;per;price", 64, "each"));
    private final TextSetting confirmWords = add(new TextSetting("Confirm Words", "Names of confirm buttons", "confirm;deliver;sell;yes;buy;purchase", 128, "confirm"));
    private final TextSetting cancelWords = add(new TextSetting("Cancel Words", "Names of cancel buttons", "cancel;no;back;close;deny", 64, "cancel"));
    private final TextSetting nextWords = add(new TextSetting("Next Page Words", "Names of the next page button", "next", 64, "next"));

    private final List<SettingSection> sections = List.of(
            SettingSection.of("Item", useHeld, itemId, pickItem),
            SettingSection.of("Selling", minPrice, keep, skipSpecial, stacksPerTick, delay, keepGoing, pages),
            SettingSection.of("Flip", flip, margin, maxSpend, maxBuyEach, rounds),
            SettingSection.of("Menus", ordersCommand, ahCommand, ordersTitle, deliverTitle, ahTitle, priceWords, confirmWords, cancelWords, nextWords));

    private Item item = Items.AIR;
    private Stage stage;
    private int wait, deadline, ticks, page, roundsDone, deliverSync = -1, menuSync = -1, phase;
    private double orderEach, bestOrder, spent, earned;
    private int sold, bought, before, moved, remaining;
    private boolean clicked;
    private AuctionMarket.Offer offer;
    private volatile boolean listingGone;
    private String status = "Off", result = "";

    public OrderDropper() {
        super("Order Dropper", "Sells an item into DonutSMP's best-paying orders fast, and can flip it from the auction house first", Category.MISC);
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
        sold = bought = roundsDone = page = 0;
        spent = earned = 0;
        if (!inGame()) {
            setEnabled(false);
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
                case SCAN_OPEN, SELL_OPEN -> "The orders menu did not open";
                case BUY_OPEN -> "The auction house did not open";
                case DELIVER_OPEN -> "The order's delivery window did not open";
                case BUY_CONFIRM -> "The purchase was not confirmed";
                default -> "The server stopped answering";
            }, false);
            return;
        }
        switch (stage) {
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
        String line1 = (flip.get() ? "Flipping " : "Selling ") + name(), line2 = status;
        String line3 = "Sold " + sold + " · " + OrderMarket.money(earned) + (spent > 0 ? " · spent " + OrderMarket.money(spent) : "");
        Fonts.beginRaw();
        try {
            float w = Math.max(Fonts.width(line1, true, 0.75f), Math.max(Fonts.width(line2, false, 0.65f), Fonts.width(line3, false, 0.65f))) + 34;
            float x = (ctx.getScaledWindowWidth() - w) / 2f, y = 30;
            Render2D.roundRect(ctx, x, y, w, 36, 6, 0xE00D0F14);
            Render2D.roundRect(ctx, x, y, 2.5f, 36, 1.25f, Theme.accent());
            var matrices = ctx.getMatrices();
            matrices.pushMatrix();
            matrices.translate(x + 7, y + 10);
            ctx.drawItem(new ItemStack(item), 0, 0);
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
}
