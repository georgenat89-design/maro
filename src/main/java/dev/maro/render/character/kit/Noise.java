// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Noise {
    private static final int[] PERM;

    private Noise() {
    }

    public static float noise(float x, float y, float z) {
        int xi = Noise.floor(x);
        int yi = Noise.floor(y);
        int zi = Noise.floor(z);
        float xf = x - (float)xi;
        float yf = y - (float)yi;
        float zf = z - (float)zi;
        int X = xi & 0xFF;
        int Y = yi & 0xFF;
        int Z = zi & 0xFF;
        float u = Noise.fade(xf);
        float v = Noise.fade(yf);
        float w = Noise.fade(zf);
        int a = PERM[X] + Y;
        int aa = PERM[a] + Z;
        int ab = PERM[a + 1] + Z;
        int b = PERM[X + 1] + Y;
        int ba = PERM[b] + Z;
        int bb = PERM[b + 1] + Z;
        float x1 = Noise.lerp(Noise.grad(PERM[aa], xf, yf, zf), Noise.grad(PERM[ba], xf - 1.0f, yf, zf), u);
        float x2 = Noise.lerp(Noise.grad(PERM[ab], xf, yf - 1.0f, zf), Noise.grad(PERM[bb], xf - 1.0f, yf - 1.0f, zf), u);
        float y1 = Noise.lerp(x1, x2, v);
        float x3 = Noise.lerp(Noise.grad(PERM[aa + 1], xf, yf, zf - 1.0f), Noise.grad(PERM[ba + 1], xf - 1.0f, yf, zf - 1.0f), u);
        float x4 = Noise.lerp(Noise.grad(PERM[ab + 1], xf, yf - 1.0f, zf - 1.0f), Noise.grad(PERM[bb + 1], xf - 1.0f, yf - 1.0f, zf - 1.0f), u);
        return Noise.lerp(y1, Noise.lerp(x3, x4, v), w);
    }

    public static float noise(float x, float y) {
        return Noise.noise(x, y, 0.37f);
    }

    public static float fbm(float x, float y, float z, int octaves) {
        float sum = 0.0f;
        float amp = 0.5f;
        float norm = 0.0f;
        for (int i = 0; i < octaves; ++i) {
            sum += amp * Noise.noise(x, y, z);
            norm += amp;
            x = x * 2.03f + 17.1f;
            y = y * 2.03f - 4.7f;
            z = z * 2.03f + 9.3f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    public static float fbm(float x, float y, int octaves) {
        return Noise.fbm(x, y, 0.37f, octaves);
    }

    public static float hash(int x, int y, int z) {
        int h = x * 0x1F1F1F1F ^ y * 1540483477 ^ z * 668265263;
        h ^= h >>> 13;
        h *= -2048144789;
        h ^= h >>> 16;
        return (float)(h & 0xFFFF) / 65535.0f;
    }

    private static int floor(float v) {
        int i = (int)v;
        return v < (float)i ? i - 1 : i;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float grad(int hash, float x, float y, float z) {
        float u;
        int h = hash & 0xF;
        float f = u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    static {
        int i;
        PERM = new int[512];
        int[] p = new int[256];
        for (int i2 = 0; i2 < 256; ++i2) {
            p[i2] = i2;
        }
        long seed = 25214903917L;
        for (i = 255; i > 0; --i) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int)((seed >>> 33) % (long)(i + 1));
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (i = 0; i < 512; ++i) {
            Noise.PERM[i] = p[i & 0xFF];
        }
    }
}
