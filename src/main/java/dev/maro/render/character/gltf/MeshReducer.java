// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class MeshReducer {
    private final float[] pos;
    private final int[] tri;
    private final boolean[] dead;
    private final int[] corner;
    private final List<List<Integer>> around = new ArrayList<List<Integer>>();
    private final boolean[] locked;
    private final double[] quad;
    private final int[] version;
    private int live;

    MeshReducer(PosedMesh s, List<Integer> source, boolean weld) {
        int i;
        int c;
        int t;
        HashMap<Integer, Integer> index = new HashMap<>();
        HashMap<String, Integer> welded = new HashMap<>();
        ArrayList<Integer> order = new ArrayList<Integer>();
        this.tri = new int[source.size() * 3];
        for (int i2 = 0; i2 < source.size(); ++i2) {
            t = source.get(i2);
            boolean back = t < 0;
            int real = back ? ~t : t;
            for (c = 0; c < 3; ++c) {
                int v = s.tris[real * 3 + (back ? 2 - c : c)];
                int key = back ? ~v : v;
                Integer id = (Integer)index.get(key);
                if (id == null && weld) {
                    String spot = Float.floatToIntBits(s.pos[v * 3]) + "," + Float.floatToIntBits(s.pos[v * 3 + 1]) + "," + Float.floatToIntBits(s.pos[v * 3 + 2]) + "," + Float.floatToIntBits(s.uv[v * 2]) + "," + Float.floatToIntBits(s.uv[v * 2 + 1]) + "," + s.material[v] + (back ? "b" : "");
                    id = (Integer)welded.get(spot);
                    if (id == null) {
                        welded.put(spot, order.size());
                    }
                    if (id != null) {
                        index.put(key, id);
                    }
                }
                if (id == null) {
                    id = order.size();
                    index.put(key, id);
                    order.add(key);
                }
                this.tri[i2 * 3 + c] = id;
            }
        }
        int n = order.size();
        this.corner = new int[n];
        this.pos = new float[n * 3];
        for (i = 0; i < n; ++i) {
            this.corner[i] = (Integer)order.get(i);
            int v = this.corner[i] < 0 ? ~this.corner[i] : this.corner[i];
            System.arraycopy(s.pos, v * 3, this.pos, i * 3, 3);
        }
        this.dead = new boolean[source.size()];
        this.live = source.size();
        for (i = 0; i < n; ++i) {
            this.around.add(new ArrayList());
        }
        for (t = 0; t < source.size(); ++t) {
            int a = this.tri[t * 3];
            int b = this.tri[t * 3 + 1];
            c = this.tri[t * 3 + 2];
            if (a == b || b == c || a == c) {
                this.dead[t] = true;
                --this.live;
                continue;
            }
            for (int k = 0; k < 3; ++k) {
                this.around.get(this.tri[t * 3 + k]).add(t);
            }
        }
        this.locked = new boolean[n];
        HashMap<Long, Integer> edges = new HashMap<Long, Integer>();
        for (int t2 = 0; t2 < source.size(); ++t2) {
            if (this.dead[t2]) continue;
            for (int c2 = 0; c2 < 3; ++c2) {
                edges.merge(MeshReducer.key(this.tri[t2 * 3 + c2], this.tri[t2 * 3 + (c2 + 1) % 3]), 1, Integer::sum);
            }
        }
        for (Map.Entry e : edges.entrySet()) {
            if ((Integer)e.getValue() == 2) continue;
            this.locked[(int)(((Long)e.getKey()).longValue() >> 32)] = true;
            this.locked[(int)(((Long)e.getKey()).longValue() & 0xFFFFFFFFL)] = true;
        }
        this.quad = new double[n * 10];
        for (int t3 = 0; t3 < source.size(); ++t3) {
            int c3;
            int b;
            int a;
            float[] f;
            double len;
            if (this.dead[t3] || (len = Math.sqrt((f = Mat4.faceNormal(this.pos, a = this.tri[t3 * 3], b = this.tri[t3 * 3 + 1], c3 = this.tri[t3 * 3 + 2]))[0] * f[0] + f[1] * f[1] + f[2] * f[2])) < 1.0E-12) continue;
            double nx = (double)f[0] / len;
            double ny = (double)f[1] / len;
            double nz = (double)f[2] / len;
            double d = -(nx * (double)this.pos[a * 3] + ny * (double)this.pos[a * 3 + 1] + nz * (double)this.pos[a * 3 + 2]);
            for (int v : new int[]{a, b, c3}) {
                this.addPlane(v, nx, ny, nz, d, len * 0.5);
            }
        }
        this.version = new int[n];
    }

    private static long key(int a, int b) {
        return (long)Math.min(a, b) << 32 | (long)Math.max(a, b);
    }

    private void addPlane(int v, double a, double b, double c, double d, double w) {
        int o;
        int n = o = v * 10;
        this.quad[n] = this.quad[n] + w * a * a;
        int n2 = o + 1;
        this.quad[n2] = this.quad[n2] + w * a * b;
        int n3 = o + 2;
        this.quad[n3] = this.quad[n3] + w * a * c;
        int n4 = o + 3;
        this.quad[n4] = this.quad[n4] + w * a * d;
        int n5 = o + 4;
        this.quad[n5] = this.quad[n5] + w * b * b;
        int n6 = o + 5;
        this.quad[n6] = this.quad[n6] + w * b * c;
        int n7 = o + 6;
        this.quad[n7] = this.quad[n7] + w * b * d;
        int n8 = o + 7;
        this.quad[n8] = this.quad[n8] + w * c * c;
        int n9 = o + 8;
        this.quad[n9] = this.quad[n9] + w * c * d;
        int n10 = o + 9;
        this.quad[n10] = this.quad[n10] + w * d * d;
    }

    private double error(int v, double x, double y, double z) {
        double[] q = this.quad;
        int o = v * 10;
        return q[o] * x * x + 2.0 * q[o + 1] * x * y + 2.0 * q[o + 2] * x * z + 2.0 * q[o + 3] * x + q[o + 4] * y * y + 2.0 * q[o + 5] * y * z + 2.0 * q[o + 6] * y + q[o + 7] * z * z + 2.0 * q[o + 8] * z + q[o + 9];
    }

    int[][] reduce(int[] targets) {
        PriorityQueue<Move> heap = new PriorityQueue<Move>((p, q) -> Double.compare(p.cost, q.cost));
        for (int v = 0; v < this.locked.length; ++v) {
            this.push(heap, v);
        }
        int[][] out = new int[targets.length][];
        Integer[] order = new Integer[targets.length];
        for (int i = 0; i < order.length; ++i) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(targets[b], targets[a]));
        Integer[] integerArray = order;
        int n = integerArray.length;
        for (int i = 0; i < n; ++i) {
            int i2 = integerArray[i];
            while (this.live > targets[i2] && !heap.isEmpty()) {
                Move m = heap.poll();
                if (this.version[m.from] != m.vf || this.version[m.to] != m.vt || this.locked[m.from] || !this.collapse(m.from, m.to)) continue;
                HashSet<Integer> touched = new HashSet<Integer>();
                touched.add(m.to);
                for (int t : this.around.get(m.to)) {
                    if (this.dead[t]) continue;
                    for (int c = 0; c < 3; ++c) {
                        touched.add(this.tri[t * 3 + c]);
                    }
                }
                for (int v : touched) {
                    this.push(heap, v);
                }
            }
            out[i2] = this.snapshot();
        }
        return out;
    }

    private void push(PriorityQueue<Move> heap, int v) {
        if (this.locked[v]) {
            return;
        }
        for (int n : this.neighbours(v)) {
            double dx = this.pos[n * 3] - this.pos[v * 3];
            double dy = this.pos[n * 3 + 1] - this.pos[v * 3 + 1];
            double dz = this.pos[n * 3 + 2] - this.pos[v * 3 + 2];
            double cost = this.error(v, this.pos[n * 3], this.pos[n * 3 + 1], this.pos[n * 3 + 2]) + 1.0E-4 * (dx * dx + dy * dy + dz * dz);
            heap.add(new Move(cost, v, n, this.version[v], this.version[n]));
        }
    }

    private List<Integer> neighbours(int v) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        for (int t : this.around.get(v)) {
            if (this.dead[t]) continue;
            for (int c = 0; c < 3; ++c) {
                int w = this.tri[t * 3 + c];
                if (w == v || out.contains(w)) continue;
                out.add(w);
            }
        }
        return out;
    }

    private boolean collapse(int u, int v) {
        List<Integer> nu = this.neighbours(u);
        List<Integer> nv = this.neighbours(v);
        if (!nu.contains(v)) {
            return false;
        }
        int shared = 0;
        int common = 0;
        for (int t : this.around.get(u)) {
            if (this.dead[t] || !this.has(t, v)) continue;
            ++shared;
        }
        for (int w : nu) {
            if (!nv.contains(w)) continue;
            ++common;
        }
        if (common != shared) {
            return false;
        }
        for (int t : this.around.get(u)) {
            if (this.dead[t] || this.has(t, v)) continue;
            float[] before = Mat4.faceNormal(this.pos, this.tri[t * 3], this.tri[t * 3 + 1], this.tri[t * 3 + 2]);
            int[] moved = new int[]{this.tri[t * 3], this.tri[t * 3 + 1], this.tri[t * 3 + 2]};
            for (int c = 0; c < 3; ++c) {
                if (moved[c] != u) continue;
                moved[c] = v;
            }
            float[] after = Mat4.faceNormal(this.pos, moved[0], moved[1], moved[2]);
            double lb = Math.sqrt(before[0] * before[0] + before[1] * before[1] + before[2] * before[2]);
            double la = Math.sqrt(after[0] * after[0] + after[1] * after[1] + after[2] * after[2]);
            if (la < 1.0E-12 || lb < 1.0E-12) {
                return false;
            }
            if (!((double)(before[0] * after[0] + before[1] * after[1] + before[2] * after[2]) / (la * lb) < 0.5)) continue;
            return false;
        }
        for (int t : this.around.get(u)) {
            if (this.dead[t]) continue;
            if (this.has(t, v)) {
                this.dead[t] = true;
                --this.live;
                continue;
            }
            for (int c = 0; c < 3; ++c) {
                if (this.tri[t * 3 + c] != u) continue;
                this.tri[t * 3 + c] = v;
            }
            this.around.get(v).add(t);
        }
        this.around.get(u).clear();
        for (int k = 0; k < 10; ++k) {
            int n = v * 10 + k;
            this.quad[n] = this.quad[n] + this.quad[u * 10 + k];
        }
        int n = u;
        this.version[n] = this.version[n] + 1;
        int n2 = v;
        this.version[n2] = this.version[n2] + 1;
        this.locked[u] = true;
        return true;
    }

    private boolean has(int t, int v) {
        return this.tri[t * 3] == v || this.tri[t * 3 + 1] == v || this.tri[t * 3 + 2] == v;
    }

    private int[] snapshot() {
        int[] out = new int[this.live * 3];
        int k = 0;
        for (int t = 0; t < this.dead.length; ++t) {
            if (this.dead[t]) continue;
            for (int c = 0; c < 3; ++c) {
                out[k++] = this.corner[this.tri[t * 3 + c]];
            }
        }
        return Arrays.copyOf(out, k);
    }

    @Environment(value=EnvType.CLIENT)
    private record Move(double cost, int from, int to, int vf, int vt) {
    }
}
