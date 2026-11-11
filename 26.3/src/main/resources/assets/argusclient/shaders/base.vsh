#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform EffectUniforms {
    vec2 u_Size;
};

layout (location = 0) in vec3 Position;


layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) out vec2 v_OneTexel;

void main() {
    gl_Position = vec4(Position, 1.0);

    v_TexCoord = (Position.xy + 1.0) / 2.0;
    v_OneTexel = 1.0 / u_Size;
}
