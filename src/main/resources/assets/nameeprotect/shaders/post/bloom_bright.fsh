#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Threshold and knee are fixed - tuned once, in Bloom.java, and baked into the
// chain when it is built. They are uniforms rather than constants here only so
// that the one place they are written down is the Java, not two places.
layout(std140) uniform BloomBrightConfig {
    float Threshold;
    float Knee;
    float Spare0;
    float Spare1;
};

// Rec. 709 luma. A plain channel average would let a saturated blue through at
// the same rate as a white of the same numbers, and blues would bloom wrongly.
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

out vec4 fragColor;

/**
 * What survives the threshold, with the soft knee.
 *
 * Rather than keeping a pixel whole or dropping it whole - which flickers as a
 * brightness crosses the line frame to frame - the region a knee wide either
 * side of the threshold is a quadratic ramp, so a pixel fades into the bloom.
 */
vec3 kept(vec2 at) {
    vec3 colour = texture(InSampler, at).rgb;
    float bright = dot(colour, LUMA);

    float knee = max(Knee, 0.0001);
    float soft = clamp(bright - Threshold + knee, 0.0, 2.0 * knee);

    soft = soft * soft / (4.0 * knee);

    return colour * max(max(soft, bright - Threshold) / max(bright, 0.0001), 0.0);
}

void main() {
    // This pass is also the first halving of resolution, so it takes four taps a
    // quarter of an output texel out and averages them. Thresholding each tap
    // before the average, rather than averaging first, is what keeps a single
    // bright pixel - a distant torch, a star - from being averaged away below the
    // threshold and never blooming at all.
    vec2 offset = 0.25 / OutSize;

    vec3 sum = kept(texCoord + vec2(-offset.x, -offset.y))
             + kept(texCoord + vec2( offset.x, -offset.y))
             + kept(texCoord + vec2(-offset.x,  offset.y))
             + kept(texCoord + vec2( offset.x,  offset.y));

    fragColor = vec4(sum * 0.25, 1.0);
}
