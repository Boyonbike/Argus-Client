#version 330
#extension GL_ARB_separate_shader_objects : require

// Copy of the vanilla GUI uniform block (26.3 dynamictransforms.glsl).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec2 localPos;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec4 shapeParams;

layout(location = 0) out vec4 fragColor;

// Signed distance to a rounded box centred on the origin, in physical pixels.
float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

// Pixel-art corner: a staircase of `steps` cells, each `cell` physical pixels square. Returns 1 inside, 0 outside.
float steppedBox(vec2 point, vec2 halfSize, float cell, float steps) {
    vec2 inward = halfSize - abs(point);
    if (inward.x <= 0.0 || inward.y <= 0.0) return 0.0;
    vec2 index = floor(inward / cell);
    return index.x + index.y >= steps ? 1.0 : 0.0;
}

void main() {
    vec2 halfSize = shapeParams.xy;
    if (shapeParams.z < 0.0) {
        // Chamfer: radius slot carries -(steps | grain << 4 | cellPixels*8 << 5) / 8.
        int code = int(round(-shapeParams.z * 8.0));
        float steps = float(code & 15), cell = float(code >> 5) / 8.0;
        bool grain = (code & 16) != 0;
        float coverage = steppedBox(localPos, halfSize, cell, steps);
        if (shapeParams.w > 0.0) {
            // Outline: the stepped ring between the shape and the same shape inset by whole cells.
            float inset = max(1.0, round(shapeParams.w / cell));
            coverage -= steppedBox(localPos, halfSize - inset * cell, cell, max(steps - inset, 0.0));
        }
        vec4 color = vertexColor;
        if (grain) {
            // Fixed hash per GUI pixel (not per framebuffer pixel), ±2% brightness: a pixel-art grain, not film noise.
            vec2 cellIndex = floor(gl_FragCoord.xy / cell);
            float n = fract(sin(dot(cellIndex, vec2(12.9898, 78.233))) * 43758.5453);
            color.rgb *= 1.0 + (n - 0.5) * 0.04;
        }
        color.a *= coverage;
        if (color.a <= 0.0) {
            discard;
        }
        fragColor = color * ColorModulator;
        return;
    }
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
