#version 330
uniform sampler2D Overlay;
in vec2 texCoord;
out vec4 fragColor;
void main() { fragColor = texture(Overlay, texCoord); }
