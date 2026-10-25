#version 330

in vec2 texCoord;
out vec4 fragColor;
layout(std140) uniform PreviewSceneData {
    vec4 Scene; // preview seconds, aspect, padding
};

// A neutral sample, independent of the live world and any already applied effect.
float box(vec2 point, vec2 center, vec2 size) {
    vec2 edge = abs(point - center) - size;
    return 1.0 - smoothstep(-0.006, 0.006, max(edge.x, edge.y));
}
void main() {
    vec2 uv = texCoord;
    vec3 color = mix(vec3(0.62,0.76,0.91), vec3(0.16,0.32,0.56), uv.y);
    float horizon = 0.48;
    if (uv.y < horizon) {
        vec2 plane = vec2((uv.x - 0.5) * Scene.y, 0.14) / max(0.035, horizon - uv.y);
        float checker = mod(floor(plane.x) + floor(plane.y), 2.0);
        color = mix(vec3(0.27,0.31,0.29), vec3(0.44,0.47,0.43), checker);
        color = mix(vec3(0.54,0.63,0.67), color, smoothstep(0.0,0.32,horizon-uv.y));
    }
    color = mix(color, vec3(0.80,0.27,0.17), box(uv,vec2(0.23,0.46),vec2(0.105,0.17)));
    color = mix(color, vec3(0.20,0.55,0.34), box(uv,vec2(0.57,0.41),vec2(0.13,0.12)));
    color = mix(color, vec3(0.28,0.42,0.75), box(uv,vec2(0.81,0.51),vec2(0.075,0.22)));
    vec2 sun = (uv-vec2(0.70,0.82))*vec2(Scene.y,1.0);
    color = mix(color, vec3(1.0,0.94,0.75), 1.0-smoothstep(0.055,0.062,length(sun)));
    fragColor = vec4(color,1.0);
}
