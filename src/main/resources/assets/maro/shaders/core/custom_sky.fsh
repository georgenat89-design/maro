#version 330

// Custom Sky.
//
// Drawn in place of the game's sky, before the terrain: every pixel works out which way it looks
// into the world (from the inverse of this frame's view-projection) and paints one of the skies
// below for that direction. Everything is procedural and animated by Params.x. When the sky is
// changed the old one is painted too and faded out, so switching never pops. The last sky is a
// picture of the player's own (u_Sky), laid on the sky as a panorama, a cube cross or wrapped round.

in vec2 ndc;

uniform sampler2D u_Sky;

// Only a mat4 and vec4s, so std140 lays it out exactly as CustomSkyRenderer writes it.
layout(std140) uniform SkyData {
    mat4 InvViewProj;   // clip space -> world direction (camera at the origin)
    vec4 Params;        // x seconds of animation, y sky shown, z sky fading out, w how much of it is left
    vec4 View;          // x brightness, y size of one pixel in radians, z picture fit (0 panorama, 1 wrap, 2 cube), w copies round when wrapped
    vec4 ImageParams;   // x turn round the horizon (radians), y height of a wrapped copy (radians)
    vec4 ImageTop;      // rgb: the picture's colour along its top, for the sky above a wrapped picture
    vec4 ImageBottom;   // rgb: its colour along the bottom, for below it
};

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;
const vec3 UP = vec3(0.0, 1.0, 0.0);

// ---- noise -----------------------------------------------------------------------------------

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

// Fractal noise, 0 to 1 (mostly 0.3 to 0.7).
float fbm2(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * valueNoise2(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum / 0.96875;
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * valueNoise3(p);
        p = p * 2.02 + vec3(17.1, 9.2, 4.7);
        amp *= 0.5;
    }
    return sum / 0.96875;
}

// Cheaper, three octaves.
float fbm3lo(vec3 p) {
    float sum = 0.5 * valueNoise3(p);
    sum += 0.25 * valueNoise3(p * 2.02 + vec3(17.1, 9.2, 4.7));
    sum += 0.125 * valueNoise3(p * 4.07 + vec3(3.3, 21.4, 11.8));
    return sum / 0.875;
}

// smoothstep that also works with its edges the other way round, to fade out instead of in.
float sstep(float e0, float e1, float x) {
    float k = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return k * k * (3.0 - 2.0 * k);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

// ---- shared pieces ---------------------------------------------------------------------------

// d seen on the plane that touches the sky at c: x to the right, y up, z how squarely d faces c
// (x and y are only meaningful while z > 0).
vec3 around(vec3 d, vec3 c) {
    vec3 right = normalize(cross(c, UP));
    vec3 up = cross(right, c);
    float z = dot(d, c);
    return vec3(dot(d, right) / max(z, 1e-4), dot(d, up) / max(z, 1e-4), z);
}

// One star at most per cell of a grid laid through the sky; about one to two pixels across
// whatever the screen size, twinkling.
vec3 starLayer(vec3 d, float scale, float density, float t) {
    vec3 p = d * scale;
    vec3 id = floor(p);
    vec3 h = hash33(id);
    if (h.x > density) return vec3(0.0);
    vec3 at = id + 0.2 + 0.6 * hash33(id + 19.19);
    float dist = length(p - at);
    float px = View.y * scale;
    float r = min(px * (0.75 + 1.2 * h.y * h.y), 0.2);
    float core = exp(-dist * dist / (r * r));
    float twinkle = 0.7 + 0.3 * sin(t * (1.5 + 3.0 * h.z) + h.y * 50.0);
    vec3 tint = mix(vec3(0.72, 0.84, 1.0), vec3(1.0, 0.86, 0.72), h.z);
    return tint * core * twinkle * (0.35 + 0.9 * h.y);
}

vec3 stars(vec3 d, float density, float t) {
    const mat3 TURN = mat3(0.36, 0.48, -0.8, -0.8, 0.6, 0.0, 0.48, 0.64, 0.6);
    return starLayer(d, 90.0, density, t) + starLayer(TURN * d, 45.0, density * 0.3, t) * 1.4;
}

// A glowing band along a great circle, like the Milky Way: 0 off it, 1 on its spine.
float galaxyBand(vec3 d, vec3 axis, float width) {
    float off = dot(d, axis) / width;
    return exp(-off * off);
}

// Coordinates that wrap round the horizon: x around (0 to 1), y up from the horizon in turns.
vec2 azimuthal(vec3 d) {
    return vec2(atan(d.z, d.x) / TAU + 0.5, asin(clamp(d.y, -1.0, 1.0)) / TAU);
}

// Things drifting up (or down) through the sky in columns: sparks, bubbles. Returns the distance to
// this cell's particle in cells (big when there is none) and the cell's random value in y.
vec2 drifters(vec3 d, float columns, float rise, float density, float t) {
    vec2 a = azimuthal(d) * columns;
    float col = floor(a.x);
    float speed = 0.6 + hash12(vec2(col, 4.1));
    a.y -= t * rise * speed;
    vec2 cell = vec2(col, floor(a.y));
    float h = hash12(cell);
    if (h > density) return vec2(9.0, 0.0);
    vec2 at = cell + vec2(0.5 + 0.3 * sin(t * 1.3 + h * 40.0), 0.5);
    // a cell is wider near the horizon than above, so measure on the sky, not in the grid
    vec2 off = a - at;
    off.x *= cos(asin(clamp(d.y, -1.0, 1.0)));
    return vec2(length(off), h);
}

// ---- the skies -------------------------------------------------------------------------------

// 0: Neon Waves - glowing violet lines flowing over a black sky.
vec3 neonWaves(vec3 d, float t) {
    vec3 col = mix(vec3(0.05, 0.0, 0.1), vec3(0.008, 0.0, 0.02), smoothstep(-0.05, 0.7, d.y));
    vec3 q = d * 1.1 + vec3(t * 0.025, t * 0.018, -t * 0.02);
    vec3 warp = vec3(valueNoise3(q * 1.6 + 3.1), valueNoise3(q * 1.6 + 7.3), valueNoise3(q * 1.6 + 11.9));
    float v = fbm3lo(q + warp * 1.1) * 6.0;
    float off = abs(fract(v) - 0.5);
    float px = off / max(fwidth(v), 1e-4);       // how far from a line, in pixels
    float core = 1.0 - smoothstep(1.2, 2.8, px);
    float pulse = 0.8 + 0.2 * sin(t * 1.4 + floor(v) * 2.3);
    float glow = (exp(-px * 0.13) * 0.65 + exp(-px * 0.03) * 0.25) * pulse;
    col += vec3(0.58, 0.18, 1.0) * glow;
    col += vec3(0.95, 0.72, 1.0) * core * pulse;
    return col;
}

// 1: Aurora - green and violet curtains rippling over a starry night.
vec3 aurora(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(0.02, 0.07, 0.1), vec3(0.003, 0.008, 0.03), smoothstep(0.0, 0.7, h));
    col += stars(d, 0.12, t) * smoothstep(-0.02, 0.3, h);
    if (h > 0.0) {
        vec3 acc = vec3(0.0);
        float jitter = hash12(gl_FragCoord.xy);
        for (int i = 0; i < 16; i++) {
            float fi = float(i) + jitter;
            // each layer is a little higher; the same wavy ridge seen through them makes curtains
            vec2 p = d.xz / (h + 0.08) * (0.55 + fi * 0.03);
            p += vec2(t * 0.012, -t * 0.006);
            float wave = valueNoise2(p * 2.6 + vec2(valueNoise2(p * 0.7 + t * 0.03) * 3.0, 0.0));
            float ridge = 1.0 - abs(wave * 2.0 - 1.0);
            float band = pow(ridge, 12.0);
            float rays = 0.35 + 0.65 * valueNoise2(vec2(p.x * 14.0 + p.y * 7.0, t * 0.5));
            vec3 tint = mix(vec3(0.1, 1.0, 0.45), vec3(0.65, 0.25, 1.0), smoothstep(3.0, 14.0, fi));
            acc += tint * band * rays * exp(-fi * 0.11);
        }
        col += acc * 0.2 * smoothstep(0.04, 0.3, h) * (1.0 - smoothstep(0.75, 1.0, h));
    }
    // a faint green glow on the horizon
    col += vec3(0.02, 0.12, 0.08) * exp(-abs(h) * 14.0);
    return col;
}

// 2: Nebula - clouds of coloured gas, dust lanes and a river of stars.
vec3 nebula(vec3 d, float t) {
    vec3 q = d * 1.7 + vec3(0.0, 0.0, t * 0.008);
    float n = fbm3(q + vec3(fbm3lo(q * 1.4 + 5.0), fbm3lo(q * 1.4 + 9.0), 0.0) * 1.3);
    float hue = fbm3lo(q * 1.2 + 13.0);
    float gas = smoothstep(0.42, 0.78, n);
    vec3 neb = mix(vec3(0.12, 0.35, 1.0), vec3(1.0, 0.15, 0.6), smoothstep(0.38, 0.62, hue)) * gas;
    neb += vec3(1.0, 0.6, 0.3) * pow(smoothstep(0.6, 0.9, n), 2.5) * 1.2;
    float dust = smoothstep(0.48, 0.7, fbm3lo(q * 2.6 + 21.0));
    neb *= 1.0 - dust * 0.75;
    vec3 col = vec3(0.008, 0.005, 0.02) + neb * 0.55;
    float band = galaxyBand(d, normalize(vec3(0.35, 0.8, 0.45)), 0.28);
    col += vec3(0.3, 0.26, 0.42) * band * smoothstep(0.35, 0.7, fbm3lo(d * 7.0)) * 0.5;
    col += stars(d, 0.22 + band * 0.4, t);
    return col;
}

// 3: Synthwave - a striped sun sinking into a neon grid under a magenta sky.
vec3 synthwave(vec3 d, float t) {
    float h = d.y;
    vec3 horizon = vec3(1.0, 0.36, 0.42);
    vec3 col = mix(horizon, vec3(0.42, 0.05, 0.42), smoothstep(0.0, 0.22, h));
    col = mix(col, vec3(0.05, 0.01, 0.14), smoothstep(0.18, 0.65, h));
    col += stars(d, 0.15, t) * smoothstep(0.25, 0.55, h);

    // derivatives are only reliable outside branches, so take them first
    vec3 s = around(d, normalize(vec3(0.0, 0.1, 1.0)));
    float r = length(s.xy);
    float aa = fwidth(r);
    vec2 g = d.xz / max(-h, 1e-3);
    g.y -= t * 1.6;
    vec2 fw = fwidth(g * 0.5);
    if (s.z > 0.0) {
        float radius = 0.3;
        float y = s.y / radius;
        vec3 sun = mix(vec3(1.0, 0.12, 0.55), vec3(1.0, 0.86, 0.3), smoothstep(-0.9, 0.9, y));
        float cut = 0.0;
        if (y < 0.35) {
            float depth = (0.35 - y) / 1.35;
            cut = step(fract(y * 6.0 + t * 0.35), min(depth * 1.2, 0.8));
        }
        float disc = sstep(radius + aa, radius - aa, r) * (1.0 - cut);
        col += sun * exp(-max(r - radius, 0.0) * 5.0) * 0.45 * step(0.0, h + 0.02);
        col = mix(col, sun * 1.25, disc * step(0.0, h));
    }

    if (h < 0.0) {
        vec2 cell = abs(fract(g * 0.5) - 0.5);
        vec2 lines = 1.0 - smoothstep(fw * 0.8, fw * 2.2, cell);
        float grid = max(lines.x, lines.y) * smoothstep(0.004, 0.06, -h);
        vec3 ground = vec3(0.04, 0.0, 0.07) + vec3(1.0, 0.2, 0.85) * grid;
        col = mix(ground, horizon * 0.9, exp(h * 40.0));
    }
    return col;
}

// 4: Golden Hour - a low sun lighting the undersides of the clouds orange and pink.
vec3 goldenHour(vec3 d, float t) {
    float h = d.y;
    vec3 sunDir = normalize(vec3(0.4, 0.06, 1.0));
    float toSun = max(dot(d, sunDir), 0.0);
    vec3 col = mix(vec3(1.0, 0.55, 0.3), vec3(0.95, 0.45, 0.5), smoothstep(0.0, 0.15, h));
    col = mix(col, vec3(0.2, 0.3, 0.62), smoothstep(0.1, 0.6, h));
    col += vec3(1.0, 0.55, 0.2) * pow(toSun, 8.0) * 0.6;
    col += vec3(1.0, 0.9, 0.7) * pow(toSun, 900.0) * 6.0;
    if (h > 0.0) {
        vec2 uv = d.xz / (h + 0.1) * 0.7 + vec2(t * 0.02, t * 0.006);
        float n = fbm2(uv);
        float cover = smoothstep(0.48, 0.72, n) * smoothstep(0.0, 0.12, h);
        float lit = pow(toSun, 3.0);
        vec3 cloud = mix(vec3(0.55, 0.28, 0.45), vec3(1.0, 0.62, 0.38), lit * 0.8 + 0.2);
        cloud += vec3(1.0, 0.5, 0.2) * sstep(0.65, 0.5, n) * lit;   // bright rims
        col = mix(col, cloud, cover * 0.9);
    } else {
        col = mix(col, vec3(0.35, 0.2, 0.3), sstep(0.0, -0.3, h));
    }
    return col;
}

// 5: Cotton Candy - pastel pink and blue with soft clouds and sparkles.
vec3 cottonCandy(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(1.0, 0.74, 0.86), vec3(0.62, 0.78, 1.0), smoothstep(-0.05, 0.6, h));
    if (h > 0.0) {
        vec2 uv = d.xz / (h + 0.15) * 0.8 + vec2(t * 0.015, 0.0);
        float n = fbm2(uv);
        float cover = smoothstep(0.42, 0.7, n) * smoothstep(0.0, 0.1, h);
        float shade = smoothstep(0.42, 0.8, fbm2(uv * 1.8 + 7.0));
        vec3 cloud = mix(vec3(1.0, 0.82, 0.93), vec3(1.0, 0.98, 1.0), shade);
        cloud = mix(cloud, vec3(0.86, 0.72, 0.98), sstep(0.62, 0.42, n) * 0.5);
        col = mix(col, cloud, cover);
    }
    vec3 sparkle = starLayer(d, 70.0, 0.08, t * 2.5);
    col += sparkle * vec3(1.0, 0.9, 1.0) * 1.5 * smoothstep(0.1, 0.4, h);
    return col;
}

// 6: Blood Moon - a huge red moon behind drifting crimson mist.
vec3 bloodMoon(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(0.34, 0.03, 0.03), vec3(0.04, 0.0, 0.01), smoothstep(0.0, 0.6, h));
    col += stars(d, 0.1, t) * vec3(1.0, 0.6, 0.55) * smoothstep(0.1, 0.5, h);
    vec3 m = around(d, normalize(vec3(-0.35, 0.42, 1.0)));
    float r = length(m.xy);
    float aa = fwidth(r);
    if (m.z > 0.0) {
        float radius = 0.22;
        vec2 uv = m.xy / radius;
        float craters = fbm2(uv * 2.5 + 3.0);
        float spots = smoothstep(0.55, 0.7, fbm2(uv * 6.0 + 11.0));
        float limb = sqrt(max(1.0 - dot(uv, uv), 0.0));
        vec3 surface = vec3(0.9, 0.2, 0.1) * (0.55 + 0.6 * craters) * (0.45 + 0.55 * limb) * (1.0 - spots * 0.35);
        float disc = sstep(radius + aa, radius - aa, r);
        col += vec3(1.0, 0.12, 0.05) * exp(-max(r - radius, 0.0) * 7.0) * 0.6;
        col = mix(col, surface * 1.9, disc);
    }
    if (h > -0.1) {
        vec2 uv = d.xz / (h + 0.2) * 0.6 + vec2(t * 0.03, t * 0.01);
        float mist = smoothstep(0.45, 0.8, fbm2(uv));
        col = mix(col, vec3(0.3, 0.02, 0.03), mist * 0.5);
    }
    return col;
}

// One shooting star, fired every few seconds from its own slot.
vec3 shootingStar(vec3 d, float t, float slot) {
    float period = 4.0 + slot * 1.7;
    float k = floor((t + slot * 2.3) / period);
    float age = (t + slot * 2.3) - k * period;
    if (age > 0.9) return vec3(0.0);
    vec3 r = hash33(vec3(k, slot, 7.0));
    float az = r.x * TAU;
    float el = 0.3 + r.y * 0.6;
    vec3 c = vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
    vec3 s = around(d, c);
    if (s.z <= 0.0) return vec3(0.0);
    float angle = (r.z - 0.5) * 2.0 - 0.6;      // mostly falling
    vec2 dir = vec2(cos(angle), sin(angle));
    vec2 p = vec2(dot(s.xy, dir), dot(s.xy, vec2(-dir.y, dir.x)));
    float head = age * 0.55;
    float len = 0.12;
    float along = clamp((p.x - (head - len)) / len, 0.0, 1.0);
    float off = length(vec2(max(0.0, max(p.x - head, (head - len) - p.x)), p.y));
    float px = off / View.y;
    float fade = sstep(0.9, 0.6, age) * smoothstep(0.0, 0.08, age);
    return vec3(0.85, 0.92, 1.0) * exp(-px * 0.7) * along * fade * 1.6;
}

// 7: Starry Night - a deep sky full of stars, the Milky Way and shooting stars.
vec3 starryNight(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(0.04, 0.06, 0.15), vec3(0.004, 0.008, 0.03), smoothstep(0.0, 0.5, h));
    vec3 axis = normalize(vec3(0.5, 0.55, -0.67));
    float band = galaxyBand(d, axis, 0.22);
    float clouds = fbm3lo(d * 5.0);
    col += vec3(0.22, 0.2, 0.32) * band * smoothstep(0.35, 0.75, clouds) * 0.7;
    col *= 1.0 - band * smoothstep(0.5, 0.7, fbm3lo(d * 9.0 + 4.0)) * 0.5;
    col += stars(d, 0.3 + band * 0.5, t) * 1.2;
    col += shootingStar(d, t, 0.0) + shootingStar(d, t, 1.0) + shootingStar(d, t, 2.0);
    col += vec3(0.05, 0.06, 0.12) * exp(-abs(h) * 10.0);
    return col;
}

// 8: Matrix - columns of green code raining down the sky.
vec3 matrixRain(vec3 d, float t) {
    const float COLUMNS = 200.0;
    vec2 a = azimuthal(d);
    float u = a.x * COLUMNS;
    float v = a.y * COLUMNS / 1.4;
    float column = floor(u);
    float row = floor(v);
    vec2 f = vec2(fract(u), fract(v));
    float hc = hash12(vec2(column, 3.7));
    float speed = 5.0 + hc * 9.0;
    float trail = 8.0 + hash12(vec2(column, 9.1)) * 22.0;
    float span = 90.0;
    float headRow = 45.0 - mod(t * speed + hc * span, span);
    float behind = row - headRow;
    float lit = behind < 0.0 ? 0.0 : exp(-behind / trail * 2.5);
    float glyph = hash13(vec3(column, row, floor(t * (2.0 + hc * 6.0) + hash12(vec2(row, column)) * 10.0)));
    vec2 g = floor(f * vec2(3.0, 5.0));
    float inside = step(0.12, f.x) * step(f.x, 0.88) * step(0.08, f.y) * step(f.y, 0.92);
    float bit = step(0.42, hash13(vec3(g, glyph * 97.0)));
    float on = bit * inside;
    vec3 green = vec3(0.12, 1.0, 0.4);
    vec3 col = green * on * lit * 0.9;
    col += vec3(0.75, 1.0, 0.85) * on * step(0.0, behind) * step(behind, 1.0);
    col += green * lit * 0.05;
    col *= sstep(1.45, 1.1, abs(a.y * TAU));     // the columns meet overhead; fade them there
    return col + vec3(0.0, 0.015, 0.005);
}

// 9: Inferno - churning fire and smoke with sparks rising.
vec3 inferno(vec3 d, float t) {
    float h = d.y;
    vec3 q = d * 1.8 + vec3(0.0, -t * 0.07, 0.0);
    float n = fbm3(q + fbm3lo(q * 1.9 + vec3(0.0, -t * 0.12, 0.0)) * 1.4);
    float heat = clamp(1.0 - h * 1.3, 0.0, 1.0);
    vec3 smoke = mix(vec3(0.05, 0.005, 0.0), vec3(0.45, 0.07, 0.01), n);
    vec3 fire = mix(vec3(1.0, 0.25, 0.02), vec3(1.0, 0.8, 0.3), smoothstep(0.55, 0.85, n));
    float burn = smoothstep(0.35, 0.95, n * (0.4 + heat * 0.9));
    vec3 col = mix(smoke, fire, burn);
    vec2 spark = drifters(d, 160.0, 2.5, 0.12, t);
    float px = spark.x / (View.y * 160.0 / TAU);
    float flicker = 0.6 + 0.4 * sin(t * 12.0 + spark.y * 60.0);
    col += vec3(1.0, 0.6, 0.15) * exp(-px * 0.6) * flicker * 1.5;
    return col;
}

// 10: Frozen - a pale winter sky, wispy cirrus and a halo round the sun.
vec3 frozen(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(0.84, 0.92, 1.0), vec3(0.32, 0.55, 0.86), smoothstep(0.0, 0.6, h));
    vec3 sunDir = normalize(vec3(-0.25, 0.35, 1.0));
    float cosA = dot(d, sunDir);
    float a = acos(clamp(cosA, -1.0, 1.0));
    col += vec3(1.0, 0.98, 0.92) * (pow(max(cosA, 0.0), 1200.0) * 4.0 + pow(max(cosA, 0.0), 40.0) * 0.35);
    // the 22 degree halo: red inside, blue-white outside
    float ring = 0.384;
    col += vec3(1.0, 0.55, 0.45) * exp(-pow((a - ring + 0.006) / 0.006, 2.0)) * 0.14;
    col += vec3(0.85, 0.95, 1.0) * exp(-pow((a - ring - 0.004) / 0.012, 2.0)) * 0.2;
    // sundogs either side of the sun
    vec3 s = around(d, sunDir);
    for (int i = 0; i < 2; i++) {
        float side = i == 0 ? -1.0 : 1.0;
        vec2 dog = vec2(side * tan(ring) * 1.05, 0.0);
        vec2 o = (s.xy - dog) / vec2(0.035, 0.012);
        col += vec3(1.0, 0.9, 0.8) * exp(-dot(o, o)) * 0.6 * step(0.0, s.z);
    }
    if (h > 0.0) {
        vec2 uv = d.xz / (h + 0.12);
        uv = vec2(uv.x * 0.35, uv.y * 2.4) + vec2(t * 0.01, 0.0);
        float wisps = smoothstep(0.5, 0.85, fbm2(uv)) * smoothstep(0.0, 0.15, h);
        col = mix(col, vec3(1.0), wisps * 0.55);
    }
    vec3 dust = starLayer(d, 60.0, 0.05, t * 4.0);
    col += dust * vec3(0.9, 0.97, 1.0) * 1.2;
    return col;
}

float caustic(vec2 p, float t) {
    vec2 i = p;
    float c = 1.0;
    float intensity = 0.005;
    for (int n = 0; n < 4; n++) {
        float tt = t * (1.0 - 3.5 / float(n + 1));
        i = p + vec2(cos(tt - i.x) + sin(tt + i.y), sin(tt - i.y) + cos(tt + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + tt) / intensity), p.y / (cos(i.y + tt) / intensity)));
    }
    c /= 4.0;
    c = 1.17 - pow(c, 1.4);
    return pow(abs(c), 8.0);
}

// 11: Deep Ocean - looking up from under the sea: rippling light, sun shafts and bubbles.
vec3 deepOcean(vec3 d, float t) {
    float h = d.y;
    vec3 col = mix(vec3(0.0, 0.04, 0.1), vec3(0.05, 0.38, 0.55), smoothstep(-0.6, 0.9, h));
    // the bright window onto the surface overhead
    float window = smoothstep(0.62, 0.78, h);
    if (h > 0.05) {
        vec2 uv = d.xz / h * 1.6;
        float c = caustic(mod(uv, TAU) - 250.0, t * 0.5);
        col += vec3(0.5, 0.9, 1.0) * c * 0.6 * smoothstep(0.05, 0.6, h);
    }
    col += vec3(0.4, 0.8, 0.9) * window * 0.35;
    float az = atan(d.z, d.x);
    float shafts = valueNoise2(vec2(az * 14.0, t * 0.25)) * valueNoise2(vec2(az * 31.0 + 4.0, t * 0.4));
    col += vec3(0.35, 0.75, 0.85) * smoothstep(0.15, 0.6, shafts) * smoothstep(-0.4, 0.8, h) * 0.35;
    vec2 b = drifters(d, 120.0, 1.2, 0.07, t);
    float cell = View.y * 120.0 / TAU;
    float rim = abs(b.x - 0.22) / cell;
    col += vec3(0.6, 0.95, 1.0) * exp(-rim * 0.9) * 0.5 * smoothstep(-0.6, 0.0, h);
    return col;
}

// 12: Black Hole - stars bent round a black hole with a blazing disk.
vec3 deepSpace(vec3 d, float t) {
    vec3 col = vec3(0.004, 0.004, 0.012);
    float n = fbm3lo(d * 2.2);
    col += mix(vec3(0.05, 0.02, 0.12), vec3(0.12, 0.03, 0.08), n) * smoothstep(0.45, 0.75, n);
    col += stars(d, 0.25, t);
    return col;
}

vec3 diskColor(float radius, float angle, float t) {
    float swirl = fbm2(vec2(radius * 9.0, angle * 2.0 / PI * 3.0 - t * 0.9 / radius));
    vec3 hot = mix(vec3(1.0, 0.95, 0.85), vec3(1.0, 0.45, 0.12), smoothstep(1.4, 3.6, radius));
    return hot * (0.45 + 0.9 * swirl);
}

vec3 blackHole(vec3 d, float t) {
    vec3 c = normalize(vec3(0.3, 0.3, 1.0));
    vec3 s = around(d, c);
    if (s.z <= 0.0) return deepSpace(d, t);
    vec2 q = s.xy;
    float shadow = 0.12;
    float r = length(q) / shadow;               // in shadow radii
    // background: light from behind is bent outwards round the hole
    vec2 bent = q * max(1.0 - 2.6 / max(r * r, 1e-3), -1.0);
    vec3 right = normalize(cross(c, UP));
    vec3 up = cross(right, c);
    vec3 col = r < 1.0 ? vec3(0.0) : deepSpace(normalize(c + right * bent.x + up * bent.y), t);
    float aa = fwidth(r);
    col *= smoothstep(1.0 - aa, 1.0 + aa, r);
    // the disk, tilted nearly edge-on
    float tilt = 0.2;
    vec2 disk = vec2(q.x, q.y / tilt) / shadow;
    float dr = length(disk);
    float angle = atan(disk.y, disk.x);
    float ring = smoothstep(1.45, 1.6, dr) * sstep(4.2, 3.2, dr);
    float doppler = 1.0 + 0.55 * disk.x / max(dr, 1e-3);
    vec3 glow = diskColor(dr, angle, t) * ring * doppler;
    // the far half passes behind the hole, the near half in front of it
    float front = step(q.y, 0.0);
    col = mix(col, glow, ring * (front + (1.0 - front) * smoothstep(1.0 - aa, 1.0 + aa, r)));
    // the far side of the disk, lensed up over the top and under the bottom of the hole
    float halo = smoothstep(1.02, 1.12, r) * sstep(1.75, 1.3, r);
    float arc = pow(abs(q.y) / max(length(q), 1e-4), 0.6);
    col += diskColor(r * 1.6, atan(q.y, q.x), t) * halo * arc * 0.9;
    // the photon ring
    col += vec3(1.0, 0.85, 0.6) * exp(-pow((r - 1.04) / 0.025, 2.0)) * 0.9;
    return col;
}

// 13: Prism - slow rainbow bands rolling across a glowing sky.
vec3 prism(vec3 d, float t) {
    float az = atan(d.z, d.x);
    float n = fbm3lo(d * 1.6 + vec3(t * 0.03));
    float hue = fract(az / TAU * 2.0 + d.y * 0.45 + n * 0.5 + t * 0.025);
    vec3 col = hsv2rgb(vec3(hue, 0.55, 1.0));
    // soft light sheets sweeping through
    float sheets = pow(0.5 + 0.5 * sin(d.y * 9.0 + n * 7.0 - t * 0.6), 6.0);
    col = mix(col, vec3(1.0), sheets * 0.35);
    col *= 0.82 + 0.18 * sin(az * 3.0 + d.y * 4.0 + t * 0.3);
    col += starLayer(d, 80.0, 0.06, t * 2.0) * 1.2;
    return col * 0.92;
}

// The bolt of a lightning strike, seen on the plane around where it strikes.
float bolt(vec2 p, float seed) {
    if (p.y > 0.55 || p.y < -0.25) return 0.0;
    float wiggle = (valueNoise2(vec2(p.y * 7.0, seed)) - 0.5) * 0.16 + (valueNoise2(vec2(p.y * 31.0, seed + 5.0)) - 0.5) * 0.04;
    float off = abs(p.x - wiggle) / View.y;
    float ends = sstep(0.55, 0.45, p.y) * smoothstep(-0.25, -0.15, p.y);
    return (exp(-off * 0.9) + exp(-off * 0.08) * 0.25) * ends;
}

// 14: Thunderstorm - heavy clouds rolling past, lit up by lightning.
vec3 thunderstorm(vec3 d, float t) {
    float h = d.y;
    vec2 uv = d.xz / (max(h, 0.0) + 0.12) * 0.55 + vec2(t * 0.05, t * 0.02);
    float n = fbm2(uv + vec2(fbm2(uv * 1.7 + t * 0.03), 0.0) * 0.9);
    n = mix(0.5, n, smoothstep(-0.05, 0.12, h));
    vec3 col = mix(vec3(0.025, 0.03, 0.045), vec3(0.26, 0.29, 0.36), smoothstep(0.38, 0.72, n));
    col *= 0.75 + 0.25 * smoothstep(0.0, 0.5, h);
    for (int i = 0; i < 2; i++) {
        float period = i == 0 ? 3.1 : 4.3;
        float shifted = t + float(i) * 1.7;
        float k = floor(shifted / period);
        float age = shifted - k * period;
        if (age > 0.5) continue;
        float seed = hash12(vec2(k, 1.3 + float(i) * 5.0));
        float flash = exp(-age * 7.0) * (0.55 + 0.45 * step(0.45, fract(age * 11.0)));
        float az = seed * TAU;
        vec3 at = normalize(vec3(cos(az), 0.25, sin(az)));
        float near = pow(max(dot(d, at), 0.0), 4.0);
        col += vec3(0.55, 0.6, 0.9) * flash * (0.18 + 1.1 * near) * (0.4 + n);
        vec3 s = around(d, at);
        if (s.z > 0.0) col += vec3(0.85, 0.88, 1.0) * bolt(s.xy, seed * 91.0) * flash * 2.0;
    }
    col = mix(col, vec3(0.06, 0.07, 0.09), sstep(0.0, -0.3, h));
    return col;
}

// ---- your own picture ------------------------------------------------------------------------

// A skybox laid out as a horizontal cross, four faces across the middle (left, front, right, back,
// front facing south) with the top above the front and the bottom below it.
vec3 cubeCross(vec3 d) {
    vec3 a = abs(d);
    vec3 c, r, t;
    vec2 cell;
    if (a.y >= a.x && a.y >= a.z) {
        if (d.y > 0.0) { c = UP; r = vec3(-1.0, 0.0, 0.0); t = vec3(0.0, 0.0, -1.0); cell = vec2(1.0, 0.0); }
        else { c = -UP; r = vec3(-1.0, 0.0, 0.0); t = vec3(0.0, 0.0, 1.0); cell = vec2(1.0, 2.0); }
    } else if (a.z >= a.x) {
        if (d.z > 0.0) { c = vec3(0.0, 0.0, 1.0); r = vec3(-1.0, 0.0, 0.0); t = UP; cell = vec2(1.0, 1.0); }
        else { c = vec3(0.0, 0.0, -1.0); r = vec3(1.0, 0.0, 0.0); t = UP; cell = vec2(3.0, 1.0); }
    } else {
        if (d.x > 0.0) { c = vec3(1.0, 0.0, 0.0); r = vec3(0.0, 0.0, 1.0); t = UP; cell = vec2(0.0, 1.0); }
        else { c = vec3(-1.0, 0.0, 0.0); r = vec3(0.0, 0.0, -1.0); t = UP; cell = vec2(2.0, 1.0); }
    }
    vec2 f = vec2(dot(d, r), dot(d, t)) / dot(d, c);       // -1 to 1 across the face
    vec2 uv = vec2(f.x, -f.y) * 0.5 + 0.5;                  // 0 to 1 from its top left
    vec2 facePixels = vec2(textureSize(u_Sky, 0)) / vec2(4.0, 3.0);
    uv = clamp(uv, 0.5 / facePixels, 1.0 - 0.5 / facePixels);  // never bleed into the next face
    return textureLod(u_Sky, (cell + uv) / vec2(4.0, 3.0), 0.0).rgb;
}

// 15: the player's picture.
vec3 imageSky(vec3 d) {
    float c = cos(ImageParams.x), s = sin(ImageParams.x);
    d = vec3(c * d.x - s * d.z, d.y, s * d.x + c * d.z);
    int fit = int(View.z + 0.5);
    // round the horizon from 0 to 1, starting behind you when facing south, increasing to the right
    float around = 0.5 + atan(-d.x, d.z) / TAU;
    float elevation = asin(clamp(d.y, -1.0, 1.0));
    if (fit == 0) return textureLod(u_Sky, vec2(around, 0.5 - elevation / PI), 0.0).rgb;
    if (fit == 2) return cubeCross(d);
    // Wrapped: copies side by side round the horizon, every other one mirrored so they meet
    // seamlessly, from a little below the horizon up; beyond them the picture's own edge colours.
    float x = around * View.w + 0.5;     // an unmirrored copy straight ahead when facing south
    float u = fract(x);
    if (mod(floor(x), 2.0) > 0.5) u = 1.0 - u;
    float v = 1.0 - (elevation + 0.17) / ImageParams.y;
    vec3 col = textureLod(u_Sky, vec2(u, clamp(v, 0.0, 1.0)), 0.0).rgb;
    col = mix(col, ImageTop.rgb, sstep(0.0, -0.3, v));
    col = mix(col, ImageBottom.rgb, sstep(1.0, 1.3, v));
    return col;
}

vec3 sky(int index, vec3 d, float t) {
    switch (index) {
        case 0: return neonWaves(d, t);
        case 1: return aurora(d, t);
        case 2: return nebula(d, t);
        case 3: return synthwave(d, t);
        case 4: return goldenHour(d, t);
        case 5: return cottonCandy(d, t);
        case 6: return bloodMoon(d, t);
        case 7: return starryNight(d, t);
        case 8: return matrixRain(d, t);
        case 9: return inferno(d, t);
        case 10: return frozen(d, t);
        case 11: return deepOcean(d, t);
        case 12: return blackHole(d, t);
        case 13: return prism(d, t);
        case 14: return thunderstorm(d, t);
        default: return imageSky(d);
    }
}

void main() {
    vec4 near = InvViewProj * vec4(ndc, -1.0, 1.0);
    vec4 far = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 d = normalize(far.xyz / far.w - near.xyz / near.w);
    float t = Params.x;

    vec3 col = sky(int(Params.y + 0.5), d, t);
    if (Params.w > 0.001) col = mix(col, sky(int(Params.z + 0.5), d, t), Params.w);

    col = max(col, vec3(0.0)) * View.x;
    // a little noise so the dark gradients do not band
    col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(col, 1.0);
}
