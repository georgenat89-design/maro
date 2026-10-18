#version 330
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 d = vec2(4.0) / vec2(textureSize(Sampler0, 0));
    float a = texture(Sampler0, texCoord0).a * 0.20;
    a += (texture(Sampler0, texCoord0 + vec2(d.x,0)).a + texture(Sampler0, texCoord0 - vec2(d.x,0)).a
        + texture(Sampler0, texCoord0 + vec2(0,d.y)).a + texture(Sampler0, texCoord0 - vec2(0,d.y)).a) * 0.12;
    a += (texture(Sampler0, texCoord0 + d).a + texture(Sampler0, texCoord0 - d).a
        + texture(Sampler0, texCoord0 + vec2(d.x,-d.y)).a + texture(Sampler0, texCoord0 + vec2(-d.x,d.y)).a) * 0.08;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a);
}
