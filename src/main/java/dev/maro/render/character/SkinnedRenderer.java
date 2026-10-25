package dev.maro.render.character;

import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.kit.CharacterModel;
import dev.maro.render.character.skin.Animator;
import dev.maro.render.character.skin.Human;
import dev.maro.render.character.skin.MotionInput;
import dev.maro.render.character.skin.Rig;
import dev.maro.render.character.skin.SkinLevel;
import dev.maro.render.character.skin.SkinMaterial;
import dev.maro.render.character.skin.SkinMath;
import dev.maro.render.character.skin.SkinPose;
import dev.maro.render.character.skin.SkinnedCharacter;
import dev.maro.render.character.skin.Skinner;
import dev.maro.render.emote.Emote;
import dev.maro.render.emote.EmoteRenderState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Arm;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws skinned characters: each player has its own animator (state machine, crossfades, planted
 * feet, emotes, the game's arm poses on top), skinned on the CPU every frame with linear blend
 * skinning, one draw per material in that material's own texture and layer. Farther players use a
 * lighter level of detail. Held items, first-person arms and name tags follow the bones.
 */
@Environment(value=EnvType.CLIENT)
final class SkinnedRenderer {
    private static final float GHOST_ALPHA = 38;
    /** Vanilla draws a player at 15/16 of its model size. */
    private static final float PLAYER_SCALE = 0.9375f;
    private static final long FORGET_NANOS = 10_000_000_000L;
    private static final Int2ObjectOpenHashMap<Instance> INSTANCES = new Int2ObjectOpenHashMap<>();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Matrix3f LINEAR = new Matrix3f();
    private static final Vector3f VECTOR = new Vector3f();
    private static final float[] Q = new float[8];
    private static final Vector3f GRIP = new Vector3f();
    private static long prunedAt;

    private SkinnedRenderer() {
    }

    // ---- the world body

    static void submit(WornCharacter worn, PlayerEntityModel pm, MatrixStack stack, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state) {
        Instance in = instance(worn, state);
        in.update(state, pm);
        boolean ghost = state.invisible;
        int overlay = LivingEntityRenderer.getOverlay((LivingEntityRenderState)state, 0.0f);
        stack.push();
        place(stack, state, worn.model, false);
        in.light = light;
        in.overlay = overlay;
        in.alpha = ghost ? (int)GHOST_ALPHA : 255;
        SkinLevel level = in.level;
        SkinMaterial[] materials = worn.model.skinned.materials;
        boolean outline = !ghost && CustomModel.outlines();
        for (int m = 0; m < level.materials(); m++) {
            if (level.quadStart[m] == level.quadStart[m + 1]) continue;
            SkinMaterial mat = materials[m];
            if (mat.prop >= 0 && (mat.prop == 0 ? state.rightArmPose : state.leftArmPose) != BipedEntityModel.ArmPose.EMPTY) continue;
            if (ghost) collector.getBatchingQueue(1).submitCustom(stack, worn.ghosts[m], in.commands[m]);
            else if (mat.mode == SkinMaterial.BLEND) collector.getBatchingQueue(1).submitCustom(stack, worn.layers[m], in.commands[m]);
            else collector.submitCustom(stack, worn.layers[m], in.commands[m]);
            if (outline && worn.outlined[m]) collector.submitCustom(stack, worn.layers[m], in.outlines[m]);
        }
        in.anchors(state, stack.peek().getPositionMatrix());
        stack.pop();
    }

    /**
     * Into the character's own space (character units, feet at 0, facing +Z): the size setting
     * about the feet, the seat when riding, the game's crouch drop taken back out (the clip
     * crouches with the feet planted), then units to model space.
     */
    private static void place(MatrixStack stack, PlayerEntityRenderState state, CharacterModel model, boolean firstPerson) {
        float factor = CustomModel.sizeFactor();
        if (factor != 1) {
            stack.translate(0, 1.5f, 0);
            stack.scale(factor, factor, factor);
            stack.translate(0, -1.5f, 0);
        }
        if (state.hasVehicle) {
            float rise = 12.0f - model.hipY;
            if (rise != 0) stack.translate(0, -rise / 16.0f, 0);
        }
        var offset = state.positionOffset;
        if (offset != null && state.isInSneakingPose && !state.isSwimming && !state.isGliding && (offset.y != 0 || offset.x != 0 || offset.z != 0)) {
            // Undo the world offset the game moved the whole crouching body by, in model space.
            stack.peek().getPositionMatrix().get3x3(LINEAR);
            if (Math.abs(LINEAR.determinant()) > 1e-9f) {
                LINEAR.invert();
                VECTOR.set((float)-offset.x, (float)-offset.y, (float)-offset.z);
                LINEAR.transform(VECTOR);
                if (Float.isFinite(VECTOR.x + VECTOR.y + VECTOR.z)) stack.translate(VECTOR.x, VECTOR.y, VECTOR.z);
            }
        }
        float k = model.scale / 16.0f;
        stack.translate(0, 1.5f, 0);
        stack.scale(k, -k, -k);
    }

    // ---- held items

    static boolean translateToHand(WornCharacter worn, PlayerEntityModel pm, PlayerEntityRenderState state, Arm arm, MatrixStack stack) {
        Rig rig = worn.model.skinned.rig;
        int hand = rig.node(arm == Arm.RIGHT ? Human.RIGHT_HAND : Human.LEFT_HAND);
        if (hand < 0) hand = rig.node(arm == Arm.RIGHT ? Human.RIGHT_FOREARM : Human.LEFT_FOREARM);
        if (hand < 0) return false;
        Instance in = instance(worn, state);
        in.update(state, pm);
        place(stack, state, worn.model, false);
        float[] w = in.animator.out.world;
        int o = hand * 12;
        // The palm, where the item is held.
        int side = arm == Arm.RIGHT ? 0 : 1;
        float px = rig.palm[side * 3], py = rig.palm[side * 3 + 1], pz = rig.palm[side * 3 + 2];
        float x = w[o] * px + w[o + 1] * py + w[o + 2] * pz + w[o + 3];
        float y = w[o + 4] * px + w[o + 5] * py + w[o + 6] * pz + w[o + 7];
        float z = w[o + 8] * px + w[o + 9] * py + w[o + 10] * pz + w[o + 11];
        stack.translate(x, y, z);
        // Turned as the hand has turned from hanging at rest, in the arm frame the game expects.
        SkinMath.rotationOf(w, o, Q, 0);
        SkinMath.mulQ(Q, 0, conj(rig.canonWorld, hand * 4, Q, 4), 4, Q, 0);
        ROTATION.set(Q[0], -Q[1], -Q[2], Q[3]);
        float k = worn.model.scale / 16.0f;
        stack.scale(1 / k, -1 / k, -1 / k);
        stack.multiply(ROTATION);
        var item = arm == Arm.RIGHT ? state.rightHandItemState : state.leftHandItemState;
        var held = arm == Arm.RIGHT ? state.rightHandItem : state.leftHandItem;
        var grip = CharacterItemGrip.offset(item, held, arm, GRIP);
        // HeldItemFeatureRenderer next applies X(-90), Y(180), then its own offset down the arm:
        // this lands that offset on the palm.
        stack.translate((arm == Arm.RIGHT ? 1.0f : -1.0f) / 16.0f + grip.x, -0.625f + grip.z, 0.125f + grip.y);
        return true;
    }

    private static float[] conj(float[] q, int o, float[] out, int oo) {
        out[oo] = -q[o];
        out[oo + 1] = -q[o + 1];
        out[oo + 2] = -q[o + 2];
        out[oo + 3] = q[o + 3];
        return out;
    }

    // ---- first person

    /** The character's arm in first person: its canonical rest arm, the palm where the game's hand holds things. */
    static boolean firstPersonHand(WornCharacter worn, MatrixStack stack, OrderedRenderCommandQueue collector, int light, ModelPart arm, boolean right) {
        Arms arms = Arms.of(worn);
        int side = right ? 0 : 1;
        if (arms.quads[side].length == 0) return false;
        arm.resetTransform();
        arm.roll = right ? 0.1f : -0.1f;
        stack.push();
        stack.translate(arm.originX / 16.0f, arm.originY / 16.0f, arm.originZ / 16.0f);
        stack.multiply(ROTATION.rotationZYX(arm.roll, arm.yaw, arm.pitch));
        stack.translate((right ? -1.0f : 1.0f) / 16.0f, 10.0f / 16.0f, -2.0f / 16.0f);
        float k = worn.model.scale / 16.0f;
        stack.scale(k, -k, -k);
        stack.translate(-arms.palm[side * 3], -arms.palm[side * 3 + 1], -arms.palm[side * 3 + 2]);
        SkinMaterial[] materials = worn.model.skinned.materials;
        for (int m = 0; m < materials.length; m++) {
            if (arms.counts[side][m] == 0 || materials[m].prop >= 0) continue;
            arms.light = light;
            collector.submitCustom(stack, worn.layers[m], arms.commands[side][m]);
        }
        stack.pop();
        return true;
    }

    // ---- per player

    private static Instance instance(WornCharacter worn, PlayerEntityRenderState state) {
        long now = System.nanoTime();
        if (now - prunedAt > 2_000_000_000L) {
            prunedAt = now;
            INSTANCES.values().removeIf(i -> now - i.used > FORGET_NANOS);
        }
        Instance in = INSTANCES.get(state.id);
        if (in == null || in.model != worn.model) {
            in = new Instance(worn.model);
            INSTANCES.put(state.id, in);
        }
        in.used = now;
        return in;
    }

    static void clear() {
        INSTANCES.clear();
        Arms.CACHE.clear();
    }
    static void forget(int entityId){INSTANCES.remove(entityId);}

    /** One player's animation and skinned mesh. */
    private static final class Instance {
        final CharacterModel model;
        final SkinnedCharacter c;
        final Animator animator;
        final MotionInput input = new MotionInput();
        final Skinner.Job job = new Skinner.Job();
        final OrderedRenderCommandQueue.Custom[] commands, outlines;
        float[] buf = new float[0], view = new float[0];
        SkinLevel level;
        long used;
        float lastTime = Float.NaN;
        int lastLevel = -1;
        int light, overlay, alpha = 255;

        Instance(CharacterModel model) {
            this.model = model;
            this.c = model.skinned;
            this.animator = new Animator(c.rig, c.clips);
            int mats = c.materials.length;
            commands = new OrderedRenderCommandQueue.Custom[mats];
            outlines = new OrderedRenderCommandQueue.Custom[mats];
            for (int m = 0; m < mats; m++) {
                final int material = m;
                commands[m] = (pose, out) -> emit(pose, out, material, false);
                outlines[m] = (pose, out) -> emit(pose, out, material, true);
            }
            level = c.level(model.detail.ordinal());
        }

        /** Poses and skins for this frame (once: a second call in the same frame does nothing). */
        void update(PlayerEntityRenderState state, PlayerEntityModel pm) {
            int wanted = levelFor(state);
            float time = state.age / 20.0f;
            if (time == lastTime && wanted == lastLevel) return;
            lastTime = time;
            lastLevel = wanted;
            level = c.level(wanted);
            fill(state, pm, input, model);
            animator.update(input);
            int n = level.vertices;
            if (buf.length < n * 6) buf = new float[n * 6];
            job.run(level, animator.out.skin, buf);
        }

        private int levelFor(PlayerEntityRenderState state) {
            int detail = model.detail.ordinal();
            var player = MinecraftClient.getInstance().player;
            if (player != null && state.id == player.getId() || state.id < 0) return detail;
            // Detail falls off with distance: full up close, medium across a room, low beyond.
            double d = state.squaredDistanceToCamera;
            if (d > 32 * 32) return 0;
            if (d > 12 * 12) return Math.min(detail, 1);
            return detail;
        }

        /** Writes one material's quads, transformed by the pose; an outline is the same surface pushed out and turned inside out. */
        private void emit(MatrixStack.Entry pose, VertexConsumer out, int material, boolean outline) {
            SkinLevel lv = level;
            if (material >= lv.materials()) return;
            int from = lv.vertexStart[material], to = lv.vertexStart[material + 1];
            int count = to - from;
            if (count <= 0) return;
            if (view.length < count * 6) view = new float[Math.max(count * 6, view.length * 2)];
            float[] b = buf, t = view;
            Matrix4f m = pose.getPositionMatrix();
            Matrix3f nm = pose.getNormalMatrix();
            float m00 = m.m00(), m01 = m.m10(), m02 = m.m20(), m03 = m.m30();
            float m10 = m.m01(), m11 = m.m11(), m12 = m.m21(), m13 = m.m31();
            float m20 = m.m02(), m21 = m.m12(), m22 = m.m22(), m23 = m.m32();
            float n00 = nm.m00(), n01 = nm.m10(), n02 = nm.m20();
            float n10 = nm.m01(), n11 = nm.m11(), n12 = nm.m21();
            float n20 = nm.m02(), n21 = nm.m12(), n22 = nm.m22();
            float push = outline ? 0.18f : 0;
            for (int v = from; v < to; v++) {
                int i = v * 6, o = (v - from) * 6;
                float nx = b[i + 3], ny = b[i + 4], nz = b[i + 5];
                float x = b[i] + nx * push, y = b[i + 1] + ny * push, z = b[i + 2] + nz * push;
                t[o] = m00 * x + m01 * y + m02 * z + m03;
                t[o + 1] = m10 * x + m11 * y + m12 * z + m13;
                t[o + 2] = m20 * x + m21 * y + m22 * z + m23;
                float tx = n00 * nx + n01 * ny + n02 * nz, ty = n10 * nx + n11 * ny + n12 * nz, tz = n20 * nx + n21 * ny + n22 * nz;
                float l = tx * tx + ty * ty + tz * tz;
                if (l > 1e-12f) {
                    l = 1.0f / (float)Math.sqrt(l);
                    tx *= l;
                    ty *= l;
                    tz *= l;
                }
                t[o + 3] = tx;
                t[o + 4] = ty;
                t[o + 5] = tz;
            }
            int[] quads = lv.quads, color = lv.color;
            float[] uv = lv.uv;
            int a = alpha, li = light, ov = overlay;
            for (int q = lv.quadStart[material], end = lv.quadStart[material + 1]; q < end; q++) {
                for (int k = 0; k < 4; k++) {
                    int v = quads[q * 4 + (outline ? 3 - k : k)];
                    int o = (v - from) * 6;
                    int col = outline ? 0xFF111114 : a == 255 ? color[v] : color[v] & 0xFFFFFF | a << 24;
                    out.vertex(t[o], t[o + 1], t[o + 2], col, uv[v * 2], uv[v * 2 + 1], ov, li, t[o + 3], t[o + 4], t[o + 5]);
                }
            }
        }

        /** Name tags and tracers: the head's top and the chest, posed. */
        void anchors(PlayerEntityRenderState state, Matrix4f pose) {
            Rig rig = c.rig;
            float[] w = animator.out.world;
            int head = rig.node(Human.HEAD);
            int chest = rig.node(Human.CHEST);
            if (chest < 0) chest = rig.node(Human.SPINE);
            if (chest < 0) chest = rig.node(Human.HIPS);
            float hx, hy, hz;
            if (head >= 0) {
                int o = head * 12;
                float[] p = rig.headTop;
                hx = w[o] * p[0] + w[o + 1] * p[1] + w[o + 2] * p[2] + w[o + 3];
                hy = w[o + 4] * p[0] + w[o + 5] * p[1] + w[o + 6] * p[2] + w[o + 7];
                hz = w[o + 8] * p[0] + w[o + 9] * p[1] + w[o + 10] * p[2] + w[o + 11];
            } else {
                hx = 0;
                hy = model.maxY;
                hz = 0;
            }
            float bx = chest >= 0 ? w[chest * 12 + 3] : 0, by = chest >= 0 ? w[chest * 12 + 7] : model.maxY * 0.6f, bz = chest >= 0 ? w[chest * 12 + 11] : 0;
            CharacterLabelAnchors.recordPoints(state, pose, hx, hy, hz, bx, by, bz);
        }
    }

    /** What the animation needs from this frame's render state (and the player model's arm poses). */
    static void fill(PlayerEntityRenderState state, PlayerEntityModel pm, MotionInput in, CharacterModel model) {
        in.reset();
        in.time = state.age / 20.0f;
        float[] motion = state instanceof CharacterHolder h ? h.maro$motion() : null;
        boolean known = motion != null && motion[5] > 0;
        in.speed = known ? motion[0] : 0;
        in.vertical = known ? motion[1] : 0;
        in.forward = known ? motion[2] : 0;
        in.strafe = known ? motion[3] : 0;
        in.grounded = !known || motion[4] > 0;
        in.sprinting = known && motion[6] > 0;
        in.inWater = state.touchingWater || known && motion[7] > 0;
        in.sneaking = state.isInSneakingPose;
        in.swimming = state.isSwimming;
        in.crawling = state.isInPose(EntityPose.SWIMMING) && !state.isSwimming;
        in.riding = state.hasVehicle;
        in.sleeping = state.isInPose(EntityPose.SLEEPING);
        in.gliding = state.isGliding;
        in.bodyYaw = state.bodyYaw;
        in.headYaw = state.relativeHeadYaw;
        in.headPitch = state.pitch;
        in.unit = PLAYER_SCALE / 16.0f * model.scale * CustomModel.sizeFactor() * Math.max(0.05f, state.baseScale);
        if (state instanceof EmoteRenderState emote && emote.maro$getEmote() != null) {
            Emote e = emote.maro$getEmote();
            in.emote = e.clip();
            in.emoteTime = emote.maro$getEmoteTime();
            in.emoteBlend = emote.maro$getEmoteBlend();
        }
        if (pm != null) arms(state, pm, in);
    }

    /** The game's own arm poses where it has something to show: swinging, aiming, blocking, holding out an item. */
    private static void arms(PlayerEntityRenderState state, PlayerEntityModel pm, MotionInput in) {
        boolean swinging = state.handSwingProgress > 0;
        Arm swingArm = state.preferredArm;
        BipedEntityModel.ArmPose right = state.rightArmPose, left = state.leftArmPose;
        boolean twoHanded = right.isTwoHanded() || left.isTwoHanded();
        float rw = weight(right, twoHanded, swinging && swingArm == Arm.RIGHT), lw = weight(left, twoHanded, swinging && swingArm == Arm.LEFT);
        if (rw > 0) {
            in.rightArm[0] = pm.rightArm.pitch;
            in.rightArm[1] = pm.rightArm.yaw;
            in.rightArm[2] = pm.rightArm.roll;
            in.rightArmWeight = rw;
            in.rightElbow = elbow(right, swinging && swingArm == Arm.RIGHT);
        }
        if (lw > 0) {
            in.leftArm[0] = pm.leftArm.pitch;
            in.leftArm[1] = pm.leftArm.yaw;
            in.leftArm[2] = pm.leftArm.roll;
            in.leftArmWeight = lw;
            in.leftElbow = elbow(left, swinging && swingArm == Arm.LEFT);
        }
        if (swinging) in.bodyTwist = pm.body.yaw;
    }

    private static float weight(BipedEntityModel.ArmPose pose, boolean twoHanded, boolean swinging) {
        if (swinging || twoHanded) return 1;
        if (pose == BipedEntityModel.ArmPose.EMPTY) return 0;
        return pose == BipedEntityModel.ArmPose.ITEM ? 0.4f : 1;
    }

    private static float elbow(BipedEntityModel.ArmPose pose, boolean swinging) {
        if (swinging) return 12;
        if (pose == BipedEntityModel.ArmPose.ITEM) return 34;
        if (pose == BipedEntityModel.ArmPose.BLOCK) return 24;
        return pose.isTwoHanded() ? 16 : 6;
    }

    /** First-person arms: each side's arm quads, skinned once at canonical rest. */
    private static final class Arms {
        static final java.util.Map<CharacterModel, Arms> CACHE = new java.util.WeakHashMap<>();
        final int[][] quads = new int[2][];
        final int[][] counts;
        final float[] buf, palm = new float[6];
        final SkinLevel level;
        final OrderedRenderCommandQueue.Custom[][] commands;
        int light;

        static Arms of(WornCharacter worn) {
            return CACHE.computeIfAbsent(worn.model, Arms::new);
        }

        private Arms(CharacterModel model) {
            SkinnedCharacter c = model.skinned;
            Rig rig = c.rig;
            level = c.level(model.detail.ordinal());
            SkinPose pose = new SkinPose(rig);
            pose.canonical();
            pose.evaluate();
            buf = new float[level.vertices * 6];
            Skinner.skin(level, pose.skin, 0, level.vertices, buf);
            int mats = level.materials();
            counts = new int[2][mats];
            commands = new OrderedRenderCommandQueue.Custom[2][mats];
            for (int side = 0; side < 2; side++) {
                int upper = rig.node(side == 0 ? Human.RIGHT_UPPER_ARM : Human.LEFT_UPPER_ARM);
                boolean[] inArm = new boolean[rig.slots.length];
                if (upper >= 0) {
                    for (int s = 0; s < rig.slots.length; s++) {
                        for (int n = rig.slots[s]; n >= 0; n = rig.parent[n]) if (n == upper) {
                            inArm[s] = true;
                            break;
                        }
                    }
                }
                java.util.List<Integer> keep = new java.util.ArrayList<>();
                for (int m = 0; m < mats; m++) {
                    for (int q = level.quadStart[m]; q < level.quadStart[m + 1]; q++) {
                        boolean all = upper >= 0;
                        for (int k = 0; k < 4 && all; k++) all = inArm[level.main[level.quads[q * 4 + k]]];
                        if (!all) continue;
                        keep.add(q);
                        counts[side][m]++;
                    }
                }
                quads[side] = keep.stream().mapToInt(Integer::intValue).toArray();
                int hand = rig.node(side == 0 ? Human.RIGHT_HAND : Human.LEFT_HAND);
                if (hand < 0) hand = rig.node(side == 0 ? Human.RIGHT_FOREARM : Human.LEFT_FOREARM);
                if (hand >= 0) {
                    float[] w = pose.world;
                    int o = hand * 12;
                    float px = rig.palm[side * 3], py = rig.palm[side * 3 + 1], pz = rig.palm[side * 3 + 2];
                    palm[side * 3] = w[o] * px + w[o + 1] * py + w[o + 2] * pz + w[o + 3];
                    palm[side * 3 + 1] = w[o + 4] * px + w[o + 5] * py + w[o + 6] * pz + w[o + 7];
                    palm[side * 3 + 2] = w[o + 8] * px + w[o + 9] * py + w[o + 10] * pz + w[o + 11];
                }
                for (int m = 0; m < mats; m++) {
                    final int s = side, material = m;
                    commands[side][m] = (p, out) -> emit(p, out, s, material);
                }
            }
        }

        private final Vector3f p = new Vector3f(), n = new Vector3f();

        private void emit(MatrixStack.Entry pose, VertexConsumer out, int side, int material) {
            Matrix4f m = pose.getPositionMatrix();
            Matrix3f nm = pose.getNormalMatrix();
            int[] list = quads[side];
            for (int q : list) {
                if (q < level.quadStart[material] || q >= level.quadStart[material + 1]) continue;
                for (int k = 0; k < 4; k++) {
                    int v = level.quads[q * 4 + k];
                    m.transformPosition(buf[v * 6], buf[v * 6 + 1], buf[v * 6 + 2], p);
                    nm.transform(buf[v * 6 + 3], buf[v * 6 + 4], buf[v * 6 + 5], n);
                    n.normalize();
                    out.vertex(p.x, p.y, p.z, level.color[v], level.uv[v * 2], level.uv[v * 2 + 1], OverlayTexture.DEFAULT_UV, light, n.x, n.y, n.z);
                }
            }
        }
    }
}
