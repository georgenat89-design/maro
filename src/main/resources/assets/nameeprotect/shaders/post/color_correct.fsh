#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

// Eleven floats, nothing wider. A vec3 in a std140 block is padded out to
// sixteen bytes and the next member has to be placed around that padding;
// a run of floats is laid out the same way by every implementation, so what
// the module writes and what the shader reads cannot drift apart.
layout(std140) uniform ColorCorrectConfig {
    float Saturation;
    float Brightness;
    float Contrast;
    float Gamma;
    float TintRed;
    float TintGreen;
    float TintBlue;
    float TintStrength;
    float Hue;
    float Shadows;
    float Boost;
};

// Rec. 709 luma. Weighting the channels by how bright the eye finds them is
// what keeps a desaturated frame looking grey rather than muddy.
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;

    color = pow(max(color, 0.0), vec3(1.0 / Gamma));
    color *= Brightness;

    // Shadows. Each pixel is made brighter or darker by an amount that depends
    // on how dark it is: all of it at black, an eighth of it half way up, none
    // of it at the top, falling away as a cube so the bright half of the picture
    // is hardly touched. It is a gain and not a lift - the pixel is multiplied,
    // nothing is added - so black stays black instead of going to grey haze,
    // and all three channels move together so colours keep their hue.
    //
    // How dark a pixel is, is taken from its brightest channel, not from how
    // bright it looks. A strong red looks dim - the eye makes little of red -
    // but its red channel is nearly full, and lifting it as though it were a
    // shadow pushes that channel past what the screen can show. Going by the
    // brightest channel, that channel follows a curve that always rises and
    // ends exactly at full, so nothing can clip and no tones can fold over
    // each other, at any setting.
    if (Shadows != 0.0) {
        float dark = 1.0 - clamp(max(color.r, max(color.g, color.b)), 0.0, 1.0);

        color *= 1.0 + Shadows * (Shadows > 0.0 ? 1.5 : 0.6) * dark * dark * dark;
    }

    // Contrast pivots on mid grey, so turning it up pushes the dark and the
    // light apart rather than simply brightening everything.
    color = (color - 0.5) * Contrast + 0.5;

    // Hue, in radians. Every colour is turned about the line of greys - the
    // line where red, green and blue are equal - so grey stays grey, and the
    // turn is built round the same luma weights as the saturation below, so a
    // colour comes out as bright as it went in and as far from grey. At nought
    // the turn is skipped outright rather than done by a matrix that ought to
    // change nothing: the picture is then bit for bit what it was before there
    // was a hue control at all.
    if (Hue != 0.0) {
        float c = cos(Hue);
        float s = sin(Hue);

        color = vec3(
            dot(color, vec3(0.2126 + c * 0.7874 - s * 0.2126, 0.7152 - c * 0.7152 - s * 0.7152, 0.0722 - c * 0.0722 + s * 0.9278)),
            dot(color, vec3(0.2126 - c * 0.2126 + s * 0.1430, 0.7152 + c * 0.2848 + s * 0.1400, 0.0722 - c * 0.0722 - s * 0.2830)),
            dot(color, vec3(0.2126 - c * 0.2126 - s * 0.7874, 0.7152 - c * 0.7152 + s * 0.7152, 0.0722 + c * 0.9278 + s * 0.0722)));
    }

    float luma = dot(color, LUMA);
    color = mix(vec3(luma), color, Saturation);

    // Colour boost. Turning saturation up multiplies every colour's distance
    // from grey alike, so what was already vivid is pushed past what the screen
    // can show and clips to a flat patch. This is weighted by how much colour a
    // pixel has not got: a dull pixel gets nearly all of the boost and a pure
    // one none, at any brightness, so colour comes up where it is missing and
    // stops where it is not. And however much is asked for, a pixel is only
    // pushed as far from grey as it can go with every channel still on the
    // screen - the most it can take is worked out, and it is given no more - so
    // a pale bright colour deepens until one channel is full or empty and then
    // holds, rather than clipping. Taking colour away has nothing to clip.
    if (Boost != 0.0) {
        float most = max(color.r, max(color.g, color.b));
        float least = min(color.r, min(color.g, color.b));
        float grey = dot(color, LUMA);
        float pure = most > 0.0 ? clamp((most - least) / most, 0.0, 1.0) : 1.0;

        float push = 1.0 + Boost * (Boost > 0.0 ? 1.0 - pure : 1.0);

        if (push > 1.0) {
            if (most > grey) push = min(push, max(1.0, (1.0 - grey) / (most - grey)));
            if (grey > least) push = min(push, max(1.0, grey / (grey - least)));
        }

        color = mix(vec3(grey), color, push);
    }

    if (TintStrength != 0.0) color = mix(color, color * vec3(TintRed, TintGreen, TintBlue), TintStrength);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
