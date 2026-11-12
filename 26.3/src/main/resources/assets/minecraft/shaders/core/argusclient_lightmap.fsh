#version 330
#extension GL_ARB_separate_shader_objects : require

// Ambience "Screen" brightness: every non-maximum lightmap cell becomes the
// chosen tint colour. LightmapInfo is the 26.3 vanilla block followed by the
// Argus tint; LightTextureMixin uploads this extended copy for Argus pipelines.
layout(std140) uniform LightmapInfo {
    float SkyFactor;
    float BlockFactor;
    float NightVisionFactor;
    float DarknessScale;
    float BossOverlayWorldDarkeningFactor;
    float BrightnessFactor;
    vec3 BlockLightTint;
    vec3 SkyLightColor;
    vec3 AmbientColor;
    vec3 NightVisionColor;
    vec4 ArgusTint;
} lightmapInfo;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    if (texCoord.x * 16 < 15 || texCoord.y * 16 < 15) {
        fragColor = vec4(lightmapInfo.ArgusTint.rgb, 1.0);
    } else {
        fragColor = vec4(1.0);
    }
}
