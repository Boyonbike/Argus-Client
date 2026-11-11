#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;

layout(location = 3) noperspective in float lineEdge;
layout(location = 4) noperspective in float halfWidthPx;
layout(location = 5) noperspective in float outerHalfPx;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor * ColorModulator;
    float distPx = abs(lineEdge) * outerHalfPx;

    float coverage = clamp(halfWidthPx + 0.5 - distPx, 0.0, 1.0);

    color.a *= coverage;
    if (color.a < 0.001) discard;

    fragColor = apply_fog(color,
        sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor
    );
}
