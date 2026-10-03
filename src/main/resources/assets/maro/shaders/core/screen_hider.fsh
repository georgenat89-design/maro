#version 330

// Screen Hider: blurs or pixelates a copy of the rendered world.
// vertexColor.r = strength (0..1), vertexColor.g > 0.5 = pixelate instead of blur.

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 texel = 1.0 / size;
    float strength = vertexColor.r;

    if (vertexColor.g > 0.5) {
        float block = 2.0 + strength * 40.0;
        vec2 cell = (floor(texCoord0 * size / block) + 0.5) * block;
        fragColor = vec4(texture(Sampler0, cell * texel).rgb, 1.0);
        return;
    }

    // spiral sampling with a per-pixel rotation: smooth, wide blur in a single pass
    const int SAMPLES = 80;
    float radius = 1.5 + strength * 48.0;
    float rotation = hash(gl_FragCoord.xy) * 6.2831853;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < SAMPLES; i++) {
        float f = (float(i) + 0.5) / float(SAMPLES);
        float angle = float(i) * 2.39996323 + rotation;
        vec2 offset = vec2(cos(angle), sin(angle)) * sqrt(f) * radius;
        float weight = exp(-2.0 * f);
        sum += texture(Sampler0, texCoord0 + offset * texel).rgb * weight;
        total += weight;
    }
    fragColor = vec4(sum / total, 1.0);
}
