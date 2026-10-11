#version 330

uniform sampler2D Scene;
in vec2 texCoord;
out vec4 fragColor;

layout(std140) uniform GeekedData {
    vec4 Feeling; // time, intensity, mode, aspect
    vec4 Vision;  // wobble, colors, haze, separation
    vec4 Lens;    // vignette, inverse width, inverse height, padding
};

vec2 bounded(vec2 uv) { return clamp(uv, Lens.yz * 0.5, vec2(1.0) - Lens.yz * 0.5); }
vec3 sampleScene(vec2 uv) { return texture(Scene, bounded(uv)).rgb; }

vec3 hueShift(vec3 color, float angle) {
    // Rotate around the grey axis so neutral blocks stay neutral.
    vec3 axis = vec3(0.577350269);
    float c = cos(angle), s = sin(angle);
    return color * c + cross(axis, color) * s + axis * dot(axis, color) * (1.0 - c);
}

void main() {
    float t = Feeling.x, strength = Feeling.y, mode = Feeling.z;
    float dreamy = step(0.5, mode), trippy = step(1.5, mode);
    vec2 p = texCoord - 0.5;
    float edge = smoothstep(0.08, 0.65, length(p));
    float motion = Vision.x * strength * (1.0 + dreamy * 0.5 + trippy * 0.6);
    float breath = sin(t * 0.85) * 0.012 * motion;
    float angle = sin(t * 0.57) * 0.014 * motion;
    mat2 roll = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    // Preserve the image's aspect while applying a tiny visual roll; no player rotation changes.
    vec2 uv = roll * (p * vec2(Feeling.w, 1.0));
    uv /= vec2(Feeling.w, 1.0);
    uv = uv / (1.0 + abs(breath) + 0.025 * motion) + 0.5;
    uv += vec2(sin(p.y * 9.0 + t * 0.8), cos(p.x * 8.0 - t * 0.65)) * 0.0045 * motion * edge;

    vec3 color = sampleScene(uv);
    if (Vision.w > 0.0) {
        vec2 fringe = (uv - 0.5) * 0.012 * Vision.w * strength * (1.0 + trippy);
        color.r = sampleScene(uv + fringe).r;
        color.b = sampleScene(uv - fringe).b;
    }
    float haze = Vision.z * strength;
    if (haze > 0.0) {
        vec2 blur = vec2(2.5 + haze * 5.0) * Lens.yz;
        vec3 soft = (sampleScene(uv + vec2(blur.x, 0.0)) + sampleScene(uv - vec2(blur.x, 0.0))
                   + sampleScene(uv + vec2(0.0, blur.y)) + sampleScene(uv - vec2(0.0, blur.y))) * 0.25;
        color = mix(color, soft, haze * 0.45);
        color += max(soft - 0.55, 0.0) * haze * 0.32;
    }
    float shift = Vision.y * strength;
    float hue = sin(t * 0.24) * shift * (0.22 + dreamy * 0.65 + trippy * 1.25);
    color = hueShift(color, hue);
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(luma), color, 1.0 + shift * (0.18 + dreamy * 0.20));
    vec3 warm = vec3(1.0, 0.87, 0.72);
    vec3 cool = vec3(0.79, 0.81, 1.0);
    vec3 tint = mix(warm, cool, 0.5 + 0.5 * sin(t * 0.31));
    color = mix(color, color * tint + tint * 0.035, shift * 0.22);
    float shade = edge * edge * Lens.x * strength;
    color *= 1.0 - shade * 0.32;
    color += tint * shade * 0.025;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
