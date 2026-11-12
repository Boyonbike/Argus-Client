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
layout(location = 1) in vec2 UV0;     // texture coordinate
layout(location = 2) in vec4 Color;
layout(location = 3) in ivec2 UV1;    // half size, physical pixels * 8
layout(location = 4) in ivec2 UV2;    // corner radius * 8, quad margin * 8
layout(location = 5) in vec3 Normal;  // quad corner as -1/+1 on x and y

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec2 localPos;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) flat out vec3 shapeParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vec2 halfSize = vec2(UV1) / 8.0;
    float margin = float(UV2.y) / 8.0;
    localPos = sign(Normal.xy) * (halfSize + margin);
    vertexColor = Color;
    shapeParams = vec3(halfSize, float(UV2.x) / 8.0);
}
