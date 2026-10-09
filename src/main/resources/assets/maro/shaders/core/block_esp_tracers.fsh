#version 330

// Block ESP tracers, worked out exactly for each pixel from the distance to the line.
//
// Clean lines: each is a crisp anti-aliased line in its block's colour with a soft glow round it,
// starting just clear of the crosshair, and a small dot where it ends. Optionally, pulses of light
// travel along it to the block. Nearer blocks get fuller, wider lines. With Style.w set only the
// line and its dot are drawn, for the bloom pass to spread.

in vec2 texCoord;
flat in float quadTag;

// Tracers in one draw: more are drawn in further batches.
const int MAX_TRACERS = 128;

// Only vec4s, so std140 lays it out exactly as BlockEspRenderer writes it.
layout(std140) uniform TracerData {
    vec4 Info;    // x tracer count, y line width (px), z glow strength, w seconds
    vec4 Style;   // x pulses on, y pulse speed, z gap at the start (px), w 1 = line only (bloom source)
    vec4 Lines[MAX_TRACERS];   // x0, y0, x1, y1 in framebuffer pixels: from the start to the block
    vec4 Colors[MAX_TRACERS];  // rgb, a weight: 1 near, less far away
};

out vec4 fragColor;

// 1 at d <= inner, 0 at d >= outer, smooth between.
float falloff(float inner, float outer, float d) {
    return 1.0 - smoothstep(inner, outer, d);
}

void main() {
    int index = int(quadTag - 0.5);
    if (index < 0 || index >= MAX_TRACERS || float(index) >= Info.x) discard;

    vec4 line = Lines[index];
    vec3 color = Colors[index].rgb;
    float weight = Colors[index].a;
    vec2 a = line.xy;
    vec2 b = line.zw;
    vec2 px = gl_FragCoord.xy;

    vec2 ab = b - a;
    float len = max(length(ab), 0.001);
    vec2 dir = ab / len;
    float along = clamp(dot(px - a, dir), 0.0, len);
    float d = length(px - (a + dir * along));

    float width = Info.y * mix(0.7, 1.0, weight);
    float halfWidth = width * 0.5;
    float core = falloff(halfWidth - 0.6, halfWidth + 0.6, d);
    float sigma = width * 1.4 + 1.5;
    float glow = exp(-0.5 * d * d / (sigma * sigma));

    // A short gap at the crosshair so the lines do not pile up on it, then full strength.
    float start = smoothstep(Style.z * 0.5, Style.z, along);

    float pulse = 0.0;
    if (Style.x > 0.5) {
        float spacing = 160.0;
        float s = (fract(along / spacing - Info.w * Style.y * 1.2) - 0.5) * spacing;
        float spread = s > 0.0 ? 6.0 : 30.0;
        pulse = exp(-0.5 * s * s / (spread * spread));
    }

    // The dot at the block.
    float de = length(px - b);
    float dotRadius = width * 1.25 + 0.6;
    float marker = falloff(dotRadius - 0.6, dotRadius + 0.6, de);

    if (Style.w > 0.5) {
        float source = clamp(core * start * (0.75 + 0.5 * pulse) + marker, 0.0, 1.0);
        if (source <= 0.002) discard;
        fragColor = vec4(color, source);
        return;
    }

    float lineAlpha = core * mix(0.8, 1.0, weight);
    float glowAlpha = glow * Info.z * 0.35 * weight;
    float alpha = (max(lineAlpha, glowAlpha) + pulse * core * 0.6) * start;
    alpha = clamp(alpha + marker + falloff(dotRadius, dotRadius * 3.0, de) * Info.z * 0.25, 0.0, 1.0);
    if (alpha <= 0.002) discard;

    // The block's own colour, a touch lighter where a pulse passes.
    fragColor = vec4(mix(color, vec3(1.0), pulse * 0.45 * core), alpha);
}
