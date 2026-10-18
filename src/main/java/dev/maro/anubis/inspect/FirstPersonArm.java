// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import dev.maro.mixin.AccessorHandItemLayer;
import dev.maro.mixin.AccessorHandItemRenderState;
import dev.maro.mixin.AccessorSkinChangeEntityRenderDispatcher;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.util.HeldItemContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.render.entity.EntityRenderManager;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(value=EnvType.CLIENT)
final class FirstPersonArm {
    static final Vector3f FOREARM = new Vector3f(0.75f, -0.45f, 0.5f).normalize();
    static final float ELBOW = 0.6f;
    private static final Vector3f ARM_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final float ARM_FIST = 0.5f;
    private static final float ARM_SHOULDER = -0.625f;
    private static final float SHOULDER_STRETCH = 3.0f;
    private static final float HANDLE_INSET = 0.1875f;
    private static final float BLOCK_BELOW = 0.17f;
    private static final Vector3f BLOCK_SHIFT = new Vector3f(0.09f, 0.0f, 0.06f);
    private static final ItemRenderState MODEL = new ItemRenderState();

    private FirstPersonArm() {
    }

    static Arm arm() {
        SkinTextures swapped;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null || player.isInvisible()) {
            return null;
        }
        EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();
        PlayerEntityRenderer<AbstractClientPlayerEntity> renderer = dispatcher.getPlayerRenderer((AbstractClientPlayerEntity)player);
        SkinTextures skin = player.getSkin();
        skin = dev.maro.module.impl.misc.SkinChanger.skinFor(player.getUuid(), skin);
        var byModel = ((AccessorSkinChangeEntityRenderDispatcher)dispatcher).anubis$skinChangePlayerRenderers().get(skin.model());
        if (byModel != null) renderer = byModel;
        return new Arm(renderer, skin.body().texturePath(), player.isModelPartVisible(PlayerModelPart.RIGHT_SLEEVE), player.isModelPartVisible(PlayerModelPart.LEFT_SLEEVE), skin.model() == PlayerSkinType.SLIM);
    }

    static void hold(MatrixStack pose, Arm arm, ItemStack stack, ItemDisplayContext context, LivingEntity holder, OrderedRenderCommandQueue collector, int light, float dx, float dy, float dz) {
        Vector3f grip = FirstPersonArm.grip(stack, context, holder);
        if (grip == null) {
            return;
        }
        Vector3f elbow = new Vector3f((Vector3fc)grip).fma(0.6f, (Vector3fc)FOREARM);
        grip.add(dx, dy, dz);
        boolean left = context.isLeftHand();
        pose.push();
        if (left) {
            pose.scale(-1.0f, 1.0f, 1.0f);
        }
        FirstPersonArm.place(pose, grip, elbow, arm.slim(), posed -> arm.draw((MatrixStack)posed, left, collector, light));
        pose.pop();
    }

    static Vector3f grip(ItemStack stack, ItemDisplayContext context, LivingEntity holder) {
        if (stack.isEmpty() || stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW) || stack.isOf(Items.SHIELD) || stack.isOf(Items.TRIDENT) || stack.isOf(Items.SPYGLASS)) {
            return null;
        }
        MinecraftClient.getInstance().getItemModelManager().clearAndUpdate(MODEL, stack, context, holder.getEntityWorld(), (HeldItemContext)holder, holder.getId() + context.ordinal());
        AccessorHandItemRenderState state = (AccessorHandItemRenderState)MODEL;
        if (state.anubis$handLayerCount() <= 0) {
            return null;
        }
        AccessorHandItemLayer layer = (AccessorHandItemLayer)state.anubis$handLayers()[0];
        Transformation transform = layer.anubis$handTransform();
        MatrixStack.Entry display = new MatrixStack.Entry();
        if (transform != null) {
            transform.apply(context.isLeftHand(), display);
        }
        Matrix4f m = display.getPositionMatrix();
        if (MODEL.isSideLit()) {
            if (!(stack.getItem() instanceof BlockItem)) {
                return null;
            }
            Vector3f center = m.transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
            if (context.isLeftHand()) {
                center.x = -center.x;
            }
            return FirstPersonArm.blockGrip(center, transform != null ? transform.scale().y() : 1.0f);
        }
        float[] handle = FirstPersonArm.handle(layer.anubis$handExtents() != null ? layer.anubis$handExtents().get() : null);
        Vector3f grip = m.transformPosition(handle[0] - 0.5f, handle[1] - 0.5f, 0.0f, new Vector3f());
        if (context.isLeftHand()) {
            grip.x = -grip.x;
        }
        return grip;
    }

    static Vector3f blockGrip(Vector3f center, float scale) {
        return new Vector3f((Vector3fc)center).add((Vector3fc)BLOCK_SHIFT).sub(0.0f, 0.5f * scale + 0.17f, 0.0f);
    }

    static float[] handle(Vector3fc[] extents) {
        float lowest = Float.MAX_VALUE;
        if (extents != null) {
            for (Vector3fc p : extents) {
                if (FirstPersonArm.squareCorner(p)) continue;
                lowest = Math.min(lowest, p.x() + p.y());
            }
        }
        if (lowest == Float.MAX_VALUE) {
            return new float[]{0.1875f, 0.1875f};
        }
        float x = 0.0f;
        float y = 0.0f;
        int tied = 0;
        for (Vector3fc p : extents) {
            if (FirstPersonArm.squareCorner(p) || p.x() + p.y() > lowest + 1.0E-4f) continue;
            x += p.x();
            y += p.y();
            ++tied;
        }
        return new float[]{x / (float)tied + 0.1875f, y / (float)tied + 0.1875f};
    }

    private static boolean squareCorner(Vector3fc p) {
        return (p.x() < 1.0E-4f || p.x() > 0.9999f) && (p.y() < 1.0E-4f || p.y() > 0.9999f);
    }

    static void place(MatrixStack pose, Vector3f hand, Vector3f elbow, boolean slim, Consumer<MatrixStack> draw) {
        Vector3f along = new Vector3f((Vector3fc)hand).sub((Vector3fc)elbow).normalize();
        Vector3f front = new Vector3f((Vector3fc)ARM_UP).fma(-ARM_UP.dot((Vector3fc)along), (Vector3fc)along).normalize();
        Vector3f side = new Vector3f((Vector3fc)along).cross((Vector3fc)front);
        pose.push();
        pose.translate(hand.x, hand.y, hand.z);
        pose.multiply((Quaternionfc)new Quaternionf().setFromNormalized((Matrix3fc)new Matrix3f((Vector3fc)side, (Vector3fc)along, (Vector3fc)front)));
        FirstPersonArm.drawPart(pose, slim, draw);
        pose.translate(0.0f, -0.625f, 0.0f);
        pose.scale(-1.0f, -3.0f, 1.0f);
        pose.translate(0.0f, 0.625f, 0.0f);
        FirstPersonArm.drawPart(pose, slim, draw);
        pose.pop();
    }

    private static void drawPart(MatrixStack pose, boolean slim, Consumer<MatrixStack> draw) {
        pose.push();
        pose.translate(slim ? 0.03125f : 0.0625f, -0.5f, 0.0f);
        pose.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotation(-0.1f));
        pose.translate(0.3125f, -0.125f, 0.0f);
        draw.accept(pose);
        pose.pop();
    }

    static void shoulder(MatrixStack pose, boolean right, boolean slim) {
        float pivotX = right ? -0.3125f : 0.3125f;
        float pivotY = 0.125f;
        float tilt = right ? 0.1f : -0.1f;
        float middle = (right ? -1.0f : 1.0f) * (slim ? 0.5f : 1.0f) / 16.0f;
        float top = -0.125f;
        pose.translate(pivotX, pivotY, 0.0f);
        pose.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotation(tilt));
        pose.translate(middle, top, 0.0f);
        pose.scale(-1.0f, -3.0f, 1.0f);
        pose.translate(-middle, -top, 0.0f);
        pose.multiply((Quaternionfc)RotationAxis.POSITIVE_Z.rotation(-tilt));
        pose.translate(-pivotX, -pivotY, 0.0f);
    }

    @Environment(value=EnvType.CLIENT)
    record Arm(PlayerEntityRenderer<AbstractClientPlayerEntity> renderer, Identifier texture, boolean rightSleeve, boolean leftSleeve, boolean slim) {
        void draw(MatrixStack pose, boolean left, OrderedRenderCommandQueue collector, int light) {
            if (left) {
                pose.scale(-1.0f, 1.0f, 1.0f);
                this.renderer.renderLeftArm(pose, collector, light, this.texture, this.leftSleeve);
            } else {
                this.renderer.renderRightArm(pose, collector, light, this.texture, this.rightSleeve);
            }
        }
    }
}
