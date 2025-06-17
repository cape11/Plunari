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
uniform int uIsShadow;             // NEW: Boolean (0 or 1) for rendering a shadow

// OUTPUT to the screen
out vec4 FragColor;

// CONSTANTS
const float MIN_AMBIENT = 0.2; // Minimum ambient light for world objects to be visible in darkness

void main() {
    // --- Path 1: Shadow Rendering ---
    // This is the highest priority. If it's a shadow, just use its vertex color.
    if (uIsShadow == 1) {
        FragColor = fColor; // The color (e.g., black with 0.4 alpha) is sent from Java.

    // --- Path 2: Font Rendering ---
    } else if (uIsFont == 1) {
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(fColor.rgb, fColor.a * alpha);

    // --- Path 3: Simple UI Element Rendering ---
    } else if (uIsSimpleUiElement == 1) {
        FragColor = fColor;

    // --- Path 4: World Object Rendering ---
    } else {
        vec4 materialColor;

        if (uHasTexture == 1) {
            vec4 texColor = texture(uTextureSampler, fTexCoord);
            materialColor = texColor * fColor;
        } else {
            materialColor = fColor;
        }

        float lightIntensity = MIN_AMBIENT + (1.0 - MIN_AMBIENT) * fLightValue;
        FragColor = vec4(materialColor.rgb * lightIntensity, materialColor.a);
    }

    // Discard fully transparent pixels.
    if (FragColor.a < 0.01) {
        discard;
    }
}
