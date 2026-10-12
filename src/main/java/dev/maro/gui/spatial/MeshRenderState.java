package dev.maro.gui.spatial;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.maro.mixin.DrawContextAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Quads with corners anywhere on screen, for panes seen in perspective: coloured, or textured
 * with per-corner texture positions. Each quad is turned to the winding the GUI renderer keeps.
 */
public final class MeshRenderState implements SimpleGuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textures;
    private final Matrix3x2f pose;
    private final float[] xy, uv;
    private final int[] colors;
    private final int count;
    private final ScreenRect bounds;

    private MeshRenderState(RenderPipeline pipeline, TextureSetup textures, Matrix3x2f pose, float[] xy, float[] uv, int[] colors, int count, ScreenRect bounds) {
        this.pipeline = pipeline;
        this.textures = textures;
        this.pose = pose;
        this.xy = xy;
        this.uv = uv;
        this.colors = colors;
        this.count = count;
        this.bounds = bounds;
    }

    @Override
    public void setupVertices(VertexConsumer vertices) {
        for (int i = 0; i < count; i++) {
            VertexConsumer v = vertices.vertex(pose, xy[i * 2], xy[i * 2 + 1]);
            if (uv != null) v = v.texture(uv[i * 2], uv[i * 2 + 1]);
            v.color(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textures;
    }

    @Override
    public ScreenRect scissorArea() {
        return null;
    }

    @Override
    public ScreenRect bounds() {
        return bounds;
    }

    /** Collects quads, then adds them to a draw context as one element. */
    public static final class Builder {
        private final boolean textured;
        private float[] xy = new float[256], uv;
        private int[] colors = new int[128];
        private int count;
        private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        public Builder(boolean textured) {
            this.textured = textured;
            if (textured) uv = new float[256];
        }

        private void vertex(float x, float y, float u, float v, int c) {
            if (count == colors.length) {
                colors = Arrays.copyOf(colors, count * 2);
                xy = Arrays.copyOf(xy, count * 4);
                if (textured) uv = Arrays.copyOf(uv, count * 4);
            }
            xy[count * 2] = x;
            xy[count * 2 + 1] = y;
            if (textured) {
                uv[count * 2] = u;
                uv[count * 2 + 1] = v;
            }
            colors[count] = c;
            count++;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        /** A quad by its corners in order round it, each with a texture position and colour. */
        public void quad(float x1, float y1, float u1, float v1, int c1, float x2, float y2, float u2, float v2, int c2,
                         float x3, float y3, float u3, float v3, int c3, float x4, float y4, float u4, float v4, int c4) {
            float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
            if (area > 0) {
                vertex(x1, y1, u1, v1, c1);
                vertex(x4, y4, u4, v4, c4);
                vertex(x3, y3, u3, v3, c3);
                vertex(x2, y2, u2, v2, c2);
            } else {
                vertex(x1, y1, u1, v1, c1);
                vertex(x2, y2, u2, v2, c2);
                vertex(x3, y3, u3, v3, c3);
                vertex(x4, y4, u4, v4, c4);
            }
        }

        /** A coloured triangle, as a quad with its last corner twice. */
        public void triangle(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            quad(x1, y1, 0, 0, c1, x2, y2, 0, 0, c2, x3, y3, 0, 0, c3, x3, y3, 0, 0, c3);
        }

        public boolean isEmpty() {
            return count == 0;
        }

        /** Adds the quads to the context's current layer. */
        public void submit(DrawContext context, RenderPipeline pipeline, TextureSetup textures, int screenW, int screenH) {
            if (count == 0) return;
            Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
            int bx = (int) Math.floor(Math.max(0, minX)), by = (int) Math.floor(Math.max(0, minY));
            int ex = (int) Math.ceil(Math.min(screenW, maxX)), ey = (int) Math.ceil(Math.min(screenH, maxY));
            if (ex <= bx || ey <= by) return;
            ScreenRect bounds = new ScreenRect(bx, by, ex - bx, ey - by).transformEachVertex(pose);
            ((DrawContextAccessor) context).maro$getState()
                    .addSimpleElement(new MeshRenderState(pipeline, textures, pose, xy, uv, colors, count, bounds));
        }
    }
}
