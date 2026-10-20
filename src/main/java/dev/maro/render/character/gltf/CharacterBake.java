// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class CharacterBake {
    static final int HEAD = 0;
    static final int BODY = 1;
    static final int RIGHT_ARM = 2;
    static final int LEFT_ARM = 3;
    static final int RIGHT_LEG = 4;
    static final int LEFT_LEG = 5;
    private static final int[] BUDGETS = new int[]{7500, 15000, 30000};
    private static final int DENSE = 150000;
    private static final float[] STICK = new float[]{0.7f, 0.0f, 0.6f, 0.6f, 0.3f, 0.3f};

    private CharacterBake() {
    }

    public static Result bake(byte[] glb, Options options, ImageDecoder decoder) throws IOException {
        Cut cut;
        GltfFile file = GltfFile.read(glb);
        PosedMesh mesh = PosedMesh.pose(file, Map.of());
        Skeleton skeleton = Skeleton.find(mesh, options.bones);
        Map<Integer, Float> turns = skeleton.armTurns();
        if (!turns.isEmpty()) {
            mesh = PosedMesh.pose(file, turns);
            skeleton = Skeleton.find(mesh, options.bones);
        }
        mesh.normalise(options.height, skeleton.facesBack);
        mesh.cluster(150000);
        Cut cut2 = cut = skeleton.found() ? CharacterBake.skeletonCut(mesh, skeleton, options) : ShapeCut.cut(mesh);
        if (cut == null) {
            cut = CharacterBake.still(mesh);
        }
        List<Piece> pieces = CharacterBake.split(mesh, cut.part());
        CharacterBake.reduce(mesh, pieces);
        TextureAtlas atlas = TextureAtlas.pack(file, CharacterBake.uvBounds(mesh, pieces), decoder);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CharacterBake.write(new DataOutputStream(bytes), mesh, cut, pieces, atlas, options);
        ArrayList<String> limbs = new ArrayList<String>();
        if (CharacterBake.has(pieces, 0)) {
            limbs.add("head");
        }
        if (CharacterBake.has(pieces, 2) || CharacterBake.has(pieces, 3)) {
            limbs.add("arms");
        }
        if (CharacterBake.has(pieces, 4) || CharacterBake.has(pieces, 5)) {
            limbs.add("legs");
        }
        return new Result(bytes.toByteArray(), limbs);
    }

    public static String describe(byte[] glb, Options options) throws IOException {
        GltfFile file = GltfFile.read(glb);
        PosedMesh mesh = PosedMesh.pose(file, Map.of());
        Skeleton skeleton = Skeleton.find(mesh, options.bones);
        StringBuilder b = new StringBuilder(String.format(Locale.ROOT, "%d vertices, %d triangles, %d bones%n", mesh.vertices, mesh.triangles, mesh.joints.size()));
        b.append(skeleton.found() ? skeleton.describe() : "no usable skeleton, limbs are cut by shape\n");
        HashSet<Integer> materials = new HashSet<Integer>();
        for (int v = 0; v < mesh.vertices; ++v) {
            materials.add(mesh.material[v]);
        }
        Iterator iterator = materials.iterator();
        while (iterator.hasNext()) {
            int m = (Integer)iterator.next();
            b.append("material ").append(file.materialName(m)).append('\n');
        }
        return b.toString();
    }

    private static Cut still(PosedMesh mesh) {
        float[][] pivot = new float[6][];
        pivot[1] = new float[]{0.0f, 24.0f, 0.0f};
        int[] part = new int[mesh.vertices];
        Arrays.fill(part, BODY);
        return new Cut(part, pivot, new float[6][], new float[6][], new float[6][]);
    }

    private static Cut skeletonCut(PosedMesh mesh, Skeleton sk, Options o) {
        int[] group = sk.groups();
        boolean legs = sk.thigh[0] >= 0 && sk.thigh[1] >= 0;
        float floor = o.floor;
        if (floor < 0.0f && sk.shin[0] >= 0 && sk.shin[1] >= 0) {
            floor = Math.min(mesh.joint(sk.shin[0])[1], mesh.joint(sk.shin[1])[1]);
        }
        boolean pinned = !o.headMaterials.isEmpty() && sk.head >= 0;
        int[] part = new int[mesh.vertices];
        float[] sum = new float[6];
        for (int v = 0; v < mesh.vertices; ++v) {
            boolean lower;
            float x = mesh.pos[v * 3];
            float y = mesh.pos[v * 3 + 1];
            if (pinned && o.headMaterials.contains(mesh.file.materialName(mesh.material[v]))) {
                part[v] = y < o.neck ? 1 : 0;
                continue;
            }
            Arrays.fill(sum, 0.0f);
            for (int k = 0; k < 4; ++k) {
                int node = mesh.influence[v * 4 + k];
                if (node < 0) continue;
                int n = group[node];
                sum[n] = sum[n] + mesh.weight[v * 4 + k];
            }
            int best = 1;
            for (int b = 0; b < 6; ++b) {
                if (b == 1 || !(sum[b] >= STICK[b]) || !(sum[b] > sum[best]) || b == 0 && pinned) continue;
                best = b;
            }
            boolean bl = lower = best == 1 || best == 4 || best == 5;
            if (legs && lower && o.hip > 0.0f) {
                best = y >= o.hip ? 1 : (x < 0.0f ? 4 : 5);
            } else if (legs && best == 1 && y < floor) {
                best = x < 0.0f ? 4 : 5;
            }
            part[v] = best;
        }
        float[][] pivot = new float[6][];
        float[][] grip = new float[6][];
        float[][] bendAt = new float[6][];
        float[][] bendWeights = new float[6][];
        pivot[1] = new float[]{0.0f, 24.0f, 0.0f};
        if (sk.head >= 0) {
            pivot[0] = mesh.joint(sk.head);
        }
        for (int s = 0; s < 2; ++s) {
            int leg;
            int arm = s == 0 ? 2 : 3;
            int n = leg = s == 0 ? 4 : 5;
            if (sk.upperArm[s] >= 0) {
                pivot[arm] = mesh.joint(sk.upperArm[s]);
                int end = sk.hand[s] >= 0 ? sk.hand[s] : sk.forearm[s];
                float[] fArray = grip[arm] = end >= 0 ? mesh.joint(end) : pivot[arm];
                if (sk.forearm[s] >= 0) {
                    bendAt[arm] = mesh.joint(sk.forearm[s]);
                    bendWeights[arm] = CharacterBake.bend(mesh, sk.forearm[s], sk.upperArm[s]);
                }
            }
            if (sk.thigh[s] < 0) continue;
            pivot[leg] = mesh.joint(sk.thigh[s]);
            if (sk.shin[s] < 0) continue;
            bendAt[leg] = mesh.joint(sk.shin[s]);
            bendWeights[leg] = CharacterBake.bend(mesh, sk.shin[s], sk.thigh[s]);
        }
        return new Cut(part, pivot, grip, bendAt, bendWeights);
    }

    static int owner(int[] part, int a, int b, int c) {
        return part[b] == part[c] ? part[b] : part[a];
    }

    private static List<Piece> split(PosedMesh mesh, int[] part) {
        Piece[][] pieces = new Piece[6][2];
        for (int t = 0; t < mesh.triangles; ++t) {
            int layer;
            int a = mesh.tris[t * 3];
            int b = mesh.tris[t * 3 + 1];
            int c = mesh.tris[t * 3 + 2];
            if (Mat4.area(mesh.pos, a, b, c) < 1.0E-9f) continue;
            int owner = CharacterBake.owner(part, a, b, c);
            Piece p = pieces[owner][layer = mesh.file.translucent(mesh.material[a]) ? 1 : 0];
            if (p == null) {
                Piece piece = new Piece(owner, layer);
                pieces[owner][layer] = piece;
                p = piece;
            }
            p.source.add(t);
            if (layer != 1 || !mesh.file.doubleSided(mesh.material[a])) continue;
            p.source.add(~t);
        }
        ArrayList<Piece> all = new ArrayList<Piece>();
        Piece[][] pieceArray = pieces;
        int n = pieceArray.length;
        for (int i = 0; i < n; ++i) {
            Piece[] row;
            for (Piece p : row = pieceArray[i]) {
                if (p == null) continue;
                all.add(p);
            }
        }
        return all;
    }

    private static void reduce(PosedMesh mesh, List<Piece> pieces) {
        double weightSum = 0.0;
        for (Piece p : pieces) {
            weightSum += (double)p.source.size() * (p.bone == 0 ? 2.0 : 1.0);
        }
        for (Piece p : pieces) {
            int[] targets = new int[BUDGETS.length];
            for (int l = 0; l < BUDGETS.length; ++l) {
                double share = (double)p.source.size() * (p.bone == 0 ? 2.0 : 1.0) / weightSum;
                targets[l] = (int)Math.min((long)p.source.size(), Math.max(16L, Math.round((double)BUDGETS[l] * share)));
            }
            boolean needed = targets[BUDGETS.length - 1] < p.source.size();
            p.levels = new MeshReducer(mesh, p.source, needed).reduce(targets);
        }
    }

    private static Map<Integer, float[]> uvBounds(PosedMesh mesh, List<Piece> pieces) {
        LinkedHashMap<Integer, float[]> used = new LinkedHashMap<Integer, float[]>();
        for (Piece p : pieces) {
            for (int t : p.source) {
                int tri = t < 0 ? ~t : t;
                for (int c = 0; c < 3; ++c) {
                    int v = mesh.tris[tri * 3 + c];
                    float[] b = used.computeIfAbsent(mesh.material[v], k -> new float[]{1e9f, 1e9f, -1e9f, -1e9f});
                    b[0] = Math.min(b[0], mesh.uv[v * 2]);
                    b[1] = Math.min(b[1], mesh.uv[v * 2 + 1]);
                    b[2] = Math.max(b[2], mesh.uv[v * 2]);
                    b[3] = Math.max(b[3], mesh.uv[v * 2 + 1]);
                }
            }
        }
        return used;
    }

    private static boolean has(List<Piece> pieces, int bone) {
        for (Piece p : pieces) {
            if (p.bone != bone) continue;
            return true;
        }
        return false;
    }

    private static Piece find(List<Piece> pieces, int bone, int layer) {
        for (Piece p : pieces) {
            if (p.bone != bone || p.layer != layer) continue;
            return p;
        }
        return null;
    }

    private static void write(DataOutputStream out, PosedMesh mesh, Cut cut, List<Piece> pieces, TextureAtlas atlas, Options o) throws IOException {
        int b;
        float[][] pivot = cut.pivot();
        float[][] grip = cut.grip();
        float[][] bendAt = cut.bendAt();
        float[][] bendWeights = cut.bendWeights();
        float[] box = mesh.bounds();
        float hipY = pivot[4] != null && pivot[5] != null ? (pivot[4][1] + pivot[5][1]) * 0.5f : 12.0f;
        out.writeInt(1095648072);
        out.writeInt(3);
        out.writeFloat(box[1]);
        out.writeFloat(box[4]);
        out.writeFloat(hipY);
        out.writeFloat(o.flat);
        out.writeBoolean(true);
        out.writeBoolean(mesh.colored);
        out.writeInt(BUDGETS.length);
        int bones = 0;
        for (b = 0; b < 6; ++b) {
            if (!CharacterBake.has(pieces, b)) continue;
            ++bones;
        }
        out.writeInt(bones);
        for (b = 0; b < 6; ++b) {
            boolean leg;
            float[] bounds;
            if (!CharacterBake.has(pieces, b)) continue;
            out.writeByte(b);
            for (float f : pivot[b]) {
                out.writeFloat(f);
            }
            for (float f : grip[b] != null ? grip[b] : pivot[b]) {
                out.writeFloat(f);
            }
            for (float f : bounds = CharacterBake.bounds(mesh, pieces, b)) {
                out.writeFloat(f);
            }
            boolean arm = b == 2 || b == 3;
            boolean bl = leg = b == 4 || b == 5;
            out.writeFloat(arm ? 0.6f : (leg ? 0.55f : 1.0f));
            out.writeFloat(arm ? 40.0f : (leg ? 30.0f : 0.0f));
            out.writeBoolean(bendAt[b] != null);
            if (bendAt[b] != null) {
                for (float f : bendAt[b]) {
                    out.writeFloat(f);
                }
            }
            for (int l = 0; l < BUDGETS.length; ++l) {
                for (int layer = 0; layer < 2; ++layer) {
                    CharacterBake.writeLevel(out, mesh, atlas, CharacterBake.find(pieces, b, layer), l, bounds, bendWeights[b], bendAt[b] != null);
                }
            }
        }
        byte[] pixels = atlas.packed();
        out.writeInt(atlas.width);
        out.writeInt(atlas.height);
        out.writeInt(pixels.length);
        out.write(pixels);
        out.flush();
    }

    private static float[] bend(PosedMesh mesh, int joint, int limb) {
        float[] at = mesh.joint(joint);
        float[] w = mesh.below(joint);
        float[] inLimb = mesh.below(limb);
        for (int v = 0; v < mesh.vertices; ++v) {
            if (inLimb[v] >= 0.5f) continue;
            float t = Math.max(0.0f, Math.min(1.0f, at[1] - mesh.pos[v * 3 + 1] + 0.5f));
            w[v] = t * t * (3.0f - 2.0f * t);
        }
        return w;
    }

    private static float[] bounds(PosedMesh mesh, List<Piece> pieces, int bone) {
        float[] b = new float[]{1.0E9f, 1.0E9f, 1.0E9f, -1.0E9f, -1.0E9f, -1.0E9f};
        for (Piece p : pieces) {
            if (p.bone != bone) continue;
            for (int t : p.source) {
                int tri = t < 0 ? ~t : t;
                for (int c = 0; c < 3; ++c) {
                    int v = mesh.tris[tri * 3 + c];
                    for (int k = 0; k < 3; ++k) {
                        b[k] = Math.min(b[k], mesh.pos[v * 3 + k]);
                        b[k + 3] = Math.max(b[k + 3], mesh.pos[v * 3 + k]);
                    }
                }
            }
        }
        for (int k = 0; k < 3; ++k) {
            if (!(b[k + 3] - b[k] < 0.001f)) continue;
            b[k + 3] = b[k] + 0.001f;
        }
        return b;
    }

    private static void writeLevel(DataOutputStream out, PosedMesh mesh, TextureAtlas atlas, Piece p, int level, float[] box, float[] bend, boolean jointed) throws IOException {
        int v;
        if (p == null) {
            out.writeInt(0);
            out.writeInt(0);
            return;
        }
        int[] corners = p.levels[level];
        HashMap<Integer, Integer> remap = new HashMap<Integer, Integer>();
        ArrayList<Integer> keys = new ArrayList<Integer>();
        int[] local = new int[corners.length];
        for (int i = 0; i < corners.length; ++i) {
            Integer idx = (Integer)remap.get(corners[i]);
            if (idx == null) {
                idx = keys.size();
                remap.put(corners[i], idx);
                keys.add(corners[i]);
            }
            local[i] = idx;
        }
        out.writeInt(keys.size());
        Iterator i = keys.iterator();
        while (i.hasNext()) {
            int k = (Integer)i.next();
            v = k < 0 ? ~k : k;
            for (int a = 0; a < 3; ++a) {
                float t = (mesh.pos[v * 3 + a] - box[a]) / (box[a + 3] - box[a]);
                out.writeChar(Math.round(Math.max(0.0f, Math.min(1.0f, t)) * 65535.0f));
            }
        }
        i = keys.iterator();
        while (i.hasNext()) {
            int k = (Integer)i.next();
            v = k < 0 ? ~k : k;
            float sign = k < 0 ? -1.0f : 1.0f;
            for (int a = 0; a < 3; ++a) {
                out.writeByte(Math.round(mesh.nrm[v * 3 + a] * sign * 127.0f));
            }
        }
        i = keys.iterator();
        while (i.hasNext()) {
            int k = (Integer)i.next();
            v = k < 0 ? ~k : k;
            float[] t = atlas.map(mesh.material[v], mesh.uv[v * 2], mesh.uv[v * 2 + 1]);
            out.writeChar(Math.round(Math.max(0.0f, Math.min(1.0f, t[0])) * 65535.0f));
            out.writeChar(Math.round(Math.max(0.0f, Math.min(1.0f, t[1])) * 65535.0f));
        }
        if (mesh.colored) {
            i = keys.iterator();
            while (i.hasNext()) {
                int k = (Integer)i.next();
                int c = mesh.color[k < 0 ? ~k : k];
                out.writeByte(c >> 16);
                out.writeByte(c >> 8);
                out.writeByte(c);
            }
        }
        if (jointed) {
            i = keys.iterator();
            while (i.hasNext()) {
                int k = (Integer)i.next();
                out.writeByte(Math.round(Math.max(0.0f, Math.min(1.0f, bend[k < 0 ? ~k : k])) * 255.0f));
            }
        }
        out.writeInt(local.length / 3);
        boolean wide = keys.size() > 65535;
        for (int i2 : local) {
            if (wide) {
                out.writeInt(i2);
                continue;
            }
            out.writeChar(i2);
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static final class Options {
        public float height = 30.0f;
        public float flat;
        public final Map<String, String> bones = new HashMap<String, String>();
        public final Set<String> headMaterials = new HashSet<String>();
        public float neck = -1.0f;
        public float hip = -1.0f;
        public float floor = -1.0f;
        /** Largest texture side kept by the skinned bake; larger ones are halved until they fit. */
        public int maxTexture = 4096;
    }

    @Environment(value=EnvType.CLIENT)
    record Cut(int[] part, float[][] pivot, float[][] grip, float[][] bendAt, float[][] bendWeights) {
    }

    @Environment(value=EnvType.CLIENT)
    public record Result(byte[] data, List<String> limbs) {
    }

    @Environment(value=EnvType.CLIENT)
    private static final class Piece {
        final int bone;
        final int layer;
        final List<Integer> source = new ArrayList<Integer>();
        int[][] levels;

        Piece(int bone, int layer) {
            this.bone = bone;
            this.layer = layer;
        }
    }
}
