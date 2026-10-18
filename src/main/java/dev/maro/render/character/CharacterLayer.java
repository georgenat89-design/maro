// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import dev.maro.module.impl.visuals.CustomModel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Arm;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;

@Environment(value=EnvType.CLIENT)
public final class CharacterLayer
extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public CharacterLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent) {
        super(parent);
    }

    @Override public void render(MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state, float yRot, float xRot) {
        WornCharacter worn = CustomModel.worn((EntityRenderState)state);
        if (worn == null || state.invisible && state.invisibleToPlayer) {
            return;
        }
        CharacterRenderer.submit(worn, (PlayerEntityModel)this.getContextModel(), poseStack, collector, light, state);
    }

    public static boolean firstPersonHand(PlayerEntityModel model, MatrixStack poseStack, OrderedRenderCommandQueue collector, int light, ModelPart arm) {
        return CharacterRenderer.firstPersonHand(model, poseStack, collector, light, arm);
    }

    public static boolean translateToHand(PlayerEntityModel model, PlayerEntityRenderState state, Arm arm, MatrixStack poseStack) {
        return CharacterRenderer.translateToHand(model, state, arm, poseStack);
    }
}
