// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import dev.maro.anubis.inspect.KnifeMesh;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
final class KnifeModels {
    static final int KARAMBIT_DETAIL = 2;
    static final float KARAMBIT_RING_X = -16.0f;
    static final float KARAMBIT_RING_Y = -1.0f;
    static final float KARAMBIT_BLADE_LENGTH = 21.0f;
    static final float[] KARAMBIT_TIP = KnifeModels.karambitTip();
    private static final float KATANA_BOW = 0.0016f;
    static final float KATANA_BOW_FROM = 3.0f;
    static final float KATANA_BOW_RADIUS = 312.5f;

    private KnifeModels() {
    }

    private static float grain(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 1540483477;
        h ^= h >>> 15;
        return 0.92f + 0.08f * ((float)(h & 0x3FF) / 1023.0f);
    }

    static KnifeMesh butterflyBlade() {
        KnifeMesh.Voxels v = new KnifeMesh.Voxels();
        for (int x = -3; x <= 21; ++x) {
            int bottom;
            int top = x <= 12 ? 2 : Math.round(2.0f - (float)(x - 12) * 3.0f / 9.0f);
            for (int y = bottom = x <= 14 ? -2 : Math.round(-2.0f + (float)Math.pow((float)(x - 14) / 7.0f, 2.0) * 1.0f); y <= top; ++y) {
                int ink = x < 0 ? 5 : (x <= 1 ? 1 : (y == bottom ? 2 : (y == top ? (x >= 13 ? 2 : 1) : 0)));
                v.set(x, y, 0, ink, KnifeModels.grain(x, y, 0));
            }
        }
        return v.mesh(0.0f, 22.0f);
    }

    static KnifeMesh butterflyHandle(int side) {
        KnifeMesh.Voxels v = new KnifeMesh.Voxels();
        int inner = side > 0 ? 1 : -1;
        int outer = side > 0 ? 2 : -2;
        int cap = side > 0 ? 3 : -3;
        for (int x = -27; x <= 0; ++x) {
            for (int y = -2; y <= 2; ++y) {
                boolean hole;
                boolean corner = (x == 0 || x == -27) && Math.abs(y) == 2;
                boolean bl = hole = Math.abs(y) <= 1 && (x <= -6 && x >= -8 || x <= -12 && x >= -14 || x <= -18 && x >= -20);
                if (corner || hole) continue;
                v.set(x, y, inner, 4, KnifeModels.grain(x, y, inner));
                int ink = 3;
                if ((x == -10 || x == -16) && Math.abs(y) <= 1) {
                    ink = 6;
                }
                if (y == 0 && (x == -2 || x == -25)) {
                    ink = 5;
                }
                v.set(x, y, outer, ink, KnifeModels.grain(x, y, outer));
            }
        }
        v.set(-2, 0, cap, 5, 1.0f);
        v.set(-25, 0, cap, 5, 1.0f);
        if (side < 0) {
            v.set(-28, 1, inner, 5, 1.0f);
            v.set(-28, 2, inner, 5, 1.0f);
            v.set(-29, 2, inner, 5, 1.0f);
        }
        return v.mesh(-29.0f, 0.0f);
    }

    private static float[] karambitCurve(float s) {
        float x = 5.5f;
        float y = 0.5f;
        float step = 0.05f;
        for (float t = 0.0f; t < s; t += step) {
            float heading = -(0.04f * t + 0.0026f * t * t);
            x += (float)Math.cos(heading) * step;
            y += (float)Math.sin(heading) * step;
        }
        float heading = -(0.04f * s + 0.0026f * s * s);
        return new float[]{x, y, (float)Math.cos(heading), (float)Math.sin(heading)};
    }

    private static float[] karambitTip() {
        float[] c = KnifeModels.karambitCurve(21.0f);
        return new float[]{c[0] * 2.0f, c[1] * 2.0f};
    }

    static KnifeMesh karambit() {
        int x;
        KnifeMesh.Voxels v = new KnifeMesh.Voxels();
        int k = 2;
        float ringX = -16.0f / (float)k;
        float ringY = -1.0f / (float)k;
        for (x = Math.round((ringX + 2.4f) * (float)k); x < 6 * k; ++x) {
            float px = ((float)x + 0.5f) / (float)k;
            float middle = 0.5f - (float)Math.pow((5.0f - px) / 11.0f, 2.0);
            for (int y = -6 * k; y <= 4 * k; ++y) {
                float across = ((float)y + 0.5f) / (float)k - middle;
                if (Math.abs(across) > 2.45f) continue;
                v.set(x, y, 0, 5, KnifeModels.grain(x, y, 0));
                boolean rivet = Math.hypot(px + 3.0f, across) < (double)0.7f || Math.hypot(px - 2.5f, across) < (double)0.7f;
                boolean inlay = Math.abs(across) < 0.5f && px > -1.5f && px < 1.5f;
                boolean rim = Math.abs(across) > 1.95f;
                for (int z : new int[]{-1, 1}) {
                    int ink = rivet ? 5 : (inlay ? 6 : (rim ? 4 : 3));
                    v.set(x, y, z, ink, KnifeModels.grain(x, y, z));
                }
                if (!rivet) continue;
                v.set(x, y, -2, 5, 1.0f);
                v.set(x, y, 2, 5, 1.0f);
            }
        }
        for (x = Math.round((ringX - 3.4f) * (float)k); x <= Math.round((ringX + 3.4f) * (float)k); ++x) {
            for (int y = Math.round((ringY - 3.4f) * (float)k); y <= Math.round((ringY + 3.4f) * (float)k); ++y) {
                float dx = ((float)x + 0.5f) / (float)k - ringX;
                float dy = ((float)y + 0.5f) / (float)k - ringY;
                float d = (float)Math.sqrt(dx * dx + dy * dy);
                if (d < 2.0f || d > 3.0f || v.filled(x, y, 0)) continue;
                float shade = d > 2.75f || d < 2.25f ? 0.86f : 1.0f;
                for (int z = -1; z <= 1; ++z) {
                    v.set(x, y, z, 5, shade * KnifeModels.grain(x, y, z));
                }
            }
        }
        int samples = 420;
        float[][] curve = new float[samples + 1][];
        for (int i = 0; i <= samples; ++i) {
            curve[i] = KnifeModels.karambitCurve(21.0f * (float)i / (float)samples);
        }
        for (int x2 = 5 * k; x2 <= 31 * k; ++x2) {
            for (int y = -24 * k; y <= 6 * k; ++y) {
                float dy;
                float dx;
                float px = ((float)x2 + 0.5f) / (float)k;
                float py = ((float)y + 0.5f) / (float)k;
                int best = 0;
                float bestDistance = Float.MAX_VALUE;
                for (int i = 0; i <= samples; ++i) {
                    dx = px - curve[i][0];
                    dy = py - curve[i][1];
                    float d = dx * dx + dy * dy;
                    if (!(d < bestDistance)) continue;
                    bestDistance = d;
                    best = i;
                }
                float[] c = curve[best];
                dx = px - c[0];
                dy = py - c[1];
                float along = dx * c[2] + dy * c[3];
                float across = -dx * c[3] + dy * c[2];
                if (best == 0 && along < -0.5f || best == samples && along > 0.3f) continue;
                float left = 1.0f - (float)best / (float)samples;
                float spine = 2.9f * (float)Math.pow(left, 0.6) + 0.15f;
                float edge = 2.9f * (float)Math.pow(left, 1.1) + 0.1f;
                if (across > spine || across < -edge || v.filled(x2, y, 0)) continue;
                int ink = across < -edge + 0.55f ? 2 : (across > spine - 0.55f ? 1 : 0);
                v.set(x2, y, 0, ink, KnifeModels.grain(x2, y, 0));
            }
        }
        return v.mesh(5.0f * (float)k, 27.0f * (float)k);
    }

    static KnifeMesh katanaScabbard() {
        KnifeMesh.Voxels v = new KnifeMesh.Voxels();
        for (int x = 1; x <= 51; ++x) {
            int bow = KnifeModels.katanaBow(Math.min(x, 48));
            boolean mouth = x <= 2;
            int low = mouth ? bow - 4 : bow - 3;
            int high = mouth ? bow + 3 : bow + 2;
            int thick = mouth ? 2 : 1;
            for (int y = low; y <= high; ++y) {
                for (int z = -thick; z <= thick; ++z) {
                    boolean outside;
                    boolean bl = outside = y == low || y == high || Math.abs(z) == thick;
                    int ink = mouth || x >= 50 ? 8 : (x >= 12 && x <= 14 && outside ? ((x + y + z) % 2 == 0 ? 7 : 6) : 4);
                    v.set(x, y, z, ink, ink == 4 ? 0.9f + 0.1f * KnifeModels.grain(x, y, z) : KnifeModels.grain(x, y, z));
                }
            }
        }
        int knob = KnifeModels.katanaBow(16);
        v.set(16, knob + 3, 0, 8, 1.0f);
        v.set(17, knob + 3, 0, 8, 1.0f);
        return v.mesh(1.0f, 51.0f);
    }

    static float katanaCentre(float x) {
        return 0.0016f * (x - 3.0f) * (x - 3.0f);
    }

    static int katanaBow(int x) {
        return Math.round(0.0016f * ((float)x - 3.0f) * ((float)x - 3.0f));
    }

    static KnifeMesh katana() {
        int z;
        int y;
        int x;
        KnifeMesh.Voxels v = new KnifeMesh.Voxels();
        for (x = -17; x <= -1; ++x) {
            for (int y2 = -1; y2 <= 1; ++y2) {
                for (int z2 = -1; z2 <= 1; ++z2) {
                    int ink;
                    if (x == -1) {
                        ink = 8;
                    } else if (x == -17) {
                        ink = y2 == 0 && z2 == 0 ? 8 : 5;
                    } else {
                        boolean face = Math.abs(y2) == 1 ^ Math.abs(z2) == 1;
                        boolean window = face && ((x + 20) % 4 == 1 || (x + 20) % 4 == 2);
                        ink = window ? 2 : 7;
                    }
                    v.set(x, y2, z2, ink, ink == 2 ? 0.82f : KnifeModels.grain(x, y2, z2));
                }
            }
        }
        for (y = -4; y <= 4; ++y) {
            for (z = -4; z <= 4; ++z) {
                float d = (float)Math.sqrt(y * y + z * z);
                if (d > 3.9f) continue;
                v.set(0, y, z, d >= 3.0f ? 8 : 5, KnifeModels.grain(0, y, z));
            }
        }
        for (y = -1; y <= 1; ++y) {
            for (z = -1; z <= 1; ++z) {
                v.set(1, y, z, 8, KnifeModels.grain(1, y, z));
            }
            v.set(2, y, 0, 8, KnifeModels.grain(2, y, 0));
        }
        for (x = 3; x <= 48; ++x) {
            int bow = KnifeModels.katanaBow(x);
            int lowest = x >= 48 ? bow + 1 : (x >= 46 ? bow : (x >= 44 ? bow - 1 : bow - 2));
            int highest = x >= 48 ? bow + 1 : bow + 1;
            for (int y3 = lowest; y3 <= highest; ++y3) {
                int ink = y3 == lowest ? 2 : (y3 == highest ? 1 : (y3 == lowest + 1 ? (x > 5 && Math.sin((double)x * 0.85) + Math.sin((double)x * 0.37) * 0.6 > 0.35 ? 2 : 0) : 0));
                v.set(x, y3, 0, ink, KnifeModels.grain(x, y3, 0));
            }
        }
        return v.mesh(3.0f, 49.0f);
    }
}
