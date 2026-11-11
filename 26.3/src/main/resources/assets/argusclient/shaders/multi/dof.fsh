#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;
    float FocusDistance;
    float BlurStrength;
    vec2 u_Direction;
};

layout(location = 0) out vec4 color;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec2 v_OneTexel;

uniform sampler2D DepthSampler;
uniform sampler2D u_Texture;



float getDepth(vec2 uv) {
    return texture(DepthSampler, uv).r;
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
    float depth = getDepth(v_TexCoord);

    float depthDiff = abs(depth - FocusDistance);
    float blurAmount = max(0.0, depthDiff);
    float finalStrength = depth > FocusDistance ? BlurStrength : 0;

    float blur = clamp(blurAmount * finalStrength, 0.0, 1.0);

    if (blur < 0.01) {
        color = texture(u_Texture, v_TexCoord);
        return;
    }

    vec4 dcolor = vec4(0.0);
    float totalWeight = 0.0;

    int samples = 6;
    float sigma = float(samples) / 2.0;

    for (int i = -samples; i <= samples; i++) {
        float fi = float(i);

        float weight = exp(-(fi * fi) / (2.0 * sigma * sigma));

        vec2 offset = fi * blur * v_OneTexel * u_Direction;

        dcolor += texture(u_Texture, v_TexCoord + offset) * weight;
        totalWeight += weight;
    }

    color = dcolor / totalWeight;
}
