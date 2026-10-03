/*
 * Adapted from the Spawner Protect addon by Larpbase (package larp.spawnerprotect),
 * which is marked All-Rights-Reserved. The detection logic, the state machine and
 * the stash routine below are that author's work, carried over rather than
 * rewritten. What changed here is the port from Meteor 26.2 to 1.21.11:
 * ContainerInput became ClickType, the package and category moved, and the mixin
 * invoker was renamed to this addon's prefix.
 */
package dev.maro.nathan.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up the screen's own click handler.
 *
 * This is the method the mouse calls: AbstractContainerScreen.mouseClicked works out which slot
 * is under the cursor and then calls slotClicked with QUICK_MOVE when shift is held. Going
 * through here means the deposit is the same click the player would make, cursor state, hover
 * bookkeeping and all, rather than a container packet built by hand.
 */
@Mixin(HandledScreen.class)
public interface ContainerScreenAccessor {
    @Invoker("onMouseClick")
    void nameeprotect$slotClicked(Slot slot, int slotId, int button, SlotActionType type);
}
