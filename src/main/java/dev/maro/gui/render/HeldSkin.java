package dev.maro.gui.render;

import dev.maro.mixin.DrawContextAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

/** Crisp skin pixels with a one-pixel coverage edge, including while the device rotates. */
public final class HeldSkin {
    private static TextureSetup setup;

    private HeldSkin() { }

    public static void arm(DrawContext ctx, Identifier skin, boolean left, boolean slim, float w, float h) {
        int width = slim ? 3 : 4;
        patch(ctx, skin, 0, 0, w, h, left ? 36 : 44, left ? 52 : 20, width, 12);
        patch(ctx, skin, 0, 0, w, h, left ? 52 : 44, left ? 52 : 36, width, 12);
    }

    public static void thumb(DrawContext ctx, Identifier skin, boolean left, boolean slim,
                             float x, float y, float w, float h) {
        patch(ctx, skin, x, y, w, h, left ? 36 : 44, left ? 60 : 28, slim ? 3 : 4, 4);
    }

    private static void patch(DrawContext ctx, Identifier skin, float x, float y, float w, float h,
                              int u, int v, int uw, int vh) {
        var client = MinecraftClient.getInstance();
        var texture = client.getTextureManager().getTexture(skin);
        if (setup == null || setup.texure0() != texture.getGlTextureView())
            setup = TextureSetup.of(texture.getGlTextureView(), texture.getSampler());
        var pose = new Matrix3x2f(ctx.getMatrices());
        double pixels = client.getWindow().getScaleFactor();
        float fx = (float) (.5 / Math.max(.001, pixels * Math.hypot(pose.m00(), pose.m01())));
        float fy = (float) (.5 / Math.max(.001, pixels * Math.hypot(pose.m10(), pose.m11())));
        fx = Math.min(fx, w / 4); fy = Math.min(fy, h / 4);
        int bx = (int) Math.floor(x - fx), by = (int) Math.floor(y - fy);
        var bounds = new ScreenRect(bx, by, (int) Math.ceil(x + w + fx) - bx,
            (int) Math.ceil(y + h + fy) - by).transformEachVertex(pose);
        var scissor = Render2D.getScissor();
        if (scissor != null) { bounds = bounds.intersection(scissor); if (bounds == null) return; }
        int alpha = Math.round(255 * Math.clamp(Render2D.getAlpha(), 0, 1));
        ((DrawContextAccessor) ctx).maro$getState().addSimpleElement(new Patch(pose, setup, bounds, scissor,
            x, y, w, h, u, v, uw, vh, fx, fy, alpha));
    }

    private record Patch(Matrix3x2f pose, TextureSetup textureSetup, ScreenRect bounds, ScreenRect scissorArea,
                         float x, float y, float w, float h, int u, int v, int uw, int vh,
                         float fx, float fy, int alpha) implements SimpleGuiElementRenderState {
        @Override public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() { return RenderPipelines.GUI_TEXTURED; }

        @Override public void setupVertices(VertexConsumer out) {
            // Nine adjacent quads share their edges. Only the silhouette fades; the sampler stays sharp.
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                vertex(out, col, row); vertex(out, col, row + 1);
                vertex(out, col + 1, row + 1); vertex(out, col + 1, row);
            }
        }

        private void vertex(VertexConsumer out, int col, int row) {
            float px = switch (col) { case 0 -> -fx; case 1 -> fx; case 2 -> w - fx; default -> w + fx; };
            float py = switch (row) { case 0 -> -fy; case 1 -> fy; case 2 -> h - fy; default -> h + fy; };
            int coverage = col == 0 || col == 3 || row == 0 || row == 3 ? 0 : alpha;
            // Clamp the fringe to this face so adjacent sleeve/skin atlas pixels cannot bleed into it.
            float tu = (u + Math.clamp(px / w * uw, .001f, uw - .001f)) / 64f;
            float tv = (v + Math.clamp(py / h * vh, .001f, vh - .001f)) / 64f;
            out.vertex(pose, x + px, y + py).color(coverage << 24 | 0xFFFFFF).texture(tu, tv);
        }
    }
}
