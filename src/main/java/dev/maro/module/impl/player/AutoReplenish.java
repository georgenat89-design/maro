package dev.maro.module.impl.player;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.OrderDropper;
import dev.maro.setting.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Meteor Auto Replenish behavior reference; native Maro inventory transactions and policies. */
public final class AutoReplenish extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Refill At",
        "Top up stackable items when their count reaches this amount", 8, 1, 63, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay",
        "Minimum ticks between refills; server settling time also applies", 2, 0, 20, 1).suffix(" ticks"));
    private final ModeSetting scope = add(new ModeSetting("Hotbar",
        "Which hotbar slots to replenish", "All Slots", "All Slots", "Held Slot"));
    private final BooleanSetting offhand = add(new BooleanSetting("Offhand",
        "Replenish your offhand first, including used totems", true));
    private final BooleanSetting remember = add(new BooleanSetting("Remember Empty",
        "Keep an empty slot's item remembered until supplies return or you edit your inventory", true));
    private final BooleanSetting unstackables = add(new BooleanSetting("Unstackables",
        "Replace consumed or broken single-stack items with matching spares", true));
    private final BooleanSetting wornTools = add(new BooleanSetting("Replace Worn Tools",
        "Swap nearly broken gear for a healthier matching spare", false));
    private final NumberSetting durability = add(new NumberSetting("Tool Durability",
        "Replace gear at or below this remaining durability percentage", 10, 1, 50, 1)
        .suffix("%").visible(wornTools::get));
    private final TextSetting excluded = add(new TextSetting("Excluded Items",
        "Comma-separated item IDs to leave alone", "", 1024, "minecraft:ender_pearl, minecraft:totem_of_undying"));

    private final ItemStack[] remembered = new ItemStack[10];
    private final Failure[] failures = new Failure[10];
    private Object player, world, connection;
    private boolean resnapshot;
    private int tick, nextAction;
    private Pending pending;
    private String parsedExclusions = "";
    private Set<String> exclusions = Set.of();
    private String status = "Off";

    public AutoReplenish() {
        super("Auto Replenish", "Keep your hotbar and offhand stocked without changing your selected slot", Category.PLAYER);
        reset();
    }

    @Override public List<SettingSection> getSettingSections() {
        return List.of(SettingSection.of("Refill", threshold, delay, scope, offhand, remember),
            SettingSection.of("Equipment", unstackables, wornTools, durability),
            SettingSection.of("Filter", excluded));
    }

    @Override protected void onEnable() { reset(); status = "Ready"; }
    @Override protected void onDisable() { reset(); status = "Off"; }
    public String status() { return status; }

    private void reset() {
        Arrays.fill(remembered, ItemStack.EMPTY);
        Arrays.fill(failures, null);
        player = world = connection = null;
        pending = null;
        tick = nextAction = 0;
        resnapshot = true;
    }

    @Override public void onTick() {
        if (!inGame() || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            reset(); status = "Waiting for a player"; return;
        }
        if (player != mc.player || world != mc.world || connection != mc.getNetworkHandler()) {
            reset(); player = mc.player; world = mc.world; connection = mc.getNetworkHandler();
        }
        tick++;
        var builder = ModuleManager.get(AutoBuilder.class);
        var orders = ModuleManager.get(OrderDropper.class);
        if (!mc.player.isAlive() || mc.player.isSpectator() || mc.currentScreen != null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.playerScreenHandler.getCursorStack().isEmpty()
            || builder != null && builder.isEnabled() && builder.busy()
            || orders != null && orders.isEnabled()) {
            resnapshot = true; pending = null; status = "Paused for inventory changes"; return;
        }
        if (resnapshot) {
            for (int i = 0; i < 10; i++) remembered[i] = stack(i).copy();
            Arrays.fill(failures, null);
            resnapshot = false;
        }
        if (pending != null) {
            if (tick < pending.until) { status = "Waiting for inventory sync"; return; }
            if (ItemStack.areEqual(stack(pending.target), pending.beforeTarget)
                && ItemStack.areEqual(mc.player.getInventory().getStack(pending.source), pending.beforeSource)) {
                Failure previous = failures[pending.target];
                int attempts = previous != null && previous.same(pending.source, pending.beforeTarget, pending.beforeSource)
                    ? previous.attempts + 1 : 1;
                failures[pending.target] = new Failure(pending.source, pending.beforeTarget, pending.beforeSource,
                    attempts, attempts >= 3 ? tick + 100 : tick);
            } else failures[pending.target] = null;
            pending = null;
        }
        // Remember each nonempty slot, retaining empty-slot identity while supplies are absent.
        for (int i = 0; i < 10; i++) {
            ItemStack current = stack(i);
            if (!current.isEmpty() && !ItemStack.areItemsAndComponentsEqual(current, remembered[i]))
                remembered[i] = current.copyWithCount(1);
            else if (current.isEmpty() && !remember.get()) remembered[i] = ItemStack.EMPTY;
        }
        if (mc.player.isUsingItem()) { status = "Waiting for item use"; return; }
        if (tick < nextAction) return;
        status = "Stocked";
        int selected = mc.player.getInventory().getSelectedSlot();
        // A depleted offhand and selected stack take priority over the other hotbar slots.
        if (offhand.get() && check(9)) return;
        if (check(selected)) return;
        if (scope.is("All Slots")) for (int i = 0; i < 9; i++) if (i != selected && check(i)) return;
    }

    private boolean check(int target) {
        ItemStack current = stack(target);
        ItemStack wanted = current.isEmpty() ? remembered[target] : current;
        if (wanted.isEmpty() || excluded(wanted)) return false;
        boolean worn = !current.isEmpty() && wornTools.get() && current.isDamageable()
            && remaining(current) <= current.getMaxDamage() * durability.get() / 100.0;
        if (current.isEmpty()) {
            if (!remember.get() || !wanted.isStackable() && !unstackables.get()) return false;
        } else if (!worn && (!current.isStackable() || current.getCount() > threshold.getInt()
            || current.getCount() >= current.getMaxCount())) return false;

        int source = -1, best = -1;
        // Storage only: never borrow equipped armour, the offhand, or another hotbar slot.
        for (int i = 9; i < 36; i++) {
            ItemStack candidate = mc.player.getInventory().getStack(i);
            if (candidate.isEmpty() || !matches(wanted, candidate, !current.isEmpty() && current.isStackable() && !worn)) continue;
            if (worn && remaining(candidate) <= candidate.getMaxDamage() * durability.get() / 100.0) continue;
            int score = candidate.isDamageable() ? remaining(candidate) : candidate.getCount();
            if (score > best) { source = i; best = score; }
        }
        if (source < 0) { status = "Waiting for matching supplies"; return false; }
        ItemStack incoming = mc.player.getInventory().getStack(source);
        Failure failed = failures[target];
        if (failed != null && failed.same(source, current, incoming) && tick < failed.retryAt) {
            status = "Server rejected refill; backing off"; return false;
        }
        refill(target, source, worn);
        return true;
    }

    private static int remaining(ItemStack stack) { return stack.getMaxDamage() - stack.getDamage(); }

    private static boolean matches(ItemStack wanted, ItemStack candidate, boolean combine) {
        if (!wanted.isOf(candidate.getItem())) return false;
        if (combine || !wanted.isDamageable()) return ItemStack.areItemsAndComponentsEqual(wanted, candidate);
        ItemStack a = wanted.copy(), b = candidate.copy();
        a.set(DataComponentTypes.DAMAGE, 0);
        b.set(DataComponentTypes.DAMAGE, 0);
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }

    private ItemStack stack(int target) {
        return target == 9 ? mc.player.getOffHandStack() : mc.player.getInventory().getStack(target);
    }

    private static int slotId(int target) { return target == 9 ? 45 : 36 + target; }

    private void refill(int target, int source, boolean worn) {
        var handler = mc.player.playerScreenHandler;
        ItemStack beforeTarget = stack(target).copy();
        ItemStack beforeSource = mc.player.getInventory().getStack(source).copy();
        if (worn || beforeTarget.isEmpty() || !beforeTarget.isStackable()) {
            mc.interactionManager.clickSlot(handler.syncId, source, target == 9 ? 40 : target, SlotActionType.SWAP, mc.player);
        } else {
            // Complete the merge in one tick, then return overflow to its original slot.
            // No cursor item remains between frames, including when every inventory slot is full.
            mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
            try {
                mc.interactionManager.clickSlot(handler.syncId, slotId(target), 0, SlotActionType.PICKUP, mc.player);
            } finally {
                if (!handler.getCursorStack().isEmpty())
                    mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
            }
        }
        remembered[target] = stack(target).copyWithCount(1);
        int settle = settleTicks();
        pending = new Pending(target, source, beforeTarget, beforeSource, tick + settle);
        nextAction = tick + Math.max(settle, delay.getInt());
        status = "Replenished " + (target == 9 ? "offhand" : "hotbar " + (target + 1));
    }

    private int settleTicks() {
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return Math.max(3, Math.min(40, (entry == null ? 0 : entry.getLatency()) * 2 / 50 + 3));
    }

    private boolean excluded(ItemStack stack) {
        if (!parsedExclusions.equals(excluded.get())) {
            parsedExclusions = excluded.get();
            Set<String> ids = new HashSet<>();
            for (String raw : parsedExclusions.toLowerCase(Locale.ROOT).split("[,;\\s]+")) {
                if (!raw.isBlank()) ids.add(raw.contains(":") ? raw : "minecraft:" + raw);
            }
            exclusions = Set.copyOf(ids);
        }
        return exclusions.contains(Registries.ITEM.getId(stack.getItem()).toString());
    }

    private record Pending(int target, int source, ItemStack beforeTarget, ItemStack beforeSource, int until) { }
    private record Failure(int source, ItemStack targetStack, ItemStack sourceStack, int attempts, int retryAt) {
        boolean same(int from, ItemStack target, ItemStack incoming) {
            return source == from && ItemStack.areEqual(targetStack, target) && ItemStack.areEqual(sourceStack, incoming);
        }
    }
}
