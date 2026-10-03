package dev.maro.gui.hider;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

/** One textured quad drawn with a custom pipeline (used for the Screen Hider blur). */
record TexturedQuadState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                         float x1, float y1, float x2, float y2,
                         float u1, float v1, float u2, float v2, int color,
                         ScreenRect scissorArea, ScreenRect bounds) implements SimpleGuiElementRenderState {
    @Override
    public void setupVertices(VertexConsumer vertices) {
        // same winding as vanilla quads (the GUI pipeline culls back faces)
        vertices.vertex(pose, x1, y1).texture(u1, v1).color(color);
        vertices.vertex(pose, x1, y2).texture(u1, v2).color(color);
        vertices.vertex(pose, x2, y2).texture(u2, v2).color(color);
        vertices.vertex(pose, x2, y1).texture(u2, v1).color(color);
    }
}
