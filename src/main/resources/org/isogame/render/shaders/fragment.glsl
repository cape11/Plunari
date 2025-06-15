#version 330 core

// INPUTS from the Vertex Shader
in vec4 fColor;          // Interpolated vertex color (used for tinting or direct color)
in vec2 fTexCoord;       // Interpolated texture coordinates
in float fLightValue;   // Interpolated normalized light value (0.0 to 1.0)

// UNIFORMS (Global variables from Java)
uniform sampler2D uTextureSampler; // The texture atlas
uniform int uHasTexture;           // Boolean (0 or 1) if we should sample from the texture
uniform int uIsFont;               // Boolean (0 or 1) for special font rendering
uniform int uIsSimpleUiElement;    // Boolean (0 or 1) for UI that ignores lighting

// OUTPUT to the screen
out vec4 FragColor;

// CONSTANTS
const float MIN_AMBIENT = 0.2; // Minimum ambient light for world objects to be visible in darkness

void main() {
    // --- Path 1: Font Rendering ---
    // This path is taken for text. It uses the texture's red channel as an alpha mask.
    if (uIsFont == 1) {
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(fColor.rgb, fColor.a * alpha);

    // --- Path 2: Simple UI Element Rendering ---
    // This path is for solid color UI backgrounds (like in the inventory).
    // It uses the vertex color directly, with no textures or lighting.
    } else if (uIsSimpleUiElement == 1) {
        FragColor = fColor;

    // --- Path 3: World Object Rendering ---
    // This is for everything in the game world (terrain, player, items, particles).
    } else {
        vec4 materialColor;

        // Check if the object is textured (player, trees, etc.)
        if (uHasTexture == 1) {
            vec4 texColor = texture(uTextureSampler, fTexCoord);
            materialColor = texColor * fColor; // Apply tint from vertex color
        }
        // If not textured (our particles)
        else {
            materialColor = fColor; // Use the vertex color directly
        }

        // Apply world lighting to all world objects
        float lightIntensity = MIN_AMBIENT + (1.0 - MIN_AMBIENT) * fLightValue;
        FragColor = vec4(materialColor.rgb * lightIntensity, materialColor.a);
    }

    // Discard fully transparent pixels. This prevents invisible parts of sprites
    // from incorrectly blocking objects behind them.
    if (FragColor.a < 0.01) {
        discard;
    }
}