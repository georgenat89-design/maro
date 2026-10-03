package dev.maro.nathan.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.maro.nathan.render.HatLayer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * Puts the hats' layer on the player's renderer as it is built.
 *
 * <p>The class is {@code AvatarRenderer} in 1.21.11 - there is no
 * {@code PlayerRenderer} any more - and it is built once for the ordinary model
 * and once for the slim one, so this runs for both and each gets its own layer.
 * Nothing else about the renderer is changed, and the layer draws nothing while
 * the module is off.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class ChefHatLayerMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRendererFactory$Context;Z)V", at = @At("TAIL"))
    private void nameeprotect$addHats(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        // The renderer is the layer's parent: that is where the layer reads the
        // posed head bone from.
        FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent = (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this;

        ((LivingEntityRendererAccessor) this).nameeprotect$addLayer(new HatLayer(parent));
    }
}
