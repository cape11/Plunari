#version 330 core

in vec4 fColor;
in vec2 fTexCoord;
in float fLightValue;  // Interpolated normalized light value (0.0 to 1.0)

uniform sampler2D uTextureSampler; // Texture unit for main texture or font atlas
uniform int uHasTexture;           // Boolean (0 or 1) if a texture should be sampled
uniform int uIsFont;               // Boolean (0 or 1) if rendering font (special handling)
uniform int uIsSimpleUiElement;    // NEW: Boolean (0 or 1) if it's a simple UI element

out vec4 FragColor;

const float MIN_AMBIENT = 0.2; // Minimum ambient light factor for world objects

void main() {
    vec4 baseColor = fColor;
    vec4 texColor = vec4(1.0); // Default to white (no texture effect if uHasTexture is 0)

    if (uIsFont == 1) {
        // Font rendering: texture alpha blended with vertex color
        // Assuming font texture's red channel stores alpha.
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(baseColor.rgb, baseColor.a * alpha);
    } else if (uIsSimpleUiElement == 1) {
        // Simple UI element (e.g., solid color button background, inventory slot)
        // Uses vertex color directly, no texturing, no world lighting.
        FragColor = baseColor;
    } else {
        // World objects (terrain, textured sprites like player/trees, textured UI buttons)
        if (uHasTexture == 1) {
            texColor = texture(uTextureSampler, fTexCoord);
        }
        // For non-textured world objects, texColor remains vec4(1.0), so materialColor = baseColor.
        // For textured world objects, materialColor is texColor * baseColor (tint).
        vec4 materialColor = texColor * baseColor;

        // Apply world lighting using fLightValue
        float lightIntensity = MIN_AMBIENT + (1.0 - MIN_AMBIENT) * fLightValue;
        FragColor = vec4(materialColor.rgb * lightIntensity, materialColor.a);
    }

    // Discard fully transparent pixels to avoid issues with depth buffer for transparent objects
    if (FragColor.a < 0.01) {
        discard;
    }
}