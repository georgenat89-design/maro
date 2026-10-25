// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import dev.maro.render.character.kit.Bone;
import dev.maro.render.character.kit.CharacterModel;
import dev.maro.render.character.kit.Motion;
import dev.maro.render.character.kit.PartMesh;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;

@Environment(value=EnvType.CLIENT)
public final class CharacterMotion {
    private static final long FORGET_NANOS = 20000000000L;
    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap();
    private static final Motion.Input INPUT = new Motion.Input();
    private static final float[] TARGET = new float[3];
    private static long prunedAt;

    private CharacterMotion() {
    }

    static float[] update(PlayerEntityRenderState state, CharacterModel model, PlayerEntityModel pm, float[] pitch) {
        float dt;
        if (model.motionSlots == 0) {
            return null;
        }
        long now = System.nanoTime();
        CharacterMotion.prune(now);
        State st = (State)STATES.get(state.id);
        if (st == null || st.model != model) {
            st = new State(model);
            st.bodyRot = state.bodyYaw;
            STATES.put(state.id, st);
        }
        // Use interpolated simulation time: duplicate UI/world render passes do not advance springs.
        dt = Float.isNaN(st.age) ? 0 : (state.age-st.age)/20f;
        if (st.age == state.age) return st.angles;
        st.age = state.age;
        st.nanos = now;
        float turn = MathHelper.wrapDegrees((float)(state.bodyYaw - st.bodyRot));
        st.bodyRot = state.bodyYaw;
        st.turnRate = dt > 0.0f && dt < 0.25f ? (st.turnRate += (turn / dt - st.turnRate) * Math.min(1.0f, dt * 12.0f)) : 0.0f;
        Motion.Input in = INPUT;
        in.time = state.age / 20.0f;
        in.walkPos = state.limbSwingAnimationProgress;
        in.walkSpeed = state.limbSwingAmplitude;
        in.turnRate = st.turnRate;
        CharacterMotion.bone(in, Bone.HEAD, pm.head, pitch);
        CharacterMotion.bone(in, Bone.BODY, pm.body, pitch);
        CharacterMotion.bone(in, Bone.RIGHT_ARM, pm.rightArm, pitch);
        CharacterMotion.bone(in, Bone.LEFT_ARM, pm.leftArm, pitch);
        CharacterMotion.bone(in, Bone.RIGHT_LEG, pm.rightLeg, pitch);
        CharacterMotion.bone(in, Bone.LEFT_LEG, pm.leftLeg, pitch);
        for (PartMesh part : model.parts) {
            if (part.motionSlot < 0 || part.motion == null) continue;
            part.motion.target(in, part.bone, TARGET);
            part.motion.step(st.spring, part.motionSlot * 6, TARGET, dt);
            System.arraycopy(st.spring, part.motionSlot * 6, st.angles, part.motionSlot * 3, 3);
        }
        return st.angles;
    }

    private static void bone(Motion.Input in, Bone bone, ModelPart part, float[] pitch) {
        int b = bone.ordinal();
        in.pitch[b] = pitch[b];
        in.yaw[b] = -part.yaw;
        in.roll[b] = -part.roll;
    }

    private static void prune(long now) {
        if (now - prunedAt < 5000000000L) {
            return;
        }
        prunedAt = now;
        STATES.values().removeIf(st -> now - st.nanos > 20000000000L);
    }

    public static void clear() {
        STATES.clear();
        SkinnedRenderer.clear();
    }

    /** Reset one detached GUI subject without touching the live players' animation state. */
    public static void forgetPreview(int entityId){
        if(entityId!=dev.maro.gui.VisualPreview.ENTITY_ID)throw new IllegalArgumentException("Not a preview entity id");
        STATES.remove(entityId);SkinnedRenderer.forget(entityId);
    }

    @Environment(value=EnvType.CLIENT)
    private static final class State {
        final CharacterModel model;
        final float[] spring;
        final float[] angles;
        float bodyRot;
        float turnRate;
        long nanos;
        float age = Float.NaN;

        State(CharacterModel model) {
            this.model = model;
            this.spring = new float[model.motionSlots * 6];
            this.angles = new float[model.motionSlots * 3];
            for (int i = 0; i < model.motionSlots; ++i) {
                this.spring[i * 6] = Float.NaN;
            }
        }
    }
}
