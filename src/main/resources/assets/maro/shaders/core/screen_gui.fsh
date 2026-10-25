#version 150

uniform sampler2D Menu;
uniform sampler2D WorldDepth;
layout(std140) uniform ScreenGuiData {
    vec4 Row0;
    vec4 Row1;
    vec4 Row2;
    vec4 ClipDepth;
};
out vec4 fragColor;

void main() {
    vec2 cursor = abs(gl_FragCoord.xy - vec2(Row0.w, Row1.w) * 0.5);
    float unit = max(1.0, Row1.w / 480.0);
    if (Row2.w > 0.5 && ((cursor.x < unit && cursor.y < unit * 5.0) || (cursor.y < unit && cursor.x < unit * 5.0))) {
        fragColor = vec4(1.0); return;
    }
    vec3 point = vec3(gl_FragCoord.x / Row0.w, 1.0 - gl_FragCoord.y / Row1.w, 1.0);
    vec3 source = vec3(dot(Row0.xyz, point), dot(Row1.xyz, point), dot(Row2.xyz, point));
    vec2 uv = source.xy / source.z;
    if (source.z <= 0.0 || any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
    // source is (u/clipW, v/clipW, 1/clipW); this is actual clipZ/clipW,
    // including the world's FOV, view bob, hurt tilt and distortion projection.
    float planeDepth = dot(ClipDepth.xyz, source) * 0.5 + 0.5;
    float terrainDepth = texelFetch(WorldDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (planeDepth < 0.0 || planeDepth > 1.0 || planeDepth > terrainDepth + 0.000002) discard;
    fragColor = texture(Menu, vec2(uv.x, 1.0 - uv.y));
}
