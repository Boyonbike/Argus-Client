#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    vec2 InSize;
    vec2 BlurDir;
    float Radius;
};

uniform sampler2D DiffuseSampler;
uniform sampler2D MaskSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in vec2 oneTexel;



layout(location = 0) out vec4 fragColor;

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
    float progRadius = Radius*texture(MaskSampler, texCoord).a; // multiply our max radius by the alpha of the current pixel
    if (progRadius < 1) {
        fragColor = texture(DiffuseSampler, texCoord); // no change, sample size less than one pixel
    } else {
        vec4 blurred = vec4(0);
        int i = 0;
        for(float r = -progRadius;r<=progRadius;r++) { // ex progRadius 2: -2, -1, 0, 1, 2
            blurred += texture(DiffuseSampler, texCoord + oneTexel * r * BlurDir);
            i++;
        }
        fragColor = blurred / i;
    }
}
