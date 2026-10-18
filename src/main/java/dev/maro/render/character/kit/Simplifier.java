// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class Simplifier {
    private static final double FLIP_LIMIT = 0.25;
    private static final int DEBRIS = 10;
    private final int nv;
    private final double[] pos;
    private final int[] tri;
    private final boolean[] triDead;
    private final int[][] around;
    private final int[] aroundCount;
    private final double[] quad;
    private final int[] version;
    private final boolean[] vertDead;
    private final int[] mark;
    private final int[] mark2;
    private int stamp;
    private int live;
    private boolean relaxed;
    private final double lengthWeight;
    private float[] heapCost = new float[1024];
    private int[] heapA = new int[1024];
    private int[] heapB = new int[1024];
    private int[] heapVa = new int[1024];
    private int[] heapVb = new int[1024];
    private int heapSize;
    private final double[] best = new double[3];

    Simplifier(Mesh mesh) {
        int i;
        this.nv = mesh.vertexCount;
        int nt = mesh.triCount;
        this.pos = new double[this.nv * 3];
        for (i = 0; i < this.nv * 3; ++i) {
            this.pos[i] = mesh.pos[i];
        }
        this.tri = Arrays.copyOf(mesh.tris, nt * 3);
        this.triDead = new boolean[nt];
        this.aroundCount = new int[this.nv];
        for (i = 0; i < nt * 3; ++i) {
            int n = this.tri[i];
            this.aroundCount[n] = this.aroundCount[n] + 1;
        }
        this.around = new int[this.nv][];
        for (int v = 0; v < this.nv; ++v) {
            this.around[v] = new int[Math.max(4, this.aroundCount[v] + 2)];
        }
        Arrays.fill(this.aroundCount, 0);
        for (int t = 0; t < nt; ++t) {
            for (int c = 0; c < 3; ++c) {
                int v;
                int n = v = this.tri[t * 3 + c];
                int n2 = this.aroundCount[n];
                this.aroundCount[n] = n2 + 1;
                this.around[v][n2] = t;
            }
        }
        this.quad = new double[this.nv * 10];
        this.version = new int[this.nv];
        this.vertDead = new boolean[this.nv];
        this.mark = new int[this.nv];
        this.mark2 = new int[this.nv];
        this.live = nt;
        double areaSum = 0.0;
        for (int t = 0; t < nt; ++t) {
            int b = this.tri[t * 3 + 1];
            int a = this.tri[t * 3];
            double uy = this.pos[b * 3 + 1] - this.pos[a * 3 + 1];
            int c = this.tri[t * 3 + 2];
            double vz = this.pos[c * 3 + 2] - this.pos[a * 3 + 2];
            double uz = this.pos[b * 3 + 2] - this.pos[a * 3 + 2];
            double vy = this.pos[c * 3 + 1] - this.pos[a * 3 + 1];
            double nx = uy * vz - uz * vy;
            double vx = this.pos[c * 3] - this.pos[a * 3];
            double ux = this.pos[b * 3] - this.pos[a * 3];
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-12) continue;
            double area = len * 0.5;
            areaSum += area;
            double d = -((nx /= len) * this.pos[a * 3] + (ny /= len) * this.pos[a * 3 + 1] + (nz /= len) * this.pos[a * 3 + 2]);
            this.addPlane(a, nx, ny, nz, d, area);
            this.addPlane(b, nx, ny, nz, d, area);
            this.addPlane(c, nx, ny, nz, d, area);
        }
        this.lengthWeight = nt > 0 ? 0.04 * areaSum / (double)nt : 0.0;
        this.dropDebris();
        this.pushAll();
    }

    private void pushAll() {
        this.heapSize = 0;
        for (int t = 0; t < this.triDead.length; ++t) {
            if (this.triDead[t]) continue;
            for (int c = 0; c < 3; ++c) {
                int a = this.tri[t * 3 + c];
                int b = this.tri[t * 3 + (c + 1) % 3];
                if (a >= b && this.hasEdgeTriangleReverse(t, a, b)) continue;
                this.push(a, b);
            }
        }
    }

    private void dropDebris() {
        int t;
        int nt = this.triDead.length;
        int[] root = new int[this.nv];
        for (int v = 0; v < this.nv; ++v) {
            root[v] = v;
        }
        for (int t2 = 0; t2 < nt; ++t2) {
            int a = Simplifier.find(root, this.tri[t2 * 3]);
            for (int c = 1; c < 3; ++c) {
                int b = Simplifier.find(root, this.tri[t2 * 3 + c]);
                if (a == b) continue;
                root[b] = a;
            }
        }
        int[] size = new int[this.nv];
        for (t = 0; t < nt; ++t) {
            int n = Simplifier.find(root, this.tri[t * 3]);
            size[n] = size[n] + 1;
        }
        for (t = 0; t < nt; ++t) {
            if (size[Simplifier.find(root, this.tri[t * 3])] >= 10) continue;
            this.triDead[t] = true;
            --this.live;
        }
    }

    private static int find(int[] root, int v) {
        while (root[v] != v) {
            root[v] = root[root[v]];
            v = root[v];
        }
        return v;
    }

    private boolean hasEdgeTriangleReverse(int self, int a, int b) {
        int[] list = this.around[a];
        for (int i = 0; i < this.aroundCount[a]; ++i) {
            int t = list[i];
            if (t == self) continue;
            for (int c = 0; c < 3; ++c) {
                if (this.tri[t * 3 + c] != b || this.tri[t * 3 + (c + 1) % 3] != a) continue;
                return true;
            }
        }
        return false;
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

    int liveTriangles() {
        return this.live;
    }

    void reduce(int target) {
        this.drain(target);
        if (this.live > target && !this.relaxed) {
            this.relaxed = true;
            this.pushAll();
            this.drain(target);
        }
    }

    private void drain(int target) {
        while (this.live > target && this.heapSize > 0) {
            int a = this.heapA[0];
            int b = this.heapB[0];
            int va = this.heapVa[0];
            int vb = this.heapVb[0];
            this.pop();
            if (this.vertDead[a] || this.vertDead[b] || this.version[a] != va || this.version[b] != vb) continue;
            this.cost(a, b);
            double x = this.best[0];
            double y = this.best[1];
            double z = this.best[2];
            if (!this.valid(a, b, x, y, z)) continue;
            this.collapse(a, b, x, y, z);
        }
    }

    Mesh snapshot() {
        int[] remap = new int[this.nv];
        Arrays.fill(remap, -1);
        int count = 0;
        int[] outTris = new int[this.live * 3];
        int tc = 0;
        for (int t = 0; t < this.triDead.length; ++t) {
            if (this.triDead[t]) continue;
            for (int c = 0; c < 3; ++c) {
                int v = this.tri[t * 3 + c];
                if (remap[v] < 0) {
                    remap[v] = count++;
                }
                outTris[tc * 3 + c] = remap[v];
            }
            ++tc;
        }
        float[] outPos = new float[count * 3];
        for (int v = 0; v < this.nv; ++v) {
            int r = remap[v];
            if (r < 0) continue;
            outPos[r * 3] = (float)this.pos[v * 3];
            outPos[r * 3 + 1] = (float)this.pos[v * 3 + 1];
            outPos[r * 3 + 2] = (float)this.pos[v * 3 + 2];
        }
        return new Mesh(outPos, count, outTris, tc);
    }

    private float cost(int a, int b) {
        double z;
        double mz;
        double y;
        double my;
        double inv;
        double x;
        double mx;
        double[] q = this.quad;
        int oa = a * 10;
        int ob = b * 10;
        double q0 = q[oa] + q[ob];
        double q1 = q[oa + 1] + q[ob + 1];
        double q2 = q[oa + 2] + q[ob + 2];
        double q3 = q[oa + 3] + q[ob + 3];
        double q4 = q[oa + 4] + q[ob + 4];
        double q5 = q[oa + 5] + q[ob + 5];
        double q6 = q[oa + 6] + q[ob + 6];
        double q7 = q[oa + 7] + q[ob + 7];
        double q8 = q[oa + 8] + q[ob + 8];
        double q9 = q[oa + 9] + q[ob + 9];
        double ax = this.pos[a * 3];
        double ay = this.pos[a * 3 + 1];
        double az = this.pos[a * 3 + 2];
        double bx = this.pos[b * 3];
        double by = this.pos[b * 3 + 1];
        double bz = this.pos[b * 3 + 2];
        double ex = bx - ax;
        double ey = by - ay;
        double ez = bz - az;
        double len2 = ex * ex + ey * ey + ez * ez;
        double det = q0 * (q4 * q7 - q5 * q5) - q1 * (q1 * q7 - q5 * q2) + q2 * (q1 * q5 - q4 * q2);
        double scale = Math.abs(q0 * q4 * q7) + 1.0E-30;
        double bestCost = Double.MAX_VALUE;
        if (Math.abs(det) > 1.0E-9 * scale && (mx = (ax + bx) * 0.5 - (x = -(inv = 1.0 / det) * (q3 * (q4 * q7 - q5 * q5) - q1 * (q6 * q7 - q5 * q8) + q2 * (q6 * q5 - q4 * q8)))) * mx + (my = (ay + by) * 0.5 - (y = -inv * (q0 * (q6 * q7 - q8 * q5) - q3 * (q1 * q7 - q5 * q2) + q2 * (q1 * q8 - q6 * q2)))) * my + (mz = (az + bz) * 0.5 - (z = -inv * (q0 * (q4 * q8 - q5 * q6) - q1 * (q1 * q8 - q6 * q2) + q3 * (q1 * q5 - q4 * q2)))) * mz <= len2) {
            bestCost = Simplifier.error(q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, x, y, z);
            this.best[0] = x;
            this.best[1] = y;
            this.best[2] = z;
        }
        if (bestCost == Double.MAX_VALUE) {
            for (int s = 0; s < 3; ++s) {
                double t = (double)s * 0.5;
                double x2 = ax + ex * t;
                double y2 = ay + ey * t;
                double z2 = az + ez * t;
                double e = Simplifier.error(q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, x2, y2, z2);
                if (!(e < bestCost)) continue;
                bestCost = e;
                this.best[0] = x2;
                this.best[1] = y2;
                this.best[2] = z2;
            }
        }
        return (float)(Math.max(0.0, bestCost) + this.lengthWeight * len2);
    }

    private static double error(double q0, double q1, double q2, double q3, double q4, double q5, double q6, double q7, double q8, double q9, double x, double y, double z) {
        return q0 * x * x + 2.0 * q1 * x * y + 2.0 * q2 * x * z + 2.0 * q3 * x + q4 * y * y + 2.0 * q5 * y * z + 2.0 * q6 * y + q7 * z * z + 2.0 * q8 * z + q9;
    }

    private boolean valid(int a, int b, double x, double y, double z) {
        int s = ++this.stamp;
        int shared = 0;
        int[] la = this.around[a];
        for (int i = 0; i < this.aroundCount[a]; ++i) {
            int t = la[i];
            if (this.triDead[t]) continue;
            boolean hasB = false;
            for (int c = 0; c < 3; ++c) {
                int v = this.tri[t * 3 + c];
                if (v == b) {
                    hasB = true;
                }
                if (v == a) continue;
                this.mark[v] = s;
            }
            if (!hasB) continue;
            ++shared;
        }
        if (shared == 0 || shared > 2) {
            return false;
        }
        int common = 0;
        int[] lb = this.around[b];
        for (int i = 0; i < this.aroundCount[b]; ++i) {
            int t = lb[i];
            if (this.triDead[t]) continue;
            for (int c = 0; c < 3; ++c) {
                int v = this.tri[t * 3 + c];
                if (v == b || v == a || this.mark[v] != s || this.mark2[v] == s) continue;
                this.mark2[v] = s;
                ++common;
            }
        }
        if (common != shared) {
            return false;
        }
        return this.keepsOrientation(a, b, x, y, z) && this.keepsOrientation(b, a, x, y, z);
    }

    private boolean keepsOrientation(int v, int other, double x, double y, double z) {
        int[] list = this.around[v];
        for (int i = 0; i < this.aroundCount[v]; ++i) {
            double vz;
            double vy;
            double vx;
            double edges;
            double limit;
            int t = list[i];
            if (this.triDead[t]) continue;
            int i0 = this.tri[t * 3];
            int i1 = this.tri[t * 3 + 1];
            int i2 = this.tri[t * 3 + 2];
            if (i0 == other || i1 == other || i2 == other) continue;
            double p0x = this.pos[i0 * 3];
            double p0y = this.pos[i0 * 3 + 1];
            double p0z = this.pos[i0 * 3 + 2];
            double p1x = this.pos[i1 * 3];
            double p1y = this.pos[i1 * 3 + 1];
            double p1z = this.pos[i1 * 3 + 2];
            double p2x = this.pos[i2 * 3];
            double p2y = this.pos[i2 * 3 + 1];
            double p2z = this.pos[i2 * 3 + 2];
            double onx = (p1y - p0y) * (p2z - p0z) - (p1z - p0z) * (p2y - p0y);
            double ony = (p1z - p0z) * (p2x - p0x) - (p1x - p0x) * (p2z - p0z);
            double onz = (p1x - p0x) * (p2y - p0y) - (p1y - p0y) * (p2x - p0x);
            if (i0 == v) {
                p0x = x;
                p0y = y;
                p0z = z;
            } else if (i1 == v) {
                p1x = x;
                p1y = y;
                p1z = z;
            } else {
                p2x = x;
                p2y = y;
                p2z = z;
            }
            double ux = p1x - p0x;
            double uy = p1y - p0y;
            double uz = p1z - p0z;
            double wx = p2x - p0x;
            double wy = p2y - p0y;
            double wz = p2z - p0z;
            double nnx = uy * wz - uz * wy;
            double nny = uz * wx - ux * wz;
            double nnz = ux * wy - uy * wx;
            double ol = Math.sqrt(onx * onx + ony * ony + onz * onz);
            double nl = Math.sqrt(nnx * nnx + nny * nny + nnz * nnz);
            if (nl < 1.0E-14) {
                return false;
            }
            double d = limit = this.relaxed ? 0.0 : 0.25;
            if (ol > 1.0E-14 && onx * nnx + ony * nny + onz * nnz < limit * ol * nl) {
                return false;
            }
            if (this.relaxed || !(nl * 3.4641016 < 0.06 * (edges = ux * ux + uy * uy + uz * uz + wx * wx + wy * wy + wz * wz + (vx = p2x - p1x) * vx + (vy = p2y - p1y) * vy + (vz = p2z - p1z) * vz))) continue;
            return false;
        }
        return true;
    }

    private void collapse(int a, int b, double x, double y, double z) {
        this.pos[b * 3] = x;
        this.pos[b * 3 + 1] = y;
        this.pos[b * 3 + 2] = z;
        for (int i = 0; i < 10; ++i) {
            int n = b * 10 + i;
            this.quad[n] = this.quad[n] + this.quad[a * 10 + i];
        }
        int[] la = this.around[a];
        for (int i = 0; i < this.aroundCount[a]; ++i) {
            int t = la[i];
            if (this.triDead[t]) continue;
            int o = t * 3;
            if (this.tri[o] == b || this.tri[o + 1] == b || this.tri[o + 2] == b) {
                this.triDead[t] = true;
                --this.live;
                continue;
            }
            for (int c = 0; c < 3; ++c) {
                if (this.tri[o + c] != a) continue;
                this.tri[o + c] = b;
            }
            this.append(b, t);
        }
        this.vertDead[a] = true;
        this.aroundCount[a] = 0;
        int n = a;
        this.version[n] = this.version[n] + 1;
        int n2 = b;
        this.version[n2] = this.version[n2] + 1;
        this.compact(b);
        int s = ++this.stamp;
        int[] lb = this.around[b];
        for (int i = 0; i < this.aroundCount[b]; ++i) {
            int t = lb[i];
            for (int c = 0; c < 3; ++c) {
                int v = this.tri[t * 3 + c];
                if (v == b || this.mark[v] == s) continue;
                this.mark[v] = s;
                this.push(b, v);
            }
        }
    }

    private void append(int v, int t) {
        int[] list = this.around[v];
        if (this.aroundCount[v] == list.length) {
            list = Arrays.copyOf(list, list.length * 2);
            this.around[v] = list;
        }
        int n = v;
        int n2 = this.aroundCount[n];
        this.aroundCount[n] = n2 + 1;
        list[n2] = t;
    }

    private void compact(int v) {
        int[] list = this.around[v];
        int n = 0;
        for (int i = 0; i < this.aroundCount[v]; ++i) {
            if (this.triDead[list[i]]) continue;
            list[n++] = list[i];
        }
        this.aroundCount[v] = n;
    }

    private void push(int a, int b) {
        int p;
        float c = this.cost(a, b);
        if (this.heapSize == this.heapCost.length) {
            int n = this.heapSize * 2;
            this.heapCost = Arrays.copyOf(this.heapCost, n);
            this.heapA = Arrays.copyOf(this.heapA, n);
            this.heapB = Arrays.copyOf(this.heapB, n);
            this.heapVa = Arrays.copyOf(this.heapVa, n);
            this.heapVb = Arrays.copyOf(this.heapVb, n);
        }
        int i = this.heapSize++;
        while (i > 0 && !(this.heapCost[p = i - 1 >> 1] <= c)) {
            this.move(p, i);
            i = p;
        }
        this.heapCost[i] = c;
        this.heapA[i] = a;
        this.heapB[i] = b;
        this.heapVa[i] = this.version[a];
        this.heapVb[i] = this.version[b];
    }

    private void pop() {
        int l;
        int last;
        if ((last = --this.heapSize) == 0) {
            return;
        }
        float c = this.heapCost[last];
        int a = this.heapA[last];
        int b = this.heapB[last];
        int va = this.heapVa[last];
        int vb = this.heapVb[last];
        int i = 0;
        while ((l = i * 2 + 1) < last) {
            int m;
            int r = l + 1;
            int n = m = r < last && this.heapCost[r] < this.heapCost[l] ? r : l;
            if (this.heapCost[m] >= c) break;
            this.move(m, i);
            i = m;
        }
        this.heapCost[i] = c;
        this.heapA[i] = a;
        this.heapB[i] = b;
        this.heapVa[i] = va;
        this.heapVb[i] = vb;
    }

    private void move(int from, int to) {
        this.heapCost[to] = this.heapCost[from];
        this.heapA[to] = this.heapA[from];
        this.heapB[to] = this.heapB[from];
        this.heapVa[to] = this.heapVa[from];
        this.heapVb[to] = this.heapVb[from];
    }
}
