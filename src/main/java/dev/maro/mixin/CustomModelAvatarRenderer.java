// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import dev.maro.render.character.CharacterLayer;
import dev.maro.module.impl.visuals.CustomModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityRenderer.class})
public abstract class CustomModelAvatarRenderer<T extends PlayerLikeEntity>
extends LivingEntityRenderer<T, PlayerEntityRenderState, PlayerEntityModel> {
    protected CustomModelAvatarRenderer(EntityRendererFactory.Context context, PlayerEntityModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean maro$slim;

    @Inject(method={"<init>"}, at={@At(value="TAIL")})
    private void maro$addCharacterLayer(EntityRendererFactory.Context context, boolean slim, CallbackInfo ci) {
        this.maro$slim = slim;
        this.addFeature((FeatureRenderer)new CharacterLayer((FeatureRendererContext)this));
    }

    @Inject(method={"updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V"}, at={@At(value="TAIL")})
    private void maro$extractCharacter(PlayerLikeEntity entity, PlayerEntityRenderState state, float partialTick, CallbackInfo ci) {
        CustomModel.extract((Entity)entity, (PlayerEntityRenderState)state);
        ((dev.maro.render.character.CharacterHolder)state).maro$setSkin(this.getTexture(state), this.maro$slim);
    }

    @Inject(method={"renderArm"}, at={@At(value="HEAD")}, cancellable=true)
    private void maro$characterFirstPersonHand(MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
        if (CharacterLayer.firstPersonHand((PlayerEntityModel)((PlayerEntityModel)this.getModel()), (MatrixStack)poseStack, (OrderedRenderCommandQueue)collector, (int)light, (ModelPart)arm)) {
            ci.cancel();
        }
    }
}
