// Adapted from Anubis 0.9.9.1 by 4ldenz (GPL-3.0-only). See THIRD_PARTY_NOTICES.md.
package dev.maro.render.character.kit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class Sample {
    public float r;
    public float g;
    public float b;
    public float glow;
    public float flat;
    public float flatWeight;
    public float occlusion;
    public float occlusionWeight;

    public void set(int argb) {
        this.r = (float)(argb >> 16 & 0xFF) / 255.0f;
        this.g = (float)(argb >> 8 & 0xFF) / 255.0f;
        this.b = (float)(argb & 0xFF) / 255.0f;
        this.glow = 0.0f;
        this.flat = 0.0f;
        this.flatWeight = 0.0f;
        this.occlusion = 0.0f;
        this.occlusionWeight = 0.0f;
    }

    public void set(Sample o) {
        this.r = o.r;
        this.g = o.g;
        this.b = o.b;
        this.glow = o.glow;
        this.flat = o.flat;
        this.flatWeight = o.flatWeight;
        this.occlusion = o.occlusion;
        this.occlusionWeight = o.occlusionWeight;
    }

    public void mix(Sample o, float t) {
        float s = 1.0f - t;
        this.r = this.r * s + o.r * t;
        this.g = this.g * s + o.g * t;
        this.b = this.b * s + o.b * t;
        this.glow = this.glow * s + o.glow * t;
        this.flat = this.flat * s + o.flat * t;
        this.flatWeight = this.flatWeight * s + o.flatWeight * t;
        this.occlusion = this.occlusion * s + o.occlusion * t;
        this.occlusionWeight = this.occlusionWeight * s + o.occlusionWeight * t;
    }

    public void toward(int argb, float t) {
        float s = 1.0f - t;
        this.r = this.r * s + (float)(argb >> 16 & 0xFF) / 255.0f * t;
        this.g = this.g * s + (float)(argb >> 8 & 0xFF) / 255.0f * t;
        this.b = this.b * s + (float)(argb & 0xFF) / 255.0f * t;
    }

    public int argb() {
        return 0xFF000000 | Sample.channel(this.r) << 16 | Sample.channel(this.g) << 8 | Sample.channel(this.b);
    }

    float flat(float fallback) {
        return Sample.blend(fallback, this.flat, this.flatWeight);
    }

    float occlusion(float fallback) {
        return Sample.blend(fallback, this.occlusion, this.occlusionWeight);
    }

    private static float blend(float fallback, float weighted, float weight) {
        if (weight <= 1.0E-4f) {
            return fallback;
        }
        float w = Math.min(1.0f, weight);
        return Math.max(0.0f, Math.min(1.0f, fallback * (1.0f - w) + weighted * (w / weight)));
    }

    static int channel(float v) {
        int c = Math.round(v * 255.0f);
        return c < 0 ? 0 : Math.min(c, 255);
    }
}
