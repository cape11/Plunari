#version 330 core

// INPUTS from the Vertex Shader
in vec4 fColor;
in vec2 fTexCoord;
in float fLightValue;

// UNIFORMS
uniform sampler2D uTextureSampler;
uniform int uHasTexture;
uniform int uIsFont;
uniform int uIsSimpleUiElement;
uniform int uIsShadow;
uniform vec3 u_ambientLightColor;

// OUTPUT to the screen
out vec4 FragColor;

void main() {
    // Path 1: Shadow Rendering (CORRECTED)
    if (uIsShadow == 1) {
        // Sample the sprite's texture to get its shape (alpha)
        float shapeAlpha = texture(uTextureSampler, fTexCoord).a;

        // If the pixel in the sprite's texture is transparent, discard this shadow pixel.
        if (shapeAlpha < 0.1) {
            discard;
        }

        // The final shadow color is the dark color passed from the vertex data (fColor).
        // The final alpha is a combination of the shadow's base transparency (fColor.a)
        // and the sprite's texture alpha, which creates softer edges.
        FragColor = vec4(fColor.rgb, fColor.a * shapeAlpha);

    // Path 2: Font Rendering
    } else if (uIsFont == 1) {
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(fColor.rgb, fColor.a * alpha);

    // Path 3: Simple UI Element Rendering
    } else if (uIsSimpleUiElement == 1) {
        FragColor = fColor;

    // --- Path 4: Main World Object Rendering ---
    } else {
        vec4 materialColor;

        if (uHasTexture == 1) {
            vec4 texColor = texture(uTextureSampler, fTexCoord);
            // Combine material color (from tint) and texture color
            materialColor = texColor * fColor;
            // Apply lighting
            vec3 lightColor = mix(u_ambientLightColor, vec3(1.0, 1.0, 1.0), fLightValue);
            FragColor = vec4(materialColor.rgb * lightColor, materialColor.a);
        } else {
            // For untextured colored objects
            FragColor = fColor;
        }
    }

    // Globally discard any pixel that is fully transparent
    if (FragColor.a < 0.01) {
        discard;
    }
}