#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    int u_Width;
    float u_GlowMultiplier;
    float u_FillOpacity;
};

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec2 v_OneTexel;

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
    vec4 center = texture(u_Texture, v_TexCoord);

    if (center.a != 0.0) {
        center.a = u_FillOpacity;
    }
    else {
        if (u_Width == 0.0) discard;

        float dist = u_Width * u_Width + 1.0;

        for (int x = -u_Width; x <= u_Width; x++) {
            for (int y = -u_Width; y <= u_Width; y++) {
                vec4 offset = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));

                if (offset.a != 0) {
                    dist = min(x * x + y * y - 1.0, dist);
                    center = offset;
                }
            }
        }

        float minDist = u_Width * u_Width;

        if (dist > minDist) center.a = 0.0;
        else {
            center.a = min((1.0 - (dist / minDist)) * u_GlowMultiplier, 1.0);
        }
    }

    color = center;
}
