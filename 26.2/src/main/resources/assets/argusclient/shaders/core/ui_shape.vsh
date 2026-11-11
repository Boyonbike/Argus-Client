#version 330

// Copy of the vanilla GUI uniform blocks (dynamictransforms.glsl + projection.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec2 UV0;     // fragment offset from the shape centre, in physical pixels
in vec4 Color;
in ivec2 UV1;    // half size, physical pixels * 8
in ivec2 UV2;    // corner radius * 8, mode parameter * 8

out vec2 localPos;
out vec4 vertexColor;
flat out vec4 shapeParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localPos = UV0;
    vertexColor = Color;
    shapeParams = vec4(vec2(UV1), vec2(UV2)) / 8.0;
}
