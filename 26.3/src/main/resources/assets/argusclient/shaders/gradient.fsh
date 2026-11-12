#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    bool u_Image;
    float u_OverlayAlpha;
    int u_Width;
    bool u_FastLines;
    int u_ShapeMode;
    bool u_GlowInner;
    int u_GlowQuality;
    float u_Time;
    bool u_Smooth;
    vec4 u_FillColor;
    vec4 u_FillColor2;
    vec4 u_FillColor3;
    vec4 u_OutlineColor;
    vec4 u_OutlineColor2;
    vec4 u_OutlineColor3;
    int u_Dots;
    int u_DotsRadius;
    float u_DotsAlpha;
    float u_Step;
    float u_Glow;
    bool u_Fade;
};

layout(location = 0) in vec2 v_TexCoord;
// One texel in UV space. Derived here, not passed from base.vsh: OpenGL requires the
// EffectUniforms block to be identical in every stage, and each effect declares its own.
#define v_OneTexel (1.0 / u_Size)

uniform sampler2D u_Texture;
uniform sampler2D u_Overlay;





layout(location = 0) out vec4 color;

float wave(float x) {
    return abs(fract(x / 1.0) * 2.0 - 1.0); // triangle pattern between 0 and 1
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 gradient(vec4 rgb1, vec4 rgb2, vec4 rgb3, float step, float time) {
    vec2 frag = vec2(gl_FragCoord.xy);
    float distance = frag.x + frag.y;

    distance = distance / step;
    distance = wave((frag.x + frag.y) / step + time);
    if (!u_Smooth) {
        return mix(rgb1, rgb3, distance);
    }

    if (distance < 0.5) {
        float t = distance / 0.5;
        return mix(rgb1, rgb2, t);
    } else {
        float t = (distance - 0.5) / 0.5;
        return mix(rgb2, rgb3, t);
    }
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

    vec4 outline = gradient(u_OutlineColor, u_OutlineColor2, u_OutlineColor3, u_Step * 300., u_Time);

    if (center.a != 0.0) {
        float a = center.a;
        vec4 fill = gradient(u_FillColor, u_FillColor2, u_FillColor3, u_Step * 300., u_Time);
        if (decorator(u_Dots, u_DotsRadius)) {
            center = vec4(fill.rgb, u_DotsAlpha);
        } else if (u_Image) {
            vec4 overlay = texture(u_Overlay, v_TexCoord * vec2(1.0, -1.0));
            if (overlay.a == 0.0) discard;
            center.rgb = mix(overlay.rgb, fill.rgb, fill.a);
            center.a = u_OverlayAlpha;
        } else {
            center = fill;
        }
        if (u_Width != 0 && u_GlowInner) {
            center = blending(center, bloom(outline, u_Glow), blurFactor(false, u_Width, u_GlowQuality, u_FastLines, u_Fade));
        }
        center.a *= a;

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
                        center.rgb = outline.rgb;
                        center.a = tex.a;
                        break;
                    }
                }
            }
        }

        if (center.a == 0.0) {
            center = outline;
            center.a *= blurFactor;
        }
    }

    color = center;
}
