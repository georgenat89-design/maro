#version 330

// The frame as the game drew it.
uniform sampler2D InSampler;

// The three levels of the pyramid, widest blur last.
uniform sampler2D L0Sampler;
uniform sampler2D L1Sampler;
uniform sampler2D L2Sampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BloomCompositeConfig {
    float Intensity;
    float Level0;
    float Level1;
    float Level2;
};

out vec4 fragColor;

void main() {
    vec3 base = texture(InSampler, texCoord).rgb;

    // A plain weighted sum, deliberately NOT divided by the weights. The weights
    // were tuned together with the threshold against a real frame, and dividing
    // by their total would undo that tuning and make Intensity 1 mean something
    // other than what it was tuned to mean.
    vec3 bleed = texture(L0Sampler, texCoord).rgb * Level0
               + texture(L1Sampler, texCoord).rgb * Level1
               + texture(L2Sampler, texCoord).rgb * Level2;

    // Added, not mixed: light adds to what is behind it. The frame is left alone
    // where nothing bloomed, which is what keeps the picture from going milky.
    fragColor = vec4(base + max(bleed, 0.0) * Intensity, 1.0);
}
