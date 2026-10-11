package dev.maro.runtime.renderer;

import java.util.Arrays;
import dev.maro.runtime.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;
import org.joml.Vector2f;

/** Reusable CPU geometry; submitted GUI states retain their own immutable snapshots. */
public final class MeshBuilder {
    private double[] vertices = new double[64 * 4];
    private int[] vertexColors = new int[64], output = new int[128];
    private int vertexCount, outputCount;
    private double x, y, u, v;
    private int color = 0xFFFFFFFF, vectors;
    private boolean building;

    public MeshBuilder(Object pipeline) {}
    public void begin() { vertexCount = outputCount = vectors = 0; building = true; }
    public void end() { building = false; }
    public boolean isBuilding() { return building; }

    public void ensureCapacity(int additionalVertices, int additionalIndices) {
        int required = vertexCount + additionalVertices;
        if (required > vertexColors.length) {
            int capacity = Math.max(required, vertexColors.length * 2);
            vertices = Arrays.copyOf(vertices, capacity * 4);
            vertexColors = Arrays.copyOf(vertexColors, capacity);
        }
        required = outputCount + additionalIndices;
        if (required > output.length) output = Arrays.copyOf(output, Math.max(required, output.length * 2));
    }
    public void ensureTriCapacity() { ensureCapacity(3, 4); }
    public void ensureQuadCapacity() { ensureCapacity(4, 4); }
    public MeshBuilder vec2(double a, double b) {
        if (vectors++ == 0) { x = a; y = b; } else { u = a; v = b; }
        return this;
    }
    public MeshBuilder color(Color color) { this.color = color.getPacked(); return this; }
    public int next() {
        ensureCapacity(1, 0);
        int offset = vertexCount * 4;
        vertices[offset] = x; vertices[offset + 1] = y;
        vertices[offset + 2] = u; vertices[offset + 3] = v;
        vertexColors[vertexCount] = color;
        vectors = 0; u = v = 0;
        return vertexCount++;
    }
    public void quad(int a, int b, int c, int d) {
        int ai = a * 4, bi = b * 4, ci = c * 4, di = d * 4;
        double area = vertices[ai] * vertices[bi + 1] - vertices[bi] * vertices[ai + 1]
            + vertices[bi] * vertices[ci + 1] - vertices[ci] * vertices[bi + 1]
            + vertices[ci] * vertices[di + 1] - vertices[di] * vertices[ci + 1]
            + vertices[di] * vertices[ai + 1] - vertices[ai] * vertices[di + 1];
        ensureCapacity(0, 4);
        output[outputCount++] = a;
        output[outputCount++] = area > 0 ? d : b;
        output[outputCount++] = c;
        output[outputCount++] = area > 0 ? b : d;
    }
    public void triangle(int a, int b, int c) { quad(a, b, c, c); }

    public void submit(TextureSetup texture, Matrix4f transform) {
        DrawContext context = Renderer2D.context();
        if (context == null || outputCount == 0) return;
        double factor = MinecraftClient.getInstance().getWindow().getScaleFactor();
        // Vanilla consumes these states later in the frame: never reuse their arrays.
        float[] xyuv = new float[outputCount * 4];
        int[] colors = new int[outputCount];
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        var position = new Vector4f();
        var screen = new Vector2f();
        for (int i = 0; i < outputCount; i++) {
            int vertex = output[i], offset = vertex * 4;
            position.set((float) vertices[offset], (float) vertices[offset + 1], 0, 1);
            if (transform != null) transform.transform(position);
            float px = (float) (position.x / factor), py = (float) (position.y / factor);
            xyuv[i * 4] = px; xyuv[i * 4 + 1] = py;
            xyuv[i * 4 + 2] = (float) vertices[offset + 2]; xyuv[i * 4 + 3] = (float) vertices[offset + 3];
            colors[i] = vertexColors[vertex];
            screen.set(px, py);
            context.getMatrices().transformPosition(screen);
            minX = Math.min(minX, screen.x); minY = Math.min(minY, screen.y);
            maxX = Math.max(maxX, screen.x); maxY = Math.max(maxY, screen.y);
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return;
        var bounds = new ScreenRect((int) Math.floor(minX), (int) Math.floor(minY),
            Math.max(1, (int) Math.ceil(maxX) - (int) Math.floor(minX)), Math.max(1, (int) Math.ceil(maxY) - (int) Math.floor(minY)));
        var state = new GuiMeshState(new Matrix3x2f(context.getMatrices()), xyuv, colors,
            texture.texure0() == null ? RenderPipelines.GUI : RenderPipelines.GUI_TEXTURED, texture, bounds);
        context.createNewRootLayer();
        ((dev.maro.mixin.DrawContextAccessor) context).maro$getState().addSimpleElement(state);
    }
}
