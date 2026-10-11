#version 150

uniform sampler2D Menu;
layout(std140) uniform ScreenGuiData {
    vec4 Row0;
    vec4 Row1;
    vec4 Row2;
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
    fragColor = texture(Menu, vec2(uv.x, 1.0 - uv.y));
}
