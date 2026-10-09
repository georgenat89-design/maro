package dev.maro.render.accessories;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.SkinAccessories;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

public final class SkinAccessoriesLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final Identifier TEXTURE = Identifier.of("maro", "textures/accessories/surface.png");
    public SkinAccessoriesLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent) { super(parent); }
    @Override public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float yaw, float pitch) {
        SkinAccessories m = ModuleManager.get(SkinAccessories.class);
        if (m == null || !m.wants(state)) return;
        float phase = m.phase(state), amount = m.motionAmount(state);
        RenderLayer solid = RenderLayers.entityCutoutNoCull(TEXTURE);
        RenderLayer detail = m.glow() ? RenderLayers.entityTranslucentEmissive(TEXTURE) : solid;
        if (m.showHead(state)) draw("head", m.head(), m, matrices, queue, light, state, phase, amount, solid, detail);
        if (m.showWings(state)) draw("wings", m.wings(), m, matrices, queue, light, state, phase, amount, solid, detail);
        draw("tail", m.tail(), m, matrices, queue, light, state, phase, amount, solid, detail);
        draw("halo", m.halo(), m, matrices, queue, light, state, phase, amount, solid, detail);
        draw("shoulders", m.shoulders(), m, matrices, queue, light, state, phase, amount, solid, detail);
        draw("back", m.back(), m, matrices, queue, light, state, phase, amount, solid, detail);
    }
    private void draw(String category, String style, SkinAccessories m, MatrixStack pose, OrderedRenderCommandQueue queue, int light,
                      PlayerEntityRenderState state, float phase, float amount, RenderLayer solid, RenderLayer detail) {
        for (var group : AccessoryModels.get(category, style)) {
            pose.push();
            switch (group.bone()) {
                case HEAD -> getContextModel().head.applyTransform(pose);
                case BODY -> getContextModel().body.applyTransform(pose);
                case LEFT_ARM -> getContextModel().leftArm.applyTransform(pose);
                case RIGHT_ARM -> getContextModel().rightArm.applyTransform(pose);
            }
            pose.translate(group.x() / 16, group.y() / 16, group.z() / 16);
            if (category.equals("head")) pose.scale(m.headSize(), m.headSize(), m.headSize());
            switch (group.motion()) {
                case LEFT_WING, RIGHT_WING -> {
                    applyWingPose(pose, group.motion(), m.wingSpread(), m.wingSize(), phase, amount);
                }
                case TAIL -> {
                    pose.scale(m.tailLength(), m.tailLength(), m.tailLength());
                    for (int i = 0; i <= group.index(); i++) {
                        if (i != 0) pose.translate(0, 0, 2.15 / 16);
                        pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-24 + i * 6 + (float)Math.sin(phase * 1.8 - i * .4f) * 3 * amount));
                        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)Math.sin(phase * 1.6 - i * .45f) * 9 * amount));
                    }
                }
                case HALO -> {
                    pose.translate(0, (-m.haloHeight() + (float)Math.sin(phase * 1.8) * .35f * amount) / 16, 0);
                    pose.multiply(RotationAxis.POSITIVE_Y.rotation(phase * .2f));
                }
                case STILL -> { }
            }
            if (group.primary() != null) queue.submitModelPart(group.primary(), pose, solid, light, OverlayTexture.DEFAULT_UV, null, m.primaryColor(), null);
            if (group.accent() != null) queue.submitModelPart(group.accent(), pose, detail,
                m.glow() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, OverlayTexture.DEFAULT_UV, null,
                category.equals("halo") ? m.haloColor() : m.accentColor(), null);
            pose.pop();
        }
    }
    public static void applyWingPose(MatrixStack pose, AccessoryModels.Motion wing, float spread, float size, float phase, float amount) {
        float opening = Math.max(0, Math.min(90, spread));
        // Wider beats the harder the player moves; the bounds below keep them behind the shoulders.
        float reach = 22 * Math.max(0, amount);
        float close = Math.min(reach, opening), open = Math.min(reach, 90 - opening);
        // A smooth bounded wave keeps even maximum-strength flaps behind the shoulders.
        float wave = ((float)Math.sin(phase * 2.2) + 1) * .5f;
        opening = opening - close + (close + open) * wave;
        float side = wing == AccessoryModels.Motion.LEFT_WING ? 1 : -1;
        // Wing meshes extend along +/-X. Negative yaw on +X folds toward +Z (the back).
        pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (opening - 90)));
        pose.scale(size, size, size);
    }
}
