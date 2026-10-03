package dev.maro.mixin;

import dev.maro.nathan.mixin.LivingEntityRendererAccessor;
import dev.maro.render.accessories.SkinAccessoriesLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class SkinAccessoriesMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("TAIL"))
    private void maro$accessories(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        var parent = (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>)(Object)this;
        ((LivingEntityRendererAccessor)this).nameeprotect$addLayer(new SkinAccessoriesLayer(parent));
    }
}
