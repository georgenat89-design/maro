#version 330

// Custom Sky: one quad over the whole screen, given straight in clip space.

in vec3 Position;

out vec2 ndc;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    ndc = Position.xy;
}
