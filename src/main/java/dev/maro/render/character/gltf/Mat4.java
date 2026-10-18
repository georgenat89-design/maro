// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class Mat4 {
    private Mat4() {
    }

    static float[] identity() {
        float[] m = new float[16];
        m[15] = 1.0f;
        m[10] = 1.0f;
        m[5] = 1.0f;
        m[0] = 1.0f;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; ++c) {
            for (int r = 0; r < 4; ++r) {
                float s = 0.0f;
                for (int k = 0; k < 4; ++k) {
                    s += a[k * 4 + r] * b[c * 4 + k];
                }
                o[c * 4 + r] = s;
            }
        }
        return o;
    }

    static float[] local(JsonObject node) {
        float[] fArray;
        JsonArray matrix = GltfFile.array(node, "matrix");
        if (matrix.size() == 16) {
            float[] o = new float[16];
            for (int i = 0; i < 16; ++i) {
                o[i] = matrix.get(i).getAsFloat();
            }
            return o;
        }
        float[] t = Mat4.vec(node, "translation", 0.0f, 3);
        float[] s = Mat4.vec(node, "scale", 1.0f, 3);
        if (node.has("rotation")) {
            fArray = Mat4.vec(node, "rotation", 0.0f, 4);
        } else {
            float[] fArray2 = new float[4];
            fArray2[0] = 0.0f;
            fArray2[1] = 0.0f;
            fArray2[2] = 0.0f;
            fArray = fArray2;
            fArray2[3] = 1.0f;
        }
        float[] r = fArray;
        float x = r[0];
        float y = r[1];
        float z = r[2];
        float w = r[3];
        return new float[]{(1.0f - 2.0f * (y * y + z * z)) * s[0], 2.0f * (x * y + z * w) * s[0], 2.0f * (x * z - y * w) * s[0], 0.0f, 2.0f * (x * y - z * w) * s[1], (1.0f - 2.0f * (x * x + z * z)) * s[1], 2.0f * (y * z + x * w) * s[1], 0.0f, 2.0f * (x * z + y * w) * s[2], 2.0f * (y * z - x * w) * s[2], (1.0f - 2.0f * (x * x + y * y)) * s[2], 0.0f, t[0], t[1], t[2], 1.0f};
    }

    private static float[] vec(JsonObject node, String key, float fill, int n) {
        float[] o = new float[n];
        JsonArray v = GltfFile.array(node, key);
        for (int i = 0; i < n; ++i) {
            o[i] = i < v.size() ? v.get(i).getAsFloat() : fill;
        }
        return o;
    }

    static float[] rollAbout(float degrees, float[] p) {
        double a = Math.toRadians(degrees);
        float c = (float)Math.cos(a);
        float s = (float)Math.sin(a);
        float[] m = Mat4.identity();
        m[0] = c;
        m[1] = s;
        m[4] = -s;
        m[5] = c;
        m[12] = p[0] - c * p[0] + s * p[1];
        m[13] = p[1] - s * p[0] - c * p[1];
        m[14] = 0.0f;
        return m;
    }

    static float[] point(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    static float[] dir(float[] m, float x, float y, float z) {
        return new float[]{m[0] * x + m[4] * y + m[8] * z, m[1] * x + m[5] * y + m[9] * z, m[2] * x + m[6] * y + m[10] * z};
    }

    static void normalise(float[] v) {
        float l = (float)Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1.0E-12f) {
            v[1] = 1.0f;
            return;
        }
        v[0] = v[0] / l;
        v[1] = v[1] / l;
        v[2] = v[2] / l;
    }

    static float[] faceNormal(float[] p, int a, int b, int c) {
        float ux = p[b * 3] - p[a * 3];
        float uy = p[b * 3 + 1] - p[a * 3 + 1];
        float uz = p[b * 3 + 2] - p[a * 3 + 2];
        float vx = p[c * 3] - p[a * 3];
        float vy = p[c * 3 + 1] - p[a * 3 + 1];
        float vz = p[c * 3 + 2] - p[a * 3 + 2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    static float area(float[] p, int a, int b, int c) {
        float[] n = Mat4.faceNormal(p, a, b, c);
        return (float)Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) * 0.5f;
    }
}
