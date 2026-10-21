package dev.maro.render.character.gltf;

import dev.maro.render.character.skin.Human;
import java.util.Arrays;
import java.util.HashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Fits a humanoid skeleton to a mesh that came without a usable one. {@link ShapeCut} finds the
 * legs, arms and head from the silhouette; joints go at the hips, spine, chest, neck, head,
 * shoulders, elbows, wrists, hips, knees, ankles and toes; every vertex gets weights that blend
 * across each joint and each part boundary, then a few smoothing passes over the welded surface,
 * so the mesh bends at the elbows, knees and waist instead of breaking into rigid pieces.
 */
@Environment(value=EnvType.CLIENT)
final class AutoRig {
    /** Height the mesh is measured at while fitting. */
    private static final float H = 30.0f;
    private static final int SMOOTHING = 4;

    // Fitted bones, in the order they are added.
    private static final Human[] BONES = {Human.HIPS, Human.SPINE, Human.CHEST, Human.NECK, Human.HEAD,
            Human.RIGHT_UPPER_ARM, Human.RIGHT_FOREARM, Human.RIGHT_HAND, Human.LEFT_UPPER_ARM, Human.LEFT_FOREARM, Human.LEFT_HAND,
            Human.RIGHT_THIGH, Human.RIGHT_SHIN, Human.RIGHT_FOOT, Human.RIGHT_TOE, Human.LEFT_THIGH, Human.LEFT_SHIN, Human.LEFT_FOOT, Human.LEFT_TOE};
    private static final int HIPS = 0, SPINE = 1, CHEST = 2, NECK = 3, HEAD = 4;
    private static final int UPPER = 5, FORE = 6, HAND = 7; // + side * 3
    private static final int THIGH = 11, SHIN = 12, FOOT = 13, TOE = 14; // + side * 4

    private AutoRig() {
    }

    /**
     * Adds the fitted bones to the scene, weights every vertex to them and returns the node of
     * each {@link Human} bone, or null when the shape gives no limbs to fit.
     */
    static int[] fit(GltfFile file, SkinBake.Scene s) {
        int verts = s.vertices();
        if (verts < 4 || s.triangles() == 0) return null;
        PosedMesh mesh = PosedMesh.of(Arrays.copyOf(s.pos.data, verts * 3), Arrays.copyOf(s.uv.data, verts * 2),
                Arrays.copyOf(s.material.data, verts), s.tris.toArray());
        mesh.normalise(H, facesBack(mesh));
        // The cut may turn raised arms down in place; fit to the shape as it stands.
        float[] p = Arrays.copyOf(mesh.pos, verts * 3);
        CharacterBake.Cut cut = ShapeCut.cut(mesh);
        int[] part = cut == null ? new int[verts] : Arrays.copyOf(cut.part(), verts);
        float[][] pivot = cut == null ? new float[6][] : cut.pivot();
        if (cut == null) Arrays.fill(part, CharacterBake.BODY);

        Fit f = new Fit(p, part, verts);
        float[] w;
        if (f.joints(pivot)) {
            w = f.weights();
            smooth(w, p, s.tris.toArray(), verts);
        } else {
            // No limbs to fit (a ball, a block): one bone carries it all.
            f.still();
            w = new float[verts * BONES.length];
            for (int v = 0; v < verts; v++) w[v * BONES.length + HIPS] = 1;
        }

        // Add the bones (world-aligned, at the fitted places in the file's own space).
        int[] node = new int[BONES.length];
        Arrays.fill(node, -1);
        int[] role = new int[Human.COUNT];
        Arrays.fill(role, -1);
        for (int b = 0; b < BONES.length; b++) {
            if (f.at[b] == null) continue;
            int parent = f.parentOf(b);
            float[] world = new float[12];
            float[] q = mesh.unnormalise(f.at[b]);
            world[0] = world[5] = world[10] = 1;
            world[3] = q[0];
            world[7] = q[1];
            world[11] = q[2];
            node[b] = s.add("maro:" + BONES[b].name().toLowerCase(java.util.Locale.ROOT), parent < 0 ? -1 : node[parent], world);
            role[BONES[b].ordinal()] = node[b];
        }

        // Strongest four per vertex.
        int nb = BONES.length;
        int[] j = new int[4];
        float[] wv = new float[4];
        for (int v = 0; v < verts; v++) {
            Arrays.fill(j, -1);
            Arrays.fill(wv, 0);
            for (int b = 0; b < nb; b++) {
                float x = w[v * nb + b];
                if (x <= 1e-4f || node[b] < 0) continue;
                for (int k = 0; k < 4; k++) {
                    if (x > wv[k]) {
                        for (int m = 3; m > k; m--) {
                            wv[m] = wv[m - 1];
                            j[m] = j[m - 1];
                        }
                        wv[k] = x;
                        j[k] = b;
                        break;
                    }
                }
            }
            if (j[0] < 0) {
                j[0] = node[HIPS] >= 0 ? HIPS : firstBone(node);
                wv[0] = 1;
            }
            float sum = wv[0] + wv[1] + wv[2] + wv[3];
            for (int k = 0; k < 4; k++) {
                s.node.data[v * 4 + k] = node[j[k] >= 0 && wv[k] > 0 ? j[k] : j[0]];
                s.weight.data[v * 4 + k] = wv[k] > 0 ? wv[k] / sum : 0;
            }
        }
        return role;
    }

    private static int firstBone(int[] node) {
        for (int b = 0; b < node.length; b++) if (node[b] >= 0) return b;
        return 0;
    }

    /** Whether the mesh faces -Z: its feet reach out further back than its shins. */
    private static boolean facesBack(PosedMesh mesh) {
        float[] box = mesh.bounds();
        float h = box[4] - box[1];
        double feet = 0, shins = 0;
        int nf = 0, ns = 0;
        for (int v = 0; v < mesh.vertices; v++) {
            float y = mesh.pos[v * 3 + 1] - box[1], z = mesh.pos[v * 3 + 2];
            if (y < h * 0.04f) {
                feet += z;
                nf++;
            } else if (y > h * 0.1f && y < h * 0.2f) {
                shins += z;
                ns++;
            }
        }
        return nf > 0 && ns > 0 && feet / nf < shins / ns - h * 0.01f;
    }

    /**
     * A few passes of neighbour averaging over the welded surface (vertices in the same place
     * count as one), so weights never jump across a part boundary or a UV seam.
     */
    private static void smooth(float[] w, float[] p, int[] tris, int verts) {
        int nb = BONES.length;
        HashMap<Long, Integer> weld = new HashMap<>(verts * 2);
        int[] id = new int[verts];
        float q = H * 1e-4f;
        int count = 0;
        for (int v = 0; v < verts; v++) {
            long key = (Math.round(p[v * 3] / q) * 73856093L) ^ (Math.round(p[v * 3 + 1] / q) * 19349663L) ^ (Math.round(p[v * 3 + 2] / q) * 83492791L);
            Integer known = weld.get(key);
            if (known == null) {
                known = count++;
                weld.put(key, known);
            }
            id[v] = known;
        }
        float[] u = new float[count * nb];
        int[] dup = new int[count];
        for (int v = 0; v < verts; v++) {
            dup[id[v]]++;
            for (int b = 0; b < nb; b++) u[id[v] * nb + b] += w[v * nb + b];
        }
        for (int i = 0; i < count; i++) for (int b = 0; b < nb; b++) u[i * nb + b] /= dup[i];
        // Neighbours of each welded vertex (compressed rows).
        int[] degree = new int[count + 1];
        for (int t = 0; t + 2 < tris.length; t += 3) {
            for (int e = 0; e < 3; e++) {
                int a = id[tris[t + e]], b = id[tris[t + (e + 1) % 3]];
                if (a == b) continue;
                degree[a]++;
                degree[b]++;
            }
        }
        int[] start = new int[count + 1];
        for (int i = 0; i < count; i++) start[i + 1] = start[i] + degree[i];
        int[] next = new int[start[count]];
        int[] fill = Arrays.copyOf(start, count);
        for (int t = 0; t + 2 < tris.length; t += 3) {
            for (int e = 0; e < 3; e++) {
                int a = id[tris[t + e]], b = id[tris[t + (e + 1) % 3]];
                if (a == b) continue;
                next[fill[a]++] = b;
                next[fill[b]++] = a;
            }
        }
        float[] out = new float[u.length];
        for (int pass = 0; pass < SMOOTHING; pass++) {
            for (int i = 0; i < count; i++) {
                int from = start[i], to = start[i + 1];
                if (to == from) {
                    System.arraycopy(u, i * nb, out, i * nb, nb);
                    continue;
                }
                float k = 0.5f / (to - from);
                for (int b = 0; b < nb; b++) out[i * nb + b] = u[i * nb + b] * 0.5f;
                for (int e = from; e < to; e++) {
                    int o = next[e] * nb;
                    for (int b = 0; b < nb; b++) out[i * nb + b] += u[o + b] * k;
                }
            }
            float[] swap = u;
            u = out;
            out = swap;
        }
        for (int v = 0; v < verts; v++) System.arraycopy(u, id[v] * nb, w, v * nb, nb);
    }

    private static float smoothstep(float a, float b, float x) {
        if (a == b) return x < a ? 0 : 1;
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** The fit, in the normalised frame: height {@link #H}, feet at 0, facing +Z, the right side at -X. */
    private static final class Fit {
        final float[] p;
        final int[] part;
        final int verts;
        final float[][] at = new float[BONES.length][];
        float hipY, crotchY, shoulderY, neckY, kneeY[] = new float[2], ankleY[] = new float[2];
        boolean legs;
        boolean[] arm = new boolean[2];
        /** Arm axis per side: shoulder, unit direction to the tip and length. */
        final float[][] armFrom = new float[2][], armDir = new float[2][];
        final float[] armLength = new float[2], elbowT = new float[2], wristT = new float[2], armTop = new float[2];
        final float[] toeZ = new float[2];

        Fit(float[] p, int[] part, int verts) {
            this.p = p;
            this.part = part;
            this.verts = verts;
        }

        void still() {
            Arrays.fill(at, null);
            at[HIPS] = trunkAt(H * 0.5f);
        }

        int parentOf(int b) {
            switch (b) {
                case HIPS:
                    return -1;
                case SPINE:
                    return HIPS;
                case CHEST:
                    return at[SPINE] != null ? SPINE : HIPS;
                case NECK:
                    return CHEST;
                case HEAD:
                    return at[NECK] != null ? NECK : CHEST;
                default:
                    break;
            }
            if (b >= UPPER && b < THIGH) {
                int k = (b - UPPER) % 3;
                return k == 0 ? CHEST : b - 1;
            }
            int k = (b - THIGH) % 4;
            return k == 0 ? HIPS : b - 1;
        }

        boolean joints(float[][] pivot) {
            legs = pivot[CharacterBake.RIGHT_LEG] != null && pivot[CharacterBake.LEFT_LEG] != null;
            for (int side = 0; side < 2; side++) arm[side] = pivot[CharacterBake.RIGHT_ARM + side] != null && count(CharacterBake.RIGHT_ARM + side) > 8;
            if (!legs && !arm[0] && !arm[1]) return false;
            if (pivot[CharacterBake.HEAD] == null || count(CharacterBake.HEAD) <= 8) findHead(pivot);
            boolean head = pivot[CharacterBake.HEAD] != null && count(CharacterBake.HEAD) > 8;
            crotchY = legs ? (pivot[CharacterBake.RIGHT_LEG][1] + pivot[CharacterBake.LEFT_LEG][1]) / 2 : H * 0.45f;
            hipY = Math.min(crotchY + H * 0.035f, H * 0.7f);
            float armpit = 0;
            int arms = 0;
            for (int side = 0; side < 2; side++) {
                if (!arm[side]) continue;
                armpit += pivot[CharacterBake.RIGHT_ARM + side][1];
                arms++;
            }
            neckY = head ? pivot[CharacterBake.HEAD][1] : -1;
            shoulderY = arms > 0 ? armpit / arms + H * 0.03f : neckY > 0 ? neckY - H * 0.05f : hipY + (H - hipY) * 0.6f;
            if (neckY > 0) shoulderY = Math.min(shoulderY, neckY - H * 0.01f);
            if (shoulderY < hipY + H * 0.08f) shoulderY = hipY + H * 0.08f;
            if (neckY > 0 && neckY < shoulderY + H * 0.01f) neckY = shoulderY + H * 0.01f;

            float trunk = shoulderY - hipY;
            at[HIPS] = trunkAt(hipY);
            at[SPINE] = trunkAt(hipY + trunk * 0.3f);
            at[CHEST] = trunkAt(hipY + trunk * 0.65f);
            if (head) {
                at[NECK] = trunkAt(shoulderY + (neckY - shoulderY) * 0.4f);
                float[] h = pivot[CharacterBake.HEAD];
                at[HEAD] = new float[]{h[0], neckY, h[2]};
            }
            for (int side = 0; side < 2; side++) if (arm[side]) fitArm(side, pivot[CharacterBake.RIGHT_ARM + side]);
            if (legs) for (int side = 0; side < 2; side++) fitLeg(side, pivot[CharacterBake.RIGHT_LEG + side]);
            return true;
        }

        /**
         * A head the cut missed (one hardly wider than its neck): above the shoulders, where the
         * body narrows to well under its width at the armpits.
         */
        private void findHead(float[][] pivot) {
            float armpit = 0;
            int arms = 0;
            for (int side = 0; side < 2; side++) {
                if (!arm[side]) continue;
                armpit += pivot[CharacterBake.RIGHT_ARM + side][1];
                arms++;
            }
            if (arms == 0) return;
            armpit /= arms;
            float band = H * 0.01f;
            float reference = width(armpit - band * 2, armpit);
            if (!(reference > 0)) return;
            for (float y = armpit + band; y < H - H * 0.08f; y += band) {
                float wd = width(y, y + band);
                if (wd > 0 && wd < reference * 0.55f) {
                    float neck = y + band * 0.5f;
                    if (H - neck > H * 0.3f) return;
                    for (int v = 0; v < verts; v++) if (part[v] == CharacterBake.BODY && p[v * 3 + 1] > neck) part[v] = CharacterBake.HEAD;
                    float[] c = trunkAt(neck);
                    pivot[CharacterBake.HEAD] = c;
                    return;
                }
            }
        }

        /** How wide the body (arms aside) is between two heights. */
        private float width(float y0, float y1) {
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < verts; v++) {
                if (part[v] != CharacterBake.BODY) continue;
                float y = p[v * 3 + 1];
                if (y < y0 || y > y1) continue;
                lo = Math.min(lo, p[v * 3]);
                hi = Math.max(hi, p[v * 3]);
            }
            return hi - lo;
        }

        private int count(int label) {
            int n = 0;
            for (int v = 0; v < verts; v++) if (part[v] == label) n++;
            return n;
        }

        /** The middle of the body's cross-section at a height. */
        private float[] trunkAt(float y) {
            for (float band = H * 0.02f; band < H; band *= 2) {
                double x = 0, z = 0;
                int n = 0;
                for (int v = 0; v < verts; v++) {
                    if (part[v] != CharacterBake.BODY || Math.abs(p[v * 3 + 1] - y) > band) continue;
                    x += p[v * 3];
                    z += p[v * 3 + 2];
                    n++;
                }
                if (n > 0) return new float[]{(float) (x / n), y, (float) (z / n)};
            }
            return new float[]{0, y, 0};
        }

        /** The middle of a part's vertices whose projection on an axis falls near t. */
        private float[] slab(int label, float[] from, float[] dir, float length, float t, float half) {
            for (float band = half; band < 1; band *= 2) {
                double x = 0, y = 0, z = 0;
                int n = 0;
                for (int v = 0; v < verts; v++) {
                    if (part[v] != label) continue;
                    float s = project(v, from, dir) / length;
                    if (Math.abs(s - t) > band) continue;
                    x += p[v * 3];
                    y += p[v * 3 + 1];
                    z += p[v * 3 + 2];
                    n++;
                }
                if (n > 0) return new float[]{(float) (x / n), (float) (y / n), (float) (z / n)};
            }
            return new float[]{from[0] + dir[0] * length * t, from[1] + dir[1] * length * t, from[2] + dir[2] * length * t};
        }

        private float project(int v, float[] from, float[] dir) {
            return (p[v * 3] - from[0]) * dir[0] + (p[v * 3 + 1] - from[1]) * dir[1] + (p[v * 3 + 2] - from[2]) * dir[2];
        }

        private void fitArm(int side, float[] top) {
            int label = CharacterBake.RIGHT_ARM + side;
            // The farthest of the arm from where it meets the body is its tip.
            float[] tip = null;
            float far = -1;
            for (int v = 0; v < verts; v++) {
                if (part[v] != label) continue;
                float dx = p[v * 3] - top[0], dy = p[v * 3 + 1] - top[1], dz = p[v * 3 + 2] - top[2];
                float d = dx * dx + dy * dy + dz * dz;
                if (d > far) {
                    far = d;
                    tip = new float[]{p[v * 3], p[v * 3 + 1], p[v * 3 + 2]};
                }
            }
            if (tip == null || far < H * H * 0.0025f) {
                arm[side] = false;
                return;
            }
            boolean hanging = tip[1] < top[1];
            // A hanging arm turns at the shoulder, a little above the armpit where the cut starts.
            float[] shoulder = {top[0], hanging ? Math.min(top[1] + H * 0.03f, shoulderY) : top[1], top[2]};
            float ex = tip[0] - top[0], ey = tip[1] - top[1], ez = tip[2] - top[2];
            float el = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (Math.abs(ey) < el * 0.9f) {
                // Held out (a T- or A-pose): the cut only finds the arm where it clears the body, part way along
                // it. Follow the arm's line back in to the side of the chest for the real shoulder, and give the
                // stretch inside back to the arm.
                float half = halfWidth(hipY + (top[1] - hipY) * 0.3f, hipY + (top[1] - hipY) * 0.55f);
                float sx = (side == 0 ? -1 : 1) * half * 0.85f;
                if (half > 0 && Math.abs(top[0]) > Math.abs(sx) && Math.abs(ex) > 1e-4f) {
                    float u = (sx - top[0]) / -ex;
                    if (u > 0 && u < 2) {
                        float[] inner = {sx, top[1] - ey * u, top[2] - ez * u};
                        float radius = radius(label, top, tip);
                        claim(label, side, inner, top, radius * 1.5f, sx);
                        shoulder = inner;
                        top = inner;
                    }
                }
            }
            float dx = tip[0] - shoulder[0], dy = tip[1] - shoulder[1], dz = tip[2] - shoulder[2];
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float[] dir = {dx / length, dy / length, dz / length};
            armFrom[side] = shoulder;
            armDir[side] = dir;
            armLength[side] = length;
            armTop[side] = ((top[0] - shoulder[0]) * dir[0] + (top[1] - shoulder[1]) * dir[1] + (top[2] - shoulder[2]) * dir[2]) / length;
            // Upper arm, forearm and hand in human proportions along the arm.
            elbowT[side] = 0.42f;
            wristT[side] = 0.76f;
            at[UPPER + side * 3] = shoulder;
            at[FORE + side * 3] = slab(label, shoulder, dir, length, elbowT[side], 0.03f);
            at[HAND + side * 3] = slab(label, shoulder, dir, length, wristT[side], 0.03f);
        }

        /** Half the body's width (arms aside) between two heights. */
        private float halfWidth(float y0, float y1) {
            float best = 0;
            for (int v = 0; v < verts; v++) {
                if (part[v] != CharacterBake.BODY) continue;
                float y = p[v * 3 + 1];
                if (y >= y0 && y <= y1) best = Math.max(best, Math.abs(p[v * 3]));
            }
            return best;
        }

        /** How thick an arm is: its vertices' mean distance from the line through it. */
        private float radius(int label, float[] a, float[] b) {
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l2 = dx * dx + dy * dy + dz * dz;
            double sum = 0;
            int n = 0;
            for (int v = 0; v < verts; v++) {
                if (part[v] != label) continue;
                float px = p[v * 3] - a[0], py = p[v * 3 + 1] - a[1], pz = p[v * 3 + 2] - a[2];
                float t = (px * dx + py * dy + pz * dz) / l2;
                float qx = px - dx * t, qy = py - dy * t, qz = pz - dz * t;
                sum += Math.sqrt(qx * qx + qy * qy + qz * qz);
                n++;
            }
            return n == 0 ? H * 0.03f : (float) (sum / n);
        }

        /** Body vertices outside the shoulder along the arm's line, within reach of it, become arm. */
        private void claim(int label, int side, float[] a, float[] b, float reach, float sx) {
            float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l2 = dx * dx + dy * dy + dz * dz;
            if (l2 < 1e-8f) return;
            for (int v = 0; v < verts; v++) {
                if (part[v] != CharacterBake.BODY) continue;
                float x = p[v * 3];
                if (side == 0 ? x > sx : x < sx) continue;
                float px = x - a[0], py = p[v * 3 + 1] - a[1], pz = p[v * 3 + 2] - a[2];
                float t = Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / l2));
                float qx = px - dx * t, qy = py - dy * t, qz = pz - dz * t;
                if (qx * qx + qy * qy + qz * qz <= reach * reach) part[v] = label;
            }
        }

        private void fitLeg(int side, float[] top) {
            int label = CharacterBake.RIGHT_LEG + side;
            float ankle = Math.max(H * 0.04f, crotchY * 0.09f);
            float knee = (hipY + ankle) * 0.5f;
            kneeY[side] = knee;
            ankleY[side] = ankle;
            at[THIGH + side * 4] = new float[]{top[0], hipY, top[2]};
            float[] k = legAt(label, knee, H * 0.012f);
            at[SHIN + side * 4] = new float[]{k[0], knee, k[2]};
            float[] a = legAt(label, ankle + H * 0.012f, H * 0.012f);
            at[FOOT + side * 4] = new float[]{a[0], ankle, a[2]};
            // The toes bend where the foot is about two thirds of the way to its front.
            float front = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < verts; v++) if (part[v] == label && p[v * 3 + 1] < ankle) front = Math.max(front, p[v * 3 + 2]);
            if (front - a[2] > H * 0.03f) {
                toeZ[side] = a[2] + (front - a[2]) * 0.68f;
                at[TOE + side * 4] = new float[]{a[0], ankle * 0.35f, toeZ[side]};
            } else toeZ[side] = Float.POSITIVE_INFINITY;
        }

        private float[] legAt(int label, float y, float half) {
            for (float band = half; band < H; band *= 2) {
                double x = 0, z = 0;
                int n = 0;
                for (int v = 0; v < verts; v++) {
                    if (part[v] != label || Math.abs(p[v * 3 + 1] - y) > band) continue;
                    x += p[v * 3];
                    z += p[v * 3 + 2];
                    n++;
                }
                if (n > 0) return new float[]{(float) (x / n), y, (float) (z / n)};
            }
            return new float[]{0, y, 0};
        }

        /** Weights per vertex and fitted bone, before smoothing. */
        float[] weights() {
            int nb = BONES.length;
            float[] w = new float[verts * nb];
            float trunk = shoulderY - hipY;
            float spineY = at[SPINE][1], chestY = at[CHEST][1], neckJoint = at[NECK] != null ? at[NECK][1] : -1;
            for (int v = 0; v < verts; v++) {
                float x = p[v * 3], y = p[v * 3 + 1], z = p[v * 3 + 2];
                int o = v * nb;
                switch (part[v]) {
                    case CharacterBake.HEAD -> {
                        float neck = at[NECK] != null ? 0.45f * (1 - smoothstep(neckY, neckY + H * 0.035f, y)) : 0;
                        w[o + HEAD] = 1 - neck;
                        w[o + NECK] = neck;
                    }
                    case CharacterBake.RIGHT_ARM, CharacterBake.LEFT_ARM -> {
                        int side = part[v] - CharacterBake.RIGHT_ARM;
                        if (!arm[side]) {
                            trunk(w, o, y, trunk, spineY, chestY, neckJoint);
                            break;
                        }
                        float t = project(v, armFrom[side], armDir[side]) / armLength[side];
                        float e = elbowT[side], r = wristT[side];
                        float fore = smoothstep(e - 0.07f, e + 0.07f, t), hand = smoothstep(r - 0.04f, r + 0.03f, t);
                        // Where the arm meets the body it still shares the chest's turn.
                        float body = 0.5f * (1 - smoothstep(armTop[side] - 0.02f, armTop[side] + 0.14f, t));
                        float limb = 1 - body;
                        w[o + UPPER + side * 3] = limb * (1 - fore);
                        w[o + FORE + side * 3] = limb * fore * (1 - hand);
                        w[o + HAND + side * 3] = limb * fore * hand;
                        w[o + CHEST] = body;
                    }
                    case CharacterBake.RIGHT_LEG, CharacterBake.LEFT_LEG -> {
                        int side = part[v] - CharacterBake.RIGHT_LEG;
                        if (!legs) {
                            trunk(w, o, y, trunk, spineY, chestY, neckJoint);
                            break;
                        }
                        float knee = kneeY[side], ankle = ankleY[side];
                        float shin = 1 - smoothstep(knee - H * 0.03f, knee + H * 0.03f, y);
                        float foot = 1 - smoothstep(ankle - H * 0.012f, ankle + H * 0.012f, y);
                        float toe = at[TOE + side * 4] != null ? smoothstep(toeZ[side] - H * 0.008f, toeZ[side] + H * 0.008f, z) * foot : 0;
                        float pelvis = 0.45f * smoothstep(crotchY - H * 0.07f, crotchY + H * 0.01f, y);
                        float limb = 1 - pelvis;
                        w[o + THIGH + side * 4] = limb * (1 - shin);
                        w[o + SHIN + side * 4] = limb * shin * (1 - foot);
                        w[o + FOOT + side * 4] = limb * shin * foot * (1 - toe);
                        w[o + TOE + side * 4] = limb * shin * foot * toe;
                        w[o + HIPS] = pelvis;
                    }
                    default -> {
                        trunk(w, o, y, trunk, spineY, chestY, neckJoint);
                        // The shoulders: body near a shoulder joint, on its side, follows the arm a little.
                        for (int side = 0; side < 2; side++) {
                            if (!arm[side]) continue;
                            float[] s = armFrom[side];
                            float dx = x - s[0], dy = y - s[1], dz = z - s[2];
                            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                            float lateral = smoothstep(Math.abs(s[0]) * 0.45f, Math.abs(s[0]) * 0.9f, side == 0 ? -x : x);
                            float a = 0.55f * (1 - smoothstep(H * 0.02f, H * 0.075f, d)) * lateral;
                            if (a <= 0) continue;
                            for (int b = 0; b < nb; b++) w[o + b] *= 1 - a;
                            w[o + UPPER + side * 3] += a;
                        }
                        // The pelvis below the hip joints turns a little with each thigh.
                        if (legs && y < hipY + H * 0.02f) {
                            for (int side = 0; side < 2; side++) {
                                float[] t = at[THIGH + side * 4];
                                float lateral = smoothstep(Math.abs(t[0]) * 0.15f, Math.abs(t[0]) * 0.9f, side == 0 ? -x : x);
                                float a = 0.4f * (1 - smoothstep(crotchY - H * 0.01f, hipY + H * 0.02f, y)) * lateral;
                                if (a <= 0) continue;
                                for (int b = 0; b < nb; b++) w[o + b] *= 1 - a;
                                w[o + THIGH + side * 4] += a;
                            }
                        }
                    }
                }
            }
            return w;
        }

        /** The body's own weights by height: hips, spine, chest, neck, head, each blended into the next. */
        private void trunk(float[] w, int o, float y, float trunk, float spineY, float chestY, float neckJoint) {
            float r1 = trunk * 0.12f, r2 = trunk * 0.12f;
            float spine = smoothstep(spineY - r1, spineY + r1, y);
            float chest = smoothstep(chestY - r2, chestY + r2, y);
            float neck = neckJoint > 0 ? smoothstep(neckJoint - H * 0.015f, neckJoint + H * 0.015f, y) : 0;
            float head = at[HEAD] != null ? smoothstep(neckY - H * 0.01f, neckY + H * 0.02f, y) : 0;
            w[o + HIPS] += 1 - spine;
            w[o + SPINE] += spine * (1 - chest);
            w[o + CHEST] += spine * chest * (1 - neck);
            w[o + NECK] += spine * chest * neck * (1 - head);
            w[o + HEAD] += spine * chest * neck * head;
            if (at[NECK] == null) {
                w[o + CHEST] += w[o + NECK];
                w[o + NECK] = 0;
            }
            if (at[HEAD] == null) {
                w[o + CHEST] += w[o + HEAD];
                w[o + HEAD] = 0;
            }
        }
    }
}
