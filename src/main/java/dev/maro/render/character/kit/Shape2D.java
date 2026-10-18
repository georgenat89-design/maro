// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public abstract class Shape2D {
    static final float FAR = 100000.0f;
    float minX = -100000.0f;
    float minY = -100000.0f;
    float maxX = 100000.0f;
    float maxY = 100000.0f;

    protected Shape2D() {
    }

    public abstract float distance(float var1, float var2);

    protected final void bounds(float minX, float minY, float maxX, float maxY) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
    }

    public final float minX() {
        return this.minX;
    }

    public final float minY() {
        return this.minY;
    }

    public final float maxX() {
        return this.maxX;
    }

    public final float maxY() {
        return this.maxY;
    }

    public static Shape2D circle(float cx, float cy, float r) {
        return new Circle(cx, cy, r);
    }

    public static Shape2D ellipse(float cx, float cy, float rx, float ry) {
        return new Ellipse(cx, cy, rx, ry);
    }

    public static Shape2D box(float cx, float cy, float halfWidth, float halfHeight, float round) {
        return new Box(cx, cy, halfWidth, halfHeight, round);
    }

    public static Shape2D line(float ax, float ay, float bx, float by, float width) {
        return new Taper(ax, ay, width * 0.5f, bx, by, width * 0.5f);
    }

    public static Shape2D taper(float ax, float ay, float widthA, float bx, float by, float widthB) {
        return new Taper(ax, ay, widthA * 0.5f, bx, by, widthB * 0.5f);
    }

    public static Shape2D curve(float ax, float ay, float cx, float cy, float bx, float by, float widthA, float widthB) {
        return new Path(new float[]{ax, ay, cx, cy, bx, by}, widthA * 0.5f, widthB * 0.5f, true);
    }

    public static Shape2D path(float widthA, float widthB, float ... xy) {
        return new Path(xy, widthA * 0.5f, widthB * 0.5f, false);
    }

    public static Shape2D polygon(float ... xy) {
        return new Polygon(xy);
    }

    public static Shape2D arc(float cx, float cy, float r, float fromDeg, float toDeg, float width) {
        int n = Math.max(3, (int)Math.ceil(Math.abs(toDeg - fromDeg) / 20.0f) + 1);
        float[] pts = new float[(n + 1) * 2];
        for (int i = 0; i <= n; ++i) {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * (float)i / (float)n);
            pts[i * 2] = cx + r * (float)Math.cos(a);
            pts[i * 2 + 1] = cy + r * (float)Math.sin(a);
        }
        return new Path(pts, width * 0.5f, width * 0.5f, false);
    }

    public static Shape2D star(float cx, float cy, float outer, float inner, int points) {
        float[] xy = new float[points * 4];
        for (int i = 0; i < points * 2; ++i) {
            double a = Math.PI * (double)i / (double)points - 1.5707963267948966;
            float r = (i & 1) == 0 ? outer : inner;
            xy[i * 2] = cx + r * (float)Math.cos(a);
            xy[i * 2 + 1] = cy + r * (float)Math.sin(a);
        }
        return new Polygon(xy);
    }

    public static Shape2D heart(float cx, float cy, float size) {
        return new Heart(cx, cy, size);
    }

    public static Shape2D field(Field field, float minX, float minY, float maxX, float maxY) {
        return new Custom(field, minX, minY, maxX, maxY);
    }

    public static Shape2D union(Shape2D ... shapes) {
        return shapes.length == 1 ? shapes[0] : new Union(0.0f, shapes);
    }

    public static Shape2D blend(float k, Shape2D ... shapes) {
        return shapes.length == 1 ? shapes[0] : new Union(k, shapes);
    }

    public Shape2D union(Shape2D other) {
        return new Union(0.0f, new Shape2D[]{this, other});
    }

    public Shape2D blend(float k, Shape2D other) {
        return new Union(k, new Shape2D[]{this, other});
    }

    public Shape2D subtract(Shape2D cutter) {
        return new Op(this, cutter, 1, 0.0f);
    }

    public Shape2D subtract(float k, Shape2D cutter) {
        return new Op(this, cutter, 1, k);
    }

    public Shape2D intersect(Shape2D other) {
        return new Op(this, other, 2, 0.0f);
    }

    public Shape2D intersect(float k, Shape2D other) {
        return new Op(this, other, 2, k);
    }

    public Shape2D inflate(float r) {
        return new Offset(this, r, false);
    }

    public Shape2D outline(float width) {
        return new Offset(this, width * 0.5f, true);
    }

    public Shape2D move(float dx, float dy) {
        return new Transform(this, 1.0f, 0.0f, dx, dy, 0.0f, 0.0f, 1.0f);
    }

    public Shape2D rotate(float degrees, float cx, float cy) {
        double a = Math.toRadians(degrees);
        return new Transform(this, (float)Math.cos(a), (float)Math.sin(a), 0.0f, 0.0f, cx, cy, 1.0f);
    }

    public Shape2D scale(float k, float cx, float cy) {
        return new Transform(this, 1.0f, 0.0f, 0.0f, 0.0f, cx, cy, k);
    }

    public Shape2D mirrorX(float axisX) {
        return new Flip(this, axisX);
    }

    static float clamp01(float v) {
        return v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }

    @Environment(value=EnvType.CLIENT)
    static final class Circle
    extends Shape2D {
        private final float cx;
        private final float cy;
        private final float r;

        Circle(float cx, float cy, float r) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
            this.bounds(cx - r, cy - r, cx + r, cy + r);
        }

        @Override
        public float distance(float x, float y) {
            float dx = x - this.cx;
            float dy = y - this.cy;
            return (float)Math.sqrt(dx * dx + dy * dy) - this.r;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Ellipse
    extends Shape2D {
        private final float cx;
        private final float cy;
        private final float ix;
        private final float iy;
        private final float iix;
        private final float iiy;
        private final float rmin;

        Ellipse(float cx, float cy, float rx, float ry) {
            this.cx = cx;
            this.cy = cy;
            this.ix = 1.0f / rx;
            this.iy = 1.0f / ry;
            this.iix = this.ix * this.ix;
            this.iiy = this.iy * this.iy;
            this.rmin = Math.min(rx, ry);
            this.bounds(cx - rx, cy - ry, cx + rx, cy + ry);
        }

        @Override
        public float distance(float x, float y) {
            float px = x - this.cx;
            float py = y - this.cy;
            float ax = px * this.ix;
            float ay = py * this.iy;
            float k0 = (float)Math.sqrt(ax * ax + ay * ay);
            float bx = px * this.iix;
            float by = py * this.iiy;
            float k1 = (float)Math.sqrt(bx * bx + by * by);
            if (k1 < 1.0E-9f) {
                return -this.rmin;
            }
            return k0 * (k0 - 1.0f) / k1;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Box
    extends Shape2D {
        private final float cx;
        private final float cy;
        private final float hx;
        private final float hy;
        private final float r;

        Box(float cx, float cy, float hx, float hy, float round) {
            this.cx = cx;
            this.cy = cy;
            this.r = Math.max(0.0f, Math.min(round, Math.min(hx, hy)));
            this.hx = hx - this.r;
            this.hy = hy - this.r;
            this.bounds(cx - hx, cy - hy, cx + hx, cy + hy);
        }

        @Override
        public float distance(float x, float y) {
            float qx = Math.abs(x - this.cx) - this.hx;
            float qy = Math.abs(y - this.cy) - this.hy;
            float ox = Math.max(qx, 0.0f);
            float oy = Math.max(qy, 0.0f);
            return (float)Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0.0f) - this.r;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Taper
    extends Shape2D {
        private final float ax;
        private final float ay;
        private final float bax;
        private final float bay;
        private final float ra;
        private final float rb;
        private final float l2;
        private final float rr;
        private final float a2;
        private final float il2;
        private final boolean circle;
        private final float sx;
        private final float sy;
        private final float sr;

        Taper(float ax, float ay, float ra, float bx, float by, float rb) {
            this.ax = ax;
            this.ay = ay;
            this.ra = ra;
            this.rb = rb;
            this.bax = bx - ax;
            this.bay = by - ay;
            this.l2 = this.bax * this.bax + this.bay * this.bay;
            this.rr = ra - rb;
            this.a2 = this.l2 - this.rr * this.rr;
            this.il2 = this.l2 > 1.0E-12f ? 1.0f / this.l2 : 0.0f;
            this.circle = this.a2 <= 1.0E-6f;
            boolean first = ra >= rb;
            this.sx = first ? ax : bx;
            this.sy = first ? ay : by;
            this.sr = Math.max(ra, rb);
            this.bounds(Math.min(ax - ra, bx - rb), Math.min(ay - ra, by - rb), Math.max(ax + ra, bx + rb), Math.max(ay + ra, by + rb));
        }

        @Override
        public float distance(float x, float y) {
            if (this.circle) {
                float dx = x - this.sx;
                float dy = y - this.sy;
                return (float)Math.sqrt(dx * dx + dy * dy) - this.sr;
            }
            float pax = x - this.ax;
            float pay = y - this.ay;
            float yy = pax * this.bax + pay * this.bay;
            float zz = yy - this.l2;
            float qx = pax * this.l2 - this.bax * yy;
            float qy = pay * this.l2 - this.bay * yy;
            float x2 = qx * qx + qy * qy;
            float y2 = yy * yy * this.l2;
            float z2 = zz * zz * this.l2;
            float k = Math.signum(this.rr) * this.rr * this.rr * x2;
            if (Math.signum(zz) * this.a2 * z2 > k) {
                return (float)Math.sqrt(x2 + z2) * this.il2 - this.rb;
            }
            if (Math.signum(yy) * this.a2 * y2 < k) {
                return (float)Math.sqrt(x2 + y2) * this.il2 - this.ra;
            }
            return ((float)Math.sqrt(x2 * this.a2 * this.il2) + yy * this.rr) * this.il2 - this.ra;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Path
    extends Shape2D {
        private final float[] seg;
        private final int count;

        Path(float[] xy, float ra, float rb, boolean exact) {
            int i;
            if (xy.length < 4 || xy.length % 2 != 0) {
                throw new IllegalArgumentException("path needs at least two points given as x,y pairs");
            }
            int n = xy.length / 2;
            float[] cum = new float[n];
            for (int i2 = 1; i2 < n; ++i2) {
                float dx = xy[i2 * 2] - xy[i2 * 2 - 2];
                float dy = xy[i2 * 2 + 1] - xy[i2 * 2 - 1];
                cum[i2] = cum[i2 - 1] + (float)Math.sqrt(dx * dx + dy * dy);
            }
            float total = Math.max(1.0E-6f, cum[n - 1]);
            float[] r = new float[n];
            for (i = 0; i < n; ++i) {
                r[i] = ra + (rb - ra) * cum[i] / total;
            }
            if (exact || n == 2) {
                this.count = 1;
                this.seg = new float[12];
                if (n == 2) {
                    Path.fill(this.seg, 0, xy[0], xy[1], r[0], (xy[0] + xy[2]) * 0.5f, (xy[1] + xy[3]) * 0.5f, (r[0] + r[1]) * 0.5f, xy[2], xy[3], r[1]);
                } else {
                    Path.fill(this.seg, 0, xy[0], xy[1], ra, xy[2], xy[3], (ra + rb) * 0.5f, xy[4], xy[5], rb);
                }
            } else {
                this.count = n - 2;
                this.seg = new float[this.count * 12];
                for (i = 1; i <= this.count; ++i) {
                    float ax = i == 1 ? xy[0] : (xy[i * 2 - 2] + xy[i * 2]) * 0.5f;
                    float ay = i == 1 ? xy[1] : (xy[i * 2 - 1] + xy[i * 2 + 1]) * 0.5f;
                    float ar = i == 1 ? r[0] : (r[i - 1] + r[i]) * 0.5f;
                    float cx = i == this.count ? xy[i * 2 + 2] : (xy[i * 2] + xy[i * 2 + 2]) * 0.5f;
                    float cy = i == this.count ? xy[i * 2 + 3] : (xy[i * 2 + 1] + xy[i * 2 + 3]) * 0.5f;
                    float cr = i == this.count ? r[i + 1] : (r[i] + r[i + 1]) * 0.5f;
                    Path.fill(this.seg, (i - 1) * 12, ax, ay, ar, xy[i * 2], xy[i * 2 + 1], r[i], cx, cy, cr);
                }
            }
            float x0 = 100000.0f;
            float y0 = 100000.0f;
            float x1 = -100000.0f;
            float y1 = -100000.0f;
            for (int i3 = 0; i3 < this.count; ++i3) {
                int o = i3 * 12;
                x0 = Math.min(x0, this.seg[o + 8]);
                y0 = Math.min(y0, this.seg[o + 9]);
                x1 = Math.max(x1, this.seg[o + 10]);
                y1 = Math.max(y1, this.seg[o + 11]);
            }
            this.bounds(x0, y0, x1, y1);
        }

        private static void fill(float[] s, int o, float ax, float ay, float ra, float bx, float by, float rb, float cx, float cy, float rc) {
            s[o] = ax;
            s[o + 1] = ay;
            s[o + 2] = bx;
            s[o + 3] = by;
            s[o + 4] = cx;
            s[o + 5] = cy;
            s[o + 6] = ra;
            s[o + 7] = rc;
            float r = Math.max(ra, Math.max(rb, rc));
            s[o + 8] = Math.min(ax, Math.min(bx, cx)) - r;
            s[o + 9] = Math.min(ay, Math.min(by, cy)) - r;
            s[o + 10] = Math.max(ax, Math.max(bx, cx)) + r;
            s[o + 11] = Math.max(ay, Math.max(by, cy)) + r;
        }

        @Override
        public float distance(float x, float y) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i < this.count; ++i) {
                float d;
                float ey;
                float ex;
                int o = i * 12;
                ex = x < this.seg[o + 8] ? this.seg[o + 8] - x : x > this.seg[o + 10] ? x - this.seg[o + 10] : 0.0f;
                ey = y < this.seg[o + 9] ? this.seg[o + 9] - y : y > this.seg[o + 11] ? y - this.seg[o + 11] : 0.0f;
                if (best > 0.0f && best != Float.MAX_VALUE && ex * ex + ey * ey >= best * best || !((d = this.segment(o, x, y)) < best)) continue;
                best = d;
            }
            return best;
        }

        private float segment(int o, float x, float y) {
            float t;
            float ax = this.seg[o];
            float ay = this.seg[o + 1];
            float bx = this.seg[o + 2];
            float by = this.seg[o + 3];
            float cx = this.seg[o + 4];
            float cy = this.seg[o + 5];
            float px = bx - ax;
            float py = by - ay;
            float qx = ax - 2.0f * bx + cx;
            float qy = ay - 2.0f * by + cy;
            float bb = qx * qx + qy * qy;
            float dx = ax - x;
            float dy = ay - y;
            float lx = cx - ax;
            float ly = cy - ay;
            float ll = lx * lx + ly * ly;
            if (bb < 1.0E-5f * Math.max(ll, 1.0E-6f)) {
                t = ll > 1.0E-12f ? Path.clamp01(((x - ax) * lx + (y - ay) * ly) / ll) : 0.0f;
            } else {
                float kk = 1.0f / bb;
                float kx = kk * (px * qx + py * qy);
                float ky = kk * (2.0f * (px * px + py * py) + dx * qx + dy * qy) / 3.0f;
                float kz = kk * (dx * px + dy * py);
                float q = kx * (2.0f * kx * kx - 3.0f * ky) + kz;
                float pp = ky - kx * kx;
                float p3 = pp * pp * pp;
                float h = q * q + 4.0f * p3;
                if (h >= 0.0f) {
                    h = (float)Math.sqrt(h);
                    t = Path.clamp01((float)(Math.cbrt((h - q) * 0.5f) + Math.cbrt((-h - q) * 0.5f)) - kx);
                } else {
                    float zz = (float)Math.sqrt(-pp);
                    float arg = q / (pp * zz * 2.0f);
                    arg = arg < -1.0f ? -1.0f : Math.min(arg, 1.0f);
                    float v = (float)Math.acos(arg) / 3.0f;
                    float m = (float)Math.cos(v);
                    float n = (float)Math.sin(v) * 1.7320508f;
                    float t1 = Path.clamp01((m + m) * zz - kx);
                    float t2 = Path.clamp01((-n - m) * zz - kx);
                    float e1x = dx + (2.0f * px + qx * t1) * t1;
                    float e1y = dy + (2.0f * py + qy * t1) * t1;
                    float e2x = dx + (2.0f * px + qx * t2) * t2;
                    float e2y = dy + (2.0f * py + qy * t2) * t2;
                    t = e1x * e1x + e1y * e1y <= e2x * e2x + e2y * e2y ? t1 : t2;
                }
            }
            float ox = dx + (2.0f * px + qx * t) * t;
            float oy = dy + (2.0f * py + qy * t) * t;
            float r = this.seg[o + 6] + (this.seg[o + 7] - this.seg[o + 6]) * t;
            return (float)Math.sqrt(ox * ox + oy * oy) - r;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Polygon
    extends Shape2D {
        private final float[] v;
        private final int n;

        Polygon(float[] xy) {
            if (xy.length < 6 || xy.length % 2 != 0) {
                throw new IllegalArgumentException("polygon needs at least three points given as x,y pairs");
            }
            this.v = (float[])xy.clone();
            this.n = xy.length / 2;
            float x0 = 100000.0f;
            float y0 = 100000.0f;
            float x1 = -100000.0f;
            float y1 = -100000.0f;
            for (int i = 0; i < this.n; ++i) {
                x0 = Math.min(x0, this.v[i * 2]);
                y0 = Math.min(y0, this.v[i * 2 + 1]);
                x1 = Math.max(x1, this.v[i * 2]);
                y1 = Math.max(y1, this.v[i * 2 + 1]);
            }
            this.bounds(x0, y0, x1, y1);
        }

        @Override
        public float distance(float x, float y) {
            float dx = x - this.v[0];
            float dy = y - this.v[1];
            float d = dx * dx + dy * dy;
            float s = 1.0f;
            int i = 0;
            int j = this.n - 1;
            while (i < this.n) {
                boolean c3;
                float ex = this.v[j * 2] - this.v[i * 2];
                float ey = this.v[j * 2 + 1] - this.v[i * 2 + 1];
                float wx = x - this.v[i * 2];
                float wy = y - this.v[i * 2 + 1];
                float ee = ex * ex + ey * ey;
                float t = ee > 1.0E-12f ? Polygon.clamp01((wx * ex + wy * ey) / ee) : 0.0f;
                float bx = wx - ex * t;
                float by = wy - ey * t;
                d = Math.min(d, bx * bx + by * by);
                boolean c1 = y >= this.v[i * 2 + 1];
                boolean c2 = y < this.v[j * 2 + 1];
                boolean bl = c3 = ex * wy > ey * wx;
                if (c1 && c2 && c3 || !c1 && !c2 && !c3) {
                    s = -s;
                }
                j = i++;
            }
            return s * (float)Math.sqrt(d);
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Heart
    extends Shape2D {
        private final float cx;
        private final float cy;
        private final float size;

        Heart(float cx, float cy, float size) {
            this.cx = cx;
            this.cy = cy;
            this.size = size;
            this.bounds(cx - size * 0.7f, cy - size * 0.35f, cx + size * 0.7f, cy + size * 0.8f);
        }

        @Override
        public float distance(float x, float y) {
            float py;
            float px = Math.abs(x - this.cx) / this.size;
            if (px + (py = (this.cy + this.size * 0.75f - y) / this.size) > 1.0f) {
                float ax = px - 0.25f;
                float ay = py - 0.75f;
                return ((float)Math.sqrt(ax * ax + ay * ay) - 0.35355338f) * this.size;
            }
            float ax = px;
            float ay = py - 1.0f;
            float t = Math.max(px + py, 0.0f) * 0.5f;
            float bx = px - t;
            float by = py - t;
            return (float)Math.sqrt(Math.min(ax * ax + ay * ay, bx * bx + by * by)) * Math.signum(px - py) * this.size;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Custom
    extends Shape2D {
        private final Field field;

        Custom(Field field, float minX, float minY, float maxX, float maxY) {
            this.field = field;
            this.bounds(minX, minY, maxX, maxY);
        }

        @Override
        public float distance(float x, float y) {
            return this.field.distance(x, y);
        }
    }

    @Environment(value=EnvType.CLIENT)
    public static interface Field {
        public float distance(float var1, float var2);
    }

    @Environment(value=EnvType.CLIENT)
    static final class Union
    extends Shape2D {
        private final Shape2D[] kids;
        private final float k;

        Union(float k, Shape2D[] kids) {
            this.kids = (Shape2D[])kids.clone();
            this.k = Math.max(0.0f, k);
            float x0 = 100000.0f;
            float y0 = 100000.0f;
            float x1 = -100000.0f;
            float y1 = -100000.0f;
            for (Shape2D s : this.kids) {
                x0 = Math.min(x0, s.minX);
                y0 = Math.min(y0, s.minY);
                x1 = Math.max(x1, s.maxX);
                y1 = Math.max(y1, s.maxY);
            }
            this.bounds(x0 - this.k, y0 - this.k, x1 + this.k, y1 + this.k);
        }

        @Override
        public float distance(float x, float y) {
            float best = this.kids[0].distance(x, y);
            for (int i = 1; i < this.kids.length; ++i) {
                float d = this.kids[i].distance(x, y);
                if (this.k > 0.0f) {
                    float h = Union.clamp01(0.5f + 0.5f * (d - best) / this.k);
                    best = d + (best - d) * h - this.k * h * (1.0f - h);
                    continue;
                }
                if (!(d < best)) continue;
                best = d;
            }
            return best;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Op
    extends Shape2D {
        private final Shape2D a;
        private final Shape2D b;
        private final int mode;
        private final float k;

        Op(Shape2D a, Shape2D b, int mode, float k) {
            this.a = a;
            this.b = b;
            this.mode = mode;
            this.k = Math.max(0.0f, k);
            if (mode == 2) {
                this.bounds(Math.max(a.minX, b.minX), Math.max(a.minY, b.minY), Math.min(a.maxX, b.maxX), Math.min(a.maxY, b.maxY));
            } else {
                this.bounds(a.minX, a.minY, a.maxX, a.maxY);
            }
        }

        @Override
        public float distance(float x, float y) {
            float da = this.a.distance(x, y);
            float db = this.b.distance(x, y);
            if (this.mode == 1) {
                if (this.k <= 0.0f) {
                    return Math.max(da, -db);
                }
                float h = Op.clamp01(0.5f - 0.5f * (da + db) / this.k);
                return da + (-db - da) * h + this.k * h * (1.0f - h);
            }
            if (this.k <= 0.0f) {
                return Math.max(da, db);
            }
            float h = Op.clamp01(0.5f - 0.5f * (db - da) / this.k);
            return db + (da - db) * h + this.k * h * (1.0f - h);
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Offset
    extends Shape2D {
        private final Shape2D child;
        private final float r;
        private final boolean ring;

        Offset(Shape2D child, float r, boolean ring) {
            this.child = child;
            this.r = r;
            this.ring = ring;
            float pad = Math.abs(r);
            this.bounds(child.minX - pad, child.minY - pad, child.maxX + pad, child.maxY + pad);
        }

        @Override
        public float distance(float x, float y) {
            float d = this.child.distance(x, y);
            return this.ring ? Math.abs(d) - this.r : d - this.r;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Transform
    extends Shape2D {
        private final Shape2D child;
        private final float c;
        private final float s;
        private final float dx;
        private final float dy;
        private final float cx;
        private final float cy;
        private final float k;

        Transform(Shape2D child, float c, float s, float dx, float dy, float cx, float cy, float k) {
            this.child = child;
            this.c = c;
            this.s = s;
            this.dx = dx;
            this.dy = dy;
            this.cx = cx;
            this.cy = cy;
            this.k = k;
            float x0 = 100000.0f;
            float y0 = 100000.0f;
            float x1 = -100000.0f;
            float y1 = -100000.0f;
            for (int i = 0; i < 4; ++i) {
                float px = ((i & 1) == 0 ? child.minX : child.maxX) - cx;
                float py = ((i & 2) == 0 ? child.minY : child.maxY) - cy;
                float rx = (c * px - s * py) * k + cx + dx;
                float ry = (s * px + c * py) * k + cy + dy;
                x0 = Math.min(x0, rx);
                y0 = Math.min(y0, ry);
                x1 = Math.max(x1, rx);
                y1 = Math.max(y1, ry);
            }
            this.bounds(x0, y0, x1, y1);
        }

        @Override
        public float distance(float x, float y) {
            float px = (x - this.dx - this.cx) / this.k;
            float py = (y - this.dy - this.cy) / this.k;
            return this.child.distance(this.c * px + this.s * py + this.cx, -this.s * px + this.c * py + this.cy) * this.k;
        }
    }

    @Environment(value=EnvType.CLIENT)
    static final class Flip
    extends Shape2D {
        private final Shape2D child;
        private final float axis;

        Flip(Shape2D child, float axis) {
            this.child = child;
            this.axis = axis;
            this.bounds(2.0f * axis - child.maxX, child.minY, 2.0f * axis - child.minX, child.maxY);
        }

        @Override
        public float distance(float x, float y) {
            return this.child.distance(2.0f * this.axis - x, y);
        }
    }
}
