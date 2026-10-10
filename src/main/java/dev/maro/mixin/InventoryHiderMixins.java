package dev.maro.mixin;

import dev.maro.module.impl.misc.InventoryHider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The hooks behind {@link InventoryHider}: only drawing is skipped, never a click. */
public final class InventoryHiderMixins {
    private InventoryHiderMixins() {
    }

    /** Hidden slots draw nothing (or a cover), with no tooltip and no item on the cursor. */
    @Mixin(HandledScreen.class)
    public abstract static class Slots {
        @Shadow
        protected Slot focusedSlot;

        @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
        private void maro$hideSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
            if (!InventoryHider.hides((Screen) (Object) this, slot)) return;
            InventoryHider.cover(context, slot);
            ci.cancel();
        }

        @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
        private void maro$hideTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
            if (focusedSlot != null && focusedSlot.hasStack() && InventoryHider.hides((Screen) (Object) this, focusedSlot)) ci.cancel();
        }

        @Inject(method = "renderCursorStack", at = @At("HEAD"), cancellable = true)
        private void maro$hideCursor(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
            if (InventoryHider.hidesAny((Screen) (Object) this)) ci.cancel();
        }
    }

    /** Invisible Menu: the whole menu is left undrawn while it stays open. */
    @Mixin(Screen.class)
    public abstract static class WholeMenu {
        @Inject(method = "renderWithTooltip", at = @At("HEAD"), cancellable = true)
        private void maro$hideMenu(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
            if (InventoryHider.hidesMenu((Screen) (Object) this)) ci.cancel();
        }
    }

    /** The figure of you in the inventory, wearing your armor and holding your items. */
    @Mixin(InventoryScreen.class)
    public abstract static class Model {
        @Inject(method = "drawEntity(Lnet/minecraft/client/gui/DrawContext;IIIIIFFFLnet/minecraft/entity/LivingEntity;)V",
                at = @At("HEAD"), cancellable = true)
        private static void maro$hideModel(DrawContext context, int x1, int y1, int x2, int y2, int size, float scale, float mouseX, float mouseY,
                                           LivingEntity entity, CallbackInfo ci) {
            if (InventoryHider.hidesPlayerModel()) ci.cancel();
        }
    }

    /** What you hold, in first person: an empty hand is drawn instead. */
    @Mixin(net.minecraft.client.render.item.HeldItemRenderer.class)
    public abstract static class HeldItem {
        @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
        private ItemStack maro$hideHeld(ItemStack item) {
            return InventoryHider.hidesHeldItem() ? ItemStack.EMPTY : item;
        }
    }

    /** The hotbar's items, and the name shown when you switch. */
    @Mixin(InGameHud.class)
    public abstract static class Hotbar {
        @Inject(method = "renderHotbarItem", at = @At("HEAD"), cancellable = true)
        private void maro$hideHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed,
                                         CallbackInfo ci) {
            if (InventoryHider.hidesHotbar()) ci.cancel();
        }

        @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
        private void maro$hideHeldName(DrawContext context, CallbackInfo ci) {
            if (InventoryHider.hidesHotbar()) ci.cancel();
        }
    }
}
