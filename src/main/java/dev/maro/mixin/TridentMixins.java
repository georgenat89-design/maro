package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.maro.module.impl.player.TridentUtil;
import dev.maro.module.impl.player.AutoTrident;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class TridentMixins {
    @Mixin(TridentItem.class)
    public static abstract class Item {
        @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
        private boolean maro$dryUse(boolean original, World world, PlayerEntity user, Hand hand) {
            return original || TridentUtil.allowOutOfWater(user);
        }
        @ModifyExpressionValue(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
        private boolean maro$dryRelease(boolean original, ItemStack stack, World world, LivingEntity user, int remaining) {
            return original || user instanceof PlayerEntity player && TridentUtil.allowOutOfWater(player);
        }
        @ModifyExpressionValue(method = "onStoppedUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
        private int maro$charge(int original, ItemStack stack, World world, LivingEntity user, int remaining) {
            return user instanceof PlayerEntity player ? TridentUtil.minChargeTicks(original, player) : original;
        }
        @Inject(method = "use", at = @At("HEAD"), cancellable = true)
        private void maro$paceUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> info) {
            if (user == MinecraftClient.getInstance().player) {
                if (!TridentUtil.ready()||!AutoTrident.allowNativeUse(user,user.getStackInHand(hand))) info.setReturnValue(ActionResult.FAIL);
                else {TridentUtil.attempted();AutoTrident.useStarted();}
            }
        }
        @Inject(method="onStoppedUsing",at=@At("RETURN"))
        private void maro$tridentReleased(ItemStack stack,World world,LivingEntity user,int remaining,CallbackInfoReturnable<Boolean> info){
            if(user==MinecraftClient.getInstance().player){AutoTrident.useFinished();TridentUtil.released();}
        }
    }
    @Mixin(ClientPlayNetworkHandler.class)
    public static abstract class Corrections {
        @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
        private void maro$tridentCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo info) { TridentUtil.serverCorrection();AutoTrident.serverCorrection(); }
    }
    @Mixin(net.minecraft.client.network.ClientPlayerInteractionManager.class)
    public static abstract class Use {
        @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
        private void maro$tridentPreflight(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> info) {
            if (player == MinecraftClient.getInstance().player && player.getStackInHand(hand).isOf(net.minecraft.item.Items.TRIDENT)
                && (!AutoTrident.allowNativeUse(player,player.getStackInHand(hand))
                    ||TridentUtil.handlesUse() && (!TridentUtil.ready() || !TridentUtil.eligible(player.getStackInHand(hand))))) info.setReturnValue(ActionResult.FAIL);
        }
    }
}
