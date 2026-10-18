// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Lights {
    public static final Lights WORLD = new Lights(0.2f, 1.0f, -0.7f, -0.2f, 1.0f, 0.7f);
    private static final float AMBIENT = 0.4f;
    private static final float POWER = 0.6f;
    private static final float FLAT_0 = 0.853f;
    private static final float FLAT_1 = 0.273f;
    private static final float FLAT_2 = -0.126f;
    public final float x0;
    public final float y0;
    public final float z0;
    public final float x1;
    public final float y1;
    public final float z1;
    private final float dx;
    private final float dy;
    private final float dz;
    private final boolean opposite;

    public Lights(float x0, float y0, float z0, float x1, float y1, float z1) {
        float l0 = Lights.length(x0, y0, z0);
        float l1 = Lights.length(x1, y1, z1);
        if (l0 < 1.0E-6f || l1 < 1.0E-6f) {
            throw new IllegalArgumentException("light directions must not be zero");
        }
        this.x0 = x0 / l0;
        this.y0 = y0 / l0;
        this.z0 = z0 / l0;
        this.x1 = x1 / l1;
        this.y1 = y1 / l1;
        this.z1 = z1 / l1;
        float sx = this.x0 + this.x1;
        float sy = this.y0 + this.y1;
        float sz = this.z0 + this.z1;
        float sl = Lights.length(sx, sy, sz);
        this.opposite = sl < 0.25f;
        this.dx = this.opposite ? this.x0 : sx / sl;
        this.dy = this.opposite ? this.y0 : sy / sl;
        this.dz = this.opposite ? this.z0 : sz / sl;
    }

    public Lights rotateY(float degrees) {
        double a = Math.toRadians(degrees);
        float c = (float)Math.cos(a);
        float s = (float)Math.sin(a);
        return new Lights(c * this.x0 + s * this.z0, this.y0, c * this.z0 - s * this.x0, c * this.x1 + s * this.z1, this.y1, c * this.z1 - s * this.x1);
    }

    public float brightness(float nx, float ny, float nz) {
        float a = Math.max(0.0f, this.x0 * nx + this.y0 * ny + this.z0 * nz);
        float b = Math.max(0.0f, this.x1 * nx + this.y1 * ny + this.z1 * nz);
        return Math.min(1.0f, (a + b) * 0.6f + 0.4f);
    }

    public void flatten(float[] normal, int offset, float amount) {
        if (!(amount > 0.0f)) {
            return;
        }
        float k = Math.min(1.0f, amount);
        float nx = normal[offset];
        float ny = normal[offset + 1];
        float nz = normal[offset + 2];
        float ex = this.dx;
        float ey = this.dy;
        float ez = this.dz;
        float c = nx * ex + ny * ey + nz * ez;
        if (this.opposite && c < 0.0f) {
            ex = -ex;
            ey = -ey;
            ez = -ez;
            c = -c;
        }
        float target = c + k * (0.853f + (0.273f + -0.126f * c) * c - c);
        float wx = nx - c * ex;
        float wy = ny - c * ey;
        float wz = nz - c * ez;
        float wl = wx * wx + wy * wy + wz * wz;
        if (wl < 1.0E-10f) {
            if (c > 0.0f) {
                return;
            }
            if (Math.abs(ex) < 0.9f) {
                wx = 0.0f;
                wy = ez;
                wz = -ey;
            } else {
                wx = ey;
                wy = -ex;
                wz = 0.0f;
            }
            wl = wx * wx + wy * wy + wz * wz;
        }
        float s = (float)Math.sqrt(Math.max(0.0f, 1.0f - target * target) / wl);
        normal[offset] = ex * target + wx * s;
        normal[offset + 1] = ey * target + wy * s;
        normal[offset + 2] = ez * target + wz * s;
    }

    private static float length(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }
}
