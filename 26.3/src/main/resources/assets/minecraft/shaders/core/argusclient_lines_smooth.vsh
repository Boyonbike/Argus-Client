#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;
layout(location = 3) in float LineWidth;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;

layout(location = 3) noperspective out float lineEdge;
layout(location = 4) noperspective out float halfWidthPx;
layout(location = 5) noperspective out float outerHalfPx;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);


void main() {
    mat4 mv = VIEW_SCALE * ModelViewMat;

    vec4 clipPos = ProjMat * mv * vec4(Position, 1.0);

    vec3 viewPos = (mv * vec4(Position, 1.0)).xyz;
    vec3 viewDir = (mv * vec4(Normal, 0.0)).xyz;

    float dd = dot(viewDir, viewDir);
    float t = dd > 0.0 ? -dot(viewPos, viewDir) / dd : 0.0;
    vec3 refPos = viewPos + viewDir * t;

    vec4 refClip = ProjMat * vec4(refPos, 1.0);
    vec4 dirClip = ProjMat * vec4(viewDir, 0.0);

    vec2 dir = dirClip.xy * refClip.w - refClip.xy * dirClip.w;
    dir *= ScreenSize;
    if (dot(dir, dir) == 0.0) {
        dir = vec2(1.0, 0.0);
    }
    vec2 lineScreenDirection = normalize(dir);

    halfWidthPx = max(LineWidth, 0.0) * 0.5;
    outerHalfPx = halfWidthPx + 0.5;

    vec2 lineOffset = vec2(-lineScreenDirection.y, lineScreenDirection.x)
                    * (outerHalfPx * 2.0 / ScreenSize);
    if (lineOffset.x < 0.0) {
        lineOffset = -lineOffset;
    }

    float side = (gl_VertexID % 2 == 0) ? 1.0 : -1.0;
    gl_Position = vec4(clipPos.xy + side * lineOffset * clipPos.w, clipPos.zw);
    lineEdge = side;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
}
