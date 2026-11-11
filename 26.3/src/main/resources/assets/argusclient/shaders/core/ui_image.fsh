#version 330

// Copy of the vanilla GUI uniform block (dynamictransforms.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec2 localPos;
in vec4 vertexColor;
flat in vec3 shapeParams;

out vec4 fragColor;

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
