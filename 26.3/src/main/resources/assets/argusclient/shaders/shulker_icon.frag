#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    // Keep only the icon's silhouette. Texture colour, shading and glint cannot tint the chosen colour.
    if (texture(Sampler0, texCoord0).a <= 0.01) discard;
    vec4 colour = vertexColor * ColorModulator;
    fragColor = vec4(colour.rgb * colour.a, colour.a);
}
