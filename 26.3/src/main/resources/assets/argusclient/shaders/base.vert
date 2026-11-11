#version 330 core

layout(std140) uniform EffectUniforms {
    vec2 u_Size;
};

layout (location = 0) in vec4 pos;


out vec2 v_TexCoord;
out vec2 v_OneTexel;

void main() {
    gl_Position = pos;

    v_TexCoord = (pos.xy + 1.0) / 2.0;
    v_OneTexel = 1.0 / u_Size;
}
