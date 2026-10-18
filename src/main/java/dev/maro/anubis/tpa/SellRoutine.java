// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.tpa;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import dev.maro.anubis.ModuleChat;
import dev.maro.anubis.ClientModule;
import dev.maro.anubis.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;

@Environment(value=EnvType.CLIENT)
final class SellRoutine {
    private static final int INVENTORY_SLOTS = 36;
    private static final int CONFIRM_SLOT = 53;
    private static final int MAX_CONFIRMS = 3;
    private static final int STRIKES_TO_SKIP = 2;
    private static final int MAX_RESENDS = 3;
    private static final long CHECK_EVERY_MS = 500L;
    private static final long COMMAND_COOLDOWN_MS = 4000L;
    private static final long OPEN_TIMEOUT_MS = 10000L;
    private static final long STRAY_MENU_MS = 5000L;
    private static final long FAIL_COOLDOWN_MS = 30000L;
    private static final long CONFIRM_COOLDOWN_MS = 900L;
    private static final long POST_CONFIRM_MS = 250L;
    private static final long SKIP_MS = 600000L;
    private final ClientModule owner;
    private final StringListSetting neverSell;
    private final BiPredicate<Integer, ItemStack> keep;
    private final Runnable onCommand;
    private State state = State.IDLE;
    private long nextCheckAt;
    private long lastCommandAt;
    private long deadline;
    private boolean rejected;
    private long retryAt;
    private int resends;
    private int menuId = -1;
    private int confirms;
    private final Map<Item, Integer> offered = new LinkedHashMap<Item, Integer>();
    private final Map<Item, Integer> strikes = new HashMap<Item, Integer>();
    private final Map<Item, Long> skipUntil = new HashMap<Item, Long>();

    SellRoutine(ClientModule owner, StringListSetting neverSell, BiPredicate<Integer, ItemStack> keep, Runnable onCommand) {
        this.owner = owner;
        this.neverSell = neverSell;
        this.keep = keep;
        this.onCommand = onCommand;
    }

    void reset() {
        this.state = State.IDLE;
        this.nextCheckAt = 0L;
        this.lastCommandAt = 0L;
        this.rejected = false;
        this.menuId = -1;
        this.offered.clear();
        this.strikes.clear();
        this.skipUntil.clear();
    }

    boolean active() {
        return this.state != State.IDLE;
    }

    void commandRejected(long retryAt) {
        if (this.state != State.OPENING) {
            return;
        }
        this.rejected = true;
        this.retryAt = retryAt;
    }

    void abort(MinecraftClient mc) {
        if (this.state == State.IDLE) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player != null && player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId) {
            player.closeHandledScreen();
        }
        this.state = State.IDLE;
        this.menuId = -1;
        this.offered.clear();
        this.nextCheckAt = System.currentTimeMillis() + 500L;
    }

    boolean step(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now, boolean canCommand) {
        return switch (this.state.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> this.stepIdle(mc, player, connection, now, canCommand);
            case 1 -> this.stepOpening(mc, player, connection, now);
            case 2 -> this.stepMoving(mc, player, now);
            case 3 -> this.stepConfirming(mc, player, now);
        };
    }

    private boolean stepIdle(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now, boolean canCommand) {
        if (now - this.lastCommandAt < 5000L && SellRoutine.sellMenuOpen(mc, player)) {
            player.closeHandledScreen();
            return true;
        }
        if (now < this.nextCheckAt) {
            return false;
        }
        this.nextCheckAt = now + 500L;
        if (mc.currentScreen != null || !canCommand || now - this.lastCommandAt < 4000L) {
            return false;
        }
        if (!this.hasSellable(player.getInventory(), now)) {
            return false;
        }
        this.resends = 0;
        this.send(connection, now);
        return true;
    }

    private void send(ClientPlayNetworkHandler connection, long now) {
        connection.sendChatCommand("sell");
        this.lastCommandAt = now;
        this.onCommand.run();
        this.deadline = now + 10000L;
        this.rejected = false;
        this.state = State.OPENING;
    }

    private boolean stepOpening(MinecraftClient mc, ClientPlayerEntity player, ClientPlayNetworkHandler connection, long now) {
        if (SellRoutine.sellMenuOpen(mc, player)) {
            this.menuId = player.currentScreenHandler.syncId;
            this.state = State.MOVING;
            return true;
        }
        if (this.rejected && now >= this.retryAt && this.resends < 3) {
            ++this.resends;
            this.send(connection, now);
            return true;
        }
        if (now < this.deadline) {
            return true;
        }
        this.state = State.IDLE;
        ModuleChat.send(this.owner, ModuleChat.body().warn("The sell menu did not open").text(", trying again in 30s."));
        this.nextCheckAt = now + 30000L;
        return false;
    }

    private boolean stepMoving(MinecraftClient mc, ClientPlayerEntity player, long now) {
        ScreenHandler menu = player.currentScreenHandler;
        if (!this.ours(player)) {
            return this.finish(null, now);
        }
        int moved = 0;
        for (Slot slot : menu.slots) {
            int index;
            if (!(slot.inventory instanceof PlayerInventory) || !slot.hasStack() || (index = slot.getIndex()) >= 36 || !this.sellable(index, slot.getStack(), now)) continue;
            mc.interactionManager.clickSlot(menu.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, (PlayerEntity)player);
            ++moved;
        }
        if (moved == 0) {
            return this.close(player, null, now);
        }
        this.offered.clear();
        this.offered.putAll(SellRoutine.waitingItems(menu));
        this.confirms = 0;
        this.deadline = now;
        this.state = State.CONFIRMING;
        return true;
    }

    private boolean stepConfirming(MinecraftClient mc, ClientPlayerEntity player, long now) {
        if (!this.ours(player)) {
            return this.finish(null, now);
        }
        if (now < this.deadline) {
            return true;
        }
        ScreenHandler menu = player.currentScreenHandler;
        if (this.confirms < 3 && SellRoutine.itemsWaiting(menu)) {
            mc.interactionManager.clickSlot(menu.syncId, 53, 0, SlotActionType.PICKUP, (PlayerEntity)player);
            ++this.confirms;
            this.deadline = now + (this.confirms == 1 ? 250L : 900L);
            return true;
        }
        return this.close(player, SellRoutine.waitingItems(menu), now);
    }

    private boolean close(ClientPlayerEntity player, Map<Item, Integer> left, long now) {
        if (this.ours(player)) {
            player.closeHandledScreen();
        }
        return this.finish(left, now);
    }

    private boolean finish(Map<Item, Integer> left, long now) {
        if (left != null) {
            this.settle(left, now);
        }
        this.offered.clear();
        this.menuId = -1;
        this.state = State.IDLE;
        this.nextCheckAt = now + 500L;
        return false;
    }

    private void settle(Map<Item, Integer> left, long now) {
        int sold = 0;
        for (Map.Entry<Item, Integer> entry : this.offered.entrySet()) {
            Item item = entry.getKey();
            int remaining = left.getOrDefault(item, 0);
            sold += Math.max(0, entry.getValue() - remaining);
            if (remaining <= 0) {
                this.strikes.remove(item);
                continue;
            }
            if (this.strikes.merge(item, 1, Integer::sum) < 2) continue;
            this.strikes.remove(item);
            this.skipUntil.put(item, now + 600000L);
            ModuleChat.send(this.owner, ModuleChat.body().warn("Could not sell ").name(item.getName().getString()).text(", skipping it for 10 minutes."));
        }
        if (sold > 0) {
            ModuleChat.send(this.owner, ModuleChat.body().text("Sold ").value(sold).text(sold == 1 ? " item." : " items."));
        }
    }

    private boolean ours(ClientPlayerEntity player) {
        return player.currentScreenHandler != player.playerScreenHandler && player.currentScreenHandler.syncId == this.menuId;
    }

    private boolean hasSellable(PlayerInventory inventory, long now) {
        for (int index = 0; index < 36; ++index) {
            ItemStack stack = inventory.getStack(index);
            if (stack.isEmpty() || !this.sellable(index, stack, now)) continue;
            return true;
        }
        return false;
    }

    private boolean sellable(int index, ItemStack stack, long now) {
        return !this.skipped(stack.getItem(), now) && !this.keep.test(index, stack) && !this.blacklisted(stack);
    }

    private boolean skipped(Item item, long now) {
        Long until = this.skipUntil.get(item);
        if (until == null) {
            return false;
        }
        if (now < until) {
            return true;
        }
        this.skipUntil.remove(item);
        return false;
    }

    private boolean blacklisted(ItemStack stack) {
        return SellRoutine.neverSell(stack, this.neverSell.get());
    }

    static boolean neverSell(ItemStack stack, List<String> entries) {
        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        String shortName = id.substring(id.indexOf(58) + 1);
        for (String raw : entries) {
            String entry = raw.trim().toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) continue;
            if (id.equals(entry) || shortName.equals(entry)) {
                return true;
            }
            if (entry.indexOf(58) >= 0 || !id.contains(entry)) continue;
            return true;
        }
        return false;
    }

    private static boolean itemsWaiting(ScreenHandler menu) {
        for (Slot slot : menu.slots) {
            if (slot.inventory instanceof PlayerInventory || slot.id == 53 || !slot.hasStack()) continue;
            return true;
        }
        return false;
    }

    private static Map<Item, Integer> waitingItems(ScreenHandler menu) {
        LinkedHashMap<Item, Integer> items = new LinkedHashMap<Item, Integer>();
        for (Slot slot : menu.slots) {
            if (slot.inventory instanceof PlayerInventory || slot.id == 53 || !slot.hasStack()) continue;
            items.merge(slot.getStack().getItem(), slot.getStack().getCount(), Integer::sum);
        }
        return items;
    }

    private static boolean sellMenuOpen(MinecraftClient mc, ClientPlayerEntity player) {
        ScreenHandler menu = player.currentScreenHandler;
        return menu != player.playerScreenHandler && mc.currentScreen instanceof HandledScreen && SellRoutine.containerSlots(menu) > 53;
    }

    private static int containerSlots(ScreenHandler menu) {
        int count = 0;
        for (Slot slot : menu.slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            ++count;
        }
        return count;
    }

    @Environment(value=EnvType.CLIENT)
    private static enum State {
        IDLE,
        OPENING,
        MOVING,
        CONFIRMING;

    }
}
