package dev.maro.nathan.render;

import dev.maro.runtime.systems.modules.Modules;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import dev.maro.nathan.modules.Hats;

/**
 * The hats' render layer, added to the player's renderer.
 *
 * <p>In 1.21.11 a feature renderer is a {@link FeatureRenderer} and its one method is
 * {@code submit}, which hands work to a {@link OrderedRenderCommandQueue} rather than
 * drawing into a buffer itself. It goes on
 * {@link net.minecraft.client.render.entity.PlayerEntityRenderer} - which is
 * what the player renderer is called in this version - from a mixin on that
 * renderer's constructor.
 *
 * <p><b>The head bone does the work.</b> {@code getParentModel().head} is the part
 * the game has already posed this frame, and {@code translateAndRotate} walks the
 * pose into it, so the hat inherits head yaw and pitch from the bone rather than
 * from anything worked out here. Sneaking, swimming, crawling and riding are poses
 * on that bone or its parent and sleeping is a rotation of the whole model above
 * both, so every one of them is inherited rather than handled.
 *
 * <p>The layer stays on the renderer for as long as the game runs, since a
 * renderer is built once, and does nothing while the module is off. That is why
 * the module and not this class owns the model and the sheets.
 */
public class HatLayer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public HatLayer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> parent) {
        super(parent);
    }

    @Override
    public void render(MatrixStack pose, OrderedRenderCommandQueue collector, int light, PlayerEntityRenderState state,
                       float yRot, float xRot) {
        Hats module = Modules.get().get(Hats.class);

        if (module == null || !module.isActive() || !module.wants(state)) return;

        pose.push();
        getContextModel().head.applyTransform(pose);
        module.submit(pose, collector, light, state);
        pose.pop();
    }
}
