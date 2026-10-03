package dev.maro.module.impl.combat;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/** Equips through the normal inventory interaction path, without disturbing the cursor stack. */
public final class AutoTotem extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Keep a totem equipped, or equip below the health threshold",
        "Always", "Always", "Low Health"));
    private final NumberSetting health = add(new NumberSetting("Health", "Equip at or below this many hearts",
        6, .5, 10, .5).suffix(" hearts").visible(() -> mode.is("Low Health")));
    private final BooleanSetting absorption = add(new BooleanSetting("Include Absorption", "Count absorption hearts toward the threshold",
        true).visible(() -> mode.is("Low Health")));
    private final BooleanSetting noDelay = add(new BooleanSetting("No Delay", "Set Delay to zero for immediate reaction on eligible ticks", true)
        .onChange(this::noDelayChanged));
    private final NumberSetting strength = add(new NumberSetting("Strength", "Higher strength reacts faster; 10 adds no reaction wait",
        10, 1, 10, 1).visible(() -> !noDelay.get()));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Reaction delay, 0–10 ticks; zero bypasses all timing waits",
        0, 0, 10, 1).suffix(" ticks").onChange(this::delayChanged));
    private final NumberSetting retryDelay = add(new NumberSetting("Retry Delay", "Minimum ticks between swap attempts",
        2, 1, 40, 1).suffix(" ticks").visible(() -> !noDelay.get()));
    private final BooleanSetting inventoryOnly = add(new BooleanSetting("Inventory Only", "Equip only while your inventory screen is open", false));
    private final BooleanSetting pauseUsing = add(new BooleanSetting("Pause While Using", "Wait while eating, drinking, blocking or using an item", true));
    private final BooleanSetting emptyOnly = add(new BooleanSetting("Empty Offhand Only", "Keep an existing offhand item instead of replacing it", false));

    private ClientPlayerEntity trackedPlayer;
    private int source = -1, eligibleTicks, cooldown;
    private int previousDelay = 2;

    public AutoTotem() {
        super("Auto Totem", "Automatically equip an available totem in your offhand", Category.COMBAT);
    }

    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }

    private void reset() {
        trackedPlayer = null; source = -1; eligibleTicks = 0; cooldown = 0;
    }
    private void cancelPending() { source = -1; eligibleTicks = 0; }

    private void noDelayChanged(Boolean enabled) {
        if (enabled) delay.set(0.0);
        else if (delay.getInt() == 0) delay.set((double) previousDelay);
        cancelPending(); cooldown = 0;
    }
    private void delayChanged(Double ticks) {
        if (ticks > 0) previousDelay = (int) Math.round(ticks);
        noDelay.set(ticks == 0);
        cancelPending(); cooldown = 0;
    }

    @Override public void onTick() {
        if (!isEnabled() || !inGame() || mc.interactionManager == null) { reset(); return; }
        var player = mc.player;
        if (player != trackedPlayer) { reset(); trackedPlayer = player; }
        boolean immediate = delay.getInt() == 0;
        if (immediate) cooldown = 0;
        else if (cooldown > 0 && --cooldown > 0) { cancelPending(); return; }
        boolean inventoryOpen = mc.currentScreen instanceof InventoryScreen;
        if (!player.isAlive() || player.isSpectator()
            || player.currentScreenHandler != player.playerScreenHandler
            || !player.playerScreenHandler.getCursorStack().isEmpty()
            || mc.currentScreen != null && !inventoryOpen
            || inventoryOnly.get() && !inventoryOpen
            || pauseUsing.get() && player.isUsingItem()
            || player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
            || emptyOnly.get() && !player.getOffHandStack().isEmpty()) {
            cancelPending(); return;
        }
        float effectiveHealth = player.getHealth() + (absorption.get() ? player.getAbsorptionAmount() : 0);
        if (mode.is("Low Health") && effectiveHealth > health.getFloat() * 2) { cancelPending(); return; }

        Slot candidate = findTotem(player);
        if (candidate == null) { cancelPending(); return; }
        if (candidate.id != source) { source = candidate.id; eligibleTicks = 0; }
        int waitTicks = immediate ? 0 : 10 - strength.getInt() + delay.getInt();
        if (eligibleTicks++ < waitTicks) return;
        // SWAP with the offhand inventory index is the same action as F over an inventory slot.
        mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, candidate.id, 40, SlotActionType.SWAP, player);
        cooldown = immediate ? 0 : retryDelay.getInt();
        cancelPending();
    }

    private static Slot findTotem(ClientPlayerEntity player) {
        Slot hotbar = null, held = null;
        for (Slot slot : player.playerScreenHandler.slots) {
            int index = slot.getIndex();
            if (slot.inventory != player.getInventory() || index < 0 || index >= 36
                || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING) || !slot.canTakeItems(player)) continue;
            // Use the main inventory first and leave the selected hand until the last resort.
            if (index >= 9) return slot;
            if (index == player.getInventory().getSelectedSlot()) held = slot;
            else if (hotbar == null) hotbar = slot;
        }
        return hotbar != null ? hotbar : held;
    }
}
