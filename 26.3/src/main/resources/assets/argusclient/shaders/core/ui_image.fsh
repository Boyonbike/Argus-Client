#version 330
#extension GL_ARB_separate_shader_objects : require

// Copy of the vanilla GUI uniform block (26.3 dynamictransforms.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec2 localPos;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) flat in vec3 shapeParams;

layout(location = 0) out vec4 fragColor;

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    float radius = min(shapeParams.z, min(shapeParams.x, shapeParams.y));
    float coverage = clamp(0.5 - roundedBox(localPos, shapeParams.xy, radius), 0.0, 1.0);
    vec4 color = texture(Sampler0, clamp(texCoord0, 0.0, 1.0)) * vertexColor;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
