#version 330

// Player ESP composite.
//
// u_Mask holds this frame's player silhouettes, supersampled (GlowParams.w mask texels per screen
// pixel) and read with linear filtering: alpha is how much of a screen pixel the players cover,
// rgb (premultiplied) is their colour. This pass turns that mask into the visible effect: an
// animated fill inside, an anti-aliased contour around it and an optional soft glow beyond. Edge
// distances are estimated to sub-pixel accuracy from that coverage, so the contour follows the
// model's real outline smoothly instead of stepping along the pixel staircase.

uniform sampler2D u_Mask;

in vec2 texCoord;

// Seven vec4s and nothing else, so std140 lays it out exactly as PlayerEspRenderer writes it.
layout(std140) uniform EspData {
    vec4 FillColorA;    // rgb
    vec4 FillColorB;    // rgb
    vec4 OutlineColor;  // rgb
    vec4 Timing;        // x seconds, y speed, z pattern scale, w star density
    vec4 FillParams;    // x style, y opacity, z edge fade, w enabled
    vec4 LineParams;    // x colour mode, y width (px), z opacity, w enabled
    vec4 GlowParams;    // x enabled, y radius (px), z strength, w mask texels per screen pixel
};

out vec4 fragColor;

const float TAU = 6.28318530718;

// ---- noise -----------------------------------------------------------------------------------

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    mat2 turn = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p);
        p = turn * p * 2.03 + vec2(17.1, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// 1 at d <= inner, 0 at d >= outer, smooth between. smoothstep itself is undefined when its first
// edge is the larger one, so falloffs are always written through this.
float falloff(float inner, float outer, float d) {
    return 1.0 - smoothstep(inner, outer, d);
}

// One layer of stars: at most one jittered point per cell, most cells empty, each twinkling on its
// own phase. Neighbouring cells are checked so a star's halo is never cut off at a cell border.
float stars(vec2 px, float cell, float density, float t) {
    vec2 grid = px / cell;
    vec2 id = floor(grid);
    vec2 f = grid - id;
    float light = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y));
            vec2 cid = id + o;
            float h = hash12(cid);
            if (h > density) continue;
            float d = length((f - o - hash22(cid)) * cell);
            float size = mix(0.5, 1.6, hash12(cid + 7.0));
            float twinkle = 0.55 + 0.45 * sin(t * mix(1.2, 3.8, hash12(cid + 3.0)) + h * 61.0);
            light += twinkle * (falloff(size * 0.35, size + 0.9, d) + 0.25 * falloff(0.0, size * 5.0, d));
        }
    }
    return light;
}

// ---- fills -----------------------------------------------------------------------------------

vec3 galaxy(vec2 p, vec2 px, float t, float density) {
    vec2 q = p * 0.9;
    float clouds = fbm(q + vec2(t * 0.05, -t * 0.03));
    float wisps = fbm(q * 1.8 + vec2(clouds * 2.0) - vec2(t * 0.04, t * 0.07));

    vec3 color = vec3(0.012, 0.004, 0.04);
    color = mix(color, FillColorA.rgb * 0.75, smoothstep(0.30, 0.80, clouds));
    color = mix(color, FillColorB.rgb, smoothstep(0.50, 0.95, wisps) * 0.85);
    // Hot cores where both layers are dense.
    color += mix(FillColorA.rgb, vec3(1.0), 0.5) * pow(clamp(clouds * wisps * 1.9, 0.0, 1.0), 4.0) * 0.9;
    // Dust lanes.
    color *= 0.75 + 0.5 * fbm(q * 6.0 + t * 0.02);

    // Two drifting star layers at different depths.
    float s = stars(px + vec2(t * 6.0, t * 2.0), 22.0, density, t)
            + 1.3 * stars(px + vec2(t * 2.5, -t * 1.0) + 311.0, 47.0, density * 0.6, t * 0.8);
    return color + vec3(0.90, 0.92, 1.0) * s;
}

vec3 aurora(vec2 p, float t) {
    float wave = fbm(vec2(p.x * 0.8 + t * 0.15, t * 0.05));
    float band = p.y * 1.2 + wave * 2.2 - t * 0.25;
    vec3 green = vec3(0.10, 1.00, 0.55);
    vec3 cyan = vec3(0.10, 0.75, 1.00);
    vec3 violet = vec3(0.60, 0.20, 1.00);
    vec3 color = mix(green, cyan, 0.5 + 0.5 * sin(band * 2.0));
    color = mix(color, violet, smoothstep(0.55, 1.0, 0.5 + 0.5 * sin(band * 1.3 + 2.0)));
    float curtain = 0.55 + 0.45 * sin(p.x * 3.0 + wave * 6.0 + t * 0.7);
    color *= 0.5 + 0.7 * curtain;
    return color * (0.75 + 0.35 * noise(vec2(p.x * 12.0, t * 0.5)));
}

vec3 plasma(vec2 p, float t) {
    vec2 q = p * 3.0;
    float v = sin(q.x + t)
            + sin((q.y + t) * 0.8)
            + sin((q.x + q.y + t) * 0.6)
            + sin(length(q - 3.0 * vec2(sin(t * 0.3), cos(t * 0.4))) - t);
    return hsv2rgb(vec3(fract(v * 0.12 + t * 0.03), 0.8, 1.0));
}

vec3 lava(vec2 p, float t) {
    vec2 q = p * 1.4;
    float n = fbm(q + vec2(0.0, -t * 0.25) + fbm(q * 1.7 + t * 0.1) * 1.3);
    vec3 color = mix(vec3(0.08, 0.0, 0.0), vec3(0.90, 0.12, 0.02), smoothstep(0.25, 0.55, n));
    color = mix(color, vec3(1.0, 0.55, 0.05), smoothstep(0.50, 0.75, n));
    return mix(color, vec3(1.0, 0.95, 0.60), smoothstep(0.72, 0.90, n));
}

vec3 hologram(vec2 p, vec2 px, float t) {
    float scan = 0.65 + 0.35 * sin(px.y * 1.6 - t * 8.0);
    float sweep = falloff(0.0, 0.06, abs(fract(p.y * 0.25 - t * 0.3) - 0.5));
    float flicker = 0.92 + 0.08 * noise(vec2(t * 20.0, 0.0));
    return FillColorA.rgb * scan * flicker + sweep * 0.6 * vec3(0.7, 1.0, 1.0);
}

vec3 fillColor(int style, vec2 p, vec2 px, float t, vec3 player) {
    if (style == 1) return player;
    if (style == 2) return mix(FillColorA.rgb, FillColorB.rgb, 0.5 + 0.5 * sin(p.y * 2.2 + p.x * 0.6 - t * 1.5));
    if (style == 3) return hsv2rgb(vec3(fract((p.x + p.y) * 0.18 - t * 0.12), 0.72, 1.0));
    if (style == 4) return galaxy(p, px, t, Timing.w);
    if (style == 5) return aurora(p, t);
    if (style == 6) return plasma(p, t);
    if (style == 7) return lava(p, t);
    if (style == 8) return hologram(p, px, t);
    return FillColorA.rgb;
}

// Non-premultiplied "src over dst".
vec4 over(vec4 dst, vec3 color, float alpha) {
    float a = alpha + dst.a * (1.0 - alpha);
    if (a <= 0.0) return vec4(0.0);
    return vec4((color * alpha + dst.rgb * dst.a * (1.0 - alpha)) / a, a);
}

// The mask at a screen-pixel offset: rgb un-premultiplied, a = coverage of that screen pixel.
vec4 maskAt(vec2 uv) {
    vec4 s = texture(u_Mask, uv);
    return vec4(s.a > 0.0 ? s.rgb / s.a : vec3(0.0), s.a);
}

void main() {
    float maskScale = max(GlowParams.w, 1.0);
    vec2 size = vec2(textureSize(u_Mask, 0)) / maskScale;
    vec2 texel = 1.0 / size;
    vec2 px = gl_FragCoord.xy;
    // Pattern space: proportional to screen height, so a pattern looks the same at any resolution,
    // and fine enough that a single player shows the pattern's detail rather than one flat patch.
    vec2 p = px / size.y * 14.0 * Timing.z;
    float t = Timing.x * Timing.y;

    bool fillOn = FillParams.w > 0.5;
    bool lineOn = LineParams.w > 0.5;
    bool glowOn = GlowParams.x > 0.5;
    float width = lineOn ? LineParams.y : 0.0;
    float edgeFade = fillOn ? FillParams.z : 0.0;

    vec4 center = maskAt(texCoord);
    bool inside = center.a >= 0.5;

    // Distance (in screen pixels) from this pixel to the silhouette, for pixels outside, and to
    // the clear area, for pixels inside. A sample's partial coverage moves its edge estimate by
    // the uncovered fraction, which is what makes the result sub-pixel smooth.
    int reach = int(ceil(width)) + 2;
    if (edgeFade > 0.0) reach = max(reach, 5);
    reach = min(reach, 9);

    float toInside = 1e4;
    float toOutside = 1e4;
    vec3 nearest = center.rgb;
    for (int y = -reach; y <= reach; y++) {
        for (int x = -reach; x <= reach; x++) {
            vec2 o = vec2(float(x), float(y));
            float d = length(o);
            if (d > float(reach) + 0.5) continue;
            vec4 s = maskAt(texCoord + o * texel);
            if (s.a > 0.0) {
                float di = d + 0.5 - s.a;
                if (di < toInside) {
                    toInside = di;
                    nearest = s.rgb;
                }
            }
            if (s.a < 1.0) toOutside = min(toOutside, d + s.a - 0.5);
        }
    }

    // Glow reaches further than the box search, so the remaining rings are walked sparsely.
    float glowDistance = toInside;
    if (glowOn && !inside && glowDistance > float(reach)) {
        int rings = int(ceil(GlowParams.y)) + 1;
        for (int r = reach + 1; r <= rings; r++) {
            bool found = false;
            float twist = (r % 2 == 0) ? 0.5 : 0.0;
            for (int k = 0; k < 24; k++) {
                float angle = (float(k) + twist) * (TAU / 24.0);
                vec4 s = maskAt(texCoord + vec2(cos(angle), sin(angle)) * float(r) * texel);
                if (s.a > 0.0) {
                    glowDistance = float(r) + 0.5 - s.a;
                    nearest = s.rgb;
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
    }

    // Nothing within reach of this pixel: skip the pattern work entirely.
    if (center.a <= 0.0 && toInside > 1e3 && glowDistance > 1e3) discard;

    // Signed distance to the silhouette's edge: negative inside, positive outside.
    float sd = inside ? -max(toOutside, 0.0) : max(toInside, 0.0);

    // ---- fill: one pixel of anti-aliasing centred on the edge.
    float coverage = clamp(0.5 - sd, 0.0, 1.0);
    float fillAlpha = 0.0;
    if (fillOn) {
        float rim = falloff(0.0, float(reach), max(toOutside, 0.0));
        fillAlpha = coverage * FillParams.y * mix(1.0, mix(0.3, 1.0, rim), edgeFade);
    }

    int style = int(FillParams.x + 0.5);
    vec3 player = center.a > 0.0 ? center.rgb : nearest;
    vec3 fill = fillColor(style, p, px, t, player);

    // ---- contour colour.
    int lineMode = int(LineParams.x + 0.5);
    vec3 lineColor = OutlineColor.rgb;
    if (lineMode == 1) lineColor = player;
    else if (lineMode == 2) lineColor = hsv2rgb(vec3(fract((px.x + px.y) / size.y * 0.6 - t * 0.15), 0.7, 1.0));
    else if (lineMode == 3) lineColor = min(fill * 1.35 + 0.08, vec3(1.0));

    // ---- contour: starts just inside the edge, under the fill's anti-aliased rim, and ends
    // `width` pixels out, each side anti-aliased over one pixel.
    float lineAlpha = 0.0;
    if (lineOn) {
        lineAlpha = clamp(width + 0.5 - sd, 0.0, 1.0) * clamp(sd + 1.0, 0.0, 1.0) * LineParams.z;
    }

    // ---- glow: quadratic falloff away from the edge, outside the silhouette only.
    float glowAlpha = 0.0;
    if (glowOn && !inside && glowDistance < 1e3) {
        float k = 1.0 - clamp(max(glowDistance, 0.0) / max(GlowParams.y, 1.0), 0.0, 1.0);
        glowAlpha = k * k * GlowParams.z * (1.0 - coverage);
    }

    vec4 color = vec4(0.0);
    color = over(color, lineColor, clamp(glowAlpha, 0.0, 1.0));
    color = over(color, fill, clamp(fillAlpha, 0.0, 1.0));
    color = over(color, lineColor, clamp(lineAlpha, 0.0, 1.0));

    if (color.a <= 0.002) discard;
    fragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
}
