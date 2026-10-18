package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.maro.anubis.inspect.WeaponInspectModule;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class WeaponInspectMixin {
    @Inject(method="renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",at=@At("HEAD"))
    private void maro$hands(float delta,MatrixStack matrices,OrderedRenderCommandQueue queue,ClientPlayerEntity player,int light,CallbackInfo ci) {
        WeaponInspectModule.handsBegin(matrices);
    }
    @WrapOperation(method="renderFirstPersonItem",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void maro$weapon(HeldItemRenderer renderer,LivingEntity holder,ItemStack stack,ItemDisplayContext context,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,Operation<Void> original,@Local(argsOnly=true) Hand hand) {
        if(!WeaponInspectModule.renderItem(matrices,holder,stack,context,queue,light,hand==Hand.MAIN_HAND))original.call(renderer,holder,stack,context,matrices,queue,light);
    }
}
