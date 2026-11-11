#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;
    vec4 u_Color;
    vec2 u_Resolution;
    vec2 u_Mouse;
    float u_Scale;
    float u_Time;
};

layout(location = 0) out vec4 color;


#define TAU 6.28318530718
#define MAX_ITER 5
#define INTENSITY 0.005

// original shader https://www.shadertoy.com/view/4sXfDj
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
    vec2 uv = (gl_FragCoord.xy / u_Resolution.xy + u_Mouse) * u_Scale;
    vec2 p = mod(uv * TAU, TAU) - 250.0;

    vec2 iterPos = p;
    float value = 1.0;
    float timeOffset = u_Time * 0.5 + 23.0;

    for (int n = 0; n < MAX_ITER; n++) {
        float iterTime = timeOffset * (1.0 - (3.5 / float(n + 1)));

        iterPos = p + vec2(
        cos(iterTime - iterPos.x) + sin(iterTime + iterPos.y),
        sin(iterTime - iterPos.y) + cos(iterTime + iterPos.x)
        );

        float sinX = sin(iterPos.x + iterTime);
        float cosY = cos(iterPos.y + iterTime);

        float compX = p.x * INTENSITY / max(abs(sinX), 0.001);
        float compY = p.y * INTENSITY / max(abs(cosY), 0.001);
        float distance = sqrt(compX * compX + compY * compY);

        value += 1.0 / distance;
    }

    value = 1.17 - pow(value / float(MAX_ITER), 1.4);

    float pwr = value * value;
    pwr = pwr * pwr;
    pwr = pwr * pwr;

    vec3 baseColor = vec3(abs(pwr));
    vec3 enhancedColor = clamp(baseColor + vec3(0.0, 0.35, 0.5), 0.0, 1.0);
    float luminance = dot(enhancedColor, vec3(0.299, 0.587, 0.114));

    color = vec4(vec3(luminance) * u_Color.rgb * 1.4, luminance * u_Color.a);
}
