// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.mixin;

import dev.maro.render.character.CharacterLayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={PlayerEntityModel.class})
public abstract class CustomModelPlayerModel {
    @Inject(method={"setArmAngle"}, at={@At(value="HEAD")}, cancellable=true)
    private void maro$characterHand(PlayerEntityRenderState state, Arm arm, MatrixStack poseStack, CallbackInfo ci) {
        if (CharacterLayer.translateToHand((PlayerEntityModel)(Object)this, (PlayerEntityRenderState)state, (Arm)arm, (MatrixStack)poseStack)) {
            ci.cancel();
        }
    }
}
