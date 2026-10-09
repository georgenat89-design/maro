#version 330

// Hand Shader: restyles the first-person hand and what it holds. The game clears depth just before
// drawing the hand, so u_Depth holds depth only where the hand is: depth below 1 is the hand.
// u_Before is the frame before the hand was drawn (the world behind it), u_After the frame with it.
// Glow.w picks the look: 0 Glow, 1 Rainbow, 2 Galaxy, 3 Hologram, 4 Chams, 5 Glass.

uniform sampler2D u_Before;
uniform sampler2D u_After;
uniform sampler2D u_Depth;

in vec2 texCoord;
flat in float quadTag;

layout(std140) uniform HandData {
    vec4 Tint;   // rgb the colour, a how strongly the look replaces the hand
    vec4 Glow;   // x outline width in pixels, y glow strength, z time, w mode
    vec4 Extra;  // x rainbow outline, y one pixel across, z one pixel down, w unused
};

out vec4 fragColor;

float handAt(vec2 uv) {
    return texture(u_Depth, uv).r < 0.99999 ? 1.0 : 0.0;
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = texCoord;
    vec3 after = texture(u_After, uv).rgb;
    float inHand = handAt(uv);
    float t = Glow.z;
    int mode = int(Glow.w + 0.5);
    vec2 px = Extra.yz;
    float lum = dot(after, vec3(0.299, 0.587, 0.114));
    // Screen gradients are taken in every pixel, before any branch, as GLSL asks.
    vec2 slope = vec2(dFdx(lum), dFdy(lum));

    // How close the hand is: the nearest ring of samples round this pixel that touches it.
    float near = 0.0;
    if (Glow.x > 0.0 && Glow.y > 0.0) {
        for (int ring = 1; ring <= 3; ring++) {
            float r = Glow.x * float(ring) / 3.0;
            float weight = 1.0 - float(ring - 1) / 3.0;
            for (int i = 0; i < 12; i++) {
                float a = 6.2831853 * (float(i) + 0.5 * float(ring)) / 12.0;
                near = max(near, handAt(uv + vec2(cos(a), sin(a)) * r * px) * weight);
            }
        }
    }
    vec3 rim = Extra.x > 0.5 ? hsv2rgb(vec3(fract(t * 0.15 + uv.x * 0.6 + uv.y * 0.3), 0.75, 1.0)) : Tint.rgb;

    vec3 color = after;
    if (inHand > 0.5 && mode != 0) {
        vec3 fill = after;
        if (mode == 1) {
            fill = hsv2rgb(vec3(fract(t * 0.2 + uv.x * 0.8 - uv.y * 0.4), 0.8, 1.0)) * (0.45 + lum * 0.9);
        } else if (mode == 2) {
            vec2 p = vec2(uv.x * (px.y / px.x), uv.y) * 3.0;
            float n = fbm(p + vec2(t * 0.05, t * 0.03));
            float m = fbm(p * 1.7 - vec2(t * 0.04, -t * 0.02));
            vec3 nebula = mix(vec3(0.03, 0.01, 0.10), mix(vec3(0.50, 0.12, 0.80), vec3(0.10, 0.62, 0.95), m), n);
            nebula = mix(nebula, Tint.rgb, 0.18);
            vec2 cell = floor(p * 40.0);
            float star = step(0.975, hash(cell)) * (0.55 + 0.45 * sin(t * 3.0 + hash(cell + 3.1) * 40.0));
            fill = nebula * (0.55 + lum * 0.9) + vec3(star);
        } else if (mode == 3) {
            vec3 behind = texture(u_Before, uv).rgb;
            float scan = 0.6 + 0.4 * sin(uv.y / px.y * 1.4 - t * 6.0);
            float flicker = 0.93 + 0.07 * sin(t * 37.0);
            vec3 holo = Tint.rgb * (0.35 + lum * 1.2) * scan * flicker;
            fill = mix(behind, holo, 0.6) + Tint.rgb * 0.06;
        } else if (mode == 4) {
            fill = Tint.rgb * (0.30 + lum * 0.9);
        } else if (mode == 5) {
            // The world behind, bent by how the hand's shading changes: 30 pixels per unit of slope.
            vec3 behind = texture(u_Before, uv + slope * 30.0 * px).rgb;
            fill = mix(behind, Tint.rgb, 0.12) + vec3(pow(lum, 3.0) * 0.45);
        }
        color = mix(after, fill, Tint.a);
    }
    // Light round the hand, strongest at its edge and fading out.
    color += rim * (1.0 - inHand) * near * Glow.y;
    fragColor = vec4(color, 1.0);
}
