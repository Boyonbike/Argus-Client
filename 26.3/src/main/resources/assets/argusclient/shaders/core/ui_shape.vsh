#version 330
#extension GL_ARB_separate_shader_objects : require

// Copy of the vanilla GUI uniform blocks (26.3 dynamictransforms.glsl + projection.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;     // fragment offset from the shape centre, in physical pixels
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV1;    // half size, physical pixels * 8
layout(location = 4) in ivec2 UV2;    // corner radius * 8, mode parameter * 8

layout(location = 0) out vec2 localPos;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out vec4 shapeParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos = UV0;
    vertexColor = Color;
    shapeParams = vec4(vec2(UV1), vec2(UV2)) / 8.0;
}
