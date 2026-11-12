#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;

    vec4 u_FillColor;
    vec4 u_OutlineColor;
    bool u_Image;
    float u_OverlayAlpha;
    int u_Width;
    bool u_FastLines;
    int u_ShapeMode;
    float u_Glow;
    int u_Dots;
    int u_DotsRadius;
    float u_DotsAlpha;
};

layout(location = 0) in vec2 v_TexCoord;
// One texel in UV space. Derived here, not passed from base.vsh: OpenGL requires the
// EffectUniforms block to be identical in every stage, and each effect declares its own.
#define v_OneTexel (1.0 / u_Size)

uniform sampler2D u_Texture;
uniform sampler2D u_Overlay;
uniform sampler2D u_Distance;


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
    vec4 overlay = texture(u_Overlay, v_TexCoord * vec2(1.0, -1.0));

    if (center.a != 0.0) {
        if (u_ShapeMode == 0 || overlay.a == 0.0) discard;
        if (decorator(u_Dots, u_DotsRadius)) {
            center = vec4(u_FillColor.rgb, u_DotsAlpha);
        } else if (u_Image) {
            center.rgb = mix(overlay.rgb, u_FillColor.rgb, u_FillColor.a);
            center.a = u_OverlayAlpha;
        } else {
            center = u_FillColor;
        }
    } else {
        if (u_ShapeMode == 1 || u_Width == 0.0) discard;

        float dist = u_Width * u_Width + 1.0;

        if (u_FastLines && u_Width > 2) {
            bool present = false;

            for (int x = -u_Width; x <= u_Width; x += u_Width) {
                for (int y = -u_Width; y <= u_Width; y += u_Width) {
                    vec4 offset = texture(u_Texture, v_TexCoord + v_OneTexel * vec2(x, y));
                    if (offset.a != 0.0) {
                        present = true;
                        break;
                    }
                }
            }

            if (!present) discard;
        }

        ivec2 coord = ivec2(gl_FragCoord.xy);
        int last = textureSize(u_Distance, 0).y - 1;

        for (int y = -u_Width; y <= u_Width; y++) {
            int x = int(texelFetch(u_Distance, ivec2(coord.x, clamp(coord.y + y, 0, last)), 0).r * 255.0 + 0.5);

            if (x <= u_Width) {
                dist = min(x * x + y * y - 1.0, dist);
            }
        }

        float minDist = u_Width * u_Width;

        if (dist > minDist)
            center.a = 0.0;
        else {
            center.rgb = u_OutlineColor.rgb;
            center.a = min((1.0 - (dist / minDist)) * u_Glow, 1.0);
            if (dist <= 1.5)
                center.a = 0.8;
        }
    }

    color = center;
}
