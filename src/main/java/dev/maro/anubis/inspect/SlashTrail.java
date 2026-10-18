// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.anubis.inspect;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;

@Environment(value=EnvType.CLIENT)
final class SlashTrail {
    private static final int CAPACITY = 96;
    private static final long GAP_NANOS = 100000000L;
    private static final float SLOW = 0.6f;
    private static final float FAST = 2.8f;
    private static final float ALPHA = 0.6f;
    private static final float BASE_ALPHA = 0.12f;
    private final float[] points = new float[576];
    private final long[] times = new long[96];
    private int newest = -1;
    private int size;

    SlashTrail() {
    }

    void add(long now, float[] sample, int from) {
        this.newest = (this.newest + 1) % 96;
        System.arraycopy(sample, from, this.points, this.newest * 6, 6);
        this.times[this.newest] = now;
        this.size = Math.min(this.size + 1, 96);
    }

    void clear() {
        this.size = 0;
        this.newest = -1;
    }

    boolean isEmpty(long now, long lifetime) {
        return this.size == 0 || now - this.times[this.newest] > lifetime;
    }

    void emit(MatrixStack.Entry pose, VertexConsumer out, long now, long lifetime, int rgb, float strength) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int k = 0;
        while (k + 1 < this.size) {
            float dz;
            float dy;
            int o;
            int n;
            float dx;
            float speed;
            float fast;
            int newer = Math.floorMod(this.newest - k, 96);
            int older = Math.floorMod(this.newest - k - 1, 96);
            long ageNewer = now - this.times[newer];
            long ageOlder = now - this.times[older];
            if (ageOlder > lifetime) break;
            long dt = this.times[newer] - this.times[older];
            if (dt > 0L && dt <= 100000000L && !((fast = MathHelper.clamp((float)(((speed = MathHelper.sqrt((float)((dx = this.points[(n = newer * 6) + 3] - this.points[(o = older * 6) + 3]) * dx + (dy = this.points[n + 4] - this.points[o + 4]) * dy + (dz = this.points[n + 5] - this.points[o + 5]) * dz)) / ((float)dt / 1.0E9f)) - 0.6f) / 2.1999998f), (float)0.0f, (float)1.0f)) <= 0.0f)) {
                int v;
                float aNewer = SlashTrail.fade(ageNewer, lifetime) * fast * strength;
                float aOlder = SlashTrail.fade(ageOlder, lifetime) * fast * strength;
                float[] quad = new float[]{this.points[o], this.points[o + 1], this.points[o + 2], aOlder * 0.12f, this.points[o + 3], this.points[o + 4], this.points[o + 5], aOlder, this.points[n + 3], this.points[n + 4], this.points[n + 5], aNewer, this.points[n], this.points[n + 1], this.points[n + 2], aNewer * 0.12f};
                for (v = 0; v < 4; ++v) {
                    SlashTrail.vertex(pose, out, quad, v, r, g, b);
                }
                for (v = 3; v >= 0; --v) {
                    SlashTrail.vertex(pose, out, quad, v, r, g, b);
                }
            }
            ++k;
        }
    }

    private static float fade(long age, long lifetime) {
        float left = 1.0f - MathHelper.clamp((float)((float)age / (float)lifetime), (float)0.0f, (float)1.0f);
        return left * left * 0.6f;
    }

    private static void vertex(MatrixStack.Entry pose, VertexConsumer out, float[] quad, int v, int r, int g, int b) {
        int i = v * 4;
        out.vertex(pose, quad[i], quad[i + 1], quad[i + 2]).color(r, g, b, Math.round(MathHelper.clamp((float)quad[i + 3], (float)0.0f, (float)1.0f) * 255.0f)).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(pose, 0.0f, 0.0f, 1.0f);
    }

    static void sparkle(MatrixStack.Entry pose, VertexConsumer out, float x, float y, float z, float size, float turn, int rgb, float alpha) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int core = Math.round(MathHelper.clamp((float)alpha, (float)0.0f, (float)1.0f) * 255.0f);
        for (int arm = 0; arm < 4; ++arm) {
            float angle = turn + (float)arm * 1.5707964f;
            float cos = MathHelper.cos((double)angle);
            float sin = MathHelper.sin((double)angle);
            float length = size * (arm % 2 == 0 ? 1.0f : 0.7f);
            float width = size * 0.16f;
            float tx = x + cos * length;
            float ty = y + sin * length;
            float lx = x - sin * width;
            float ly = y + cos * width;
            float rx = x + sin * width;
            float ry = y - cos * width;
            SlashTrail.star(pose, out, x, y, z, r, g, b, core);
            SlashTrail.star(pose, out, lx, ly, z, r, g, b, core / 3);
            SlashTrail.star(pose, out, tx, ty, z, r, g, b, 0);
            SlashTrail.star(pose, out, rx, ry, z, r, g, b, core / 3);
            SlashTrail.star(pose, out, rx, ry, z, r, g, b, core / 3);
            SlashTrail.star(pose, out, tx, ty, z, r, g, b, 0);
            SlashTrail.star(pose, out, lx, ly, z, r, g, b, core / 3);
            SlashTrail.star(pose, out, x, y, z, r, g, b, core);
        }
    }

    private static void star(MatrixStack.Entry pose, VertexConsumer out, float x, float y, float z, int r, int g, int b, int a) {
        out.vertex(pose, x, y, z).color(r, g, b, a).texture(0.5f, 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(pose, 0.0f, 0.0f, 1.0f);
    }
}
