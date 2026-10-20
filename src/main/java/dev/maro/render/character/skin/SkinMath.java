package dev.maro.render.character.skin;

/**
 * Allocation-free quaternion and affine-matrix helpers for skeletal animation.
 *
 * <p>Quaternions are four floats (x, y, z, w). Matrices are affine 3x4, row major: twelve floats
 * {@code m00 m01 m02 m03 m10 ... m23}, mapping a point to {@code (m00 x + m01 y + m02 z + m03, ...)}.
 * Every method writes into an output array at an offset, which may alias an input only where noted.
 */
public final class SkinMath {
    private SkinMath() {
    }

    // ---- quaternions ----------------------------------------------------------------------

    public static void identityQ(float[] q, int o) {
        q[o] = 0;
        q[o + 1] = 0;
        q[o + 2] = 0;
        q[o + 3] = 1;
    }

    /** {@code out = a * b} (b applied first). {@code out} may alias {@code a} or {@code b}. */
    public static void mulQ(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        float ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
        float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        out[oo] = aw * bx + ax * bw + ay * bz - az * by;
        out[oo + 1] = aw * by - ax * bz + ay * bw + az * bx;
        out[oo + 2] = aw * bz + ax * by - ay * bx + az * bw;
        out[oo + 3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    /** {@code out = conj(a) * b}: the rotation from {@code a} to {@code b}. */
    public static void mulConjQ(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        float ax = -a[ao], ay = -a[ao + 1], az = -a[ao + 2], aw = a[ao + 3];
        float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        out[oo] = aw * bx + ax * bw + ay * bz - az * by;
        out[oo + 1] = aw * by - ax * bz + ay * bw + az * bx;
        out[oo + 2] = aw * bz + ax * by - ay * bx + az * bw;
        out[oo + 3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    public static void normalizeQ(float[] q, int o) {
        float l = q[o] * q[o] + q[o + 1] * q[o + 1] + q[o + 2] * q[o + 2] + q[o + 3] * q[o + 3];
        if (!(l > 1e-20f) || !Float.isFinite(l)) {
            identityQ(q, o);
            return;
        }
        float k = (float) (1 / Math.sqrt(l));
        q[o] *= k;
        q[o + 1] *= k;
        q[o + 2] *= k;
        q[o + 3] *= k;
    }

    /** Normalised linear blend along the shorter arc, {@code t} of the way from a to b. Output may alias a. */
    public static void nlerpQ(float[] a, int ao, float[] b, int bo, float t, float[] out, int oo) {
        float dot = a[ao] * b[bo] + a[ao + 1] * b[bo + 1] + a[ao + 2] * b[bo + 2] + a[ao + 3] * b[bo + 3];
        float s = dot < 0 ? -t : t, r = 1 - t;
        out[oo] = r * a[ao] + s * b[bo];
        out[oo + 1] = r * a[ao + 1] + s * b[bo + 1];
        out[oo + 2] = r * a[ao + 2] + s * b[bo + 2];
        out[oo + 3] = r * a[ao + 3] + s * b[bo + 3];
        normalizeQ(out, oo);
    }

    /** Spherical blend, for large angles (keyframes far apart); output may alias a. */
    public static void slerpQ(float[] a, int ao, float[] b, int bo, float t, float[] out, int oo) {
        float ax = a[ao], ay = a[ao + 1], az = a[ao + 2], aw = a[ao + 3];
        float bx = b[bo], by = b[bo + 1], bz = b[bo + 2], bw = b[bo + 3];
        float dot = ax * bx + ay * by + az * bz + aw * bw;
        if (dot < 0) {
            dot = -dot;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float k0, k1;
        if (dot > 0.9995f) {
            k0 = 1 - t;
            k1 = t;
        } else {
            double theta = Math.acos(Math.min(1, dot)), sin = Math.sin(theta);
            k0 = (float) (Math.sin((1 - t) * theta) / sin);
            k1 = (float) (Math.sin(t * theta) / sin);
        }
        out[oo] = k0 * ax + k1 * bx;
        out[oo + 1] = k0 * ay + k1 * by;
        out[oo + 2] = k0 * az + k1 * bz;
        out[oo + 3] = k0 * aw + k1 * bw;
        normalizeQ(out, oo);
    }

    /** Rotation of {@code radians} about a unit axis. */
    public static void axisAngleQ(float x, float y, float z, float radians, float[] out, int o) {
        float s = (float) Math.sin(radians / 2);
        out[o] = x * s;
        out[o + 1] = y * s;
        out[o + 2] = z * s;
        out[o + 3] = (float) Math.cos(radians / 2);
    }

    /** {@code out = Rz(rz) * Ry(ry) * Rx(rx)}: x applied first, radians. */
    public static void eulerZYX(float rx, float ry, float rz, float[] out, int o) {
        float cx = (float) Math.cos(rx / 2), sx = (float) Math.sin(rx / 2);
        float cy = (float) Math.cos(ry / 2), sy = (float) Math.sin(ry / 2);
        float cz = (float) Math.cos(rz / 2), sz = (float) Math.sin(rz / 2);
        // q = qz * qy * qx
        float yx = cy * sx, yy = sy * cx, yz = -sy * sx, yw = cy * cx;
        out[o] = cz * yx - sz * yy;
        out[o + 1] = cz * yy + sz * yx;
        out[o + 2] = cz * yz + sz * yw;
        out[o + 3] = cz * yw - sz * yz;
    }

    /** Rotates the vector at {@code v[vo..]} by q, writing to {@code out}; out may alias v. */
    public static void rotate(float[] q, int qo, float[] v, int vo, float[] out, int oo) {
        float qx = q[qo], qy = q[qo + 1], qz = q[qo + 2], qw = q[qo + 3];
        float x = v[vo], y = v[vo + 1], z = v[vo + 2];
        float tx = 2 * (qy * z - qz * y), ty = 2 * (qz * x - qx * z), tz = 2 * (qx * y - qy * x);
        out[oo] = x + qw * tx + (qy * tz - qz * ty);
        out[oo + 1] = y + qw * ty + (qz * tx - qx * tz);
        out[oo + 2] = z + qw * tz + (qx * ty - qy * tx);
    }

    /** The shortest rotation turning direction a onto direction b (both need not be unit length). */
    public static void arcQ(float ax, float ay, float az, float bx, float by, float bz, float[] out, int o) {
        float la = (float) Math.sqrt(ax * ax + ay * ay + az * az), lb = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 1e-9f || lb < 1e-9f) {
            identityQ(out, o);
            return;
        }
        ax /= la;
        ay /= la;
        az /= la;
        bx /= lb;
        by /= lb;
        bz /= lb;
        float dot = ax * bx + ay * by + az * bz;
        if (dot < -0.999999f) {
            // Opposite: turn half way round any axis square to a.
            float px = Math.abs(ax) < 0.9f ? 1 : 0, py = px == 1 ? 0 : 1;
            float cx = ay * 0 - az * py, cy = az * px - ax * 0, cz = ax * py - ay * px;
            float l = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            axisAngleQ(cx / l, cy / l, cz / l, (float) Math.PI, out, o);
            return;
        }
        out[o] = ay * bz - az * by;
        out[o + 1] = az * bx - ax * bz;
        out[o + 2] = ax * by - ay * bx;
        out[o + 3] = 1 + dot;
        normalizeQ(out, o);
    }

    /** Raises a rotation to a power: the same axis, {@code t} times the angle. */
    public static void powQ(float[] q, int o, float t, float[] out, int oo) {
        float x = q[o], y = q[o + 1], z = q[o + 2], w = q[o + 3];
        if (w < 0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        float s = (float) Math.sqrt(x * x + y * y + z * z);
        if (s < 1e-9f) {
            identityQ(out, oo);
            return;
        }
        float angle = (float) (2 * Math.atan2(s, w)) * t;
        axisAngleQ(x / s, y / s, z / s, angle, out, oo);
    }

    // ---- affine 3x4 matrices -----------------------------------------------------------------

    public static void identityM(float[] m, int o) {
        for (int i = 0; i < 12; i++) m[o + i] = 0;
        m[o] = 1;
        m[o + 5] = 1;
        m[o + 10] = 1;
    }

    /** Translation, rotation then scale (scale applied first): the glTF node transform. */
    public static void composeM(float[] t, int to, float[] q, int qo, float[] s, int so, float[] m, int o) {
        float x = q[qo], y = q[qo + 1], z = q[qo + 2], w = q[qo + 3];
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        float sx = s[so], sy = s[so + 1], sz = s[so + 2];
        m[o] = (1 - 2 * (yy + zz)) * sx;
        m[o + 1] = 2 * (xy - wz) * sy;
        m[o + 2] = 2 * (xz + wy) * sz;
        m[o + 3] = t[to];
        m[o + 4] = 2 * (xy + wz) * sx;
        m[o + 5] = (1 - 2 * (xx + zz)) * sy;
        m[o + 6] = 2 * (yz - wx) * sz;
        m[o + 7] = t[to + 1];
        m[o + 8] = 2 * (xz - wy) * sx;
        m[o + 9] = 2 * (yz + wx) * sy;
        m[o + 10] = (1 - 2 * (xx + yy)) * sz;
        m[o + 11] = t[to + 2];
    }

    /** {@code out = a * b} (b applied first). out must not alias a or b. */
    public static void mulM(float[] a, int ao, float[] b, int bo, float[] out, int oo) {
        for (int r = 0; r < 3; r++) {
            float a0 = a[ao + r * 4], a1 = a[ao + r * 4 + 1], a2 = a[ao + r * 4 + 2], a3 = a[ao + r * 4 + 3];
            out[oo + r * 4] = a0 * b[bo] + a1 * b[bo + 4] + a2 * b[bo + 8];
            out[oo + r * 4 + 1] = a0 * b[bo + 1] + a1 * b[bo + 5] + a2 * b[bo + 9];
            out[oo + r * 4 + 2] = a0 * b[bo + 2] + a1 * b[bo + 6] + a2 * b[bo + 10];
            out[oo + r * 4 + 3] = a0 * b[bo + 3] + a1 * b[bo + 7] + a2 * b[bo + 11] + a3;
        }
    }

    /** Inverts a general affine matrix; a singular one becomes the identity. out must not alias m. */
    public static void invertM(float[] m, int o, float[] out, int oo) {
        float a = m[o], b = m[o + 1], c = m[o + 2], d = m[o + 4], e = m[o + 5], f = m[o + 6], g = m[o + 8], h = m[o + 9], i = m[o + 10];
        float A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        float det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-20f || !Float.isFinite(det)) {
            identityM(out, oo);
            return;
        }
        float k = 1 / det;
        out[oo] = A * k;
        out[oo + 1] = -(b * i - c * h) * k;
        out[oo + 2] = (b * f - c * e) * k;
        out[oo + 4] = B * k;
        out[oo + 5] = (a * i - c * g) * k;
        out[oo + 6] = -(a * f - c * d) * k;
        out[oo + 8] = C * k;
        out[oo + 9] = -(a * h - b * g) * k;
        out[oo + 10] = (a * e - b * d) * k;
        float tx = m[o + 3], ty = m[o + 7], tz = m[o + 11];
        out[oo + 3] = -(out[oo] * tx + out[oo + 1] * ty + out[oo + 2] * tz);
        out[oo + 7] = -(out[oo + 4] * tx + out[oo + 5] * ty + out[oo + 6] * tz);
        out[oo + 11] = -(out[oo + 8] * tx + out[oo + 9] * ty + out[oo + 10] * tz);
    }

    /** A column-major glTF 4x4 matrix as a 3x4 affine one. */
    public static void fromGltf(float[] g, int go, float[] m, int o) {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 4; c++) m[o + r * 4 + c] = g[go + c * 4 + r];
    }

    public static void point(float[] m, int o, float x, float y, float z, float[] out, int oo) {
        out[oo] = m[o] * x + m[o + 1] * y + m[o + 2] * z + m[o + 3];
        out[oo + 1] = m[o + 4] * x + m[o + 5] * y + m[o + 6] * z + m[o + 7];
        out[oo + 2] = m[o + 8] * x + m[o + 9] * y + m[o + 10] * z + m[o + 11];
    }

    /**
     * Splits an affine matrix into translation, rotation and per-axis scale. A mirrored matrix
     * keeps a negative x scale, so recomposing gives it back.
     */
    public static void decomposeM(float[] m, int o, float[] t, int to, float[] q, int qo, float[] s, int so) {
        t[to] = m[o + 3];
        t[to + 1] = m[o + 7];
        t[to + 2] = m[o + 11];
        float sx = (float) Math.sqrt(m[o] * m[o] + m[o + 4] * m[o + 4] + m[o + 8] * m[o + 8]);
        float sy = (float) Math.sqrt(m[o + 1] * m[o + 1] + m[o + 5] * m[o + 5] + m[o + 9] * m[o + 9]);
        float sz = (float) Math.sqrt(m[o + 2] * m[o + 2] + m[o + 6] * m[o + 6] + m[o + 10] * m[o + 10]);
        float det = m[o] * (m[o + 5] * m[o + 10] - m[o + 6] * m[o + 9]) - m[o + 1] * (m[o + 4] * m[o + 10] - m[o + 6] * m[o + 8])
                + m[o + 2] * (m[o + 4] * m[o + 9] - m[o + 5] * m[o + 8]);
        if (det < 0) sx = -sx;
        s[so] = sx;
        s[so + 1] = sy;
        s[so + 2] = sz;
        float ix = Math.abs(sx) < 1e-12f ? 0 : 1 / sx, iy = sy < 1e-12f ? 0 : 1 / sy, iz = sz < 1e-12f ? 0 : 1 / sz;
        float r00 = m[o] * ix, r01 = m[o + 1] * iy, r02 = m[o + 2] * iz;
        float r10 = m[o + 4] * ix, r11 = m[o + 5] * iy, r12 = m[o + 6] * iz;
        float r20 = m[o + 8] * ix, r21 = m[o + 9] * iy, r22 = m[o + 10] * iz;
        fromRotation(r00, r01, r02, r10, r11, r12, r20, r21, r22, q, qo);
    }

    /** A quaternion from a rotation matrix's nine entries (row major). */
    public static void fromRotation(float r00, float r01, float r02, float r10, float r11, float r12, float r20, float r21, float r22, float[] q, int o) {
        float trace = r00 + r11 + r22;
        if (trace > 0) {
            float s = (float) Math.sqrt(trace + 1) * 2;
            q[o + 3] = 0.25f * s;
            q[o] = (r21 - r12) / s;
            q[o + 1] = (r02 - r20) / s;
            q[o + 2] = (r10 - r01) / s;
        } else if (r00 > r11 && r00 > r22) {
            float s = (float) Math.sqrt(1 + r00 - r11 - r22) * 2;
            q[o + 3] = (r21 - r12) / s;
            q[o] = 0.25f * s;
            q[o + 1] = (r01 + r10) / s;
            q[o + 2] = (r02 + r20) / s;
        } else if (r11 > r22) {
            float s = (float) Math.sqrt(1 + r11 - r00 - r22) * 2;
            q[o + 3] = (r02 - r20) / s;
            q[o] = (r01 + r10) / s;
            q[o + 1] = 0.25f * s;
            q[o + 2] = (r12 + r21) / s;
        } else {
            float s = (float) Math.sqrt(1 + r22 - r00 - r11) * 2;
            q[o + 3] = (r10 - r01) / s;
            q[o] = (r02 + r20) / s;
            q[o + 1] = (r12 + r21) / s;
            q[o + 2] = 0.25f * s;
        }
        normalizeQ(q, o);
    }

    /** The rotation part of an affine matrix with its scale and shear removed (by normalising columns). */
    public static void rotationOf(float[] m, int o, float[] q, int qo) {
        float sx = (float) Math.sqrt(m[o] * m[o] + m[o + 4] * m[o + 4] + m[o + 8] * m[o + 8]);
        float sy = (float) Math.sqrt(m[o + 1] * m[o + 1] + m[o + 5] * m[o + 5] + m[o + 9] * m[o + 9]);
        float sz = (float) Math.sqrt(m[o + 2] * m[o + 2] + m[o + 6] * m[o + 6] + m[o + 10] * m[o + 10]);
        float det = m[o] * (m[o + 5] * m[o + 10] - m[o + 6] * m[o + 9]) - m[o + 1] * (m[o + 4] * m[o + 10] - m[o + 6] * m[o + 8])
                + m[o + 2] * (m[o + 4] * m[o + 9] - m[o + 5] * m[o + 8]);
        if (det < 0) sx = -sx;
        float ix = Math.abs(sx) < 1e-12f ? 0 : 1 / sx, iy = sy < 1e-12f ? 0 : 1 / sy, iz = sz < 1e-12f ? 0 : 1 / sz;
        fromRotation(m[o] * ix, m[o + 1] * iy, m[o + 2] * iz, m[o + 4] * ix, m[o + 5] * iy, m[o + 6] * iz, m[o + 8] * ix, m[o + 9] * iy,
                m[o + 10] * iz, q, qo);
    }

    public static boolean finite(float[] a) {
        for (float v : a) if (!Float.isFinite(v)) return false;
        return true;
    }
}
