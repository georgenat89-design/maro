#version 330

// Hand Shader: restyles the first-person hand and what it holds. The game clears depth just before
// drawing the hand, so u_Depth holds depth only where the hand is: depth below 1 is the hand.
// u_Before is the frame before the hand was drawn (the world behind it), u_After the frame with it.
// Glow.w picks the look: 0 Glow, 1 Rainbow, 2 Galaxy, 3 Hologram, 4 Chams, 5 Glass, 6 Flame,
// 7 Lava, 8 Ice, 9 Electric, 10 Chrome, 11 Matrix.

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

// Cells: {distance to the nearest cell centre, distance to the edge between the two nearest}, and
// which cell it is, for cracks, facets and the like.
vec3 cells(vec2 p, out vec2 id) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    id = i;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = vec2(hash(i + g), hash(i + g + 17.3));
            float d = length(g + o - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = i + g;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return vec3(d1, d2 - d1, 0.0);
}

// A bright band sweeping across, as light catches a polished surface.
float sheen(vec2 uv, float t, float width) {
    float s = abs(fract(uv.x * 0.9 + uv.y * 0.55 - t * 0.3) - 0.5);
    return smoothstep(width, 0.0, s);
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
    float aspect = px.y / px.x;
    vec2 q = vec2(uv.x * aspect, uv.y);

    vec3 rim = Extra.x > 0.5 ? hsv2rgb(vec3(fract(t * 0.15 + uv.x * 0.6 + uv.y * 0.3), 0.75, 1.0)) : Tint.rgb;

    // How near the hand's own edge a pixel inside it is: lights the rim from within so every look
    // reads as a solid shape, not a flat cut-out.
    float edgeIn = 0.0;
    if (cover > 0.0) {
        float inner = blurred(uv, px, max(2.5, Glow.x * 0.7), 12);
        edgeIn = clamp((1.0 - inner) * 2.6, 0.0, 1.0) * cover;
    }

    vec3 color = after;
    if (cover > 0.0 && mode != 0) {
        vec3 fill = after;
        if (mode == 1) {
            // Rainbow: bands of colour flowing across the hand, with a sheen sweeping over them.
            float hue = fract(t * 0.25 + uv.x * 2.4 - uv.y * 1.6);
            fill = hsv2rgb(vec3(hue, 0.82, 1.0)) * (0.5 + lum * 0.85);
            fill += vec3(sheen(uv, t, 0.06) * 0.35) + hsv2rgb(vec3(fract(hue + 0.5), 0.6, 1.0)) * edgeIn * 0.45;
        } else if (mode == 2) {
            // Galaxy: two layers of nebula drifting at different depths, near and far stars twinkling.
            vec2 p = q * 3.0;
            float n = fbm(p + vec2(t * 0.05, t * 0.03));
            float m = fbm(p * 1.7 - vec2(t * 0.04, -t * 0.02));
            float k = fbm(p * 0.8 + vec2(-t * 0.02, t * 0.035) + 4.0);
            vec3 deep = vec3(0.02, 0.01, 0.07);
            vec3 nebula = mix(deep, mix(vec3(0.50, 0.12, 0.80), vec3(0.10, 0.62, 0.95), m), n);
            nebula = mix(nebula, vec3(0.95, 0.30, 0.65), smoothstep(0.55, 0.85, k) * 0.6);
            nebula = mix(nebula, Tint.rgb, 0.15);
            vec2 cell = floor(p * 40.0), far = floor(p * 90.0 + vec2(t * 0.6, 0.0));
            float star = step(0.975, hash(cell)) * (0.55 + 0.45 * sin(t * 3.0 + hash(cell + 3.1) * 40.0));
            float dust = step(0.985, hash(far)) * 0.45;
            fill = nebula * (0.55 + lum * 0.9) + vec3(star + dust) + Tint.rgb * edgeIn * 0.45;
        } else if (mode == 3) {
            // Hologram: see-through, bright scanlines rolling up, a flicker, slices that glitch sideways now and then.
            float slice = floor(uv.y / px.y / 6.0);
            float glitch = step(0.94, hash(vec2(slice, floor(t * 9.0)))) * (hash(vec2(slice, 7.0)) - 0.5) * 18.0 * px.x;
            vec3 behind = texture(u_Before, uv + vec2(glitch, 0.0)).rgb;
            float scan = 0.55 + 0.45 * smoothstep(0.35, 0.65, fract(uv.y / px.y / 3.0 + t * 2.0));
            float flicker = 0.9 + 0.1 * sin(t * 37.0) * sin(t * 13.0);
            vec3 holo = Tint.rgb * (0.55 + lum * 1.4) * scan * flicker + vec3(hash(uv * 300.0 + t) * 0.05);
            fill = mix(behind, holo, 0.62) + Tint.rgb * (0.12 + edgeIn * 0.9);
        } else if (mode == 4) {
            // Chams: one colour, shaded as the hand is lit, with a bright rim.
            fill = Tint.rgb * (0.32 + lum * 0.95) + mix(Tint.rgb, vec3(1.0), 0.5) * edgeIn * 0.5;
        } else if (mode == 5) {
            // Glass: the world behind bent through it, a cool tint, a bright rim and a glint sweeping across.
            vec3 behind = texture(u_Before, uv + slope * 60.0 * px).rgb;
            fill = mix(behind, Tint.rgb, 0.16) * 1.05 + vec3(pow(lum, 3.0) * 0.4)
                    + vec3(edgeIn * 0.55) + vec3(sheen(uv, t, 0.035) * 0.45);
        } else if (mode == 6) {
            // Burning: glowing embers over the hand, brightest where it is lit.
            float e = fbm(q * 9.0 - vec2(0.0, t * (0.25 + Flame.x * 2.0) * 1.2));
            fill = fireRamp(0.25 + e * 0.55 + lum * 0.3) * (0.45 + lum * 0.9) * (0.6 + Flame.w * 0.7);
        } else if (mode == 7) {
            // Lava: a dark crust broken by glowing cracks that pulse and slowly shift.
            vec2 id;
            vec3 c = cells(q * 7.0 + vec2(0.0, t * 0.12), id);
            float crack = smoothstep(0.14, 0.0, c.y);
            float pulse = 0.75 + 0.25 * sin(t * 2.5 + hash(id) * 6.28);
            vec3 crust = vec3(0.09, 0.035, 0.025) * (0.6 + lum * 1.2) * (0.8 + hash(id) * 0.4);
            vec3 hot = mix(vec3(0.95, 0.22, 0.02), vec3(1.0, 0.78, 0.25), crack * pulse);
            fill = mix(crust, hot * (0.8 + pulse * 0.4), crack) + vec3(1.0, 0.35, 0.05) * edgeIn * 0.5;
        } else if (mode == 8) {
            // Ice: frosted crystal facets, cold and pale, with frost and sparkles.
            vec2 id;
            vec3 c = cells(q * 9.0, id);
            float facet = hash(id);
            vec3 ice = mix(vec3(0.45, 0.72, 0.98), vec3(0.88, 0.96, 1.0), facet * 0.6 + lum * 0.5);
            ice = mix(ice, Tint.rgb, 0.1) * (0.7 + lum * 0.5);
            float frost = fbm(q * 24.0) * 0.18;
            float edge = smoothstep(0.05, 0.0, c.y) * 0.25;
            float sparkle = step(0.988, hash(floor(q * 60.0) + floor(t * 5.0))) * 0.9;
            fill = ice + vec3(frost + edge + sparkle) + vec3(0.8, 0.95, 1.0) * edgeIn * 0.55;
        } else if (mode == 9) {
            // Electric: dark, crackling with arcs of light that jump about, bright at the edges.
            vec3 dark = vec3(0.015, 0.015, 0.04) + after * 0.12;
            float jump = floor(t * 14.0);
            float a1 = fbm(q * 3.5 + vec2(jump * 0.37, t * 0.8));
            float a2 = fbm(q * 5.0 - vec2(t * 0.6, jump * 0.21) + 9.0);
            float arc = (1.0 - smoothstep(0.0, 0.022, abs(a1 - 0.5))) + (1.0 - smoothstep(0.0, 0.016, abs(a2 - 0.5))) * 0.7;
            float flicker = 0.7 + 0.3 * step(0.5, hash(vec2(jump, 3.0)));
            vec3 bolt = mix(Tint.rgb, vec3(1.0), 0.35);
            fill = dark + bolt * arc * 1.6 * flicker + vec3(pow(arc, 3.0)) * 0.6 + bolt * edgeIn * 0.6;
        } else if (mode == 10) {
            // Chrome: polished metal mirroring the world (sky above, ground below), with a moving glint.
            vec2 bent = vec2(uv.x, 1.0 - uv.y) + slope * 40.0 * px;
            vec3 refl = (texture(u_Before, bent).rgb + texture(u_Before, bent + px * 3.0).rgb + texture(u_Before, bent - px * 3.0).rgb) / 3.0;
            float r = dot(refl, vec3(0.299, 0.587, 0.114));
            vec3 metal = mix(vec3(pow(r, 1.4) * 1.25), Tint.rgb * (0.4 + r), 0.18);
            fill = metal * (0.55 + lum * 0.75) + vec3(sheen(uv, t, 0.05) * 0.55) + vec3(edgeIn * 0.4);
        } else if (mode == 11) {
            // Matrix: green code raining down the hand, the lead glyph of each stream bright.
            float colW = 7.0 / (px.y * 1080.0);
            float col = floor(uv.x / px.x / colW);
            float speed = 0.35 + hash(vec2(col, 1.0)) * 0.9;
            float stream = fract(uv.y * 1.6 + t * speed + hash(vec2(col, 2.0)) * 9.0);
            float row = floor(uv.y / px.y / colW);
            float glyph = step(0.45, hash(vec2(col, row + floor(t * speed * 6.0))));
            float lead = smoothstep(0.93, 1.0, stream);
            vec3 green = vec3(0.12, 1.0, 0.35);
            fill = vec3(0.0, 0.05, 0.015) + green * glyph * pow(stream, 2.5) * 0.9 + vec3(0.75, 1.0, 0.8) * lead * glyph
                    + green * edgeIn * 0.35 + green * lum * 0.12;
        }
        color = mix(after, fill, Tint.a * cover);
    }

    // The outline: a thin clean line hugging the hand and a soft glow fading out from it, both
    // from blurred copies of the hand so they are smooth all the way round.
    if (Glow.x > 0.0 && Glow.y > 0.0 && cover < 1.0) {
        float lineR = clamp(Glow.x * 0.45, 1.25, 4.0);
        float line = smoothstep(0.04, 0.3, blurred(uv, px, lineR, 12));
        float halo = blurred(uv, px, Glow.x * 3.0, 32);
        float light = (line * 0.85 + halo * 1.45) * Glow.y;
        color += rim * light * (1.0 - cover);
    }
    // Glow alone also lights the hand's rim from within.
    if (mode == 0 && Glow.y > 0.0) color += rim * edgeIn * 0.55 * Glow.y;

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
