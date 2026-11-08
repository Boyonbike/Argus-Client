#version 330

// Copy of the vanilla GUI uniform block (dynamictransforms.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 localPos;
in vec4 vertexColor;
flat in vec4 shapeParams;

out vec4 fragColor;

// Signed distance to a rounded box centred on the origin, in physical pixels.
float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    vec2 halfSize = shapeParams.xy;
    float radius = min(shapeParams.z, min(halfSize.x, halfSize.y));
    float parameter = shapeParams.w;
    float distance = roundedBox(localPos, halfSize, radius);
    float coverage;
    if (parameter < 0.0) {
        // Soft drop shadow: parameter is the negative blur width.
        float blur = -parameter;
        coverage = 1.0 - smoothstep(-blur * 0.6, blur, distance);
        coverage *= coverage;
    } else if (parameter > 0.0) {
        // Outline ring of the given thickness, inside the shape edge.
        coverage = clamp(0.5 - distance, 0.0, 1.0) - clamp(0.5 - distance - parameter, 0.0, 1.0);
    } else {
        coverage = clamp(0.5 - distance, 0.0, 1.0);
    }
    vec4 color = vertexColor;
    color.a *= coverage;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
