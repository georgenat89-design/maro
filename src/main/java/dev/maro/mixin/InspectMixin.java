package dev.maro.mixin;

import dev.maro.module.impl.visuals.ItemInspect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render the selected knife before native item-specific branches, including maps and blocks. */
@Mixin(HeldItemRenderer.class)
public abstract class InspectMixin {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void maro$knife(AbstractClientPlayerEntity player,float tickDelta,float pitch,Hand hand,float swing,
                            ItemStack stack,float equip,MatrixStack pose,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){
        if(player==MinecraftClient.getInstance().player&&hand==Hand.MAIN_HAND&&dev.maro.module.impl.visuals.CustomPickaxe.renderHand(pose,queue,light,stack,player.getMainArm()==Arm.LEFT,equip,swing)){ci.cancel();return;}
        if(player==MinecraftClient.getInstance().player&&hand==Hand.MAIN_HAND&&!dev.maro.module.impl.visuals.CustomPickaxe.handles(stack)
                &&(ItemInspect.renderHand(pose,queue,light,stack,player.getMainArm()==Arm.LEFT,equip,swing)
                ||dev.maro.anubis.inspect.WeaponInspectModule.renderHand(pose,queue,light,stack,player.getMainArm()==Arm.LEFT,equip,swing)))ci.cancel();
    }
}
