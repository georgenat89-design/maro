#version 330

// Block ESP bloom. Step.z picks the job:
//   0  blurs u_Source along Step.xy: a 9-tap Gaussian taken as five linear-filtered reads;
//   1  adds u_Source onto the frame, Step.w times as bright, rolled off so stacked glow does not
//      burn out to flat white. Alpha is 0 so the frame's own alpha is left as it was.
// u_Source's colour is premultiplied light: the ESP was drawn into it over transparent black.

uniform sampler2D u_Source;

in vec2 texCoord;
flat in float quadTag;

layout(std140) uniform GlowData {
    vec4 Step;  // xy one step along the blur in source uv, z mode, w strength
};

out vec4 fragColor;

void main() {
    if (Step.z > 0.5) {
        vec3 light = texture(u_Source, texCoord).rgb * Step.w;
        fragColor = vec4(vec3(1.0) - exp(-light * 1.6), 0.0);
        return;
    }

    vec2 s = Step.xy;
    vec4 c = texture(u_Source, texCoord) * 0.2270270270;
    c += (texture(u_Source, texCoord + s * 1.3846153846) + texture(u_Source, texCoord - s * 1.3846153846)) * 0.3162162162;
    c += (texture(u_Source, texCoord + s * 3.2307692308) + texture(u_Source, texCoord - s * 3.2307692308)) * 0.0702702703;
    fragColor = c;
}
