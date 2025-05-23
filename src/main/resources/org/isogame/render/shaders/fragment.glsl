#version 330 core

// Inputs from Vertex Shader
in vec4 fColor;       // Interpolated vertex color
in vec2 fTexCoord;    // Interpolated texture coordinates
in float fLightValue;  // Interpolated normalized light value (0.0 to 1.0)

// Uniforms
uniform sampler2D uTextureSampler; // Texture unit for main texture or font atlas
uniform int uHasTexture;           // Boolean (0 or 1) if a texture should be sampled
uniform int uIsFont;               // Boolean (0 or 1) if rendering font (special handling)

// Output color
out vec4 FragColor;

// Lighting constant
const float MIN_AMBIENT = 0.2; // Minimum ambient light factor

void main() {
    vec4 baseColor = fColor;
    vec4 texColor = vec4(1.0);

    if (uIsFont == 1) {
        float alpha = texture(uTextureSampler, fTexCoord).r;
        FragColor = vec4(baseColor.rgb, baseColor.a * alpha);
        // Fonts are not affected by world lighting.
    } else {
        // Non-font rendering (terrain, sprites like player and trees)
        if (uHasTexture == 1) {
            texColor = texture(uTextureSampler, fTexCoord);
        }
        vec4 materialColor = texColor * baseColor;

        // Apply lighting using fLightValue for ALL non-font objects
        float lightIntensity = MIN_AMBIENT + (1.0 - MIN_AMBIENT) * fLightValue;
        FragColor = vec4(materialColor.rgb * lightIntensity, materialColor.a);
    }

    if (FragColor.a < 0.01) {
        discard;
    }
}
