#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;
    bool u_Image;
    float u_OverlayAlpha;
    int u_Width;
    bool u_FastLines;
    bool u_GlowInner;
    int u_GlowQuality;
    vec4 u_FillColor;
    vec4 u_OutlineColor;
    int u_Dots;
    int u_DotsRadius;
    float u_DotsAlpha;
    float u_Glow;
    float u_Time;
    bool u_Fade;
};

layout(location = 0) in vec2 v_TexCoord;
layout(location = 1) in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform sampler2D u_Overlay;

layout(location = 0) out vec4 color;

vec4 mainImage(bool outline) {
    if (outline) return u_OutlineColor;
    return u_FillColor;
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

uniform sampler2D u_Coverage;

const vec2 OFFS[8] = vec2[8](
    vec2( 1.0,  0.0), vec2(-1.0,  0.0),
    vec2( 0.0,  1.0), vec2( 0.0, -1.0),
    vec2( 1.0,  1.0), vec2( 1.0, -1.0),
    vec2(-1.0,  1.0), vec2(-1.0, -1.0)
);

float blurFactor(bool outline, int width, int glowQuality, bool fastlines, bool fade)
{
    if (width == 0) return 0.0;

    float blurred = 0.0;

    ivec2 coord = ivec2(gl_FragCoord.xy);
    int last = textureSize(u_Coverage, 0).y - 1;

    for (int y = -width; y <= width; y++) {
        blurred += unpack16(texelFetch(u_Coverage, ivec2(coord.x, clamp(coord.y + y * glowQuality, 0, last)), 0).rg);
    }

    float norm = float((width * width + width) * 4);
    return clamp(blurred * float(2 * width + 1) / norm, 0.0, 1.0);
}

void main() {
    vec4 center = texture(u_Texture, v_TexCoord);
    vec4 image = mainImage(true);

    if (center.a != 0.0) {
        float r = center.a;
        vec4 fillImage = mainImage(false);

        if (decorator(u_Dots, u_DotsRadius)) {
            center = vec4(fillImage.rgb, u_DotsAlpha);
        } else if (u_Image) {
            vec4 overlay = texture(u_Overlay, v_TexCoord * vec2(1.0, -1.0));
            if (overlay.a == 0.0) discard;
            center.rgb = mix(overlay.rgb, fillImage.rgb, fillImage.a);
            center.a = u_OverlayAlpha * overlay.a;
        } else {
            center = fillImage;
        }
        if (u_Width != 0 && u_GlowInner) {
            center = blending(center, bloom(image, u_Glow), blurFactor(false, u_Width, u_GlowQuality, u_FastLines, u_Fade));
        }
        center.a *= r;
    } else if (u_Width > 0.0) {
        float blurFactor = blurFactor(true, u_Width, u_GlowQuality, u_FastLines, u_Fade) * u_Glow;
        if (blurFactor == 0.0) discard;

        if (u_GlowQuality * u_Width > 1) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 0 && y == 0)
                        continue;

                    vec4 tex = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));
                    if (tex.a > 0.0) {
                        center.rgb = u_OutlineColor.rgb;
                        center.a = tex.a;
                        break;
                    }
                }
            }
        }

        if (center.a == 0.0) {
            center = image;
            center.a *= blurFactor;
        }
    }

    color = center;
}
