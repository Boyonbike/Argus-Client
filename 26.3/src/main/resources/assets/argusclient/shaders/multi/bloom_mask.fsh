#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    vec2 u_Direction;
    float u_Width;
    float u_Sigma;
    float u_Bloom;
    float u_Modulate;
    float u_Opacity;
};

layout(location = 0) in vec2 v_TexCoord;
// One texel in UV space. Derived here, not passed from base.vsh: OpenGL requires the
// EffectUniforms block to be identical in every stage, and each effect declares its own.
#define v_OneTexel (1.0 / u_Size)

uniform sampler2D u_Texture;


layout(location = 0) out vec4 color;

float pdf(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

// utils
#define bloom(color, weight) (vec4(color.rgb, color.a * weight))

bool decorator(int dots, int radius) {
    if (dots == 0) return false;
    if (dots == 1) {
        return int(gl_FragCoord.x) - (radius * int(gl_FragCoord.x / radius)) == 0
            && int(gl_FragCoord.y) - (radius * int(gl_FragCoord.y / radius)) == 0;
    }
    return int(gl_FragCoord.x) % radius == 0 || int(gl_FragCoord.y) % radius == 0;
}

vec4 blending(vec4 base, vec4 blend, float bloomFactor) {
    blend.a *= 1.0 - bloomFactor;
    float outA = blend.a + base.a * (1.0 - blend.a);
    vec3 outRGB = (blend.rgb * blend.a + base.rgb * base.a * (1.0 - blend.a)) / max(outA, 1e-6);

    return vec4(outRGB, outA);
}

vec2 pack16(float value) {
    float i = floor(clamp(value, 0.0, 1.0) * 65535.0 + 0.5);
    float hi = floor(i / 256.0);
    return vec2(hi, i - hi * 256.0) / 255.0;
}

float unpack16(vec2 encoded) {
    vec2 bytes = floor(encoded * 255.0 + 0.5);
    return (bytes.x * 256.0 + bytes.y) / 65535.0;
}

void main() {
    int radius = int(u_Width);
    float totalWeight = pdf(0.0, u_Sigma);
    vec4 blurred = texture(u_Texture, v_TexCoord) * totalWeight;
    for (int j = 1; j <= radius; ++j) {
        float weight = pdf(float(j), u_Sigma);
        vec2 offset = v_OneTexel * float(j) * u_Direction;
        blurred += texture(u_Texture, v_TexCoord + offset) * weight;
        blurred += texture(u_Texture, v_TexCoord - offset) * weight;
        totalWeight += 2.0 * weight;
    }
    blurred /= totalWeight;
    float fac = max(blurred.a * u_Modulate, 1.0);
    color = vec4(min(blurred.rgb * fac, 1.0), min(blurred.a * u_Bloom, u_Opacity));
}
