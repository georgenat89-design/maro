// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.kit.Bone;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.SwingAnimationType;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.entity.model.BipedEntityModel;

@Environment(value=EnvType.CLIENT)
final class CharacterJoints {
    static final float CROUCH_HIP = 0.5f;
    static final float SADDLE_KNEE = 1.35f;
    static final float SEAT_KNEE = 0.3f;
    private static final float CROUCH_KNEE = 0.62f;
    private static final float CROUCH_ELBOW = 0.2f;
    private static final float MAX_KNEE = 1.7f;
    private static final float MAX_ELBOW = 1.6f;

    private CharacterJoints() {
    }

    static float angle(PlayerEntityRenderState state, Bone bone, float swing) {
        float fade;
        fade = state.isGliding || state.usingRiptide ? 0.0f : 1.0f - state.leaningPitch * 0.8f;
        if (fade <= 0.0f) {
            return 0.0f;
        }
        if (bone.leg()) {
            return CharacterJoints.knee(state, bone) * fade;
        }
        if (bone.arm()) {
            return -CharacterJoints.elbow(state, bone, swing) * fade;
        }
        return 0.0f;
    }

    private static float knee(PlayerEntityRenderState state, Bone bone) {
        if (state.hasVehicle) {
            return CustomModel.seat((EntityRenderState)state);
        }
        float speed = Math.min(1.0f, state.limbSwingAmplitude / state.limbAmplitudeInverse);
        float phase = state.limbSwingAnimationProgress * 0.6662f + (bone == Bone.LEFT_LEG ? (float)Math.PI : 0.0f);
        float flex = speed * (0.1f + 0.85f * Math.max(0.0f, MathHelper.sin((double)phase)));
        if (state.isInSneakingPose) {
            flex += 0.62f;
        }
        return Math.min(1.7f, flex);
    }

    private static float elbow(PlayerEntityRenderState state, Bone bone, float swing) {
        Arm arm;
        float flex;
        BipedEntityModel.ArmPose other;
        boolean right = bone == Bone.RIGHT_ARM;
        BipedEntityModel.ArmPose pose = right ? state.rightArmPose : state.leftArmPose;
        other = right ? state.leftArmPose : state.rightArmPose;
        if (other.isTwoHanded()) {
            return 0.0f;
        }
        float speed = Math.min(1.0f, state.limbSwingAmplitude / state.limbAmplitudeInverse);
        switch (pose) {
            case EMPTY: {
                flex = 0.16f + 0.45f * Math.max(0.0f, -swing) + 0.3f * speed;
                break;
            }
            case ITEM: {
                flex = 0.3f + 0.2f * speed;
                break;
            }
            case BLOCK: {
                flex = 0.55f;
                break;
            }
            case BRUSH: {
                flex = 0.4f;
                break;
            }
            default: {
                flex = 0.0f;
            }
        }
        if (flex <= 0.0f) {
            return 0.0f;
        }
        if (state.isInSneakingPose) {
            flex += 0.2f;
        }
        arm = right ? Arm.RIGHT : Arm.LEFT;
        if (state.handSwingProgress > 0.0f && state.preferredArm == arm && state.swingAnimationType == SwingAnimationType.WHACK) {
            flex += 0.6f * MathHelper.sin((double)(state.handSwingProgress * (float)Math.PI));
        }
        return Math.min(1.6f, flex);
    }
}
