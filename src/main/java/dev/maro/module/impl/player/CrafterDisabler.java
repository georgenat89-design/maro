package dev.maro.module.impl.player;

import dev.maro.module.Category;
import dev.maro.module.Module;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ButtonSetting;
import dev.maro.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.CrafterScreen;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import net.minecraft.screen.CrafterScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Opens a crafter and disables the slots you picked in {@link CrafterSlotsScreen}, a few clicks a
 * tick, the same clicks you would make by hand. With Remove Items on, anything sitting in one of
 * those slots is shift-clicked out first, since a slot only disables when it is empty.
 */
public class CrafterDisabler extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Slots handled each tick", 3, 1, 9, 1));
    private final BooleanSetting removeItems = add(new BooleanSetting("Remove Items", "Take items out of the chosen slots so they can be disabled", true));
    private final BooleanSetting enableOthers = add(new BooleanSetting("Enable Others", "Turn back on any disabled slot you did not choose", false));
    /** The chosen slots, one bit each, slot 0 top left to slot 8 bottom right. */
    private final NumberSetting slots = add(new NumberSetting("Slots", "The slots to disable", 0, 0, 511, 1).visible(() -> false));
    private final ButtonSetting configure = add(new ButtonSetting("Configure Slots", "Choose which of the nine slots to disable", "Edit",
            () -> mc.setScreen(new CrafterSlotsScreen(mc.currentScreen, this))) {
        @Override
        public String getLabel() {
            return "Edit (" + chosenCount() + ")";
        }
    });

    public CrafterDisabler() {
        super("Crafter Disabler", "Disables the crafter slots you choose as soon as you open one", Category.PLAYER);
    }

    public boolean chosen(int slot) {
        return (slots.getInt() >> slot & 1) == 1;
    }

    public void toggle(int slot) {
        slots.set((double) (slots.getInt() ^ (1 << slot)));
    }

    public void clearAll() {
        slots.set(0.0);
    }

    public int chosenCount() {
        return Integer.bitCount(slots.getInt());
    }

    @Override
    public void onTick() {
        if (!inGame() || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (!(mc.currentScreen instanceof CrafterScreen screen)) return;
        CrafterScreenHandler handler = screen.getScreenHandler();
        // Nothing is moved while an item is held on the cursor, so it is never dropped or swapped.
        if (!handler.getCursorStack().isEmpty()) return;

        int budget = speed.getInt();
        for (int slot = 0; slot < 9 && budget > 0; slot++) {
            boolean disabled = handler.isSlotDisabled(slot);
            if (chosen(slot)) {
                if (disabled) continue;
                if (handler.getSlot(slot).hasStack()) {
                    if (!removeItems.get()) continue;
                    mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    budget--;
                    if (handler.getSlot(slot).hasStack() || budget <= 0) continue;
                }
                setEnabled(handler, slot, false);
                budget--;
            } else if (enableOthers.get() && disabled) {
                setEnabled(handler, slot, true);
                budget--;
            }
        }
    }

    /** What clicking an empty crafter slot does: flip it here, and tell the server. */
    private void setEnabled(CrafterScreenHandler handler, int slot, boolean enabled) {
        handler.setSlotEnabled(slot, enabled);
        mc.getNetworkHandler().sendPacket(new SlotChangedStateC2SPacket(slot, handler.syncId, enabled));
    }
}
