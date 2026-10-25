package dev.maro.mixin;

import dev.maro.module.impl.visuals.Acrobatics;
import dev.maro.render.emote.Pose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Acrobatics: once the game has posed a player, their arms, legs and head move through the trick. */
@Mixin(BipedEntityModel.class)
public abstract class AcrobaticsPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("RETURN"))
    private void maro$acrobaticsPose(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState player)) return;
        var preview=dev.maro.gui.render.PlayerPreviewData.of(player);
        if(preview!=null&&preview.maro$previewModule() instanceof Acrobatics acrobatics){
            Pose pose=preview.maro$previewAnimated()?acrobatics.previewPose(preview.maro$previewSeconds()):null;
            if(pose!=null)pose.apply((BipedEntityModel<?>)(Object)this,acrobatics.previewBlend(preview.maro$previewSeconds()));
            return;
        }
        float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true);
        Pose pose = Acrobatics.pose(player.id, delta);
        if (pose != null) pose.apply((BipedEntityModel<?>) (Object) this, Acrobatics.poseBlend(player.id, delta));
    }
}
