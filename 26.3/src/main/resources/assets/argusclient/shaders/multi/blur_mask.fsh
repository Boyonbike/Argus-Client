#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    vec2 u_Direction;
    float u_Width;
    float u_Sigma;
};

layout(location = 0) in vec2 v_TexCoord;
// One texel in UV space. Derived here, not passed from base.vsh: OpenGL requires the
// EffectUniforms block to be identical in every stage, and each effect declares its own.
#define v_OneTexel (1.0 / u_Size)

uniform sampler2D u_Texture;
uniform sampler2D u_Mask;


layout(location = 0) out vec4 color;

float pdf(float x, float sigma) {
    return exp(-((x*x) / (sigma*sigma)));
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
    float radiusMul = texture(u_Mask, v_TexCoord).a;
    if (radiusMul == 0) {
        color = texture(u_Texture, v_TexCoord);
        return;
    }
    float fullRadius = (u_Width * radiusMul);
    float sum = 0;
    for(float j = -fullRadius; j <= fullRadius; j++) {
        sum += pdf(j, u_Sigma);
    }
    vec4 blurred = vec4(0);
    for(float j = -fullRadius; j <= fullRadius; j+=1.0) {
        float normalizedWeight = pdf(j, u_Sigma) / sum;
        vec2 offset = v_OneTexel * j * u_Direction;
        vec4 current = texture(u_Texture, v_TexCoord + offset);
        blurred += current * normalizedWeight;
    }
    color = blurred;
}
