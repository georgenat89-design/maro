// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.maro.module.impl.visuals.CustomModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={LivingEntityRenderer.class})
public abstract class CustomModelLivingEntityRenderer {
    @Inject(method={"getRenderLayer"}, at={@At(value="HEAD")}, cancellable=true)
    private void maro$hideModelUnderCharacter(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderLayer> cir) {
        if (CustomModel.hidesModel((EntityRenderState)state)) {
            cir.setReturnValue(null);
        }
    }

    @WrapOperation(method={"render"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V")})
    private void maro$hideLayersUnderCharacter(FeatureRenderer<?, ?> layer, MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, EntityRenderState state, float yRot, float xRot, Operation<Void> original) {
        if (CustomModel.hidesLayer(layer, (EntityRenderState)state)) {
            return;
        }
        original.call(new Object[]{layer, poseStack, collector, light, state, Float.valueOf(yRot), Float.valueOf(xRot)});
    }
}
