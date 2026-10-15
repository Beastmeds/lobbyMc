#version 330

#CreateConstant

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#endif

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

#GenerateOtherDefinedMethod

in vec2 beastLocalPosition;
in float beastEffect;
in float beastClock;

void main() {
#ifdef IS_GRAYSCALE
    vec4 texColor = texture(Sampler0, texCoord0).rrrr;
#else
    vec4 texColor = texture(Sampler0, texCoord0);
#endif
#ifdef IS_SEE_THROUGH
    vec4 color = texColor * vertexColor;
#else
    vec4 color = texColor * vertexColor * ColorModulator;
#endif

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
#ifdef IS_SEE_THROUGH
    fragColor = color * ColorModulator;
#elif defined(IS_GUI)
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
