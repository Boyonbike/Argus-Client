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
in vec2 UV0;     // texture coordinate
in vec4 Color;
in ivec2 UV1;    // half size, physical pixels * 8
in ivec2 UV2;    // corner radius * 8, quad margin * 8
in vec3 Normal;  // quad corner as -1/+1 on x and y

out vec2 texCoord0;
out vec2 localPos;
out vec4 vertexColor;
flat out vec3 shapeParams;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vec2 halfSize = vec2(UV1) / 8.0;
    float margin = float(UV2.y) / 8.0;
    localPos = sign(Normal.xy) * (halfSize + margin);
    vertexColor = Color;
    shapeParams = vec3(halfSize, float(UV2.x) / 8.0);
}
