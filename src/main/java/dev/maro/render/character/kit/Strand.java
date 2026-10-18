// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Strand
extends Shape {
    private static final int STRIDE = 20;
    private final int count;
    private final float[] seg;
    private float ratio = 1.0f;
    private int flatMode;
    private float fx;
    private float fy;
    private float fz;
    private float minHalf;

    private Strand(float[] seg, int count) {
        this.seg = seg;
        this.count = count;
        float x0 = 100000.0f;
        float y0 = 100000.0f;
        float z0 = 100000.0f;
        float x1 = -100000.0f;
        float y1 = -100000.0f;
        float z1 = -100000.0f;
        for (int i = 0; i < count; ++i) {
            int o = i * 20;
            x0 = Math.min(x0, seg[o + 14]);
            y0 = Math.min(y0, seg[o + 15]);
            z0 = Math.min(z0, seg[o + 16]);
            x1 = Math.max(x1, seg[o + 17]);
            y1 = Math.max(y1, seg[o + 18]);
            z1 = Math.max(z1, seg[o + 19]);
        }
        this.bounds(x0, y0, z0, x1, y1, z1);
    }

    static Strand tapered(float r0, float r1, float[] xyz) {
        if (xyz.length < 6 || xyz.length % 3 != 0) {
            throw new IllegalArgumentException("strand needs at least two points given as x,y,z triples");
        }
        int n = xyz.length / 3;
        float[] cum = new float[n];
        for (int i = 1; i < n; ++i) {
            float dx = xyz[i * 3] - xyz[i * 3 - 3];
            float dy = xyz[i * 3 + 1] - xyz[i * 3 - 2];
            float dz = xyz[i * 3 + 2] - xyz[i * 3 - 1];
            cum[i] = cum[i - 1] + (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        float total = Math.max(1.0E-6f, cum[n - 1]);
        float[] xyzr = new float[n * 4];
        for (int i = 0; i < n; ++i) {
            xyzr[i * 4] = xyz[i * 3];
            xyzr[i * 4 + 1] = xyz[i * 3 + 1];
            xyzr[i * 4 + 2] = xyz[i * 3 + 2];
            xyzr[i * 4 + 3] = r0 + (r1 - r0) * cum[i] / total;
        }
        return Strand.of(xyzr, false);
    }

    static Strand of(float[] p, boolean exact) {
        if (p.length < 8 || p.length % 4 != 0) {
            throw new IllegalArgumentException("tube needs at least two points given as x,y,z,radius quadruples");
        }
        int n = p.length / 4;
        if (exact) {
            float[] seg = new float[20];
            Strand.fill(seg, 0, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11]);
            return new Strand(seg, 1);
        }
        if (n == 2) {
            float[] seg = new float[20];
            Strand.fill(seg, 0, p[0], p[1], p[2], p[3], (p[0] + p[4]) * 0.5f, (p[1] + p[5]) * 0.5f, (p[2] + p[6]) * 0.5f, (p[3] + p[7]) * 0.5f, p[4], p[5], p[6], p[7]);
            return new Strand(seg, 1);
        }
        int count = n - 2;
        float[] seg = new float[count * 20];
        for (int i = 1; i <= count; ++i) {
            float cr;
            float cz;
            float cy;
            float cx;
            float ar;
            float az;
            float ay;
            float ax;
            int prev = (i - 1) * 4;
            int cur = i * 4;
            int next = (i + 1) * 4;
            if (i == 1) {
                ax = p[0];
                ay = p[1];
                az = p[2];
                ar = p[3];
            } else {
                ax = (p[prev] + p[cur]) * 0.5f;
                ay = (p[prev + 1] + p[cur + 1]) * 0.5f;
                az = (p[prev + 2] + p[cur + 2]) * 0.5f;
                ar = (p[prev + 3] + p[cur + 3]) * 0.5f;
            }
            if (i == count) {
                cx = p[next];
                cy = p[next + 1];
                cz = p[next + 2];
                cr = p[next + 3];
            } else {
                cx = (p[cur] + p[next]) * 0.5f;
                cy = (p[cur + 1] + p[next + 1]) * 0.5f;
                cz = (p[cur + 2] + p[next + 2]) * 0.5f;
                cr = (p[cur + 3] + p[next + 3]) * 0.5f;
            }
            Strand.fill(seg, (i - 1) * 20, ax, ay, az, ar, p[cur], p[cur + 1], p[cur + 2], p[cur + 3], cx, cy, cz, cr);
        }
        return new Strand(seg, count);
    }

    private static void fill(float[] s, int o, float ax, float ay, float az, float ra, float bx, float by, float bz, float rb, float cx, float cy, float cz, float rc) {
        s[o] = ax;
        s[o + 1] = ay;
        s[o + 2] = az;
        s[o + 3] = bx - ax;
        s[o + 4] = by - ay;
        s[o + 5] = bz - az;
        s[o + 6] = ax - 2.0f * bx + cx;
        s[o + 7] = ay - 2.0f * by + cy;
        s[o + 8] = az - 2.0f * bz + cz;
        s[o + 9] = ra;
        s[o + 10] = rb;
        s[o + 11] = rc;
        float bb = s[o + 6] * s[o + 6] + s[o + 7] * s[o + 7] + s[o + 8] * s[o + 8];
        float lx = cx - ax;
        float ly = cy - ay;
        float lz = cz - az;
        float ll = lx * lx + ly * ly + lz * lz;
        boolean straight = bb < 1.0E-5f * Math.max(ll, 1.0E-6f);
        float f = s[o + 12] = straight ? -1.0f : 1.0f / bb;
        s[o + 13] = straight ? (ll > 1.0E-12f ? 1.0f / ll : 0.0f) : 0.0f;
        float r = Math.max(ra, Math.max(rb, rc));
        s[o + 14] = Math.min(ax, Math.min(bx, cx)) - r;
        s[o + 15] = Math.min(ay, Math.min(by, cy)) - r;
        s[o + 16] = Math.min(az, Math.min(bz, cz)) - r;
        s[o + 17] = Math.max(ax, Math.max(bx, cx)) + r;
        s[o + 18] = Math.max(ay, Math.max(by, cy)) + r;
        s[o + 19] = Math.max(az, Math.max(bz, cz)) + r;
    }

    Strand thickened(float half) {
        if (half <= this.minHalf) {
            return this;
        }
        Strand copy = new Strand(this.seg, this.count);
        copy.ratio = this.ratio;
        copy.flatMode = this.flatMode;
        copy.fx = this.fx;
        copy.fy = this.fy;
        copy.fz = this.fz;
        copy.minHalf = half;
        copy.paint = this.paint;
        return copy;
    }

    public Strand flatten(float ratio, float dx, float dy, float dz) {
        float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6f || ratio <= 1.0f) {
            this.flatMode = 0;
            return this;
        }
        this.ratio = ratio;
        this.flatMode = 1;
        this.fx = dx / l;
        this.fy = dy / l;
        this.fz = dz / l;
        return this;
    }

    public Strand flattenFrom(float ratio, float cx, float cy, float cz) {
        if (ratio <= 1.0f) {
            this.flatMode = 0;
            return this;
        }
        this.ratio = ratio;
        this.flatMode = 2;
        this.fx = cx;
        this.fy = cy;
        this.fz = cz;
        return this;
    }

    @Override
    public float distance(float x, float y, float z) {
        return this.distance(x, y, z, Float.MAX_VALUE);
    }

    @Override
    float distance(float x, float y, float z, float limit) {
        float[] s = this.seg;
        int first = 0;
        float firstGap = Float.MAX_VALUE;
        for (int i = 0; i < this.count; ++i) {
            float g = Strand.gap(s, i * 20, x, y, z);
            if (!(g < firstGap)) continue;
            firstGap = g;
            first = i;
        }
        if (firstGap >= limit) {
            return firstGap;
        }
        float best = this.segment(s, first * 20, x, y, z);
        for (int i = 0; i < this.count; ++i) {
            float d;
            if (i == first || Strand.gap(s, i * 20, x, y, z) >= Math.min(best, limit) || !((d = this.segment(s, i * 20, x, y, z)) < best)) continue;
            best = d;
        }
        return best;
    }

    private static float gap(float[] s, int o, float x, float y, float z) {
        float ey;
        float ex;
        ex = x < s[o + 14] ? s[o + 14] - x : x > s[o + 17] ? x - s[o + 17] : 0.0f;
        ey = y < s[o + 15] ? s[o + 15] - y : y > s[o + 18] ? y - s[o + 18] : 0.0f;
        float ez = z < s[o + 16] ? s[o + 16] - z : (z > s[o + 19] ? z - s[o + 19] : 0.0f);
        return (float)Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    private float segment(float[] s, int o, float x, float y, float z) {
        float dz;
        float dy;
        float dx;
        float t;
        float ax = s[o];
        float ay = s[o + 1];
        float az = s[o + 2];
        float px = s[o + 3];
        float py = s[o + 4];
        float pz = s[o + 5];
        float qx = s[o + 6];
        float qy = s[o + 7];
        float qz = s[o + 8];
        float kk = s[o + 12];
        if (kk < 0.0f) {
            float lx = 2.0f * px + qx;
            float ly = 2.0f * py + qy;
            float lz = 2.0f * pz + qz;
            t = Shapes.clamp01(((x - ax) * lx + (y - ay) * ly + (z - az) * lz) * s[o + 13]);
        } else {
            float kx = kk * (px * qx + py * qy + pz * qz);
            float dx2 = ax - x;
            float dy2 = ay - y;
            float dz2 = az - z;
            float ky = kk * (2.0f * (px * px + py * py + pz * pz) + dx2 * qx + dy2 * qy + dz2 * qz) / 3.0f;
            float kz = kk * (dx2 * px + dy2 * py + dz2 * pz);
            float q = kx * (2.0f * kx * kx - 3.0f * ky) + kz;
            float pp = ky - kx * kx;
            float p3 = pp * pp * pp;
            float h = q * q + 4.0f * p3;
            if (h >= 0.0f) {
                h = (float)Math.sqrt(h);
                t = Shapes.clamp01((float)(Math.cbrt((h - q) * 0.5f) + Math.cbrt((-h - q) * 0.5f)) - kx);
            } else {
                float zz = (float)Math.sqrt(-pp);
                float arg = q / (pp * zz * 2.0f);
                arg = arg < -1.0f ? -1.0f : Math.min(arg, 1.0f);
                float v = (float)Math.acos(arg) / 3.0f;
                float m = (float)Math.cos(v);
                float n = (float)Math.sin(v) * 1.7320508f;
                float t1 = Shapes.clamp01((m + m) * zz - kx);
                float t2 = Shapes.clamp01((-n - m) * zz - kx);
                float e1x = dx2 + (2.0f * px + qx * t1) * t1;
                float e1y = dy2 + (2.0f * py + qy * t1) * t1;
                float e1z = dz2 + (2.0f * pz + qz * t1) * t1;
                float e2x = dx2 + (2.0f * px + qx * t2) * t2;
                float e2y = dy2 + (2.0f * py + qy * t2) * t2;
                float e2z = dz2 + (2.0f * pz + qz * t2) * t2;
                t = e1x * e1x + e1y * e1y + e1z * e1z <= e2x * e2x + e2y * e2y + e2z * e2z ? t1 : t2;
            }
        }
        float cx = ax + (2.0f * px + qx * t) * t;
        float cy = ay + (2.0f * py + qy * t) * t;
        float cz = az + (2.0f * pz + qz * t) * t;
        float ox = x - cx;
        float oy = y - cy;
        float oz = z - cz;
        float u = 1.0f - t;
        float r = u * u * s[o + 9] + 2.0f * u * t * s[o + 10] + t * t * s[o + 11];
        float len2 = ox * ox + oy * oy + oz * oz;
        if (this.flatMode == 0) {
            return (float)Math.sqrt(len2) - Math.max(r, this.minHalf);
        }
        r = Math.max(r, this.minHalf * this.ratio);
        float tx = 2.0f * (px + qx * t);
        float ty = 2.0f * (py + qy * t);
        float tz = 2.0f * (pz + qz * t);
        float tl = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl > 1.0E-6f) {
            tx /= tl;
            ty /= tl;
            tz /= tl;
        }
        if (this.flatMode == 1) {
            dx = this.fx;
            dy = this.fy;
            dz = this.fz;
        } else {
            dx = cx - this.fx;
            dy = cy - this.fy;
            dz = cz - this.fz;
        }
        float along = dx * tx + dy * ty + dz * tz;
        float dl = (float)Math.sqrt((dx -= along * tx) * dx + (dy -= along * ty) * dy + (dz -= along * tz) * dz);
        if (dl < 1.0E-6f) {
            return (float)Math.sqrt(len2) - r;
        }
        float a = (ox * dx + oy * dy + oz * dz) / dl;
        float b2 = Math.max(0.0f, len2 - a * a);
        return ((float)Math.sqrt(b2 + this.ratio * this.ratio * a * a) - r) / this.ratio;
    }
}
