#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    int u_Pass;
    int u_Width;
    float u_Sigma;
    float u_Bloom;
    float u_Opacity;
};

layout(location = 0) in vec2 v_TexCoord;
// One texel in UV space. Derived here, not passed from base.vsh: OpenGL requires the
// EffectUniforms block to be identical in every stage, and each effect declares its own.
#define v_OneTexel (1.0 / u_Size)

uniform sampler2D u_Texture;


layout(location = 0) out vec4 color;

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
    float sigma = max(u_Sigma, 0.0001);
    float gaussExp = -1.0 / (2.0 * sigma * sigma);

    vec2 direction = u_Pass == 0 ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec2 step = v_OneTexel * direction;

    vec4 sum = texture(u_Texture, v_TexCoord);
    float weightSum = 1.0;

    for (int i = 1; i <= u_Width; i += 2) {
        float f1 = float(i);
        float f2 = f1 + 1.0;
        float w1 = exp(f1 * f1 * gaussExp);
        float w2 = i + 1 <= u_Width ? exp(f2 * f2 * gaussExp) : 0.0;
        float weight = w1 + w2;
        vec2 offset = step * ((f1 * w1 + f2 * w2) / weight);

        sum += (texture(u_Texture, v_TexCoord + offset) + texture(u_Texture, v_TexCoord - offset)) * weight;
        weightSum += weight * 2.0;
    }

    vec4 blurred = sum / weightSum;

    if (u_Pass == 0) {
        color = blurred;
        return;
    }

    if (blurred.a <= 0.0) {
        color = vec4(0.0);
        return;
    }

    color = vec4(min(blurred.rgb / blurred.a, 1.0), min(blurred.a * u_Bloom, u_Opacity));
}
