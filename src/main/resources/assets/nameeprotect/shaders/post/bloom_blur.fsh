#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Direction is per pass, radius per level; both are baked in when the chain is
// built, because neither depends on anything the player can change.
layout(std140) uniform BloomBlurConfig {
    float DirX;
    float DirY;
    float Radius;
    float Taps;
};

out vec4 fragColor;

// A fixed bound with a break rather than a loop that runs to a uniform. Both are
// legal in GLSL 330, but a constant bound is unrolled by every driver, and a
// uniform bound is the kind of thing an older one quietly miscompiles.
const int MAX_TAPS = 12;

void main() {
    // One step is one texel of the TARGET. When this pass is also the downsample -
    // it reads the level above at twice the size, with bilinear filtering on -
    // stepping in the smaller grid is what makes the blur reach twice as far
    // across the screen for the same cost. That is the whole point of the pyramid.
    vec2 step = vec2(DirX, DirY) * Radius / OutSize;

    int taps = int(Taps);

    vec3 sum = texture(InSampler, texCoord).rgb;
    float total = 1.0;

    for (int i = 1; i <= MAX_TAPS; i++) {
        if (i > taps) break;

        // A gaussian falling to about 5% at the last tap, so the kernel is not
        // cut off with a visible step at its edge.
        float x = float(i) / float(taps);
        float weight = exp(-3.0 * x * x);

        sum += texture(InSampler, texCoord + step * float(i)).rgb * weight;
        sum += texture(InSampler, texCoord - step * float(i)).rgb * weight;
        total += 2.0 * weight;
    }

    fragColor = vec4(sum / total, 1.0);
}
