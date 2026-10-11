#version 330

// Hand Shader: restyles the first-person hand and what it holds. The game clears depth just before
// drawing the hand, so u_Depth holds depth only where the hand is: depth below 1 is the hand.
// u_Before is the frame before the hand was drawn (the world behind it), u_After the frame with it.
// Glow.w picks the look: 0 Glow, 1 Rainbow, 2 Galaxy, 3 Hologram, 4 Chams, 5 Glass, 6 Flame.

uniform sampler2D u_Before;
uniform sampler2D u_After;
uniform sampler2D u_Depth;

in vec2 texCoord;
flat in float quadTag;

layout(std140) uniform HandData {
    vec4 Tint;   // rgb the colour, a how strongly the look replaces the hand
    vec4 Glow;   // x outline width in pixels, y glow strength, z time, w mode
    vec4 Extra;  // x rainbow outline, y one pixel across, z one pixel down, w natural fire colours
    vec4 Flame;  // x rise, y wobble, z length, w brightness (all 0 to 1)
};

out vec4 fragColor;

float handAt(vec2 uv) {
    return textureLod(u_Depth, uv, 0.0).r < 0.99999 ? 1.0 : 0.0;
}

// How much of this pixel is hand, smoothed over its neighbours (a 3x3 tent) so the edge is not jagged.
float coverage(vec2 uv, vec2 px) {
    float c = handAt(uv) * 4.0;
    c += (handAt(uv + vec2(px.x, 0.0)) + handAt(uv - vec2(px.x, 0.0)) + handAt(uv + vec2(0.0, px.y)) + handAt(uv - vec2(0.0, px.y))) * 2.0;
    c += handAt(uv + px) + handAt(uv - px) + handAt(uv + vec2(px.x, -px.y)) + handAt(uv + vec2(-px.x, px.y));
    return c / 16.0;
}

// The hand blurred over a disc of this many pixels: golden-angle spiral samples with a Gaussian
// fall-off. An average rather than a nearest hit, so the glow fades evenly with no rings or steps.
float blurred(vec2 uv, vec2 px, float radius, int samples) {
    float sum = 0.0, weight = 0.0;
    for (int i = 0; i < samples; i++) {
        float f = (float(i) + 0.5) / float(samples);
        float r = radius * sqrt(f);
        float a = float(i) * 2.39996323;
        float w = exp(-2.5 * f);
        sum += handAt(uv + vec2(cos(a), sin(a)) * r * px) * w;
        weight += w;
    }
    return sum / weight;
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

// Fire from cool to hot: deep red to orange to yellow to white, or the chosen colour to white.
vec3 fireRamp(float i) {
    i = clamp(i, 0.0, 1.0);
    if (Extra.w > 0.5) {
        vec3 c = mix(vec3(0.30, 0.02, 0.0), vec3(0.95, 0.22, 0.02), smoothstep(0.0, 0.35, i));
        c = mix(c, vec3(1.0, 0.62, 0.08), smoothstep(0.3, 0.65, i));
        return mix(c, vec3(1.0, 0.95, 0.78), smoothstep(0.68, 1.0, i));
    }
    vec3 c = mix(Tint.rgb * 0.22, Tint.rgb, smoothstep(0.0, 0.5, i));
    return mix(c, mix(Tint.rgb, vec3(1.0), 0.8), smoothstep(0.55, 1.0, i));
}

// How strongly flames burn here: the hand somewhere below (looking down the screen, swayed by the
// wobble) and how near it is, carved into flickering tongues by noise that rises with time. The
// screen's y runs upwards here (0 is the bottom), so below is -y and rising is towards +y.
float flames(vec2 uv, vec2 px, float t, out float heat) {
    float aspect = px.y / px.x;
    vec2 q = vec2(uv.x * aspect, uv.y);
    float rise = 0.25 + Flame.x * 2.0;
    float reach = 0.04 + Flame.z * 0.2;
    float sway = (noise(q * vec2(3.0, 1.6) - vec2(t * 0.35, t * rise * 0.9)) - 0.5) * (0.01 + Flame.y * 0.07);
    float n = fbm(q * vec2(7.0, 4.5) - vec2(sway * 6.0, t * rise * 1.4));
    // Down from here, nearest first: the first step that finds the hand says how far up the flame it is.
    float jitter = hash(uv * 913.7) * 0.8;
    float h = 0.0;
    for (int j = 0; j < 20; j++) {
        float f = (float(j) + jitter) / 20.0;
        if (handAt(uv + vec2(sway * f * 1.6, -f * reach)) > 0.5) {
            h = 1.0 - f;
            break;
        }
    }
    heat = n;
    // Stretched so the tongues reach most of the way up, then carved by the noise.
    float tall = pow(h, 0.6);
    return clamp(tall * 1.55 - (1.0 - n) * 1.0, 0.0, 1.0);
}

void main() {
    vec2 uv = texCoord;
    vec3 after = texture(u_After, uv).rgb;
    vec2 px = Extra.yz;
    float t = Glow.z;
    int mode = int(Glow.w + 0.5);
    float cover = coverage(uv, px);
    float lum = dot(after, vec3(0.299, 0.587, 0.114));
    // Screen gradients are taken in every pixel, before any branch, as GLSL asks.
    vec2 slope = vec2(dFdx(lum), dFdy(lum));

    vec3 rim = Extra.x > 0.5 ? hsv2rgb(vec3(fract(t * 0.15 + uv.x * 0.6 + uv.y * 0.3), 0.75, 1.0)) : Tint.rgb;

    vec3 color = after;
    if (cover > 0.0 && mode != 0) {
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
        } else if (mode == 6) {
            // Burning: glowing embers over the hand, brightest where it is lit.
            vec2 q = vec2(uv.x * (px.y / px.x), uv.y);
            float e = fbm(q * 9.0 - vec2(0.0, t * (0.25 + Flame.x * 2.0) * 1.2));
            fill = fireRamp(0.25 + e * 0.55 + lum * 0.3) * (0.45 + lum * 0.9) * (0.6 + Flame.w * 0.7);
        }
        color = mix(after, fill, Tint.a * cover);
    }

    // The outline: a thin clean line hugging the hand and a soft glow fading out from it, both
    // from blurred copies of the hand so they are smooth all the way round.
    if (Glow.x > 0.0 && Glow.y > 0.0 && cover < 1.0) {
        float lineR = clamp(Glow.x * 0.45, 1.25, 4.0);
        float line = smoothstep(0.04, 0.3, blurred(uv, px, lineR, 12));
        float halo = blurred(uv, px, Glow.x * 3.0, 32);
        float light = (line * 0.7 + halo * 1.25) * Glow.y;
        color += rim * light * (1.0 - cover);
    }

    // Flames rising off the hand, added over the world and over the hand's top edge.
    if (mode == 6) {
        float heat;
        float fire = flames(uv, px, t, heat);
        float bright = 0.5 + Flame.w * 1.3;
        // Kept below white-hot except at the brightest tongues, so the flames read as fire, not a glow.
        vec3 flame = fireRamp(fire * (0.55 + heat * 0.35)) * smoothstep(0.02, 0.35, fire) * bright * (0.4 + Tint.a);
        color += flame * (1.0 - cover * 0.65);
    }
    fragColor = vec4(color, 1.0);
}
