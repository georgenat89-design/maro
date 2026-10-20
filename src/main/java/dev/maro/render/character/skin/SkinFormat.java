package dev.maro.render.character.skin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * The skinned character cache (.anc version 5): the rig, every material, the images as the GLB
 * stored them, the GLB's clips, one shared vertex set and each level of detail's quads per
 * material. Levels are compacted to their own vertices as they load.
 */
public final class SkinFormat {
    public static final int MAGIC = 1095648072;
    public static final int VERSION = 5;

    private SkinFormat() {
    }

    /** Everything a bake produces before levels are split: shared vertices and per-level, per-material quads. */
    public static final class Baked {
        public Rig rig;
        public float[] pos, nrm, uv;
        public int[] color;
        public short[] joint;
        public float[] weight;
        /** quads[level][material]: four vertex indices per quad into the shared vertices. */
        public int[][][] quads;
        public SkinMaterial[] materials;
        public byte[][] images;
        public int[] imageWidth, imageHeight;
        public boolean[] imageRaw;
        public NodeClip[] clips;
        public boolean rigged;
    }

    public static void write(DataOutputStream out, Baked b) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeBoolean(b.rigged);
        Rig r = b.rig;
        out.writeInt(r.nodes);
        for (int n = 0; n < r.nodes; n++) {
            out.writeUTF(clip(r.names[n]));
            out.writeInt(r.parent[n]);
            floats(out, r.restT, n * 3, 3);
            floats(out, r.restR, n * 4, 4);
            floats(out, r.restS, n * 3, 3);
            floats(out, r.bindInverse, n * 12, 12);
            floats(out, r.canonLocal, n * 4, 4);
            floats(out, r.canonWorld, n * 4, 4);
            out.writeByte(r.drive[n * 2]);
            out.writeByte(r.drive[n * 2 + 1]);
            out.writeFloat(r.share[n * 2]);
            out.writeFloat(r.share[n * 2 + 1]);
        }
        out.writeInt(r.slots.length);
        for (int s : r.slots) out.writeInt(s);
        out.writeInt(r.role.length);
        for (int x : r.role) out.writeInt(x);
        floats(out, r.rootRotation, 0, 4);
        floats(out, r.root, 0, 12);
        out.writeFloat(r.height);
        out.writeFloat(r.hipHeight);
        out.writeFloat(r.legLength);
        out.writeFloat(r.armLength);
        floats(out, r.footRest, 0, 2);
        floats(out, r.toeRest, 0, 2);
        floats(out, r.headTop, 0, 3);
        floats(out, r.palm, 0, 6);
        for (int i = 0; i < 6; i++) out.writeInt(r.soleNode[i]);
        floats(out, r.solePoint, 0, 18);
        out.writeBoolean(r.humanoid);

        out.writeInt(b.materials.length);
        for (SkinMaterial m : b.materials) {
            out.writeUTF(clip(m.name));
            out.writeInt(m.image);
            out.writeByte(m.mode);
            out.writeFloat(m.cutoff);
            out.writeBoolean(m.doubleSided);
            out.writeByte(m.prop);
        }
        out.writeInt(b.images.length);
        for (int i = 0; i < b.images.length; i++) {
            out.writeBoolean(b.imageRaw[i]);
            out.writeInt(b.imageWidth[i]);
            out.writeInt(b.imageHeight[i]);
            out.writeInt(b.images[i].length);
            out.write(b.images[i]);
        }
        out.writeInt(b.clips.length);
        for (NodeClip c : b.clips) {
            out.writeUTF(clip(c.name));
            out.writeFloat(c.duration);
            out.writeInt(c.node.length);
            for (int k = 0; k < c.node.length; k++) {
                out.writeInt(c.node[k]);
                out.writeByte(c.path[k]);
                out.writeByte(c.interpolation[k]);
                out.writeInt(c.times[k].length);
                floats(out, c.times[k], 0, c.times[k].length);
                out.writeInt(c.values[k].length);
                floats(out, c.values[k], 0, c.values[k].length);
            }
        }
        int verts = b.pos.length / 3;
        out.writeInt(verts);
        floats(out, b.pos, 0, b.pos.length);
        for (float v : b.nrm) out.writeByte(Math.round(Math.max(-1, Math.min(1, v)) * 127));
        floats(out, b.uv, 0, b.uv.length);
        for (int c : b.color) out.writeInt(c);
        for (short j : b.joint) out.writeShort(j);
        for (float w : b.weight) out.writeByte(Math.round(Math.max(0, Math.min(1, w)) * 255));
        out.writeInt(b.quads.length);
        for (int[][] level : b.quads) {
            out.writeInt(level.length);
            for (int[] q : level) {
                out.writeInt(q.length);
                boolean wide = verts > 65535;
                for (int i : q) {
                    if (wide) out.writeInt(i);
                    else out.writeChar(i);
                }
            }
        }
        out.flush();
    }

    private static String clip(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static void floats(DataOutputStream out, float[] a, int o, int n) throws IOException {
        for (int i = 0; i < n; i++) out.writeFloat(a[o + i]);
    }

    private static float[] floats(DataInputStream in, int n) throws IOException {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = in.readFloat();
        return a;
    }

    private static void floats(DataInputStream in, float[] a, int o, int n) throws IOException {
        for (int i = 0; i < n; i++) a[o + i] = in.readFloat();
    }

    /** Reads a version 5 file whose magic and version have already been read. */
    public static SkinnedCharacter read(DataInputStream in) throws IOException {
        boolean rigged = in.readBoolean();
        int n = in.readInt();
        if (n <= 0 || n > 4096) throw new IOException("bad skeleton");
        String[] names = new String[n];
        int[] parent = new int[n], drive = new int[n * 2];
        float[] restT = new float[n * 3], restR = new float[n * 4], restS = new float[n * 3], bind = new float[n * 12];
        float[] canonLocal = new float[n * 4], canonWorld = new float[n * 4], share = new float[n * 2];
        for (int i = 0; i < n; i++) {
            names[i] = in.readUTF();
            parent[i] = in.readInt();
            floats(in, restT, i * 3, 3);
            floats(in, restR, i * 4, 4);
            floats(in, restS, i * 3, 3);
            floats(in, bind, i * 12, 12);
            floats(in, canonLocal, i * 4, 4);
            floats(in, canonWorld, i * 4, 4);
            drive[i * 2] = in.readByte();
            drive[i * 2 + 1] = in.readByte();
            share[i * 2] = in.readFloat();
            share[i * 2 + 1] = in.readFloat();
        }
        int[] slots = new int[in.readInt()];
        for (int i = 0; i < slots.length; i++) slots[i] = in.readInt();
        int[] role = new int[in.readInt()];
        for (int i = 0; i < role.length; i++) role[i] = in.readInt();
        if (role.length != Human.COUNT) role = Arrays.copyOf(role, Human.COUNT);
        float[] rootRotation = floats(in, 4), root = floats(in, 12);
        float height = in.readFloat(), hip = in.readFloat(), leg = in.readFloat(), arm = in.readFloat();
        float[] footRest = floats(in, 2), toeRest = floats(in, 2), headTop = floats(in, 3), palm = floats(in, 6);
        int[] soleNode = new int[6];
        for (int i = 0; i < 6; i++) {
            soleNode[i] = in.readInt();
            if (soleNode[i] >= n) soleNode[i] = -1;
        }
        float[] solePoint = floats(in, 18);
        boolean humanoid = in.readBoolean();
        Rig rig = new Rig(names, parent, restT, restR, restS, bind, slots, role, canonLocal, canonWorld, drive, share, rootRotation, root,
                height, hip, leg, arm, footRest, toeRest, headTop, palm, soleNode, solePoint, humanoid);

        SkinMaterial[] materials = new SkinMaterial[in.readInt()];
        for (int i = 0; i < materials.length; i++) {
            materials[i] = new SkinMaterial(in.readUTF(), in.readInt(), in.readByte(), in.readFloat(), in.readBoolean(), in.readByte());
        }
        int imageCount = in.readInt();
        byte[][] images = new byte[imageCount][];
        int[] iw = new int[imageCount], ih = new int[imageCount];
        boolean[] raw = new boolean[imageCount];
        for (int i = 0; i < imageCount; i++) {
            raw[i] = in.readBoolean();
            iw[i] = in.readInt();
            ih[i] = in.readInt();
            images[i] = new byte[in.readInt()];
            in.readFully(images[i]);
        }
        NodeClip[] clips = new NodeClip[in.readInt()];
        for (int i = 0; i < clips.length; i++) {
            String name = in.readUTF();
            float duration = in.readFloat();
            int channels = in.readInt();
            int[] node = new int[channels], path = new int[channels], interp = new int[channels];
            float[][] times = new float[channels][], values = new float[channels][];
            for (int k = 0; k < channels; k++) {
                node[k] = in.readInt();
                path[k] = in.readByte();
                interp[k] = in.readByte();
                times[k] = floats(in, in.readInt());
                values[k] = floats(in, in.readInt());
            }
            clips[i] = new NodeClip(name, duration, node, path, interp, times, values);
        }
        int verts = in.readInt();
        float[] pos = floats(in, verts * 3);
        float[] nrm = new float[verts * 3];
        for (int i = 0; i < nrm.length; i++) nrm[i] = in.readByte() / 127f;
        for (int v = 0; v < verts; v++) {
            float x = nrm[v * 3], y = nrm[v * 3 + 1], z = nrm[v * 3 + 2], l = (float) Math.sqrt(x * x + y * y + z * z);
            if (l > 1e-6f) {
                nrm[v * 3] = x / l;
                nrm[v * 3 + 1] = y / l;
                nrm[v * 3 + 2] = z / l;
            }
        }
        float[] uv = floats(in, verts * 2);
        int[] color = new int[verts];
        for (int i = 0; i < verts; i++) color[i] = in.readInt();
        short[] joint = new short[verts * 4];
        for (int i = 0; i < joint.length; i++) joint[i] = in.readShort();
        float[] weight = new float[verts * 4];
        for (int i = 0; i < weight.length; i++) weight[i] = in.readUnsignedByte() / 255f;
        for (int v = 0; v < verts; v++) {
            float sum = weight[v * 4] + weight[v * 4 + 1] + weight[v * 4 + 2] + weight[v * 4 + 3];
            if (sum <= 0) {
                weight[v * 4] = 1;
                continue;
            }
            for (int k = 0; k < 4; k++) weight[v * 4 + k] /= sum;
        }
        int levelCount = in.readInt();
        SkinLevel[] levels = new SkinLevel[levelCount];
        for (int l = 0; l < levelCount; l++) {
            int mats = in.readInt();
            int[][] quads = new int[mats][];
            for (int m = 0; m < mats; m++) {
                quads[m] = new int[in.readInt()];
                for (int i = 0; i < quads[m].length; i++) quads[m][i] = verts > 65535 ? in.readInt() : in.readChar();
            }
            levels[l] = compact(pos, nrm, uv, color, joint, weight, quads);
        }
        return new SkinnedCharacter(rig, levels, materials, images, iw, ih, raw, clips, rigged);
    }

    /** One level with only the vertices its quads use, grouped by material. */
    public static SkinLevel compact(float[] pos, float[] nrm, float[] uv, int[] color, short[] joint, float[] weight, int[][] quads) {
        int mats = quads.length;
        int verts = pos.length / 3;
        int[] map = new int[verts];
        int[] vertexStart = new int[mats + 1], quadStart = new int[mats + 1];
        int total = 0, quadTotal = 0;
        for (int m = 0; m < mats; m++) {
            Arrays.fill(map, -1);
            vertexStart[m] = total;
            quadStart[m] = quadTotal / 4;
            for (int i : quads[m]) if (map[i] < 0) map[i] = total++;
            quadTotal += quads[m].length;
        }
        vertexStart[mats] = total;
        quadStart[mats] = quadTotal / 4;
        float[] p = new float[total * 3], nn = new float[total * 3], t = new float[total * 2], w = new float[total * 4];
        int[] c = new int[total], q = new int[quadTotal];
        short[] j = new short[total * 4];
        int qi = 0;
        for (int m = 0; m < mats; m++) {
            Arrays.fill(map, -1);
            int next = vertexStart[m];
            for (int i : quads[m]) {
                if (map[i] < 0) {
                    int v = next++;
                    map[i] = v;
                    System.arraycopy(pos, i * 3, p, v * 3, 3);
                    System.arraycopy(nrm, i * 3, nn, v * 3, 3);
                    System.arraycopy(uv, i * 2, t, v * 2, 2);
                    System.arraycopy(joint, i * 4, j, v * 4, 4);
                    System.arraycopy(weight, i * 4, w, v * 4, 4);
                    c[v] = color[i];
                }
                q[qi++] = map[i];
            }
        }
        return new SkinLevel(p, nn, t, c, j, w, q, vertexStart, quadStart);
    }
}
