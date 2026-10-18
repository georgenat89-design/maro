package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.impl.movement.TridentBoost;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

public final class TridentMixins {
    @Mixin(TridentItem.class)
    public static abstract class Item {
        @ModifyExpressionValue(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
        private boolean maro$boostDryUse(boolean original,World world,PlayerEntity user,Hand hand){
            return original||TridentBoost.allowOutOfWater(user);
        }
        @ModifyExpressionValue(method="onStoppedUsing",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
        private boolean maro$boostDryRelease(boolean original,ItemStack stack,World world,LivingEntity user,int remaining){
            return original||user instanceof PlayerEntity player&&TridentBoost.allowOutOfWater(player);
        }
        // Adapted from Anubis's Trident Boost mixin; scope the impulse to the local client.
        @WrapOperation(method="onStoppedUsing",at=@At(value="INVOKE",target="Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"))
        private void maro$boostPush(PlayerEntity player,double x,double y,double z,Operation<Void> original){
            double multiplier=TridentBoost.riptideMultiplier(player);
            original.call(player,x*multiplier,y*multiplier,z*multiplier);
        }
    }
}
