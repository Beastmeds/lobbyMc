#version 330
#extension GL_ARB_separate_shader_objects : require

#CreateConstant

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#include <minecraft:fog.glsl>
#endif

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

uniform sampler2D Sampler0;

layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

#GenerateOtherDefinedMethod

vec4 calculateFinalColor(vec4 color) {
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif

#if !defined(IS_SEE_THROUGH) && !defined(IS_GUI)

#ifdef OIT_ACCUMULATE
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
#else
    vec4 fogColor = FogColor;
#endif

    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif

    return color;
}

layout(location = 5) in vec2 beastLocalPosition;
layout(location = 6) in float beastEffect;
layout(location = 7) in float beastClock;

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif

    vec4 color = texColor * vertexColor * ColorModulator;

    // Animate only the BeastSMP background and header; text and other HUDs stay unchanged.
    if (beastEffect > 0.5 && beastEffect < 1.5) {
        if (texColor.b > 0.3 && texColor.r > 0.2 && texColor.g < 0.55 && texColor.b > texColor.r * 1.05) {
            float pulse = 0.5 + 0.5 * sin(beastClock * 1.05);
            color.rgb *= 0.65 + 0.65 * pulse;
        }
        if (beastLocalPosition.y > 78.0 && texColor.r > 0.8 && texColor.g > 0.25 && texColor.g < 0.85 && texColor.b < 0.35) {
            float flicker = 0.5 + 0.3 * sin(beastClock * 8.0 + beastLocalPosition.x) + 0.2 * sin(beastClock * 13.0);
            color.rgb *= 0.75 + 0.65 * flicker;
        }
    } else if (beastEffect > 1.5) {
        bool pumpkin = beastLocalPosition.x < -126.0 || beastLocalPosition.x > -35.0;
        if (pumpkin && texColor.r > 0.45 && texColor.g > 0.15 && texColor.b < 0.45) {
            float glow = 0.5 + 0.5 * sin(beastClock * 1.6 + (beastLocalPosition.x > -35.0 ? 0.7 : 0.0));
            color.rgb *= 0.50 + 0.85 * glow;
        }
    }

    #GenerateOtherMainMethod

    if (color.a < 0.1) {
        discard;
    }

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = calculateFinalColor(color);
#endif
}