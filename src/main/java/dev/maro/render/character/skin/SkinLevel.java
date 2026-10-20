package dev.maro.render.character.skin;

/**
 * One level of detail of a skinned mesh, in character units at its bind pose. Vertices are
 * grouped by material: material m owns vertices {@code [vertexStart[m], vertexStart[m + 1])} and
 * quads {@code [quadStart[m], quadStart[m + 1])}, so each material can be skinned and drawn alone.
 */
public final class SkinLevel {
    public final int vertices;
    public final float[] pos;
    public final float[] nrm;
    public final float[] uv;
    /** Vertex colour (ARGB) times the material's base colour. */
    public final int[] color;
    /** Four skinning slots and weights per vertex; unused ones have weight 0. */
    public final short[] joint;
    public final float[] weight;
    /** Four vertex indices per quad; a triangle repeats its last vertex. */
    public final int[] quads;
    public final int[] vertexStart;
    public final int[] quadStart;
    /** Slot each vertex is mostly moved by, for picking out arms in first person. */
    public final short[] main;

    public SkinLevel(float[] pos, float[] nrm, float[] uv, int[] color, short[] joint, float[] weight, int[] quads, int[] vertexStart, int[] quadStart) {
        this.vertices = pos.length / 3;
        this.pos = pos;
        this.nrm = nrm;
        this.uv = uv;
        this.color = color;
        this.joint = joint;
        this.weight = weight;
        this.quads = quads;
        this.vertexStart = vertexStart;
        this.quadStart = quadStart;
        this.main = new short[vertices];
        for (int v = 0; v < vertices; v++) {
            int best = 0;
            for (int k = 1; k < 4; k++) if (weight[v * 4 + k] > weight[v * 4 + best]) best = k;
            main[v] = joint[v * 4 + best];
        }
    }

    public int materials() {
        return vertexStart.length - 1;
    }

    public int triangles() {
        int n = 0;
        for (int q = 0; q < quads.length; q += 4) n += quads[q + 2] == quads[q + 3] ? 1 : 2;
        return n;
    }
}
