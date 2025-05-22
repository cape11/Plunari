#version 330 core

// Inputs from Vertex Shader
in vec4 fColor;
in vec2 fTexCoord;

// Uniforms
uniform sampler2D uTextureSampler;
uniform int uHasTexture; // Boolean (0 or 1)
uniform int uIsFont;     // Boolean (0 or 1)

// Output color
out vec4 FragColor;

void main() {
    vec4 baseColor = fColor; // Use vertex color as base

    if (uIsFont == 1) {
        // Font rendering: Sample texture (alpha channel for font) and modulate with fColor (text color)
        // The font texture is single channel (GL_R8), so sample .r and use it for alpha.
        // Color comes from fColor.
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(baseColor.rgb, baseColor.a * alpha);
    } else if (uHasTexture == 1) {
        // Textured object (terrain or sprite)
        vec4 texColor = texture(uTextureSampler, fTexCoord);
        FragColor = texColor * baseColor; // Modulate texture color with vertex color (tint)
    } else {
        // Non-textured object, use vertex color directly
        FragColor = baseColor;
    }

    // Basic alpha discard (optional, good for sprites with fully transparent parts)
    if (FragColor.a < 0.01) {
        discard;
    }
}
