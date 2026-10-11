package dev.maro.module.impl.player;

import dev.maro.gui.notification.Notifications;
import dev.maro.inventory.ChestSessions;
import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.OrderDropper;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.SettingSection;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Chest Stealer/Dumper behavior adapted from the supplied CC0 Custom Addon 0.1.0. */
public abstract class ChestTransfer extends Module {
    private final boolean take;
    private final BooleanSetting autoClose = add(new BooleanSetting("Auto Close", "Close after moving everything that fits", true));
    private final BooleanSetting containersOnly = add(new BooleanSetting("Containers Only", "Only real chests, barrels and ender chests you opened; leaves server menus alone", true));
    private final BooleanSetting hotbar;
    private final BooleanSetting prioritize;
    private final BooleanSetting randomize = add(new BooleanSetting("Randomize Order", "Shuffle transfer order; valuable items still go first with Smart Prioritize", false));
    private final BooleanSetting cursor = add(new BooleanSetting("Simulate Cursor", "Move the mouse to each transferred slot", true));
    private final NumberSetting speed;
    private final NumberSetting minDelay = add(new NumberSetting("Min Delay", "Minimum time between transfer batches", 10, 0, 500, 1).suffix(" ms"));
    private final NumberSetting maxDelay = add(new NumberSetting("Max Delay", "Maximum time between transfer batches", 25, 0, 500, 1).suffix(" ms"));
    private final NumberSetting initialDelay = add(new NumberSetting("Initial Delay", "Wait after opening; includes a little timing variation", 20, 0, 1000, 1).suffix(" ms"));
    private final NumberSetting closeDelay = add(new NumberSetting("Close Delay", "Wait before closing; also leaves time for server corrections", 15, 0, 1000, 1).suffix(" ms"));
    private final Map<Integer, Attempt> attempts = new HashMap<>();
    private GenericContainerScreenHandler handler;
    private ClientPlayNetworkHandler connection;
    private Object world;
    private long nextAction, closeAt, lastMove;
    private boolean moved;
    private String status = "Off";

    protected ChestTransfer(String name, boolean take) {
        super(name, take ? "Takes items from opened chests, with valuable items first" : "Puts your inventory into opened chests, keeping your hotbar by default", Category.PLAYER);
        this.take = take;
        hotbar = take ? null : add(new BooleanSetting("Dump Hotbar", "Also put your hotbar into the chest; armour and offhand are kept", false));
        prioritize = take ? add(new BooleanSetting("Smart Prioritize", "Shulkers, elytra, totems and valuable gear first", true)) : null;
        speed = add(new NumberSetting("Slots Per Tick", "Maximum shift-clicks each game tick", 2, 1, take ? 54 : 36, 1));
    }

    @Override
    public List<SettingSection> getSettingSections() {
        List<Setting<?>> delays = List.of(minDelay, maxDelay, initialDelay, closeDelay);
        SettingSection general = new SettingSection("General");
        getSettings().stream().filter(s -> !delays.contains(s)).forEach(general::add);
        return List.of(general, SettingSection.of("Delays", minDelay, maxDelay, initialDelay, closeDelay));
    }

    @Override
    protected void onEnable() {
        ChestTransfer other = take ? ModuleManager.get(ChestDumper.class) : ModuleManager.get(ChestStealer.class);
        if (other != null) other.setEnabled(false);
        reset();
        status = "Open a chest";
    }

    @Override
    protected void onDisable() { reset(); }

    private void reset() {
        handler = null;
        connection = null;
        world = null;
        attempts.clear();
        nextAction = closeAt = lastMove = 0;
        moved = false;
    }

    public String status() { return status; }

    @Override
    public void onTick() {
        if (!inGame() || mc.interactionManager == null || mc.getNetworkHandler() == null) { reset(); return; }
        AutoBuilder builder = ModuleManager.get(AutoBuilder.class);
        OrderDropper orders = ModuleManager.get(OrderDropper.class);
        if (builder != null && builder.isEnabled() && builder.busy() || orders != null && orders.isEnabled()) {
            reset();
            status = "Waiting for the other transfer";
            return;
        }
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)
                || mc.player.currentScreenHandler != screen.getScreenHandler()) { reset(); status = "Open a chest"; return; }
        GenericContainerScreenHandler current = screen.getScreenHandler();
        if (containersOnly.get() && !ChestSessions.physical(current)) { reset(); status = "Server menu left alone"; return; }
        if (!ChestSessions.ready(current)) { status = "Waiting for chest contents"; return; }
        long now = System.nanoTime();
        if (handler != current || connection != mc.getNetworkHandler() || world != mc.world) {
            reset();
            handler = current;
            connection = mc.getNetworkHandler();
            world = mc.world;
            nextAction = now + jitter(initialDelay.getInt()) * 1_000_000L;
        }
        if (!handler.getCursorStack().isEmpty()) { closeAt = 0; status = "Put down the held item to continue"; return; }
        if (now < nextAction) return;

        int chestSize = handler.getRows() * 9;
        List<Integer> targets = new ArrayList<>();
        boolean rejected = false;
        int start = take ? 0 : chestSize;
        int end = take ? chestSize : Math.min(handler.slots.size(), chestSize + (hotbar.get() ? 36 : 27));
        for (int i = start; i < end; i++) {
            Slot source = handler.getSlot(i);
            if (!source.hasStack() || !source.canTakeItems(mc.player) || !fits(source.getStack(), chestSize)) continue;
            Attempt previous = attempts.get(i);
            if (previous != null && previous.count >= 3 && ItemStack.areEqual(previous.stack, source.getStack())) { rejected = true; continue; }
            targets.add(i);
        }
        if (targets.isEmpty()) {
            if (rejected) {
                status = "Transfers rejected; reopen the chest to retry";
                Notifications.push(getName(), status, Notifications.Type.WARNING);
                setEnabled(false);
                return;
            }
            status = moved ? "Finished; everything that fits was moved" : "Nothing to move, or no matching space";
            if (!autoClose.get() || !moved) return;
            if (closeAt == 0) closeAt = Math.max(now + jitter(closeDelay.getInt()) * 1_000_000L, lastMove + settleMillis() * 1_000_000L);
            if (now >= closeAt) { mc.player.closeHandledScreen(); reset(); }
            return;
        }
        closeAt = 0;
        if (randomize.get()) Collections.shuffle(targets);
        if (take && prioritize.get()) targets.sort(Comparator.comparingInt((Integer i) -> priority(handler.getSlot(i).getStack())).reversed());
        int budget = speed.getInt();
        for (int i : targets) {
            Slot source = handler.getSlot(i);
            if (!fits(source.getStack(), chestSize)) continue;
            ItemStack before = source.getStack().copy();
            Attempt previous = attempts.get(i);
            attempts.put(i, new Attempt(before, previous != null && ItemStack.areEqual(previous.stack, before) ? previous.count + 1 : 1));
            if (cursor.get()) pointCursor(screen, source);
            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
            if (!ItemStack.areEqual(before, source.getStack())) { moved = true; lastMove = now; }
            if (--budget == 0) break;
        }
        int min = minDelay.getInt(), max = Math.max(min, maxDelay.getInt());
        nextAction = now + ThreadLocalRandom.current().nextInt(min, max + 1) * 1_000_000L;
        status = take ? "Taking chest items" : "Storing inventory";
    }

    private boolean fits(ItemStack incoming, int chestSize) {
        if (incoming.isEmpty()) return false;
        int start = take ? chestSize : 0, end = take ? handler.slots.size() : chestSize;
        for (int i = start; i < end; i++) {
            Slot destination = handler.getSlot(i);
            if (!destination.canInsert(incoming)) continue;
            ItemStack current = destination.getStack();
            int limit = destination.getMaxItemCount(incoming);
            if (limit > 0 && (current.isEmpty() || ItemStack.areItemsAndComponentsEqual(current, incoming) && current.getCount() < limit)) return true;
        }
        return false;
    }

    private long settleMillis() {
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return Math.max(150, Math.min(2000, entry == null ? 150 : entry.getLatency() * 2L + 50));
    }

    private static int jitter(int delay) { return delay + (delay > 2 ? ThreadLocalRandom.current().nextInt(Math.max(1, delay / 3)) : 0); }

    private static void pointCursor(GenericContainerScreen screen, Slot slot) {
        var window = mc.getWindow();
        float left = (screen.width - 176) / 2f, top = (screen.height - (114 + screen.getScreenHandler().getRows() * 18)) / 2f;
        int[] width = new int[1], height = new int[1];
        GLFW.glfwGetWindowSize(window.getHandle(), width, height);
        double x = (left + slot.x + 8) * width[0] / screen.width;
        double y = (top + slot.y + 8) * height[0] / screen.height;
        GLFW.glfwSetCursorPos(window.getHandle(), x, y);
    }

    private static int priority(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        if (id.contains("shulker")) return 1000;
        if (id.equals("elytra")) return 950;
        if (id.equals("totem_of_undying")) return 900;
        if (id.contains("netherite")) return 850;
        if (id.equals("enchanted_golden_apple")) return 800;
        if (id.equals("golden_apple")) return 750;
        if (id.contains("diamond")) return 700;
        if (id.contains("crystal")) return 650;
        if (id.equals("respawn_anchor")) return 620;
        if (id.contains("obsidian")) return 600;
        if (id.contains("glowstone")) return 580;
        if (id.contains("potion")) return 550;
        if (id.contains("experience") || id.equals("emerald") || id.equals("gold_ingot")) return 500;
        return stack.hasEnchantments() ? 400 : 100 + Math.min(64, stack.getCount());
    }

    private record Attempt(ItemStack stack, int count) { }
}
