package dev.maro.render.character.skin;

import java.util.Arrays;

/**
 * The game's own player, rebuilt as a bendable skinned mesh for emotes: the standard skin's head,
 * body, arms and legs (wide or slim arms) with their outer layers, cut along each limb so elbows,
 * knees and the waist bend smoothly, on a humanoid skeleton the emote clips drive. Character units
 * are the model's pixels (the player is 32 tall); UVs are the 64x64 skin layout.
 */
public final class SkinBody {
    private static final int SEGMENTS = 6;
    private static final String[] NAMES = {"hips", "spine", "chest", "neck", "head",
            "right_upper_arm", "right_forearm", "right_hand", "left_upper_arm", "left_forearm", "left_hand",
            "right_thigh", "right_shin", "right_foot", "left_thigh", "left_shin", "left_foot"};
    private static final int HIPS = 0, SPINE = 1, CHEST = 2, NECK = 3, HEAD = 4, R_UPPER = 5, R_FORE = 6, R_HAND = 7, L_UPPER = 8, L_FORE = 9, L_HAND = 10,
            R_THIGH = 11, R_SHIN = 12, R_FOOT = 13, L_THIGH = 14, L_SHIN = 15, L_FOOT = 16;
    private static final int[] PARENT = {-1, HIPS, SPINE, CHEST, NECK, CHEST, R_UPPER, R_FORE, CHEST, L_UPPER, L_FORE, HIPS, R_THIGH, R_SHIN, HIPS, L_THIGH, L_SHIN};
    private static final Human[] ROLES = {Human.HIPS, Human.SPINE, Human.CHEST, Human.NECK, Human.HEAD,
            Human.RIGHT_UPPER_ARM, Human.RIGHT_FOREARM, Human.RIGHT_HAND, Human.LEFT_UPPER_ARM, Human.LEFT_FOREARM, Human.LEFT_HAND,
            Human.RIGHT_THIGH, Human.RIGHT_SHIN, Human.RIGHT_FOOT, Human.LEFT_THIGH, Human.LEFT_SHIN, Human.LEFT_FOOT};

    private static SkinnedCharacter wide, slim;

    private SkinBody() {
    }

    public static synchronized SkinnedCharacter of(boolean slimArms) {
        if (slimArms) {
            if (slim == null) slim = build(true);
            return slim;
        }
        if (wide == null) wide = build(false);
        return wide;
    }

    static SkinnedCharacter build(boolean slimArms) {
        float arm = slimArms ? 3 : 4;
        // Joint positions (x left, y up, z forward), in model pixels from the feet.
        float[][] at = new float[NAMES.length][];
        at[HIPS] = new float[]{0, 12, 0};
        at[SPINE] = new float[]{0, 15, 0};
        at[CHEST] = new float[]{0, 19, 0};
        at[NECK] = new float[]{0, 23, 0};
        at[HEAD] = new float[]{0, 24, 0};
        float ax = 4 + arm / 2;
        at[R_UPPER] = new float[]{-ax, 22, 0};
        at[R_FORE] = new float[]{-ax, 18, 0};
        at[R_HAND] = new float[]{-ax, 14.5f, 0};
        at[L_UPPER] = new float[]{ax, 22, 0};
        at[L_FORE] = new float[]{ax, 18, 0};
        at[L_HAND] = new float[]{ax, 14.5f, 0};
        at[R_THIGH] = new float[]{-2, 12, 0};
        at[R_SHIN] = new float[]{-2, 6, 0};
        at[R_FOOT] = new float[]{-2, 1.5f, 0};
        at[L_THIGH] = new float[]{2, 12, 0};
        at[L_SHIN] = new float[]{2, 6, 0};
        at[L_FOOT] = new float[]{2, 1.5f, 0};

        Mesh mesh = new Mesh();
        // Base layer (material 0), then the outer layer a little larger (material 1).
        for (int layer = 0; layer < 2; layer++) {
            float g = layer == 0 ? 0 : 0.25f, gh = layer == 0 ? 0 : 0.5f;
            int m = layer;
            mesh.box(m, -4 - gh, 24 - gh, -4 - gh, 8 + gh * 2, 8 + gh * 2, 8 + gh * 2, layer == 0 ? 0 : 32, 0, 8, 8, 8, 2, Part.HEAD);
            mesh.box(m, -4 - g, 12 - g, -2 - g, 8 + g * 2, 12 + g * 2, 4 + g * 2, 16, layer == 0 ? 16 : 32, 8, 12, 4, SEGMENTS, Part.BODY);
            mesh.box(m, -4 - arm - g, 12 - g, -2 - g, arm + g * 2, 12 + g * 2, 4 + g * 2, 40, layer == 0 ? 16 : 32, (int) arm, 12, 4, SEGMENTS, Part.RIGHT_ARM);
            mesh.box(m, 4 - g, 12 - g, -2 - g, arm + g * 2, 12 + g * 2, 4 + g * 2, layer == 0 ? 32 : 48, 48, (int) arm, 12, 4, SEGMENTS, Part.LEFT_ARM);
            mesh.box(m, -4 - g, 0 - g, -2 - g, 4 + g * 2, 12 + g * 2, 4 + g * 2, 0, layer == 0 ? 16 : 32, 4, 12, 4, SEGMENTS, Part.RIGHT_LEG);
            mesh.box(m, 0 - g, 0 - g, -2 - g, 4 + g * 2, 12 + g * 2, 4 + g * 2, layer == 0 ? 16 : 0, 48, 4, 12, 4, SEGMENTS, Part.LEFT_LEG);
        }

        int n = NAMES.length;
        float[] restT = new float[n * 3], restR = new float[n * 4], restS = new float[n * 3], bindInverse = new float[n * 12];
        for (int i = 0; i < n; i++) {
            float[] p = at[i], q = PARENT[i] < 0 ? new float[3] : at[PARENT[i]];
            restT[i * 3] = p[0] - q[0];
            restT[i * 3 + 1] = p[1] - q[1];
            restT[i * 3 + 2] = p[2] - q[2];
            SkinMath.identityQ(restR, i * 4);
            restS[i * 3] = restS[i * 3 + 1] = restS[i * 3 + 2] = 1;
            SkinMath.identityM(bindInverse, i * 12);
            bindInverse[i * 12 + 3] = -p[0];
            bindInverse[i * 12 + 7] = -p[1];
            bindInverse[i * 12 + 11] = -p[2];
        }
        int[] slots = new int[n];
        for (int i = 0; i < n; i++) slots[i] = i;
        int[] role = new int[Human.COUNT];
        Arrays.fill(role, -1);
        int[] drive = new int[n * 2];
        float[] share = new float[n * 2];
        Arrays.fill(drive, -1);
        for (int i = 0; i < n; i++) {
            role[ROLES[i].ordinal()] = i;
            drive[i * 2] = ROLES[i].ordinal();
            share[i * 2] = 1;
        }
        float[] canonWorld = new float[n * 4];
        for (int i = 0; i < n; i++) SkinMath.identityQ(canonWorld, i * 4);
        float[] rootRotation = new float[4];
        SkinMath.identityQ(rootRotation, 0);
        float[] root = new float[12];
        SkinMath.identityM(root, 0);
        float[] footRest = {1.5f, 1.5f}, toeRest = {1.5f, 1.5f}, headTop = {0, 8, 0};
        float[] palm = {0, -2, 0, 0, -2, 0};
        // Soles: heel, ball and toe of each foot's bottom, in the foot's frame.
        int[] soleNode = {R_FOOT, R_FOOT, R_FOOT, L_FOOT, L_FOOT, L_FOOT};
        float[] solePoint = {0, -1.5f, -2, 0, -1.5f, 0, 0, -1.5f, 2, 0, -1.5f, -2, 0, -1.5f, 0, 0, -1.5f, 2};
        Rig rig = new Rig(NAMES.clone(), PARENT.clone(), restT, restR, restS, bindInverse, slots, role, restR.clone(), canonWorld, drive, share,
                rootRotation, root, 32, 12, 12, 10.5f, footRest, toeRest, headTop, palm, soleNode, solePoint, true);

        float[] pos = mesh.pos.toArray(), nrm = mesh.nrm.toArray(), uv = mesh.uv.toArray(), weight = new float[mesh.vertices() * 4];
        short[] joint = new short[mesh.vertices() * 4];
        int[] color = new int[mesh.vertices()];
        Arrays.fill(color, -1);
        for (int v = 0; v < mesh.vertices(); v++) weigh(mesh.part.data[v], pos[v * 3], pos[v * 3 + 1], joint, weight, v);
        int[][] quads = new int[2][];
        quads[0] = mesh.quads[0].toArray();
        quads[1] = mesh.quads[1].toArray();
        SkinLevel level = SkinFormat.compact(pos, nrm, uv, color, joint, weight, quads);
        SkinMaterial[] materials = {new SkinMaterial("skin", 0, SkinMaterial.CUTOUT, 0.1f, false, -1),
                new SkinMaterial("skin overlay", 0, SkinMaterial.BLEND, 0.1f, true, -1)};
        return new SkinnedCharacter(rig, new SkinLevel[]{level, level, level}, materials, new byte[0][], new int[0], new int[0], new boolean[0],
                new NodeClip[0], false);
    }

    private enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /** Each vertex follows its limb's bones, blended across the elbow, wrist, knee, ankle, waist and neck. */
    private static void weigh(int part, float x, float y, short[] joint, float[] weight, int v) {
        int a, b;
        float t;
        switch (Part.values()[part]) {
            case HEAD -> {
                a = HEAD;
                b = HEAD;
                t = 0;
            }
            case BODY -> {
                if (y < 16) {
                    a = HIPS;
                    b = SPINE;
                    t = smooth((y - 13) / 3);
                } else if (y < 21) {
                    a = SPINE;
                    b = CHEST;
                    t = smooth((y - 17) / 3);
                } else {
                    a = CHEST;
                    b = NECK;
                    t = smooth((y - 23.2f) / 0.8f);
                }
            }
            case RIGHT_ARM, LEFT_ARM -> {
                boolean right = part == Part.RIGHT_ARM.ordinal();
                int up = right ? R_UPPER : L_UPPER, fore = right ? R_FORE : L_FORE, hand = right ? R_HAND : L_HAND;
                if (y > 17) {
                    a = fore;
                    b = up;
                    t = smooth((y - 16.5f) / 3);
                } else {
                    a = hand;
                    b = fore;
                    t = smooth((y - 13.5f) / 2);
                }
            }
            default -> {
                boolean right = part == Part.RIGHT_LEG.ordinal();
                int thigh = right ? R_THIGH : L_THIGH, shin = right ? R_SHIN : L_SHIN, foot = right ? R_FOOT : L_FOOT;
                if (y > 5) {
                    a = shin;
                    b = thigh;
                    t = smooth((y - 4.5f) / 3);
                } else {
                    a = foot;
                    b = shin;
                    t = smooth((y - 1.2f) / 1.6f);
                }
            }
        }
        int o = v * 4;
        if (t >= 0.5f) {
            joint[o] = (short) b;
            weight[o] = t;
            joint[o + 1] = (short) a;
            weight[o + 1] = 1 - t;
        } else {
            joint[o] = (short) a;
            weight[o] = 1 - t;
            joint[o + 1] = (short) b;
            weight[o + 1] = t;
        }
        if (weight[o + 1] <= 0) joint[o + 1] = joint[o];
        joint[o + 2] = joint[o + 3] = joint[o];
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** Boxes cut into rows along their height, with the skin's box UV layout. */
    private static final class Mesh {
        final Floats pos = new Floats(), nrm = new Floats(), uv = new Floats();
        final Ints part = new Ints();
        final Ints[] quads = {new Ints(), new Ints()};

        int vertices() {
            return pos.size / 3;
        }

        /**
         * A box from (x, y, z) of size (w, h, d) drawn with the skin region at (u, v) for a
         * (tw, th, td) texture box, its sides cut into {@code rows} along the height.
         */
        void box(int material, float x, float y, float z, float w, float h, float d, int u, int v, int tw, int th, int td, int rows, Part p) {
            float x0 = x, x1 = x + w, y0 = y, y1 = y + h, z0 = z, z1 = z + d;
            // Sides, looking at each from outside: corners in (left-of-view, bottom) order with their skin columns.
            // Front (+z): viewer's left is the character's right (-x).
            side(material, p, rows, x0, z1, x1, z1, y0, y1, 0, 0, 1, u + td, v + td, tw, th);
            // Character's left side (+x): from the front edge to the back.
            side(material, p, rows, x1, z1, x1, z0, y0, y1, 1, 0, 0, u + td + tw, v + td, td, th);
            // Back (-z): from the character's left to its right.
            side(material, p, rows, x1, z0, x0, z0, y0, y1, 0, 0, -1, u + td * 2 + tw, v + td, tw, th);
            // Character's right side (-x): from the back edge to the front.
            side(material, p, rows, x0, z0, x0, z1, y0, y1, -1, 0, 0, u, v + td, td, th);
            // Top: right to left across, back to front down the texture.
            cap(material, p, x0, x1, z0, z1, y1, 1, u + td, v, tw, td, false);
            // Bottom.
            cap(material, p, x0, x1, z0, z1, y0, -1, u + td + tw, v, tw, td, true);
        }

        private void side(int material, Part p, int rows, float ax, float az, float bx, float bz, float y0, float y1, float nx, float ny, float nz,
                          int u, int v, int tw, int th) {
            int base = vertices();
            for (int r = 0; r <= rows; r++) {
                float f = r / (float) rows, y = y1 + (y0 - y1) * f;
                float tv = (v + th * f) / 64f;
                vertex(ax, y, az, nx, ny, nz, u / 64f, tv, p);
                vertex(bx, y, bz, nx, ny, nz, (u + tw) / 64f, tv, p);
            }
            for (int r = 0; r < rows; r++) {
                int a = base + r * 2;
                // Counter-clockwise from outside: top-left, bottom-left, bottom-right, top-right.
                quads[material].add(a, a + 2, a + 3, a + 1);
            }
        }

        private void cap(int material, Part p, float x0, float x1, float z0, float z1, float y, float ny, int u, int v, int tw, int td, boolean bottom) {
            int base = vertices();
            float u0 = u / 64f, u1 = (u + tw) / 64f, v0 = v / 64f, v1 = (v + td) / 64f;
            // Corners: right-back, left-back, left-front, right-front.
            vertex(x0, y, z0, 0, ny, 0, u0, bottom ? v1 : v0, p);
            vertex(x1, y, z0, 0, ny, 0, u1, bottom ? v1 : v0, p);
            vertex(x1, y, z1, 0, ny, 0, u1, bottom ? v0 : v1, p);
            vertex(x0, y, z1, 0, ny, 0, u0, bottom ? v0 : v1, p);
            if (ny > 0) quads[material].add(base, base + 3, base + 2, base + 1);
            else quads[material].add(base, base + 1, base + 2, base + 3);
        }

        private void vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, Part p) {
            pos.add(x, y, z);
            nrm.add(nx, ny, nz);
            uv.add(u, v);
            part.add(p.ordinal());
        }
    }

    private static final class Floats {
        float[] data = new float[256];
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

    private static final class Ints {
        int[] data = new int[256];
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
