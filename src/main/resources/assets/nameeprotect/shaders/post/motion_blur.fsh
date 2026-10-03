#version 330

// Motion blur, in two parts:
//
//  1. Each pixel is smeared along the way it has moved on the screen since the
//     last frame - worked out from its depth and the two frames' matrices, so
//     a turn smears sideways, a fall smears downwards, and a wall you walk
//     past smears more the nearer it is. That is what makes the blur smooth: a
//     spread of samples along the motion, not one frame laid over another.
//
//  2. That smeared frame is then mixed with the frame shown before it, a
//     little, so the trail carries on across frames.

uniform sampler2D CurrentSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HistorySampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 CurrentSize;
    vec2 DepthSize;
    vec2 HistorySize;
};

layout(std140) uniform MotionBlurConfig {
    mat4 InvViewProj;   // this frame's clip space back to camera-relative world
    mat4 PrevViewProj;  // last frame's camera-relative world to clip space
    vec4 CameraDelta;   // how far the camera moved since last frame, world units
    float Alpha;        // share of this frame in the mix with the last: 1 - exp(-dt / tau)
    float Scale;        // the smear is the motion over the last frame, times this (shutter / dt)
    float Seed;         // moves the dither each frame
    float MaxLength;    // the longest smear, as a fraction of the screen
};

out vec4 fragColor;

const int SAMPLES = 12;

// A 4x4 ordered (Bayer) pattern for this pixel, in [0, 1), shifted by the seed.
float bayer(vec2 pixel) {
    ivec2 p = ivec2(mod(pixel + vec2(Seed * 16.0, Seed * 8.0), 4.0));
    int m[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);

    return float(m[p.x + p.y * 4]) / 16.0;
}

void main() {
    // Where this pixel was last frame. Its depth places it in the world;
    // last frame's matrices place that point on last frame's screen.
    float depth = texture(DepthSampler, texCoord).r;
    vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 world = InvViewProj * clip;
    world /= world.w;

    vec4 prevClip = PrevViewProj * vec4(world.xyz + CameraDelta.xyz, 1.0);
    vec2 prevUv = prevClip.xy / prevClip.w * 0.5 + 0.5;

    // The smear: that motion, scaled to the shutter time, and no longer than
    // MaxLength. A point that was behind the camera last frame (w < 0) has
    // no sensible place there, so it gets no smear.
    vec2 smear = prevClip.w > 0.0 ? (texCoord - prevUv) * Scale : vec2(0.0);
    float len = length(smear);

    if (len > MaxLength) smear *= MaxLength / len;

    // Samples along the smear, centred on the pixel, each pixel's row of
    // samples shifted by a different fraction so the steps do not line up
    // into visible bands.
    float jitter = bayer(gl_FragCoord.xy) - 0.5;
    vec3 sum = vec3(0.0);

    for (int i = 0; i < SAMPLES; i++) {
        float t = (float(i) + 0.5 + jitter) / float(SAMPLES) - 0.5;
        sum += texture(CurrentSampler, clamp(texCoord + smear * t, vec2(0.0), vec2(1.0))).rgb;
    }

    vec3 current = sum / float(SAMPLES);
    vec3 history = texture(HistorySampler, texCoord).rgb;
    vec3 mixed = mix(history, current, Alpha);

    // Stored in 8 bits a channel: a little noise before the rounding, so a
    // slow fade keeps fading instead of settling into bands.
    float dither = (bayer(gl_FragCoord.xy + 2.0) - 0.5) / 255.0;

    fragColor = vec4(clamp(mixed + dither, 0.0, 1.0), 1.0);
}
