#version 330 core

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

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform sampler2D u_Overlay;
uniform sampler2D u_Distance;


out vec4 color;

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
