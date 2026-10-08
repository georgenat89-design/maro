#version 330

// Block ESP tracers, worked out exactly for each pixel from the distance to the line.
//
// Each one is a laser: a white-hot core inside a line in the block's colour inside a soft halo,
// with packets of light shooting along it to the block like comets, a sharp head and a long tail.
// It fades in over the first stretch from the start so the crosshair stays clear, and where it
// ends a glowing marker sits with a ring rippling out from it. Nearer blocks get fuller, wider
// lines. With Style.w set only the bright core and packets are drawn, for the bloom pass to spread.

in vec2 texCoord;
flat in float quadTag;

const int MAX_TRACERS = 64;

// Only vec4s, so std140 lays it out exactly as BlockEspRenderer writes it.
layout(std140) uniform TracerData {
    vec4 Info;    // x tracer count, y line width (px), z halo strength, w seconds
    vec4 Style;   // x packets on, y packet speed, z start fade length (px), w 1 = core only (bloom source)
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

    float width = Info.y * mix(0.6, 1.0, weight);
    float halfWidth = width * 0.5;
    float core = falloff(halfWidth - 0.5, halfWidth + 0.5, d);
    float hot = falloff(0.0, max(halfWidth * 0.6, 0.7), d);
    float sigma = width * 1.8 + 2.0;
    float halo = exp(-0.5 * d * d / (sigma * sigma));

    float fadeIn = smoothstep(Style.z * 0.25, Style.z, along);
    // Brighter towards the block, so the eye follows each line out to what it points at.
    float toward = mix(0.7, 1.0, along / len);

    // Comets: one every 140 px, moving to the block. s is how far ahead of the head this pixel is.
    float packet = 0.0;
    if (Style.x > 0.5) {
        float spacing = 140.0;
        float s = (fract(along / spacing - Info.w * Style.y * 1.4) - 0.5) * spacing;
        float spread = s > 0.0 ? 5.0 : 34.0;
        packet = exp(-0.5 * s * s / (spread * spread)) * fadeIn;
    }

    // The marker at the block: a bright dot, a halo, and a ring rippling out once a second.
    float de = length(px - b);
    float dotRadius = width * 1.6 + 0.8;
    float marker = falloff(dotRadius - 0.5, dotRadius + 0.5, de);
    float ripple = fract(Info.w * 0.9);
    float ringRadius = dotRadius + 2.0 + ripple * 11.0 * mix(0.7, 1.0, weight);
    float ring = falloff(0.5, 1.3, abs(de - ringRadius)) * (1.0 - ripple);
    float markerHalo = exp(-0.5 * de * de / (sigma * sigma * 2.0));

    vec3 bright = mix(color, vec3(1.0), 0.75);

    if (Style.w > 0.5) {
        float source = (core * (0.65 + 0.9 * packet) + packet * 0.35) * fadeIn * toward + marker;
        source = clamp(source, 0.0, 1.0);
        if (source <= 0.002) discard;
        fragColor = vec4(mix(color, bright, packet * 0.5), source);
        return;
    }

    float lineAlpha = core * mix(0.75, 1.0, weight) * toward;
    float haloAlpha = halo * Info.z * 0.45 * weight;
    float packetAlpha = packet * (core + halo * (0.35 + Info.z * 0.6));
    float alpha = (max(lineAlpha, haloAlpha) + packetAlpha) * fadeIn;
    alpha += marker + ring * 0.85 * weight + markerHalo * Info.z * 0.5;
    alpha = clamp(alpha, 0.0, 1.0);
    if (alpha <= 0.002) discard;

    vec3 shade = mix(color, bright, clamp(hot * 0.8 + packet * 0.7 + marker * 0.6, 0.0, 1.0));
    fragColor = vec4(shade, alpha);
}
