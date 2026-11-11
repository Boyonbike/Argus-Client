#version 330 core

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

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform sampler2D u_Overlay;

out vec4 color;

vec4 mainImage(bool outline) {
    if (outline) return u_OutlineColor;
    return u_FillColor;
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
