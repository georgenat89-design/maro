// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character;

import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.kit.Bone;
import dev.maro.render.character.kit.CharacterModel;
import dev.maro.render.character.kit.Lights;
import dev.maro.render.character.kit.PartMesh;
import dev.maro.render.character.kit.Surface;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

@Environment(value=EnvType.CLIENT)
final class CharacterRenderer {
    private static final int GHOST_ALPHA = 38;
    private static final Bone[] BONES = Bone.values();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final CharacterRotationTable LEVELS = new CharacterRotationTable();
    private static final float[] PITCH = new float[BONES.length];
    private static final float[] JOINT = new float[BONES.length];
    private static final org.joml.Vector3f ITEM_GRIP=new org.joml.Vector3f();
    private static final CharacterRotationTable JOINT_LEVELS = new CharacterRotationTable();
    private static float[] scratch = new float[24576];

    private CharacterRenderer() {
    }

    static void submit(WornCharacter worn, PlayerEntityModel pm, MatrixStack stack, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state) {
        CharacterModel model = worn.model;
        PartMesh[] parts = model.parts;
        boolean ghost = state.invisible;
        int overlay = LivingEntityRenderer.getOverlay((LivingEntityRenderState)state, (float)0.0f);
        float[] pitch = CharacterRenderer.pitches(state, model, pm);
        float[] joint = CharacterRenderer.joints(state, model);
        float lookPitch = pm.head.pitch;
        float lookYaw = -pm.head.yaw;
        float[] angles = CharacterMotion.update(state, model, pm, pitch);
        PartMesh labelPart=model.part(Bone.HEAD);
        if(labelPart==null)labelPart=model.part(Bone.BODY);
        PartMesh tracerPart=model.part(Bone.BODY);if(tracerPart==null)tracerPart=labelPart;
        stack.push();
        pm.getRootPart().applyTransform(stack);
        CharacterRenderer.resize(stack);
        CharacterRenderer.lift(stack, state, model);
        MatrixStack.Entry torso = null;
        if (model.weldedSeams > 0) {
            for (int i = 0; i < parts.length; i++) if (!parts[i].extra && parts[i].bone == Bone.BODY) {
                stack.push();
                CharacterRenderer.chain(stack, pm, model, i, angles, true, null, pitch);
                stack.translate(-parts[i].pivotX, -parts[i].pivotY, -parts[i].pivotZ);
                torso = stack.peek().copy();
                stack.pop();
                break;
            }
        }
        for (int i = 0; i < parts.length; ++i) {
            PartMesh part = parts[i];
            if (part.base == null && part.outline == null && part.decals == null) continue;
            if (part.name.startsWith("prop_") && (part.bone == Bone.RIGHT_ARM && state.rightArmPose != BipedEntityModel.ArmPose.EMPTY
                || part.bone == Bone.LEFT_ARM && state.leftArmPose != BipedEntityModel.ArmPose.EMPTY)) continue;
            boolean bends = part.bends() && angles != null;
            float bendPitch = bends ? angles[part.motionSlot * 3] : 0.0f;
            float yaw = bends ? angles[part.motionSlot * 3 + 1] : 0.0f;
            float roll = bends ? angles[part.motionSlot * 3 + 2] : 0.0f;
            float bent = 0.0f;
            float turn = 0.0f;
            if (part.neck) {
                bent = CharacterRenderer.look(part.lookPitch * lookPitch, part.lookLimit);
                turn = CharacterRenderer.look(part.lookYaw * lookYaw, part.lookLimit);
            } else if (part.jointed) {
                bent = joint[part.bone.ordinal()];
            }
            stack.push();
            CharacterRenderer.chain(stack, pm, model, i, angles, !bends, null, pitch);
            if(part==labelPart)CharacterLabelAnchors.record(state,part,stack.peek().getPositionMatrix());
            if(part==tracerPart)CharacterLabelAnchors.recordBody(state,part,stack.peek().getPositionMatrix());
            CharacterRenderer.submitPart(worn, part, stack, collector, light, overlay, ghost, bends, bendPitch, yaw, roll, bent, turn, torso);
            stack.pop();
        }
        stack.pop();
    }

    static boolean firstPersonHand(PlayerEntityModel pm, MatrixStack stack, OrderedRenderCommandQueue collector, int light, ModelPart arm) {
        WornCharacter worn = CustomModel.firstPerson();
        if (worn == null) {
            return false;
        }
        CharacterModel model = worn.model;
        Bone bone = arm == pm.rightArm ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
        PartMesh main = model.part(bone);
        if (main == null || main.base == null) {
            return false;
        }
        arm.resetTransform();
        arm.roll = bone == Bone.RIGHT_ARM ? 0.1f : -0.1f;
        PartMesh[] parts = model.parts;
        for (int i = 0; i < parts.length; ++i) {
            PartMesh part = parts[i];
            if (part.bone != bone || part.base == null && part.outline == null && part.decals == null) continue;
            stack.push();
            CharacterRenderer.chain(stack, pm, model, i, null, true, arm, null);
            CharacterRenderer.submitPart(worn, part, stack, collector, light, OverlayTexture.DEFAULT_UV, false, false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, null);
            stack.pop();
        }
        return true;
    }

    private static void submitPart(WornCharacter worn, PartMesh part, MatrixStack stack, OrderedRenderCommandQueue collector, int light, int overlay, boolean ghost, boolean bends, float pitch, float yaw, float roll, float bent, float turn, MatrixStack.Entry torso) {
        if (ghost) {
            if (part.base != null) {
                collector.getBatchingQueue(1).submitCustom(stack, worn.ghost, (pose, out) -> CharacterRenderer.emit(pose, out, part.base, light, overlay, 38, bends, pitch, yaw, roll, part, bent, turn, torso));
            }
            return;
        }
        if (part.base != null || part.outline != null) {
            collector.submitCustom(stack, worn.solid, (pose, out) -> {
                if (part.base != null) {
                    CharacterRenderer.emit(pose, out, part.base, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn, torso);
                }
                if (part.outline != null && CustomModel.outlines()) {
                    CharacterRenderer.emit(pose, out, part.outline, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn, torso);
                }
            });
        }
        if (part.decals != null) {
            collector.getBatchingQueue(1).submitCustom(stack, worn.decal, (pose, out) -> CharacterRenderer.emit(pose, out, part.decals, light, overlay, 255, bends, pitch, yaw, roll, part, bent, turn, torso));
        }
    }

    static boolean translateToHand(PlayerEntityModel pm, PlayerEntityRenderState state, Arm arm, MatrixStack stack) {
        float bent;
        WornCharacter worn = CustomModel.worn((EntityRenderState)state);
        if (worn == null) {
            return false;
        }
        CharacterModel model = worn.model;
        Bone bone = arm == Arm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM;
        PartMesh part = model.part(bone);
        if (part == null) {
            return false;
        }
        float s = model.scale;
        ModelPart vp = CharacterRenderer.vanilla(pm, bone);
        pm.getRootPart().applyTransform(stack);
        CharacterRenderer.resize(stack);
        CharacterRenderer.lift(stack, state, model);
        CharacterRenderer.pivot(stack, vp, part, s, CharacterRenderer.pitch(state, part, vp.pitch));
        float f = bent = part.jointed ? CharacterJoints.angle(state, bone, CharacterRenderer.walk(state, bone) * CharacterRenderer.weight(state, bone)) : 0.0f;
        if (bent != 0.0f) {
            float jy = part.jointY * s / 16.0f;
            float jz = part.jointZ * s / 16.0f;
            stack.translate(0.0f, -jy, -jz);
            stack.multiply((Quaternionfc)ROTATION.rotationX(bent));
            stack.translate(0.0f, jy, jz);
        }
        stack.translate((part.gripX - part.pivotX) * s / 16.0f, -(part.gripY - part.pivotY) * s / 16.0f, -(part.gripZ - part.pivotZ) * s / 16.0f);
        stack.scale(s, s, s);
        var item=arm==Arm.RIGHT?state.rightHandItemState:state.leftHandItemState;
        var held=arm==Arm.RIGHT?state.rightHandItem:state.leftHandItem;
        var grip=CharacterItemGrip.offset(item,held,arm,ITEM_GRIP);
        // HeldItemFeatureRenderer next applies X(-90), Y(180), then its native offset.
        // Bring the rendered contact point to the palm in that exact coordinate frame.
        stack.translate((arm == Arm.RIGHT ? 1.0f : -1.0f) / 16.0f+grip.x, -0.625f+grip.z, 0.125f+grip.y);
        return true;
    }

    private static float[] pitches(PlayerEntityRenderState state, CharacterModel model, PlayerEntityModel pm) {
        float[] out = PITCH;
        for (Bone bone : BONES) {
            float xRot = CharacterRenderer.vanilla((PlayerEntityModel)pm, (Bone)bone).pitch;
            PartMesh part = bone.arm() || bone.leg() ? model.part(bone) : null;
            out[bone.ordinal()] = part == null ? xRot : CharacterRenderer.pitch(state, part, xRot);
        }
        return out;
    }

    private static float pitch(PlayerEntityRenderState state, PartMesh part, float xRot) {
        float out = part.swingPitch(xRot, CharacterRenderer.walk(state, part.bone), CharacterRenderer.weight(state, part.bone));
        if (part.jointed && part.bone.leg() && state.isInSneakingPose && !state.hasVehicle) {
            out -= 0.5f * (1.0f - state.leaningPitch);
        }
        return out;
    }

    private static float[] joints(PlayerEntityRenderState state, CharacterModel model) {
        float[] out = JOINT;
        for (Bone bone : BONES) {
            PartMesh part = bone.arm() || bone.leg() ? model.part(bone) : null;
            out[bone.ordinal()] = part != null && part.jointed ? CharacterJoints.angle(state, bone, CharacterRenderer.walk(state, bone) * CharacterRenderer.weight(state, bone)) : 0.0f;
        }
        return out;
    }

    private static float look(float angle, float limitDegrees) {
        float max = limitDegrees * ((float)Math.PI / 180);
        return MathHelper.clamp((float)angle, (float)(-max), (float)max);
    }

    private static float walk(PlayerEntityRenderState state, Bone bone) {
        float phase = state.limbSwingAnimationProgress * 0.6662f;
        float speed = state.limbSwingAmplitude / state.limbAmplitudeInverse;
        return switch (bone) {
            case RIGHT_LEG -> MathHelper.cos((double)phase) * 1.4f * speed;
            case LEFT_LEG -> MathHelper.cos((double)(phase + (float)Math.PI)) * 1.4f * speed;
            case RIGHT_ARM -> MathHelper.cos((double)(phase + (float)Math.PI)) * speed;
            case LEFT_ARM -> MathHelper.cos((double)phase) * speed;
            default -> 0.0f;
        };
    }

    private static float weight(PlayerEntityRenderState state, Bone bone) {
        BipedEntityModel.ArmPose other;
        if (bone.leg()) {
            return state.hasVehicle ? 0.0f : 1.0f - state.leaningPitch;
        }
        if (!bone.arm()) {
            return 0.0f;
        }
        BipedEntityModel.ArmPose pose = bone == Bone.RIGHT_ARM ? state.rightArmPose : state.leftArmPose;
        other = bone == Bone.RIGHT_ARM ? state.leftArmPose : state.rightArmPose;
        if (other.isTwoHanded()) {
            return 0.0f;
        }
        float share = switch (pose) {
            case EMPTY -> 1.0f;
            case ITEM, BLOCK, BRUSH, THROW_TRIDENT -> 0.5f;
            default -> 0.0f;
        };
        return state.isUsingItem ? share : share * (1.0f - state.leaningPitch);
    }

    private static void lift(MatrixStack stack, PlayerEntityRenderState state, CharacterModel model) {
        if (!state.hasVehicle) {
            return;
        }
        float rise = 12.0f - model.scale * model.hipY;
        if (rise != 0.0f) {
            stack.translate(0.0f, -rise / 16.0f, 0.0f);
        }
    }

    /** Keep feet planted and use the identical transform for mesh and held-item attachment. */
    private static void resize(MatrixStack stack) {
        float factor = CustomModel.sizeFactor();
        if (factor == 1) return;
        stack.translate(0, 1.5f, 0);
        stack.scale(factor, factor, factor);
        stack.translate(0, -1.5f, 0);
    }

    private static void chain(MatrixStack stack, PlayerEntityModel pm, CharacterModel model, int index, float[] angles, boolean rotate, ModelPart hand, float[] pitch) {
        PartMesh part = model.parts[index];
        float s = model.scale;
        if (!part.extra) {
            if (hand == null) {
                ModelPart vp = CharacterRenderer.vanilla(pm, part.bone);
                CharacterRenderer.pivot(stack, vp, part, s, pitch == null ? vp.pitch : pitch[part.bone.ordinal()]);
            } else {
                stack.translate(hand.originX / 16.0f, hand.originY / 16.0f, hand.originZ / 16.0f);
                stack.multiply((Quaternionfc)ROTATION.rotationZYX(hand.roll, hand.yaw, hand.pitch));
                float side = part.bone == Bone.RIGHT_ARM ? -1.0f : 1.0f;
                stack.translate((side - (part.gripX - part.pivotX) * s) / 16.0f, (10.0f + (part.gripY - part.pivotY) * s) / 16.0f, (-2.0f + (part.gripZ - part.pivotZ) * s) / 16.0f);
            }
            float k = s / 16.0f;
            stack.scale(k, -k, -k);
            return;
        }
        CharacterRenderer.chain(stack, pm, model, part.parent, angles, true, hand, pitch);
        PartMesh parent = model.parts[part.parent];
        stack.translate(part.pivotX - parent.pivotX, part.pivotY - parent.pivotY, part.pivotZ - parent.pivotZ);
        if (!rotate || angles == null || part.motionSlot < 0) {
            return;
        }
        int o = part.motionSlot * 3;
        if (angles[o] != 0.0f || angles[o + 1] != 0.0f || angles[o + 2] != 0.0f) {
            stack.multiply((Quaternionfc)ROTATION.rotationZYX(angles[o + 2], angles[o + 1], angles[o]));
        }
    }

    private static void pivot(MatrixStack stack, ModelPart vp, PartMesh part, float s, float xRot) {
        ModelTransform rest = vp.getDefaultTransform();
        stack.translate((s * part.pivotX + (vp.originX - rest.x()) * s) / 16.0f, (24.0f - s * part.pivotY + (vp.originY - rest.y()) * s) / 16.0f, (-s * part.pivotZ + (vp.originZ - rest.z()) * s) / 16.0f);
        if (xRot != 0.0f || vp.yaw != 0.0f || vp.roll != 0.0f) {
            stack.multiply((Quaternionfc)ROTATION.rotationZYX(vp.roll, vp.yaw, xRot));
        }
        if (vp.xScale != 1.0f || vp.yScale != 1.0f || vp.zScale != 1.0f) {
            stack.scale(vp.xScale, vp.yScale, vp.zScale);
        }
    }

    private static ModelPart vanilla(PlayerEntityModel pm, Bone bone) {
        return switch (bone) {
            default -> throw new MatchException(null, null);
            case HEAD -> pm.head;
            case BODY -> pm.body;
            case RIGHT_ARM -> pm.rightArm;
            case LEFT_ARM -> pm.leftArm;
            case RIGHT_LEG -> pm.rightLeg;
            case LEFT_LEG -> pm.leftLeg;
        };
    }

    private static void emit(MatrixStack.Entry pose, VertexConsumer out, Surface s, int light, int overlay, int alpha, boolean bends, float pitch, float yaw, float roll, PartMesh part, float bent, float turn, MatrixStack.Entry torso) {
        int n = s.vertexCount();
        if (scratch.length < n * 6) {
            scratch = new float[Math.max(n * 6, scratch.length * 2)];
        }
        float[] buf = scratch;
        Matrix4f m = pose.getPositionMatrix();
        Matrix3f nm = pose.getNormalMatrix();
        float m00 = m.m00();
        float m01 = m.m10();
        float m02 = m.m20();
        float m03 = m.m30();
        float m10 = m.m01();
        float m11 = m.m11();
        float m12 = m.m21();
        float m13 = m.m31();
        float m20 = m.m02();
        float m21 = m.m12();
        float m22 = m.m22();
        float m23 = m.m32();
        float n00 = nm.m00();
        float n01 = nm.m10();
        float n02 = nm.m20();
        float n10 = nm.m01();
        float n11 = nm.m11();
        float n12 = nm.m21();
        float n20 = nm.m02();
        float n21 = nm.m12();
        float n22 = nm.m22();
        float[] pos = s.pos;
        float[] nrm = s.nrm;
        float[] parent = torso == null ? null : s.parentWeight;
        Matrix4f tm = parent == null ? null : torso.getPositionMatrix();
        Matrix3f tn = parent == null ? null : torso.getNormalMatrix();
        float[] weight = bends ? s.weight : null;
        float[] flat = s.flat;
        Lights lights = flat != null ? CharacterLighting.current() : null;
        boolean reuseRotations = true;
        if (weight != null) {
            LEVELS.update(pitch, yaw, roll, reuseRotations);
        }
        float[] joint = (float[])(bent != 0.0f || turn != 0.0f ? s.joint : null);
        float jx = part.jointX;
        float jy = part.jointY;
        float jz = part.jointZ;
        if (joint != null) {
            JOINT_LEVELS.update(bent, turn, 0.0f, reuseRotations);
        }
        for (int v = 0; v < n; ++v) {
            float[] r;
            int k;
            float x = pos[v * 3];
            float y = pos[v * 3 + 1];
            float z = pos[v * 3 + 2];
            float nx = nrm[v * 3];
            float ny = nrm[v * 3 + 1];
            float nz = nrm[v * 3 + 2];
            if (joint != null && joint[v] > 0) {
                r = CharacterRenderer.JOINT_LEVELS.values;
                int o = 16 * 9;
                float amount = joint[v];
                float dx = x - jx;
                float dy = y - jy;
                float dz = z - jz;
                // Linear skinning retains continuous source weights around a bent joint.
                x += amount * (jx + r[o] * dx + r[o + 1] * dy + r[o + 2] * dz - x);
                y += amount * (jy + r[o + 3] * dx + r[o + 4] * dy + r[o + 5] * dz - y);
                z += amount * (jz + r[o + 6] * dx + r[o + 7] * dy + r[o + 8] * dz - z);
                float rx = r[o] * nx + r[o + 1] * ny + r[o + 2] * nz;
                float ry = r[o + 3] * nx + r[o + 4] * ny + r[o + 5] * nz;
                float rz = r[o + 6] * nx + r[o + 7] * ny + r[o + 8] * nz;
                nx += amount * (rx - nx);
                ny += amount * (ry - ny);
                nz += amount * (rz - nz);
            }
            if (weight != null) {
                k = Math.round(weight[v] * 16.0f) * 9;
                r = CharacterRenderer.LEVELS.values;
                float bx = r[k] * x + r[k + 1] * y + r[k + 2] * z;
                float by = r[k + 3] * x + r[k + 4] * y + r[k + 5] * z;
                float bz = r[k + 6] * x + r[k + 7] * y + r[k + 8] * z;
                float cx = r[k] * nx + r[k + 1] * ny + r[k + 2] * nz;
                float cy = r[k + 3] * nx + r[k + 4] * ny + r[k + 5] * nz;
                float cz = r[k + 6] * nx + r[k + 7] * ny + r[k + 8] * nz;
                x = bx;
                y = by;
                z = bz;
                nx = cx;
                ny = cy;
                nz = cz;
            }
            int o = v * 6;
            buf[o] = m00 * x + m01 * y + m02 * z + m03;
            buf[o + 1] = m10 * x + m11 * y + m12 * z + m13;
            buf[o + 2] = m20 * x + m21 * y + m22 * z + m23;
            float tx = n00 * nx + n01 * ny + n02 * nz;
            float ty = n10 * nx + n11 * ny + n12 * nz;
            float tz = n20 * nx + n21 * ny + n22 * nz;
            if (parent != null && parent[v] > 0) {
                // Shared borders follow the torso exactly; neighboring vertices fade into the limb.
                float w = parent[v], ax = pos[v * 3] + part.pivotX, ay = pos[v * 3 + 1] + part.pivotY, az = pos[v * 3 + 2] + part.pivotZ;
                buf[o] += w * (tm.m00() * ax + tm.m10() * ay + tm.m20() * az + tm.m30() - buf[o]);
                buf[o+1] += w * (tm.m01() * ax + tm.m11() * ay + tm.m21() * az + tm.m31() - buf[o+1]);
                buf[o+2] += w * (tm.m02() * ax + tm.m12() * ay + tm.m22() * az + tm.m32() - buf[o+2]);
                ax = nrm[v * 3]; ay = nrm[v * 3 + 1]; az = nrm[v * 3 + 2];
                tx += w * (tn.m00() * ax + tn.m10() * ay + tn.m20() * az - tx);
                ty += w * (tn.m01() * ax + tn.m11() * ay + tn.m21() * az - ty);
                tz += w * (tn.m02() * ax + tn.m12() * ay + tn.m22() * az - tz);
            }
            float l = tx * tx + ty * ty + tz * tz;
            if (l > 1.0E-12f && Math.abs(l - 1.0f) > 0.001f) {
                l = 1.0f / (float)Math.sqrt(l);
                tx *= l;
                ty *= l;
                tz *= l;
            }
            buf[o + 3] = tx;
            buf[o + 4] = ty;
            buf[o + 5] = tz;
            if (lights == null || !(flat[v] > 0.0f)) continue;
            lights.flatten(buf, o + 3, flat[v]);
        }
        int[] quads = s.quads;
        int[] color = s.color;
        float[] uv = s.uv;
        byte[] glow = s.glow;
        for (int q = 0; q < quads.length; ++q) {
            int v = quads[q];
            int o = v * 6;
            int c = alpha == 255 ? color[v] : color[v] & 0xFFFFFF | alpha << 24;
            int l = glow != null && glow[v] != 0 ? 0xF000F0 : light;
            out.vertex(buf[o], buf[o + 1], buf[o + 2], c, uv[v * 2], uv[v * 2 + 1], overlay, l, buf[o + 3], buf[o + 4], buf[o + 5]);
        }
    }
}
