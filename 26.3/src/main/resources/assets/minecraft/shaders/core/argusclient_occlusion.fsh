#version 330
#extension GL_ARB_separate_shader_objects : require

// Ambience "Occlusion": the vanilla 26.3 lightmap, converted to luminance and
// tinted. LightmapInfo is the 26.3 vanilla block followed by the Argus tint;
// LightTextureMixin uploads this extended copy for Argus pipelines.
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

float get_brightness(float level) {
    return level / (4.0 - 3.0 * level);
}

vec3 notGamma(vec3 color) {
    float maxComponent = max(max(color.x, color.y), color.z);
    float maxInverted = 1.0f - maxComponent;
    float maxScaled = 1.0f - maxInverted * maxInverted * maxInverted * maxInverted;
    return color * (maxScaled / max(maxComponent, 0.00001));
}

float parabolicMixFactor(float level) {
    return (2.0 * level - 1.0) * (2.0 * level - 1.0);
}

void main() {
    float block_level = floor(texCoord.x * 16) / 15;
    float sky_level = floor(texCoord.y * 16) / 15;

    float block_brightness = get_brightness(block_level) * lightmapInfo.BlockFactor;
    float sky_brightness = get_brightness(sky_level) * lightmapInfo.SkyFactor;

    vec3 nightVisionColor = lightmapInfo.NightVisionColor * lightmapInfo.NightVisionFactor;
    vec3 color = max(lightmapInfo.AmbientColor, nightVisionColor);

    color += lightmapInfo.SkyLightColor * sky_brightness;

    vec3 blockLightColor = mix(lightmapInfo.BlockLightTint, vec3(1.0), 0.9 * parabolicMixFactor(block_level));
    color += blockLightColor * block_brightness;

    color = mix(color, color * vec3(0.7, 0.6, 0.6), lightmapInfo.BossOverlayWorldDarkeningFactor);
    color = color - vec3(lightmapInfo.DarknessScale);

    color = clamp(color, 0.0, 1.0);
    color = mix(color, notGamma(color), lightmapInfo.BrightnessFactor);

    float luminance = 0.2126 * color.r + 0.7152 * color.g + 0.0722 * color.b;
    if (texCoord.x * 16 < 15 || texCoord.y * 16 < 15) {
        fragColor = vec4(vec3(luminance) * lightmapInfo.ArgusTint.rgb, 1.0);
    } else {
        fragColor = vec4(1.0);
    }
}
