package dev.maro.gui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/** A batch of coloured quads submitted to the vanilla GUI renderer as a single element. */
public final class ShapeRenderState implements SimpleGuiElementRenderState {
    private final Matrix3x2f pose;
    private final float[] xy;
    private final int[] colors;
    private final int count;
    private final ScreenRect scissor;
    private final ScreenRect bounds;

    private ShapeRenderState(Matrix3x2f pose, float[] xy, int[] colors, int count, ScreenRect scissor, ScreenRect bounds) {
        this.pose = pose;
        this.xy = xy;
        this.colors = colors;
        this.count = count;
        this.scissor = scissor;
        this.bounds = bounds;
    }

    @Override
    public void setupVertices(VertexConsumer vertices) {
        for (int i = 0; i < count; i++) {
            vertices.vertex(pose, xy[i * 2], xy[i * 2 + 1]).color(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.empty();
    }

    @Override
    public ScreenRect scissorArea() {
        return scissor;
    }

    @Override
    public ScreenRect bounds() {
        return bounds;
    }

    /** Collects quads for one shape. Triangles are emitted as quads with a repeated vertex. */
    public static final class Builder {
        private final DrawContext context;
        private float[] xy = new float[512];
        private int[] colors = new int[256];
        private int count;
        private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        Builder(DrawContext context) {
            this.context = context;
        }

        DrawContext context() {
            return context;
        }

        private void vertex(float x, float y, int c) {
            if (count == colors.length) {
                colors = Arrays.copyOf(colors, count * 2);
                xy = Arrays.copyOf(xy, count * 4);
            }
            xy[count * 2] = x;
            xy[count * 2 + 1] = y;
            colors[count] = c;
            count++;
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }

        void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
            // The GUI pipeline culls back faces, so every quad must share the winding vanilla uses
            // (negative signed area in y-down screen space). Flip the ones that don't.
            float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
            if (area > 0) {
                vertex(x1, y1, c1);
                vertex(x4, y4, c4);
                vertex(x3, y3, c3);
                vertex(x2, y2, c2);
            } else {
                vertex(x1, y1, c1);
                vertex(x2, y2, c2);
                vertex(x3, y3, c3);
                vertex(x4, y4, c4);
            }
        }

        ShapeRenderState build(ScreenRect scissor) {
            if (count == 0) return null;
            Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
            int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
            int bw = Math.max(1, (int) Math.ceil(maxX) - bx), bh = Math.max(1, (int) Math.ceil(maxY) - by);
            ScreenRect bounds = new ScreenRect(bx, by, bw, bh).transformEachVertex(pose);
            if (scissor != null) {
                bounds = scissor.intersection(bounds);
                if (bounds == null) return null;
            }
            return new ShapeRenderState(pose, xy, colors, count, scissor, bounds);
        }
    }
}
