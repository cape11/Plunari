#version 330 core

// INPUTS from the Vertex Shader
in vec4 fColor;          // Interpolated vertex color (used for tinting or direct color)
in vec2 fTexCoord;       // Interpolated texture coordinates
in float fLightValue;   // For terrain/sprites, this is light. For particles, this is ALPHA.

// UNIFORMS (Global variables from Java)
uniform sampler2D uTextureSampler;
uniform int uHasTexture;
uniform int uIsFont;
uniform int uIsSimpleUiElement;
uniform int uIsShadow;

// OUTPUT to the screen
out vec4 FragColor;

// CONSTANTS
const float MIN_AMBIENT = 0.2;

void main() {
    // Path 1: Shadow Rendering (No change)
    if (uIsShadow == 1) {
        FragColor = fColor;

    // Path 2: Font Rendering (No change)
    } else if (uIsFont == 1) {
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(fColor.rgb, fColor.a * alpha);

    // Path 3: Simple UI Element Rendering (No change)
    } else if (uIsSimpleUiElement == 1) {
        FragColor = fColor;

    // --- Path 4: Main World Object Rendering ---
    } else {
        vec4 materialColor;

        if (uHasTexture == 1) {
            // This is for textured objects like the player, tiles, and trees.
            vec4 texColor = texture(uTextureSampler, fTexCoord);
            materialColor = texColor * fColor; // Apply tint

            // Apply world lighting
            float lightIntensity = MIN_AMBIENT + (1.0 - MIN_AMBIENT) * fLightValue;
            FragColor = vec4(materialColor.rgb * lightIntensity, materialColor.a);

        } else {
            // --- THIS IS THE CORRECTED LOGIC FOR PARTICLES ---
            // This is for un-textured objects like particles.
            materialColor = fColor;

            // The particle's color is used directly, and the fLightValue,
            // which contains the calculated alpha, is used for the transparency.
            FragColor = vec4(materialColor.rgb, materialColor.a * fLightValue);
        }
    }

    // Discard fully transparent pixels. This is important for performance.
    if (FragColor.a < 0.01) {
        discard;
    }
}
