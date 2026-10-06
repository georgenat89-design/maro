#version 330

// Player ESP: quads given straight in clip space. z is not a depth but a tag: 0 is the
// silhouettes' rectangle, n is the quad around tracer n - 1.

in vec3 Position;

out vec2 texCoord;
flat out float quadTag;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
    quadTag = Position.z;
}
