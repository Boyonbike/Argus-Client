#version 330 core
#extension GL_ARB_separate_shader_objects : require

// Shared full-screen vertex shader for every effect. It declares no uniform
// block: OpenGL rejects a program whose stages declare EffectUniforms with
// different members, and each fragment shader declares its own block.
layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 v_TexCoord;

void main() {
    gl_Position = vec4(Position, 1.0);
    v_TexCoord = (Position.xy + 1.0) / 2.0;
}
