package dev.maro.render.character.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.maro.render.character.skin.Human;
import dev.maro.render.character.skin.NodeClip;
import dev.maro.render.character.skin.Rig;
import dev.maro.render.character.skin.SkinFormat;
import dev.maro.render.character.skin.SkinMaterial;
import dev.maro.render.character.skin.SkinMath;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Bakes a GLB for skeletal animation. Keeps the whole skeleton and every vertex's joint weights
 * (four at most), rebuilds the bind pose from the inverse bind matrices, finds the humanoid bones
 * and the canonical rest (limbs hanging straight), keeps the mesh whole (at High nothing is
 * removed below the cap), every texture at its own resolution and the file's own clips. A model
 * without a usable skeleton gets one fitted to its shape; returns null when neither works, so
 * the rigid-part bake can take over.
 */
@Environment(value=EnvType.CLIENT)
public final class SkinBake {
    /** Triangle budgets for Low, Medium and High; High keeps everything up to its cap. */
    private static final int[] BUDGETS = new int[]{14000, 40000, 110000};
    private static final float HEIGHT = 30.0f;
    private static final int MAX_TEXTURE = 4096;
    /** Hanging arms are turned out from the body by this much at canonical rest. */
    private static final float ARM_OUT = 6.0f;

    private SkinBake() {
    }

    public static byte[] bake(byte[] glb, CharacterBake.Options options, ImageDecoder decoder) throws IOException {
        SkinFormat.Baked baked = build(glb, options, decoder);
        if (baked == null) return null;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SkinFormat.write(new DataOutputStream(bytes), baked);
        return bytes.toByteArray();
    }

    public static SkinFormat.Baked build(byte[] glb, CharacterBake.Options options, ImageDecoder decoder) throws IOException {
        GltfFile file = GltfFile.read(glb);
        Scene s = new Scene(file);
        s.collect();
        if (s.triangles() == 0) throw new IOException("it has no triangles");
        s.weldNormals();
        int[] role = Skeleton.roles(s.names(), s.parentArray(), s.xs(), s.usable());
        boolean rigged = s.skinned && s.humanoid(role);
        if (!rigged) {
            role = AutoRig.fit(file, s);
            if (role == null) return null;
        }
        return s.finish(role, rigged, options, decoder);
    }

    // ---- the scene as read from the file --------------------------------------------------

    static final class Scene {
        final GltfFile file;
        final List<String> name = new ArrayList<>();
        final List<Integer> parent = new ArrayList<>();
        /** Local and bind world matrices (3x4) per node. */
        final List<float[]> local = new ArrayList<>(), bind = new ArrayList<>();
        int fileNodes;
        boolean skinned;
        // vertices (bind world), four influences (node indices) each
        FloatList pos = new FloatList(), nrm = new FloatList(), uv = new FloatList();
        IntList color = new IntList(), material = new IntList(), node = new IntList(), tris = new IntList(), primitive = new IntList();
        FloatList weight = new FloatList();
        /** Which primitives came with normals. */
        final List<Boolean> hadNormals = new ArrayList<>();
        final List<String> meshName = new ArrayList<>();

        Scene(GltfFile file) {
            this.file = file;
            fileNodes = file.nodes();
            int[] par = new int[fileNodes];
            Arrays.fill(par, -1);
            for (int n = 0; n < fileNodes; n++) {
                for (JsonElement c : GltfFile.array(file.node(n), "children")) {
                    int ci = c.getAsInt();
                    if (ci >= 0 && ci < fileNodes) par[ci] = n;
                }
            }
            for (int n = 0; n < fileNodes; n++) {
                name.add(file.nodeName(n));
                parent.add(par[n]);
                float[] m = new float[12];
                SkinMath.fromGltf(Mat4.local(file.node(n)), 0, m, 0);
                local.add(m);
                bind.add(null);
            }
        }

        String[] names() {
            return name.toArray(new String[0]);
        }

        int[] parentArray() {
            int[] p = new int[parent.size()];
            for (int i = 0; i < p.length; i++) p[i] = parent.get(i);
            return p;
        }

        /** The nodes that can play a bone: every skin's joints, or every node when nothing is skinned. */
        boolean[] usable() {
            boolean[] out = new boolean[nodes()];
            boolean any = false;
            for (JsonElement sk : GltfFile.array(file.json, "skins")) {
                for (JsonElement j : GltfFile.array(sk.getAsJsonObject(), "joints")) {
                    int jn = j.getAsInt();
                    if (jn >= 0 && jn < out.length) {
                        out[jn] = true;
                        any = true;
                    }
                }
            }
            if (!any) Arrays.fill(out, true);
            return out;
        }

        float[] xs() {
            float[] x = new float[bind.size()];
            for (int i = 0; i < x.length; i++) x[i] = bind.get(i) == null ? 0 : bind.get(i)[3];
            return x;
        }

        int nodes() {
            return name.size();
        }

        int vertices() {
            return pos.size / 3;
        }

        int triangles() {
            return tris.size / 3;
        }

        /** Adds a node (for a fitted skeleton); returns its index. */
        int add(String n, int p, float[] bindWorld) {
            name.add(n);
            parent.add(p);
            float[] l = new float[12];
            if (p < 0) System.arraycopy(bindWorld, 0, l, 0, 12);
            else {
                float[] inv = new float[12];
                SkinMath.invertM(bind.get(p), 0, inv, 0);
                SkinMath.mulM(inv, 0, bindWorld, 0, l, 0);
            }
            local.add(l);
            bind.add(bindWorld.clone());
            return name.size() - 1;
        }

        List<Integer> roots() {
            List<Integer> out = new ArrayList<>();
            JsonArray scenes = GltfFile.array(file.json, "scenes");
            if (!scenes.isEmpty()) {
                int sc = Math.max(0, Math.min(scenes.size() - 1, GltfFile.integer(file.json, "scene", 0)));
                for (JsonElement r : GltfFile.array(scenes.get(sc).getAsJsonObject(), "nodes")) out.add(r.getAsInt());
            }
            if (out.isEmpty()) for (int n = 0; n < fileNodes; n++) if (parent.get(n) < 0) out.add(n);
            return out;
        }

        /** Reads every triangle primitive in the scene, its vertices placed at the bind pose. */
        void collect() throws IOException {
            float[][] scene = new float[fileNodes][];
            for (int r : roots()) world(r, null, scene);
            // The bind pose of each joint: its inverse bind matrix, under where the skin is placed. A
            // skinned mesh's own node transform does not count (the glTF rule); the skin sits where
            // its joints, as the file poses them, carry their inverse bind matrices.
            JsonArray skins = GltfFile.array(file.json, "skins");
            skinPlace = new float[skins.size()][];
            skinPosed = new float[skins.size()][][];
            float[][] jointBind = new float[fileNodes][];
            for (int skin = 0; skin < skins.size(); skin++) {
                JsonObject sk = skins.get(skin).getAsJsonObject();
                JsonArray joints = GltfFile.array(sk, "joints");
                float[] ibm = sk.has("inverseBindMatrices") ? file.floats(sk.get("inverseBindMatrices").getAsInt()) : null;
                int count = joints.size();
                float[][] inv = new float[count][];
                float[][] place = new float[count][];
                for (int j = 0; j < count; j++) {
                    int jn = joints.get(j).getAsInt();
                    inv[j] = new float[12];
                    if (ibm != null && ibm.length >= j * 16 + 16) SkinMath.fromGltf(ibm, j * 16, inv[j], 0);
                    else SkinMath.identityM(inv[j], 0);
                    if (jn < 0 || jn >= fileNodes || scene[jn] == null) continue;
                    place[j] = new float[12];
                    SkinMath.mulM(scene[jn], 0, inv[j], 0, place[j], 0);
                }
                skinPlace[skin] = agreed(place);
                skinPosed[skin] = place;
                for (int j = 0; j < count; j++) {
                    int jn = joints.get(j).getAsInt();
                    if (jn < 0 || jn >= fileNodes || jointBind[jn] != null) continue;
                    if (skinPlace[skin] == null) {
                        // The file shows the skin posed away from its bind pose: that pose is the rest.
                        jointBind[jn] = scene[jn] != null ? scene[jn].clone() : identity();
                        continue;
                    }
                    float[] b = new float[12], world = new float[12];
                    SkinMath.invertM(inv[j], 0, b, 0);
                    SkinMath.mulM(skinPlace[skin], 0, b, 0, world, 0);
                    jointBind[jn] = world;
                }
            }
            // Every node's bind world: a joint's from its skin, anything else from its parent.
            int[] par = parentArray();
            int[] order = orderOf(par);
            for (int n : order) {
                float[] b;
                if (jointBind[n] != null) b = jointBind[n];
                else if (par[n] >= 0 && bind.get(par[n]) != null) {
                    b = new float[12];
                    SkinMath.mulM(bind.get(par[n]), 0, local.get(n), 0, b, 0);
                } else b = scene[n] != null ? scene[n] : local.get(n).clone();
                bind.set(n, b);
            }
            for (int n : order) {
                int p = par[n];
                float[] l = new float[12];
                if (p < 0) System.arraycopy(bind.get(n), 0, l, 0, 12);
                else {
                    float[] inv = new float[12];
                    SkinMath.invertM(bind.get(p), 0, inv, 0);
                    SkinMath.mulM(inv, 0, bind.get(n), 0, l, 0);
                }
                local.set(n, l);
            }
            for (int r : roots()) walk(r, scene, 0);
        }

        /** Per skin: where its space sits at bind, or null when the file poses it elsewhere ({@link #skinPosed} then). */
        float[][] skinPlace = new float[0][];
        /** Per skin and joint: the joint's file pose times its inverse bind matrix. */
        float[][][] skinPosed = new float[0][][];

        /**
         * The placement most joints agree on (each joint's file pose times its inverse bind
         * matrix): where the skin's own space sits when the file shows the bind pose.
         */
        private static float[] agreed(float[][] place) {
            float[] best = null;
            int bestVotes = -1;
            for (int i = 0; i < place.length; i++) {
                if (place[i] == null) continue;
                float size = 1e-6f;
                for (int k = 0; k < 12; k++) size = Math.max(size, Math.abs(place[i][k]));
                int votes = 0;
                for (int j = 0; j < place.length; j++) {
                    if (place[j] == null) continue;
                    boolean same = true;
                    for (int k = 0; k < 12 && same; k++) same = Math.abs(place[i][k] - place[j][k]) <= size * 1e-3f;
                    if (same) votes++;
                }
                if (votes > bestVotes) {
                    bestVotes = votes;
                    best = place[i];
                }
            }
            int present = 0;
            for (float[] p : place) if (p != null) present++;
            // Most joints must agree; otherwise the file shows the skin in some other pose.
            if (best == null) return present == 0 ? identity() : null;
            return bestVotes * 5 >= present * 4 ? best : null;
        }

        private void world(int n, float[] parentWorld, float[][] out) {
            if (n < 0 || n >= fileNodes || out[n] != null) return;
            float[] w = new float[12];
            if (parentWorld == null) System.arraycopy(local.get(n), 0, w, 0, 12);
            else SkinMath.mulM(parentWorld, 0, local.get(n), 0, w, 0);
            out[n] = w;
            for (JsonElement c : GltfFile.array(file.node(n), "children")) world(c.getAsInt(), w, out);
        }

        private void walk(int n, float[][] scene, int depth) throws IOException {
            if (n < 0 || n >= fileNodes || depth > fileNodes) return;
            JsonObject nd = file.node(n);
            if (nd.has("mesh")) {
                JsonObject mesh = GltfFile.array(file.json, "meshes").get(nd.get("mesh").getAsInt()).getAsJsonObject();
                int skin = GltfFile.integer(nd, "skin", -1);
                String meshLabel = name.get(n) + " " + GltfFile.object(mesh, "name");
                for (JsonElement p : GltfFile.array(mesh, "primitives")) primitive(p.getAsJsonObject(), n, skin, meshLabel);
            }
            for (JsonElement c : GltfFile.array(nd, "children")) walk(c.getAsInt(), scene, depth + 1);
        }

        private void primitive(JsonObject p, int n, int skin, String label) throws IOException {
            if (GltfFile.integer(p, "mode", 4) != 4) return;
            JsonObject attrs = GltfFile.object(p, "attributes");
            if (!attrs.has("POSITION")) return;
            int mat = GltfFile.integer(p, "material", -1);
            float[] ps = file.floats(attrs.get("POSITION").getAsInt());
            int count = ps.length / 3;
            float[] ns = attrs.has("NORMAL") ? file.floats(attrs.get("NORMAL").getAsInt()) : null;
            String uvKey = "TEXCOORD_" + file.texCoord(mat);
            float[] ts = attrs.has(uvKey) ? file.floats(attrs.get(uvKey).getAsInt()) : new float[count * 2];
            float[] cs = attrs.has("COLOR_0") ? file.floats(attrs.get("COLOR_0").getAsInt()) : null;
            int cn = cs == null ? 0 : cs.length / Math.max(1, count);
            int[] idx = p.has("indices") ? file.ints(p.get("indices").getAsInt()) : null;
            if (idx == null) {
                idx = new int[count];
                for (int i = 0; i < count; i++) idx[i] = i;
            }
            for (int i : idx) if (i < 0 || i >= count) throw new IOException("a mesh has broken indices");
            if (triangles() + idx.length / 3 > 4_000_000) throw new IOException("it has more than four million triangles");
            int[] jointNodes = null, js = null;
            float[] ws = null;
            if (skin >= 0 && attrs.has("JOINTS_0") && attrs.has("WEIGHTS_0")) {
                JsonObject sk = GltfFile.array(file.json, "skins").get(skin).getAsJsonObject();
                JsonArray list = GltfFile.array(sk, "joints");
                jointNodes = new int[list.size()];
                for (int j = 0; j < jointNodes.length; j++) jointNodes[j] = list.get(j).getAsInt();
                js = file.ints(attrs.get("JOINTS_0").getAsInt());
                ws = file.floats(attrs.get("WEIGHTS_0").getAsInt());
                skinned = true;
            }
            // A skinned mesh's vertices are at the bind pose in the skin's space; anything else hangs from its node.
            float[] place = jointNodes != null && skin < skinPlace.length ? skinPlace[skin] : bind.get(n);
            float[][] posed = jointNodes != null && place == null ? skinPosed[skin] : null;
            float[] blended = new float[12];
            int prim = hadNormals.size();
            hadNormals.add(ns != null);
            meshName.add(label + " " + file.materialName(mat));
            int base = vertices();
            float[] q = new float[3];
            for (int i = 0; i < count; i++) {
                if (posed != null) {
                    // Skinned to the file's pose with its own weights.
                    Arrays.fill(blended, 0);
                    float total = 0;
                    for (int k = 0; k < 4; k++) {
                        int ji = js[i * 4 + k];
                        float wk = ws[i * 4 + k];
                        if (wk <= 0 || ji < 0 || ji >= posed.length || posed[ji] == null) continue;
                        for (int e = 0; e < 12; e++) blended[e] += posed[ji][e] * wk;
                        total += wk;
                    }
                    if (total <= 1e-6f) SkinMath.identityM(blended, 0);
                    else for (int e = 0; e < 12; e++) blended[e] /= total;
                    place = blended;
                }
                SkinMath.point(place, 0, ps[i * 3], ps[i * 3 + 1], ps[i * 3 + 2], q, 0);
                pos.add(q[0], q[1], q[2]);
                float nx = ns == null ? 0 : ns[i * 3], ny = ns == null ? 1 : ns[i * 3 + 1], nz = ns == null ? 0 : ns[i * 3 + 2];
                float rx = place[0] * nx + place[1] * ny + place[2] * nz, ry = place[4] * nx + place[5] * ny + place[6] * nz, rz = place[8] * nx + place[9] * ny + place[10] * nz;
                float l = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (l > 1e-9f) nrm.add(rx / l, ry / l, rz / l);
                else nrm.add(0, 1, 0);
                uv.add(ts[i * 2], ts[i * 2 + 1]);
                color.add(cs == null ? -1 : 0xFF000000 | srgb(cs[i * cn]) << 16 | srgb(cs[i * cn + 1]) << 8 | srgb(cs[i * cn + 2]));
                material.add(mat);
                primitive.add(prim);
                if (jointNodes != null) {
                    // Strongest four, strongest first.
                    float[] w = new float[4];
                    int[] j = new int[4];
                    for (int k = 0; k < 4; k++) {
                        int ji = js[i * 4 + k];
                        w[k] = ws[i * 4 + k];
                        j[k] = ji >= 0 && ji < jointNodes.length ? jointNodes[ji] : -1;
                        if (j[k] < 0) w[k] = 0;
                    }
                    sortInfluences(j, w);
                    float sum = w[0] + w[1] + w[2] + w[3];
                    if (sum <= 1e-6f) {
                        j[0] = n;
                        w[0] = 1;
                        sum = 1;
                    }
                    for (int k = 0; k < 4; k++) {
                        node.add(w[k] > 0 ? j[k] : j[0]);
                        weight.add(w[k] / sum);
                    }
                } else {
                    node.add(n);
                    node.add(n);
                    node.add(n);
                    node.add(n);
                    weight.add(1, 0, 0, 0);
                }
            }
            for (int t = 0; t + 2 < idx.length; t += 3) tris.add(base + idx[t], base + idx[t + 1], base + idx[t + 2]);
            if (ns == null) smoothNormals(base, count);
        }

        /** Area-weighted normals for a primitive that had none, shared by vertices in the same place. */
        private void smoothNormals(int base, int count) {
            float[] p = pos.data;
            HashMap<Long, float[]> sums = new HashMap<>();
            long[] keys = new long[count];
            for (int v = 0; v < count; v++) {
                int i = base + v;
                long key = (long) Float.floatToIntBits(p[i * 3]) * 31L * 31L + (long) Float.floatToIntBits(p[i * 3 + 1]) * 31L + Float.floatToIntBits(p[i * 3 + 2]);
                keys[v] = key;
                sums.computeIfAbsent(key, k -> new float[3]);
            }
            for (int t = 0; t < tris.size; t += 3) {
                int a = tris.data[t], b = tris.data[t + 1], c = tris.data[t + 2];
                if (a < base || a >= base + count) continue;
                float ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
                float vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
                float fx = uy * vz - uz * vy, fy = uz * vx - ux * vz, fz = ux * vy - uy * vx;
                for (int v : new int[]{a, b, c}) {
                    float[] s = sums.get(keys[v - base]);
                    s[0] += fx;
                    s[1] += fy;
                    s[2] += fz;
                }
            }
            for (int v = 0; v < count; v++) {
                float[] s = sums.get(keys[v]);
                float l = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
                int i = base + v;
                if (l > 1e-12f) {
                    nrm.data[i * 3] = s[0] / l;
                    nrm.data[i * 3 + 1] = s[1] / l;
                    nrm.data[i * 3 + 2] = s[2] / l;
                }
            }
        }

        /**
         * Vertices in the same place (split for UVs or between primitives) share one normal where
         * their normals are close, so seams do not show in the lighting; real creases (normals far
         * apart) stay sharp.
         */
        void weldNormals() {
            int verts = vertices();
            float[] p = pos.data, n = nrm.data;
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < verts * 3; i++) {
                lo = Math.min(lo, p[i]);
                hi = Math.max(hi, p[i]);
            }
            float cell = Math.max(1e-9f, (hi - lo) * 2e-6f);
            HashMap<Long, int[]> first = new HashMap<>(verts * 2);
            int[] next = new int[verts];
            Arrays.fill(next, -1);
            int[] head = new int[verts];
            for (int v = 0; v < verts; v++) {
                long key = (Math.round(p[v * 3] / cell) * 73856093L) ^ (Math.round(p[v * 3 + 1] / cell) * 19349663L) ^ (Math.round(p[v * 3 + 2] / cell) * 83492791L);
                int[] h = first.get(key);
                if (h == null) {
                    first.put(key, new int[]{v});
                    head[v] = v;
                } else {
                    // Chain onto the group.
                    next[v] = next[h[0]];
                    next[h[0]] = v;
                    head[v] = h[0];
                }
            }
            float[] out = n.clone();
            float close = (float) Math.cos(Math.toRadians(55));
            for (int v = 0; v < verts; v++) {
                int g = head[v];
                if (next[g] < 0 && g == v) continue;
                float x = 0, y = 0, z = 0;
                for (int u = g; u >= 0; u = next[u]) {
                    float d = n[v * 3] * n[u * 3] + n[v * 3 + 1] * n[u * 3 + 1] + n[v * 3 + 2] * n[u * 3 + 2];
                    if (d < close) continue;
                    x += n[u * 3];
                    y += n[u * 3 + 1];
                    z += n[u * 3 + 2];
                }
                float l = (float) Math.sqrt(x * x + y * y + z * z);
                if (l > 1e-6f) {
                    out[v * 3] = x / l;
                    out[v * 3 + 1] = y / l;
                    out[v * 3 + 2] = z / l;
                }
            }
            System.arraycopy(out, 0, n, 0, verts * 3);
        }

        /** A humanoid the file's skeleton can drive: arms, legs, head and their vertices weighted to them. */
        boolean humanoid(int[] role) {
            Human[] needed = {Human.HEAD, Human.RIGHT_UPPER_ARM, Human.LEFT_UPPER_ARM, Human.RIGHT_FOREARM, Human.LEFT_FOREARM,
                    Human.RIGHT_THIGH, Human.LEFT_THIGH, Human.RIGHT_SHIN, Human.LEFT_SHIN};
            for (Human h : needed) if (role[h.ordinal()] < 0) return false;
            // The limbs must actually move vertices (a skeleton beside an unskinned mesh does not count).
            boolean[] moving = new boolean[nodes()];
            for (int i = 0; i < node.size; i++) if (weight.data[i] > 0.05f) moving[node.data[i]] = true;
            int[] par = parentArray();
            for (Human h : new Human[]{Human.RIGHT_FOREARM, Human.LEFT_FOREARM, Human.RIGHT_SHIN, Human.LEFT_SHIN}) {
                int r = role[h.ordinal()];
                boolean any = false;
                for (int n = 0; n < moving.length && !any; n++) if (moving[n] && under(par, n, r)) any = true;
                if (!any) return false;
            }
            return true;
        }

        // ---- finishing: facing, canonical rest, units, measurements, materials, levels, clips

        SkinFormat.Baked finish(int[] role, boolean rigged, CharacterBake.Options options, ImageDecoder decoder) throws IOException {
            int n = nodes();
            int[] par = parentArray();
            int[] order = orderOf(par);
            float[] restT = new float[n * 3], restR = new float[n * 4], restS = new float[n * 3];
            for (int i = 0; i < n; i++) SkinMath.decomposeM(local.get(i), 0, restT, i * 3, restR, i * 4, restS, i * 3);

            // Facing: the character's right is from its left arm (or leg) to its right one; forward = up x right.
            float[] rootQ = facing(role);
            float[] rootRot = new float[12];
            float[] zero = {0, 0, 0}, one = {1, 1, 1};
            SkinMath.composeM(zero, 0, rootQ, 0, one, 0, rootRot, 0);

            // Canonical rest: arms and legs hanging straight, each limb turned about its own joint.
            float[] canonLocal = restR.clone();
            float[] canonWorld = new float[n * 4];
            float[] worldC = new float[n * 12];
            canonical(par, order, role, restT, restS, rootRot, rootQ, canonLocal, canonWorld, worldC);

            // Units: feet on y = 0, the hips over the origin, the height as the options ask.
            float[] skinC = skinMatrices(worldC, null);
            float[] placed = placeVertices(skinC);
            float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY,
                    minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < vertices(); v++) {
                minX = Math.min(minX, placed[v * 3]);
                maxX = Math.max(maxX, placed[v * 3]);
                minY = Math.min(minY, placed[v * 3 + 1]);
                maxY = Math.max(maxY, placed[v * 3 + 1]);
                minZ = Math.min(minZ, placed[v * 3 + 2]);
                maxZ = Math.max(maxZ, placed[v * 3 + 2]);
            }
            float target = options.height > 0 ? options.height : HEIGHT;
            float scale = target / Math.max(1e-6f, maxY - minY);
            int hips = role[Human.HIPS.ordinal()];
            float cx = hips >= 0 ? worldC[hips * 12 + 3] : (minX + maxX) / 2, cz = hips >= 0 ? worldC[hips * 12 + 11] : (minZ + maxZ) / 2;
            if (Math.abs(cx - (minX + maxX) / 2) > (maxX - minX) * 0.25f) cx = (minX + maxX) / 2;
            if (Math.abs(cz - (minZ + maxZ) / 2) > (maxZ - minZ) * 0.35f) cz = (minZ + maxZ) / 2;
            float[] root = new float[12];
            for (int i = 0; i < 12; i++) root[i] = rootRot[i] * scale;
            root[3] = -cx * scale;
            root[7] = -minY * scale;
            root[11] = -cz * scale;
            float[] unit = new float[12];
            SkinMath.identityM(unit, 0);
            unit[0] = unit[5] = unit[10] = scale;
            unit[3] = -cx * scale;
            unit[7] = -minY * scale;
            unit[11] = -cz * scale;

            // Bind matrices in character units, and the vertices placed there.
            float[] bindInverse = new float[n * 12];
            float[] w = new float[12];
            for (int i = 0; i < n; i++) {
                float[] rotated = new float[12];
                SkinMath.mulM(root, 0, bindRoot(i), 0, w, 0);
                SkinMath.invertM(w, 0, bindInverse, i * 12);
            }
            float[] vp = new float[vertices() * 3], vn = new float[vertices() * 3];
            float[] q = new float[3];
            for (int v = 0; v < vertices(); v++) {
                SkinMath.point(root, 0, pos.data[v * 3], pos.data[v * 3 + 1], pos.data[v * 3 + 2], q, 0);
                System.arraycopy(q, 0, vp, v * 3, 3);
                float nx = nrm.data[v * 3], ny = nrm.data[v * 3 + 1], nz = nrm.data[v * 3 + 2];
                vn[v * 3] = rootRot[0] * nx + rootRot[1] * ny + rootRot[2] * nz;
                vn[v * 3 + 1] = rootRot[4] * nx + rootRot[5] * ny + rootRot[6] * nz;
                vn[v * 3 + 2] = rootRot[8] * nx + rootRot[9] * ny + rootRot[10] * nz;
            }

            // Measurements at canonical rest, in character units.
            float[] worldN = new float[n * 12];
            for (int i = 0; i < n; i++) SkinMath.mulM(unit, 0, worldC, i * 12, worldN, i * 12);
            float[] canonPlaced = new float[vertices() * 3];
            for (int v = 0; v < vertices(); v++) {
                SkinMath.point(unit, 0, placed[v * 3], placed[v * 3 + 1], placed[v * 3 + 2], q, 0);
                System.arraycopy(q, 0, canonPlaced, v * 3, 3);
            }
            float hipY = hips >= 0 ? worldN[hips * 12 + 7] : target * 0.5f;
            float leg = 0, arm = 0;
            int legs = 0, arms = 0;
            float[] footRest = new float[2], toeRest = new float[2], palm = new float[6];
            for (int side = 0; side < 2; side++) {
                Human thigh = side == 0 ? Human.RIGHT_THIGH : Human.LEFT_THIGH, shin = side == 0 ? Human.RIGHT_SHIN : Human.LEFT_SHIN;
                Human foot = side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT, toe = side == 0 ? Human.RIGHT_TOE : Human.LEFT_TOE;
                Human upper = side == 0 ? Human.RIGHT_UPPER_ARM : Human.LEFT_UPPER_ARM, fore = side == 0 ? Human.RIGHT_FOREARM : Human.LEFT_FOREARM;
                Human hand = side == 0 ? Human.RIGHT_HAND : Human.LEFT_HAND;
                int t = role[thigh.ordinal()], k = role[shin.ordinal()], f = role[foot.ordinal()], o = role[toe.ordinal()];
                if (t >= 0 && k >= 0) {
                    float l = distance(worldN, t, k) + (f >= 0 ? distance(worldN, k, f) : distance(worldN, t, k));
                    leg += l;
                    legs++;
                }
                footRest[side] = f >= 0 ? worldN[f * 12 + 7] : k >= 0 ? worldN[k * 12 + 7] : 0;
                toeRest[side] = o >= 0 ? worldN[o * 12 + 7] : footRest[side];
                int u = role[upper.ordinal()], e = role[fore.ordinal()], h = role[hand.ordinal()];
                if (u >= 0 && e >= 0) {
                    arm += distance(worldN, u, e) + (h >= 0 ? distance(worldN, e, h) : distance(worldN, u, e));
                    arms++;
                }
                if (h >= 0) {
                    float[] c = centroid(canonPlaced, h, par, 0.5f);
                    float[] inv = new float[12];
                    SkinMath.invertM(worldN, h * 12, inv, 0);
                    if (c != null) {
                        SkinMath.point(inv, 0, c[0], c[1], c[2], q, 0);
                        System.arraycopy(q, 0, palm, side * 3, 3);
                    }
                }
            }
            // The soles: heel, ball and toe tip of each foot at rest, in their bones' frames.
            int[] soleNode = new int[6];
            float[] solePoint = new float[18];
            Arrays.fill(soleNode, -1);
            for (int side = 0; side < 2; side++) {
                int f = role[(side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT).ordinal()];
                int o = role[(side == 0 ? Human.RIGHT_TOE : Human.LEFT_TOE).ordinal()];
                if (f < 0) continue;
                float ankleZ = worldN[f * 12 + 11], ankleY = worldN[f * 12 + 7];
                int heel = -1, ball = -1, tip = -1;
                for (int v = 0; v < vertices(); v++) {
                    if (!movedBy(v, f, par, 0.5f)) continue;
                    float y = canonPlaced[v * 3 + 1], z = canonPlaced[v * 3 + 2];
                    if (z <= ankleZ) {
                        if (heel < 0 || y < canonPlaced[heel * 3 + 1] - 1e-3f || (Math.abs(y - canonPlaced[heel * 3 + 1]) <= 1e-3f && z < canonPlaced[heel * 3 + 2])) heel = v;
                    } else if (ball < 0 || y < canonPlaced[ball * 3 + 1]) ball = v;
                    if (y < ankleY * 0.5f && (tip < 0 || z > canonPlaced[tip * 3 + 2])) tip = v;
                }
                int[] picks = {heel, ball, tip};
                for (int k = 0; k < 3; k++) {
                    int v = picks[k];
                    if (v < 0) continue;
                    int bone = o >= 0 && movedBy(v, o, par, 0.5f) ? o : f;
                    float[] inv = new float[12];
                    SkinMath.invertM(worldN, bone * 12, inv, 0);
                    SkinMath.point(inv, 0, canonPlaced[v * 3], canonPlaced[v * 3 + 1], canonPlaced[v * 3 + 2], q, 0);
                    soleNode[side * 3 + k] = bone;
                    System.arraycopy(q, 0, solePoint, (side * 3 + k) * 3, 3);
                }
            }
            leg = legs > 0 ? leg / legs : hipY;
            arm = arms > 0 ? arm / arms : target * 0.4f;
            float[] headTop = {0, target * 0.1f, 0};
            int head = role[Human.HEAD.ordinal()];
            if (head >= 0) {
                float top = Float.NEGATIVE_INFINITY;
                float[] at = null;
                for (int v = 0; v < vertices(); v++) {
                    if (!movedBy(v, head, par, 0.4f)) continue;
                    if (canonPlaced[v * 3 + 1] > top) {
                        top = canonPlaced[v * 3 + 1];
                        at = new float[]{worldN[head * 12 + 3], top, worldN[head * 12 + 11]};
                    }
                }
                if (at != null) {
                    float[] inv = new float[12];
                    SkinMath.invertM(worldN, head * 12, inv, 0);
                    SkinMath.point(inv, 0, at[0], at[1], at[2], headTop, 0);
                }
            }

            // Who drives what: the spine and neck chains share their bends.
            int[] drive = new int[n * 2];
            float[] share = new float[n * 2];
            Arrays.fill(drive, -1);
            drives(role, par, drive, share);

            // Skinning slots: the nodes vertices are weighted to.
            int[] slotOf = new int[n];
            Arrays.fill(slotOf, -1);
            List<Integer> slots = new ArrayList<>();
            short[] joint = new short[vertices() * 4];
            for (int i = 0; i < node.size; i++) {
                int nd = node.data[i];
                if (slotOf[nd] < 0) {
                    slotOf[nd] = slots.size();
                    slots.add(nd);
                }
                joint[i] = (short) slotOf[nd];
            }
            int[] slotArray = new int[slots.size()];
            for (int i = 0; i < slotArray.length; i++) slotArray[i] = slots.get(i);

            Rig rig = new Rig(names(), par, restT, restR, restS, bindInverse, slotArray, role, canonLocal, canonWorld, drive, share, rootQ, root,
                    target, hipY, leg, arm, footRest, toeRest, headTop, palm, soleNode, solePoint, rigged || role[Human.HEAD.ordinal()] >= 0);

            SkinFormat.Baked b = new SkinFormat.Baked();
            b.rig = rig;
            b.pos = vp;
            b.nrm = vn;
            b.uv = uv.toArray();
            b.joint = joint;
            b.weight = weight.toArray();
            b.rigged = rigged;
            materials(b, role, par, options, decoder);
            b.quads = levels(b);
            b.clips = rigged ? clips(role) : new NodeClip[0];
            return b;
        }

        /** The world matrix of node i at bind, before the character placement. */
        private float[] bindRoot(int i) {
            return bind.get(i);
        }

        private float[] facing(int[] role) {
            float[] q = new float[4];
            SkinMath.identityQ(q, 0);
            int r = role[Human.RIGHT_UPPER_ARM.ordinal()], l = role[Human.LEFT_UPPER_ARM.ordinal()];
            if (r < 0 || l < 0) {
                r = role[Human.RIGHT_THIGH.ordinal()];
                l = role[Human.LEFT_THIGH.ordinal()];
            }
            if (r < 0 || l < 0) return q;
            float rx = bind.get(r)[3] - bind.get(l)[3], rz = bind.get(r)[11] - bind.get(l)[11];
            // forward = up x right = (rz, 0, -rx)
            float fx = rz, fz = -rx;
            if (fx * fx + fz * fz < 1e-12f) return q;
            float yaw = (float) Math.atan2(fx, fz);
            SkinMath.axisAngleQ(0, 1, 0, -yaw, q, 0);
            return q;
        }

        /**
         * Straightens each arm and leg at rest: the upper arm and forearm hang down (turned out a
         * little), the thigh and shin point down, each by the shortest turn about its own joint.
         * Fills the canonical local and world rotations and world matrices.
         */
        private void canonical(int[] par, int[] order, int[] role, float[] restT, float[] restS, float[] rootRot, float[] rootQ,
                               float[] canonLocal, float[] canonWorld, float[] worldC) {
            int n = par.length;
            HashMap<Integer, float[]> targets = new HashMap<>();
            HashMap<Integer, Integer> next = new HashMap<>();
            float out = (float) Math.toRadians(ARM_OUT);
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? -1 : 1;
                float[] down = {sx * (float) Math.sin(out), -(float) Math.cos(out), 0};
                Human upper = side == 0 ? Human.RIGHT_UPPER_ARM : Human.LEFT_UPPER_ARM, fore = side == 0 ? Human.RIGHT_FOREARM : Human.LEFT_FOREARM;
                Human hand = side == 0 ? Human.RIGHT_HAND : Human.LEFT_HAND;
                Human thigh = side == 0 ? Human.RIGHT_THIGH : Human.LEFT_THIGH, shin = side == 0 ? Human.RIGHT_SHIN : Human.LEFT_SHIN;
                Human foot = side == 0 ? Human.RIGHT_FOOT : Human.LEFT_FOOT;
                link(role, upper, fore, down, targets, next);
                link(role, fore, hand, down, targets, next);
                link(role, thigh, shin, new float[]{0, -1, 0}, targets, next);
                link(role, shin, foot, new float[]{0, -1, 0}, targets, next);
            }
            float[] m = new float[12], q = new float[8], c = new float[3];
            for (int i : order) {
                int p = par[i];
                SkinMath.composeM(restT, i * 3, canonLocal, i * 4, restS, i * 3, m, 0);
                if (p < 0) SkinMath.mulM(rootRot, 0, m, 0, worldC, i * 12);
                else SkinMath.mulM(worldC, p * 12, m, 0, worldC, i * 12);
                if (p < 0) SkinMath.mulQ(rootQ, 0, canonLocal, i * 4, canonWorld, i * 4);
                else SkinMath.mulQ(canonWorld, p * 4, canonLocal, i * 4, canonWorld, i * 4);
                float[] target = targets.get(i);
                if (target == null) continue;
                int child = next.get(i);
                if (!childPosition(i, child, par, restT, canonLocal, restS, worldC, c)) continue;
                float dx = c[0] - worldC[i * 12 + 3], dy = c[1] - worldC[i * 12 + 7], dz = c[2] - worldC[i * 12 + 11];
                SkinMath.arcQ(dx, dy, dz, target[0], target[1], target[2], q, 0);
                if (Math.abs(q[3]) > Math.cos(Math.toRadians(1.5) / 2)) continue;
                SkinMath.mulQ(q, 0, canonWorld, i * 4, q, 4);
                if (p < 0) SkinMath.mulConjQ(rootQ, 0, q, 4, canonLocal, i * 4);
                else SkinMath.mulConjQ(canonWorld, p * 4, q, 4, canonLocal, i * 4);
                System.arraycopy(q, 4, canonWorld, i * 4, 4);
                SkinMath.composeM(restT, i * 3, canonLocal, i * 4, restS, i * 3, m, 0);
                if (p < 0) SkinMath.mulM(rootRot, 0, m, 0, worldC, i * 12);
                else SkinMath.mulM(worldC, p * 12, m, 0, worldC, i * 12);
            }
        }

        private static void link(int[] role, Human bone, Human child, float[] target, Map<Integer, float[]> targets, Map<Integer, Integer> next) {
            int b = role[bone.ordinal()], c = role[child.ordinal()];
            if (b < 0 || c < 0) return;
            targets.put(b, target);
            next.put(b, c);
        }

        /** Where a descendant joint sits under node i's current world matrix, through any nodes between. */
        private static boolean childPosition(int i, int child, int[] par, float[] restT, float[] rot, float[] restS, float[] worldC, float[] out) {
            List<Integer> path = new ArrayList<>();
            for (int q = child; q >= 0 && q != i; q = par[q]) path.add(0, q);
            if (path.isEmpty() || par[path.get(0)] != i) return false;
            float[] m = worldC.clone(), l = new float[12], tmp = new float[12];
            float[] cur = new float[12];
            System.arraycopy(worldC, i * 12, cur, 0, 12);
            for (int q : path) {
                SkinMath.composeM(restT, q * 3, rot, q * 4, restS, q * 3, l, 0);
                SkinMath.mulM(cur, 0, l, 0, tmp, 0);
                System.arraycopy(tmp, 0, cur, 0, 12);
            }
            out[0] = cur[3];
            out[1] = cur[7];
            out[2] = cur[11];
            return true;
        }

        /** Skinning matrices for a world pose (world x bind^-1), per node. */
        private float[] skinMatrices(float[] worldPose, float[] unused) {
            int n = nodes();
            float[] out = new float[n * 12], inv = new float[12];
            for (int i = 0; i < n; i++) {
                SkinMath.invertM(bind.get(i), 0, inv, 0);
                SkinMath.mulM(worldPose, i * 12, inv, 0, out, i * 12);
            }
            return out;
        }

        private float[] placeVertices(float[] skin) {
            int verts = vertices();
            float[] out = new float[verts * 3];
            float[] q = new float[3];
            for (int v = 0; v < verts; v++) {
                float x = 0, y = 0, z = 0;
                for (int k = 0; k < 4; k++) {
                    float wv = weight.data[v * 4 + k];
                    if (wv <= 0) continue;
                    SkinMath.point(skin, node.data[v * 4 + k] * 12, pos.data[v * 3], pos.data[v * 3 + 1], pos.data[v * 3 + 2], q, 0);
                    x += q[0] * wv;
                    y += q[1] * wv;
                    z += q[2] * wv;
                }
                out[v * 3] = x;
                out[v * 3 + 1] = y;
                out[v * 3 + 2] = z;
            }
            return out;
        }

        private boolean movedBy(int v, int bone, int[] par, float min) {
            float sum = 0;
            for (int k = 0; k < 4; k++) if (under(par, node.data[v * 4 + k], bone)) sum += weight.data[v * 4 + k];
            return sum >= min;
        }

        private float[] centroid(float[] placed, int bone, int[] par, float min) {
            double x = 0, y = 0, z = 0;
            int count = 0;
            for (int v = 0; v < vertices(); v++) {
                if (!movedBy(v, bone, par, min)) continue;
                x += placed[v * 3];
                y += placed[v * 3 + 1];
                z += placed[v * 3 + 2];
                count++;
            }
            return count == 0 ? null : new float[]{(float) (x / count), (float) (y / count), (float) (z / count)};
        }

        /** Spine: hips to chest split into the clip's spine and chest bends; the neck chain shares the neck's. */
        private void drives(int[] role, int[] par, int[] drive, float[] share) {
            for (Human h : Human.ALL) {
                int r = role[h.ordinal()];
                if (r < 0 || h == Human.SPINE || h == Human.CHEST || h == Human.NECK) continue;
                drive[r * 2] = h.ordinal();
                share[r * 2] = 1;
            }
            int spine = role[Human.SPINE.ordinal()], chest = role[Human.CHEST.ordinal()], neck = role[Human.NECK.ordinal()], head = role[Human.HEAD.ordinal()];
            if (spine >= 0 && chest >= 0 && under(par, chest, spine)) {
                List<Integer> chain = new ArrayList<>();
                for (int q = chest; q >= 0; q = par[q]) {
                    chain.add(0, q);
                    if (q == spine) break;
                }
                if (chain.size() == 1) {
                    drive[spine * 2] = Human.SPINE.ordinal();
                    share[spine * 2] = 1;
                    drive[spine * 2 + 1] = Human.CHEST.ordinal();
                    share[spine * 2 + 1] = 1;
                } else {
                    int lower = (chain.size() + 1) / 2, upper = chain.size() - lower;
                    for (int i = 0; i < chain.size(); i++) {
                        int nd = chain.get(i);
                        boolean low = i < lower;
                        drive[nd * 2] = (low ? Human.SPINE : Human.CHEST).ordinal();
                        share[nd * 2] = 1f / (low ? lower : upper);
                    }
                }
            } else if (spine >= 0) {
                drive[spine * 2] = Human.SPINE.ordinal();
                share[spine * 2] = 1;
                drive[spine * 2 + 1] = Human.CHEST.ordinal();
                share[spine * 2 + 1] = 1;
            } else if (chest >= 0) {
                drive[chest * 2] = Human.SPINE.ordinal();
                share[chest * 2] = 1;
                drive[chest * 2 + 1] = Human.CHEST.ordinal();
                share[chest * 2 + 1] = 1;
            }
            if (head >= 0 && chest >= 0) {
                List<Integer> chain = new ArrayList<>();
                for (int q = par[head]; q >= 0 && q != chest; q = par[q]) chain.add(0, q);
                if (!chain.isEmpty() && under(par, head, chest)) {
                    for (int nd : chain) {
                        drive[nd * 2] = Human.NECK.ordinal();
                        share[nd * 2] = 1f / chain.size();
                    }
                    role[Human.NECK.ordinal()] = chain.get(0);
                }
            } else if (neck >= 0) {
                drive[neck * 2] = Human.NECK.ordinal();
                share[neck * 2] = 1;
            }
        }

        // ---- materials and images

        private void materials(SkinFormat.Baked b, int[] role, int[] par, CharacterBake.Options options, ImageDecoder decoder) throws IOException {
            JsonArray mats = GltfFile.array(file.json, "materials");
            // A prop (Tung's bat): a mesh named so, mostly moved by one hand.
            int[] propSide = new int[hadNormals.size()];
            Arrays.fill(propSide, -1);
            for (int prim = 0; prim < propSide.length; prim++) {
                String label = meshName.get(prim).toLowerCase(Locale.ROOT);
                if (!(label.contains("prop") || label.contains("bat") || label.contains("weapon") || label.contains("sword") || label.contains("club"))) continue;
                propSide[prim] = handOf(prim, role, par);
            }
            LinkedHashMap<Long, Integer> key = new LinkedHashMap<>();
            int verts = vertices();
            int[] matOf = new int[verts];
            for (int v = 0; v < verts; v++) {
                int m = material.data[v], prop = propSide[primitive.data[v]];
                long k = (long) m * 4 + (prop + 1);
                Integer id = key.get(k);
                if (id == null) {
                    id = key.size();
                    key.put(k, id);
                }
                matOf[v] = id;
            }
            HashMap<Integer, Integer> imageOf = new HashMap<>();
            List<byte[]> images = new ArrayList<>();
            List<int[]> sizes = new ArrayList<>();
            List<Boolean> raw = new ArrayList<>();
            List<Boolean> clear = new ArrayList<>();
            SkinMaterial[] out = new SkinMaterial[key.size()];
            int[] factor = new int[key.size()];
            for (Map.Entry<Long, Integer> e : key.entrySet()) {
                int m = (int) (e.getKey() / 4), prop = (int) (e.getKey() % 4) - 1;
                JsonObject mo = m >= 0 && m < mats.size() ? mats.get(m).getAsJsonObject() : new JsonObject();
                String alpha = mo.has("alphaMode") ? mo.get("alphaMode").getAsString() : "OPAQUE";
                float cutoff = mo.has("alphaCutoff") ? mo.get("alphaCutoff").getAsFloat() : 0.5f;
                int mode = alpha.equals("BLEND") ? SkinMaterial.BLEND : alpha.equals("MASK") ? SkinMaterial.CUTOUT : SkinMaterial.OPAQUE;
                int source = imageSource(m);
                int image = -1;
                if (source >= 0) {
                    Integer known = imageOf.get(source);
                    if (known == null) {
                        byte[] bytes = file.baseColorImage(m);
                        if (bytes != null) {
                            ImageDecoder.Picture picture = decoder.decode(bytes);
                            // PNG and JPEG are kept as the file has them; anything larger than the cap is halved and kept raw.
                            boolean encoded = encoded(bytes);
                            int w = picture.width(), h = picture.height();
                            int[] argb = picture.argb();
                            int cap = options.maxTexture > 0 ? options.maxTexture : MAX_TEXTURE;
                            while (Math.max(w, h) > cap) {
                                argb = half(argb, w, h);
                                w = Math.max(1, w / 2);
                                h = Math.max(1, h / 2);
                                encoded = false;
                            }
                            int transparent = 0;
                            for (int px : argb) if ((px >>> 24) < 128) transparent++;
                            known = images.size();
                            images.add(encoded ? bytes : deflate(argb));
                            sizes.add(new int[]{w, h});
                            raw.add(!encoded);
                            clear.add(transparent > argb.length / 500);
                        }
                        if (known != null) imageOf.put(source, known);
                    }
                    if (known != null) image = known;
                }
                if (mode == SkinMaterial.OPAQUE && image >= 0 && clear.get(image)) mode = SkinMaterial.CUTOUT;
                boolean doubleSided = mo.has("doubleSided") && mo.get("doubleSided").getAsBoolean();
                out[e.getValue()] = new SkinMaterial(file.materialName(m), image, mode, cutoff, doubleSided, prop);
                float[] f = file.baseColorFactor(m);
                factor[e.getValue()] = (Math.round(Math.max(0, Math.min(1, f[3])) * 255) << 24) | srgb(f[0]) << 16 | srgb(f[1]) << 8 | srgb(f[2]);
            }
            int[] colors = new int[verts];
            for (int v = 0; v < verts; v++) colors[v] = multiply(color.data[v], factor[matOf[v]]);
            b.color = colors;
            b.materials = out;
            b.images = images.toArray(new byte[0][]);
            b.imageWidth = new int[sizes.size()];
            b.imageHeight = new int[sizes.size()];
            b.imageRaw = new boolean[raw.size()];
            for (int i = 0; i < sizes.size(); i++) {
                b.imageWidth[i] = sizes.get(i)[0];
                b.imageHeight[i] = sizes.get(i)[1];
                b.imageRaw[i] = raw.get(i);
            }
            this.matOf = matOf;
        }

        int[] matOf;

        private int handOf(int prim, int[] role, int[] par) {
            float[] sum = new float[2];
            for (int v = 0; v < vertices(); v++) {
                if (primitive.data[v] != prim) continue;
                for (int side = 0; side < 2; side++) {
                    int hand = role[(side == 0 ? Human.RIGHT_HAND : Human.LEFT_HAND).ordinal()];
                    int fore = role[(side == 0 ? Human.RIGHT_FOREARM : Human.LEFT_FOREARM).ordinal()];
                    int bone = hand >= 0 ? hand : fore;
                    if (bone >= 0 && movedBy(v, bone, par, 0.5f)) sum[side]++;
                }
            }
            if (sum[0] == 0 && sum[1] == 0) return -1;
            return sum[0] >= sum[1] ? 0 : 1;
        }

        private int imageSource(int m) {
            return m < 0 ? -1 : file.baseColorSource(m);
        }

        // ---- levels of detail

        private int[][][] levels(SkinFormat.Baked b) {
            int mats = b.materials.length;
            int tcount = triangles();
            int[] all = tris.toArray();
            int[][] level = new int[BUDGETS.length][];
            List<Integer> source = null;
            int[] targets = new int[BUDGETS.length];
            boolean reduce = false;
            for (int l = 0; l < BUDGETS.length; l++) {
                targets[l] = Math.min(tcount, BUDGETS[l]);
                if (targets[l] < tcount) reduce = true;
            }
            int[][] reduced = null;
            if (reduce) {
                source = new ArrayList<>(tcount);
                for (int t = 0; t < tcount; t++) source.add(t);
                PosedMesh mesh = PosedMesh.of(b.pos, b.uv, matOf, all);
                reduced = new MeshReducer(mesh, source, true).reduce(targets);
            }
            int[][][] out = new int[BUDGETS.length][][];
            for (int l = 0; l < BUDGETS.length; l++) {
                int[] corners = targets[l] >= tcount || reduced == null ? all : reduced[l];
                out[l] = quads(corners, mats, b.pos);
            }
            return out;
        }

        /**
         * Triangles to quads per material: two triangles sharing a diagonal become one quad, the
         * rest repeat their last corner.
         */
        private int[][] quads(int[] corners, int mats, float[] p) {
            int count = corners.length / 3;
            HashMap<Long, Integer> edge = new HashMap<>(count * 4);
            int[] c = new int[corners.length];
            for (int i = 0; i < corners.length; i++) c[i] = corners[i] < 0 ? ~corners[i] : corners[i];
            for (int t = 0; t < count; t++) {
                for (int e = 0; e < 3; e++) edge.put(key(c[t * 3 + e], c[t * 3 + (e + 1) % 3]), t);
            }
            boolean[] used = new boolean[count];
            IntList[] per = new IntList[mats];
            for (int m = 0; m < mats; m++) per[m] = new IntList();
            for (int t = 0; t < count; t++) {
                if (used[t]) continue;
                int a = c[t * 3], bb = c[t * 3 + 1], cc = c[t * 3 + 2];
                if (a == bb || bb == cc || a == cc) {
                    used[t] = true;
                    continue;
                }
                int mat = matOf[a];
                used[t] = true;
                boolean paired = false;
                int[] tri = {a, bb, cc};
                for (int r = 0; r < 3 && !paired; r++) {
                    int x = tri[r], y = tri[(r + 1) % 3], z = tri[(r + 2) % 3];
                    Integer other = edge.get(key(x, z));
                    if (other == null || used[other]) continue;
                    int ox = c[other * 3], oy = c[other * 3 + 1], oz = c[other * 3 + 2];
                    int w = ox != x && ox != z ? ox : oy != x && oy != z ? oy : oz;
                    if (matOf[w] != mat || w == y) continue;
                    used[other] = true;
                    per[mat].add(x, y, z, w);
                    paired = true;
                }
                if (!paired) per[mat].add(a, bb, cc, cc);
            }
            int[][] out = new int[mats][];
            for (int m = 0; m < mats; m++) out[m] = per[m].toArray();
            return out;
        }

        private static long key(int a, int b) {
            return ((long) a << 32) | (b & 0xFFFFFFFFL);
        }

        // ---- clips

        private NodeClip[] clips(int[] role) throws IOException {
            List<NodeClip> out = new ArrayList<>();
            JsonArray anims = GltfFile.array(file.json, "animations");
            for (int a = 0; a < anims.size(); a++) {
                JsonObject anim = anims.get(a).getAsJsonObject();
                String clipName = anim.has("name") ? anim.get("name").getAsString() : "Animation " + a;
                JsonArray samplers = GltfFile.array(anim, "samplers");
                List<int[]> meta = new ArrayList<>();
                List<float[]> times = new ArrayList<>(), values = new List0();
                float duration = 0;
                for (JsonElement ce : GltfFile.array(anim, "channels")) {
                    JsonObject ch = ce.getAsJsonObject();
                    JsonObject target = GltfFile.object(ch, "target");
                    int nd = GltfFile.integer(target, "node", -1);
                    String path = target.has("path") ? target.get("path").getAsString() : "";
                    int p = path.equals("translation") ? NodeClip.TRANSLATION : path.equals("rotation") ? NodeClip.ROTATION : path.equals("scale") ? NodeClip.SCALE : -1;
                    int si = GltfFile.integer(ch, "sampler", -1);
                    if (nd < 0 || nd >= fileNodes || p < 0 || si < 0 || si >= samplers.size()) continue;
                    JsonObject sm = samplers.get(si).getAsJsonObject();
                    String in = sm.has("interpolation") ? sm.get("interpolation").getAsString() : "LINEAR";
                    int interp = in.equals("STEP") ? NodeClip.STEP : in.equals("CUBICSPLINE") ? NodeClip.CUBIC : NodeClip.LINEAR;
                    float[] t = file.floats(sm.get("input").getAsInt());
                    float[] v = file.floats(sm.get("output").getAsInt());
                    int width = p == NodeClip.ROTATION ? 4 : 3;
                    if (v.length < t.length * width * (interp == NodeClip.CUBIC ? 3 : 1)) continue;
                    if (t.length > 0) duration = Math.max(duration, t[t.length - 1]);
                    meta.add(new int[]{nd, p, interp});
                    times.add(t);
                    values.add(v);
                }
                if (meta.isEmpty() || duration <= 0) continue;
                int[] node = new int[meta.size()], path = new int[meta.size()], interp = new int[meta.size()];
                for (int i = 0; i < meta.size(); i++) {
                    node[i] = meta.get(i)[0];
                    path[i] = meta.get(i)[1];
                    interp[i] = meta.get(i)[2];
                }
                // Clips start at their first key.
                float start = Float.POSITIVE_INFINITY;
                for (float[] t : times) if (t.length > 0) start = Math.min(start, t[0]);
                if (start > 0 && Float.isFinite(start)) {
                    for (float[] t : times) for (int i = 0; i < t.length; i++) t[i] -= start;
                    duration -= start;
                }
                NodeClip clip = new NodeClip(clipName, duration, node, path, interp, times.toArray(new float[0][]), values.toArray(new float[0][]));
                if (clip.state >= 0 && dev.maro.render.character.skin.Locomotion.State.values()[clip.state].strides()) inPlace(clip, role);
                out.add(clip);
            }
            return out.toArray(new NodeClip[0]);
        }

        /** A walk or run in place: the hips' (or highest moving node's) drift through the clip is taken out. */
        private void inPlace(NodeClip clip, int[] role) {
            int[] par = parentArray();
            int hips = role[Human.HIPS.ordinal()];
            int best = -1, bestDepth = Integer.MAX_VALUE;
            for (int c = 0; c < clip.node.length; c++) {
                if (clip.path[c] != NodeClip.TRANSLATION) continue;
                int nd = clip.node[c];
                if (hips >= 0 && !under(par, hips, nd) && nd != hips) continue;
                int d = 0;
                for (int q = par[nd]; q >= 0; q = par[q]) d++;
                if (d < bestDepth) {
                    best = c;
                    bestDepth = d;
                }
            }
            if (best < 0) return;
            float[] t = clip.times[best], v = clip.values[best];
            if (t.length < 2) return;
            boolean cubic = clip.interpolation[best] == NodeClip.CUBIC;
            int stride = cubic ? 9 : 3, mid = cubic ? 3 : 0;
            float span = t[t.length - 1] - t[0];
            if (span <= 0) return;
            float[] drift = new float[3];
            for (int k = 0; k < 3; k++) drift[k] = v[(t.length - 1) * stride + mid + k] - v[mid + k];
            // Only along the ground: keep the up-down bob (the clip's vertical drift is near zero anyway).
            for (int i = 0; i < t.length; i++) {
                float f = (t[i] - t[0]) / span;
                for (int k = 0; k < 3; k++) v[i * stride + mid + k] -= drift[k] * f;
            }
        }
    }

    // ---- shared helpers ---------------------------------------------------------------------

    static boolean under(int[] par, int node, int ancestor) {
        for (int q = node, steps = 0; q >= 0 && steps <= par.length; q = par[q], steps++) if (q == ancestor) return true;
        return false;
    }

    static int[] orderOf(int[] par) {
        int n = par.length;
        int[] out = new int[n];
        boolean[] done = new boolean[n];
        int count = 0;
        for (int pass = 0; pass <= n && count < n; pass++) {
            for (int i = 0; i < n; i++) {
                if (done[i]) continue;
                int p = par[i];
                if (p < 0 || p >= n || done[p]) {
                    done[i] = true;
                    out[count++] = i;
                }
            }
        }
        for (int i = 0; i < n && count < n; i++) if (!done[i]) out[count++] = i;
        return out;
    }

    private static float distance(float[] world, int a, int b) {
        float dx = world[a * 12 + 3] - world[b * 12 + 3], dy = world[a * 12 + 7] - world[b * 12 + 7], dz = world[a * 12 + 11] - world[b * 12 + 11];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float[] identity() {
        float[] m = new float[12];
        SkinMath.identityM(m, 0);
        return m;
    }

    private static void sortInfluences(int[] j, float[] w) {
        for (int i = 0; i < 4; i++) {
            for (int k = i + 1; k < 4; k++) {
                if (w[k] > w[i]) {
                    float tw = w[i];
                    w[i] = w[k];
                    w[k] = tw;
                    int tj = j[i];
                    j[i] = j[k];
                    j[k] = tj;
                }
            }
        }
        // The same joint twice: merge.
        for (int i = 0; i < 4; i++) for (int k = i + 1; k < 4; k++) if (w[k] > 0 && j[k] == j[i]) {
            w[i] += w[k];
            w[k] = 0;
        }
    }

    static int srgb(float linear) {
        return Math.round((float) Math.pow(Math.max(0.0f, Math.min(1.0f, linear)), 1 / 2.2) * 255.0f);
    }

    private static int multiply(int a, int b) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = (a >>> shift) & 0xFF, y = (b >>> shift) & 0xFF;
            out |= ((x * y + 127) / 255) << shift;
        }
        return out;
    }

    /** PNG or JPEG bytes, which the game decodes as they are. */
    public static boolean encoded(byte[] b) {
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        return png || jpeg;
    }

    private static int[] half(int[] argb, int w, int h) {
        int nw = Math.max(1, w / 2), nh = Math.max(1, h / 2);
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) for (int x = 0; x < nw; x++) {
            int[] acc = new int[4];
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
                int px = argb[Math.min(h - 1, y * 2 + dy) * w + Math.min(w - 1, x * 2 + dx)];
                for (int c = 0; c < 4; c++) acc[c] += (px >>> (c * 8)) & 0xFF;
            }
            int p = 0;
            for (int c = 0; c < 4; c++) p |= ((acc[c] + 2) / 4) << (c * 8);
            out[y * nw + x] = p;
        }
        return out;
    }

    private static byte[] deflate(int[] argb) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, new Deflater(6))) {
            byte[] row = new byte[argb.length * 4];
            for (int i = 0; i < argb.length; i++) {
                int p = argb[i];
                row[i * 4] = (byte) (p >> 16);
                row[i * 4 + 1] = (byte) (p >> 8);
                row[i * 4 + 2] = (byte) p;
                row[i * 4 + 3] = (byte) (p >>> 24);
            }
            out.write(row);
        }
        return bytes.toByteArray();
    }

    /** A list that is also fine to hand to the clip (just ArrayList, named for the generic). */
    private static final class List0 extends ArrayList<float[]> {
    }

    static final class FloatList {
        float[] data = new float[1024];
        int size;

        void add(float... v) {
            if (size + v.length > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
            System.arraycopy(v, 0, data, size, v.length);
            size += v.length;
        }

        float[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    static final class IntList {
        int[] data = new int[1024];
        int size;

        void add(int... v) {
            if (size + v.length > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + v.length));
            System.arraycopy(v, 0, data, size, v.length);
            size += v.length;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
